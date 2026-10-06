import { useEffect, useState, useMemo, useRef } from 'react'
import { usePageTitle } from '../hooks/usePageTitle'
import { Link, NavLink } from 'react-router-dom'
import { getPlayers } from '../api/players'
import { getRandomizer, getLastMatch, submitMatch } from '../api/ranked'
import type { LastMatchPlayer, PlayerMatchStat } from '../api/ranked'
import type { PlayerWithStats } from '../types'
import { useAuth } from '../context/AuthContext'
import { getRankByDistributionFraction } from '../utils/rankIcons'
import { Footer } from '../components/Footer'
import rankedImg from '../assets/images/ranked.jpg'
import kyberSvg from '../assets/images/SVG/kyber.svg'
import beskarSvg from '../assets/images/SVG/beskar.svg'
import diamondSvg from '../assets/images/SVG/diamond.svg'
import platinumSvg from '../assets/images/SVG/platinum.svg'
import aurodiumSvg from '../assets/images/SVG/aurodium.svg'
import chromiumSvg from '../assets/images/SVG/chromium.svg'
import bronziumSvg from '../assets/images/SVG/bronzium.svg'

const RANK_ICON_URLS: Record<string, string> = {
  kyber: kyberSvg, beskar: beskarSvg, diamond: diamondSvg, platinum: platinumSvg,
  aurodium: aurodiumSvg, chromium: chromiumSvg, bronzium: bronziumSvg,
}

const FLAG_BASE = 'https://cdnjs.cloudflare.com/ajax/libs/flag-icon-css/2.8.0/flags/4x3'
const MIN_PLAYERS = 8

// --- Balance algorithm ---

type Subset = { team: PlayerWithStats[]; sum: number }

function getSubsetsOfExactSize(players: PlayerWithStats[], size: number): Subset[] {
  const result: Subset[] = []
  function backtrack(start: number, current: PlayerWithStats[], sum: number) {
    if (current.length === size) {
      result.push({ team: [...current], sum })
      return
    }
    for (let i = start; i < players.length; i++) {
      backtrack(i + 1, [...current, players[i]], sum + players[i].br)
    }
  }
  backtrack(0, [], 0)
  return result
}

function findClosestSubset(subsets: Subset[], target: number): Subset {
  let lo = 0
  let hi = subsets.length - 1
  let closest = subsets[0]
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2)
    const cur = subsets[mid]
    if (Math.abs(cur.sum - target) < Math.abs(closest.sum - target)) closest = cur
    if (cur.sum === target) return cur
    else if (cur.sum < target) lo = mid + 1
    else hi = mid - 1
  }
  return closest
}

function generateCombinations(players: PlayerWithStats[]) {
  const sorted = [...players].sort((a, b) => b.br - a.br || a.nickname.localeCompare(b.nickname))
  const teamSize = sorted.length / 2
  const totalRating = sorted.reduce((sum, p) => sum + p.br, 0)
  const halfTotal = totalRating / 2
  const left = sorted.slice(0, teamSize)
  const right = sorted.slice(teamSize)

  let bestDiff = Infinity
  let bestTeam1: PlayerWithStats[] = []

  for (let k = 0; k <= teamSize; k++) {
    const leftSubsets = getSubsetsOfExactSize(left, k)
    const rightSubsets = getSubsetsOfExactSize(right, teamSize - k)
    rightSubsets.sort((a, b) => a.sum - b.sum)
    for (const ls of leftSubsets) {
      const matchRight = findClosestSubset(rightSubsets, halfTotal - ls.sum)
      const diff = Math.abs(totalRating - 2 * (ls.sum + matchRight.sum))
      if (diff < bestDiff) {
        bestDiff = diff
        bestTeam1 = [...ls.team, ...matchRight.team]
      }
      if (bestDiff === 0) break
    }
    if (bestDiff === 0) break
  }

  const bestTeam2 = sorted.filter((p) => !bestTeam1.includes(p))
  const byBr = (a: PlayerWithStats, b: PlayerWithStats) => b.br - a.br || a.nickname.localeCompare(b.nickname)
  return { team1: [...bestTeam1].sort(byBr), team2: [...bestTeam2].sort(byBr) }
}

function avgBr(team: PlayerWithStats[]): number {
  return team.reduce((sum, p) => sum + p.br, 0) / team.length
}

function formatAvg(avg: number): string {
  return avg === Math.floor(avg) ? String(avg) : avg.toFixed(1)
}



// --- BR calculation ---

interface PlayerCalcData {
  id: number
  br: number
  score: number
  kills: number
  deaths: number
  faction: 'Rebel' | 'Imperial'
  outcome: 'Won' | 'Lost' | 'Draw'
  partnerless: boolean
}

interface PlayerCalcResult {
  id: number
  br: number
  score: number
  kills: number
  deaths: number
  faction: 'Rebel' | 'Imperial'
  outcome: 'Won' | 'Lost' | 'Draw'
  change: number
  perf: number
  NewBR: number
}

function getKdModifier(kills: number, deaths: number, teamAverageKD: number): number {
  const kd = kills / Math.max(deaths, 1)
  const relativeKD = teamAverageKD > 0 ? kd / teamAverageKD : 1
  const cappedKDImpact = Math.max(-1, Math.min(1, relativeKD - 1))
  return 1 + 0.05 * cappedKDImpact
}

function teamAvgKD(team: PlayerCalcData[]): number {
  return team.reduce((s, p) => s + p.kills / Math.max(p.deaths, 1), 0) / team.length
}

function effectiveScore(p: PlayerCalcData, avgKD: number): number {
  const partnerlessScore = p.partnerless ? Math.round(p.score * 1.1) : p.score
  return Math.round(partnerlessScore * getKdModifier(p.kills, p.deaths, avgKD))
}

function calcWin(team: PlayerCalcData[], teamBR: number, teamDelta: number): PlayerCalcResult[] {
  const teamtot = team.reduce((s, p) => s + p.score, 0)
  const avgKD = teamAvgKD(team)
  return team.map((p) => {
    const exCarry = p.br / teamBR
    const ps = effectiveScore(p, avgKD)
    const acCarry = teamtot === 0 ? 1 : (ps / teamtot) / (1 / team.length) / exCarry
    const playerDelta = Math.round(teamDelta - (1 - acCarry) * teamDelta)
    return { id: p.id, br: p.br, score: p.score, kills: p.kills, deaths: p.deaths, faction: p.faction, outcome: p.outcome, change: playerDelta, perf: parseFloat(acCarry.toFixed(2)), NewBR: p.br + playerDelta }
  })
}

function calcLoss(team: PlayerCalcData[], teamBR: number, teamDelta: number, scoreDiff: number): PlayerCalcResult[] {
  const teamtot = team.reduce((s, p) => s + p.score, 0)
  const objectiveBonus = [0, 3, 2, 1, 0, -1][Math.min(scoreDiff, 5)]
  const avgKD = teamAvgKD(team)
  return team.map((p) => {
    const exCarry = p.br / teamBR
    const ps = effectiveScore(p, avgKD)
    const acCarry = teamtot === 0 ? 1 : (ps / teamtot) / (1 / team.length) / exCarry
    const playerDelta = Math.round(teamDelta + 1.25 * (1 - acCarry) * teamDelta) + objectiveBonus
    return { id: p.id, br: p.br, score: p.score, kills: p.kills, deaths: p.deaths, faction: p.faction, outcome: p.outcome, change: playerDelta, perf: parseFloat(acCarry.toFixed(2)), NewBR: p.br + playerDelta }
  })
}

function calcDraw(team: PlayerCalcData[], teamBR: number, teamDelta: number): PlayerCalcResult[] {
  const teamtot = team.reduce((s, p) => s + p.score, 0)
  const drawBonus = 4
  const avgKD = teamAvgKD(team)
  return team.map((p) => {
    const exCarry = p.br / teamBR
    const ps = effectiveScore(p, avgKD)
    const acCarry = teamtot === 0 ? 1 : (ps / teamtot) / (1 / team.length) / exCarry
    const playerDelta = Math.round(teamDelta - (1 - acCarry) * 20) + drawBonus
    return { id: p.id, br: p.br, score: p.score, kills: p.kills, deaths: p.deaths, faction: p.faction, outcome: p.outcome, change: playerDelta, perf: parseFloat(acCarry.toFixed(2)), NewBR: p.br + playerDelta }
  })
}

// --- Component ---

export function RankedPage() {
  usePageTitle('Ranked')
  const { user } = useAuth()
  const canSubmit = user?.role === 'admin' || user?.role === 'editor' || user?.role === 'supervisor'

  const [allPlayers, setAllPlayers] = useState<PlayerWithStats[]>([])
  const [selected, setSelected] = useState<PlayerWithStats[]>([])
  const [rebels, setRebels] = useState<PlayerWithStats[]>([])
  const [imperials, setImperials] = useState<PlayerWithStats[]>([])
  const [rebelAvg, setRebelAvg] = useState(0)
  const [imperialAvg, setImperialAvg] = useState(0)
  const [teamsShown, setTeamsShown] = useState(false)
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [currentMap, setCurrentMap] = useState('')
  const [currentRule, setCurrentRule] = useState('')

  // Score inputs (admin/editor/supervisor only)
  const [rebelScore, setRebelScore] = useState<string>('')
  const [imperialScore, setImperialScore] = useState<string>('')
  // Per-player score/kills/deaths inputs: key = `${faction}-${index}`
  const [playerScores, setPlayerScores] = useState<Record<string, string>>({})
  const [playerKills, setPlayerKills] = useState<Record<string, string>>({})
  const [playerDeaths, setPlayerDeaths] = useState<Record<string, string>>({})
  // Partnerless checkboxes: at most one per faction
  const [partnerlessRebel, setPartnerlessRebel] = useState<number | null>(null)
  const [partnerlessImperial, setPartnerlessImperial] = useState<number | null>(null)
  // Calculated results
  const [calcResults, setCalcResults] = useState<{ rebels: PlayerCalcResult[]; imperials: PlayerCalcResult[] } | null>(null)
  const [calculated, setCalculated] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [toast, setToast] = useState(false)

  const rebelCircleRef = useRef<SVGCircleElement>(null)
  const imperialCircleRef = useRef<SVGCircleElement>(null)
  const pendingMatchRef = useRef<{ map: string; teamSize: number; mvpId: number; rebelResult: number; imperialResult: number; rule: string } | null>(null)

  useEffect(() => {
    getPlayers()
      .then((data) => setAllPlayers(Array.isArray(data) ? data : []))
      .catch(() => setError('Could not load players'))
      .finally(() => setLoading(false))
  }, [])

  const rankFractionMap = useMemo(() => {
    const ranked = [...allPlayers]
      .filter((p) => p.played >= 5)
      .sort((a, b) => b.br - a.br || a.nickname.localeCompare(b.nickname))
    const total = ranked.length || 1
    const map = new Map<number, number>()
    ranked.forEach((p, i) => map.set(p.id, (i + 1) / total))
    return map
  }, [allPlayers])

  function getRankIcon(player: PlayerWithStats): string | null {
    if (player.played < 5) return null
    const fraction = rankFractionMap.get(player.id)
    if (fraction === undefined) return null
    return getRankByDistributionFraction(fraction)
  }

  const available = useMemo(() => {
    const selectedIds = new Set(selected.map((p) => p.id))
    return [...allPlayers]
      .filter((p) => !selectedIds.has(p.id))
      .sort((a, b) => {
        if (a.played >= 5 && b.played < 5) return -1
        if (a.played < 5 && b.played >= 5) return 1
        return b.br - a.br || a.nickname.localeCompare(b.nickname)
      })
  }, [allPlayers, selected])

  const filteredAvailable = useMemo(() => {
    if (!search.trim()) return available
    const upper = search.toUpperCase()
    return available.filter((p) =>
      [p.nickname, p.br].join(' ').toUpperCase().includes(upper),
    )
  }, [available, search])

  function runBalance(players: PlayerWithStats[]) {
    const { team1, team2 } = generateCombinations(players)
    setRebels(team1)
    setImperials(team2)
    setRebelAvg(avgBr(team1))
    setImperialAvg(avgBr(team2))
  }

  function resetScoreState() {
    setRebelScore('')
    setImperialScore('')
    setPlayerScores({})
    setPlayerKills({})
    setPlayerDeaths({})
    setPartnerlessRebel(null)
    setPartnerlessImperial(null)
    setCalcResults(null)
    setCalculated(false)
    if (rebelCircleRef.current) rebelCircleRef.current.setAttribute('stroke-dasharray', 'calc(0 * 31.4 / 100) 31.4')
    if (imperialCircleRef.current) imperialCircleRef.current.setAttribute('stroke-dasharray', 'calc(0 * 31.4 / 100) 31.4')
  }

  function handleSelect(player: PlayerWithStats) {
    if (selected.length >= 12) {
      setError('Max players reached')
      return
    }
    setError('')
    const next = [...selected, player]
    setSelected(next)
    if (teamsShown && next.length >= MIN_PLAYERS && next.length % 2 === 0) {
      runBalance(next)
      resetScoreState()
    }
  }

  function handleRemove(player: PlayerWithStats) {
    setError('')
    const next = selected.filter((p) => p.id !== player.id)
    setSelected(next)
    if (teamsShown && next.length >= MIN_PLAYERS && next.length % 2 === 0) {
      runBalance(next)
      resetScoreState()
    } else if (next.length < MIN_PLAYERS) {
      setRebels([])
      setImperials([])
      setRebelAvg(0)
      setImperialAvg(0)
      resetScoreState()
    }
  }

  async function handleBalance() {
    if (selected.length < MIN_PLAYERS) {
      setError(`${MIN_PLAYERS} players minimum to start a game`)
      return
    }
    if (selected.length % 2 !== 0) {
      setError('Odd number of players')
      return
    }
    setError('')
    setTeamsShown(true)
    runBalance(selected)
    resetScoreState()
    try {
      const rand = await getRandomizer()
      setCurrentMap(rand.map)
      setCurrentRule(rand.rule)
    } catch {
      // silent fail
    }
  }

  async function handleLoadLastMatch() {
    setError('')
    try {
      const data: LastMatchPlayer[] = await getLastMatch()
      if (data.length === 0) {
        setError('Could not find any match')
        return
      }
      const asPlayerWithStats: PlayerWithStats[] = data.map((p) => ({
        ...p,
        dzrating: 0,
        rating: p.rating,
      }))
      setSelected(asPlayerWithStats)
      const next = asPlayerWithStats
      if (next.length >= MIN_PLAYERS && next.length % 2 === 0) {
        setTeamsShown(true)
        runBalance(next)
        resetScoreState()
        try {
          const rand = await getRandomizer()
          setCurrentMap(rand.map)
          setCurrentRule(rand.rule)
        } catch {
          // silent fail
        }
      }
    } catch {
      setError('Could not find any match')
    }
  }

  function handleReset() {
    setSelected([])
    setRebels([])
    setImperials([])
    setRebelAvg(0)
    setImperialAvg(0)
    setTeamsShown(false)
    setError('')
    setSearch('')
    setCurrentMap('')
    setCurrentRule('')
    resetScoreState()
  }

  function updateCircle(ref: React.RefObject<SVGCircleElement | null>, value: string) {
    const v = Math.min(Math.max(parseInt(value) || 0, 0), 5)
    if (ref.current) {
      ref.current.setAttribute('stroke-dasharray', `calc(${v * 20} * 31.4 / 100) 31.4`)
    }
  }

  function handleRebelScoreChange(v: string) {
    setRebelScore(v)
    updateCircle(rebelCircleRef, v)
    setCalculated(false)
    setCalcResults(null)
  }

  function handleImperialScoreChange(v: string) {
    setImperialScore(v)
    updateCircle(imperialCircleRef, v)
    setCalculated(false)
    setCalcResults(null)
  }

  function handlePlayerScoreChange(key: string, value: string) {
    setPlayerScores((prev) => ({ ...prev, [key]: value }))
    setCalculated(false)
    setCalcResults(null)
  }

  function handlePlayerKillsChange(key: string, value: string) {
    setPlayerKills((prev) => ({ ...prev, [key]: value }))
    setCalculated(false)
    setCalcResults(null)
  }

  function handlePlayerDeathsChange(key: string, value: string) {
    setPlayerDeaths((prev) => ({ ...prev, [key]: value }))
    setCalculated(false)
    setCalcResults(null)
  }

  function handlePartnerlessRebel(idx: number) {
    setPartnerlessRebel((prev) => (prev === idx ? null : idx))
    setCalculated(false)
    setCalcResults(null)
  }

  function handlePartnerlessImperial(idx: number) {
    setPartnerlessImperial((prev) => (prev === idx ? null : idx))
    setCalculated(false)
    setCalcResults(null)
  }

  function isNonNegativeInteger(value: string): boolean {
    return /^\d+$/.test(value.trim())
  }

  function handleCalculate() {
    setError('')

    if (!isNonNegativeInteger(rebelScore) || !isNonNegativeInteger(imperialScore)) {
      setError('Invalid result')
      return
    }
    const rebelResult = parseInt(rebelScore, 10)
    const imperialResult = parseInt(imperialScore, 10)
    if (rebelResult > 5 || imperialResult > 5) {
      setError('Invalid result')
      return
    }

    const teamSize = rebels.length
    for (let i = 0; i < teamSize; i++) {
      const rsStr = playerScores[`rebel-${i}`] ?? ''
      const isStr = playerScores[`imperial-${i}`] ?? ''
      const rkStr = playerKills[`rebel-${i}`] ?? ''
      const rdStr = playerDeaths[`rebel-${i}`] ?? ''
      const ikStr = playerKills[`imperial-${i}`] ?? ''
      const idStr = playerDeaths[`imperial-${i}`] ?? ''
      if (!isNonNegativeInteger(rsStr) || !isNonNegativeInteger(isStr)) {
        setError('Invalid score input')
        return
      }
      if (!isNonNegativeInteger(rkStr) || !isNonNegativeInteger(rdStr) ||
          !isNonNegativeInteger(ikStr) || !isNonNegativeInteger(idStr)) {
        setError('Invalid kills/deaths input')
        return
      }
      const rs = parseInt(rsStr, 10)
      const is = parseInt(isStr, 10)
      if (rs > 100000 || is > 100000) {
        setError('Invalid score input')
        return
      }
      const rk = parseInt(rkStr, 10), rd = parseInt(rdStr, 10)
      const ik = parseInt(ikStr, 10), id = parseInt(idStr, 10)
      if (rk > 100000 || rd > 100000 || ik > 100000 || id > 100000) {
        setError('Invalid kills/deaths input')
        return
      }
    }

    let rebelOutcome: 'Won' | 'Lost' | 'Draw'
    let imperialOutcome: 'Won' | 'Lost' | 'Draw'
    if (rebelResult > imperialResult) {
      rebelOutcome = 'Won'; imperialOutcome = 'Lost'
    } else if (rebelResult < imperialResult) {
      rebelOutcome = 'Lost'; imperialOutcome = 'Won'
    } else {
      rebelOutcome = 'Draw'; imperialOutcome = 'Draw'
    }

    const scoreDiff = Math.abs(rebelResult - imperialResult)
    const rebelBR = Math.round(rebelAvg)
    const imperialBR = Math.round(imperialAvg)

    const expRebel = 1 / (1 + Math.pow(10, (imperialBR - rebelBR) / 400))
    const expImperial = 1 / (1 + Math.pow(10, (rebelBR - imperialBR) / 400))

    const rebelPlayers: PlayerCalcData[] = rebels.map((p, i) => ({
      id: p.id,
      br: p.br,
      score: parseInt(playerScores[`rebel-${i}`] ?? '0') || 0,
      kills: parseInt(playerKills[`rebel-${i}`] ?? '0') || 0,
      deaths: parseInt(playerDeaths[`rebel-${i}`] ?? '0') || 0,
      faction: 'Rebel',
      outcome: rebelOutcome,
      partnerless: partnerlessRebel === i,
    }))
    const imperialPlayers: PlayerCalcData[] = imperials.map((p, i) => ({
      id: p.id,
      br: p.br,
      score: parseInt(playerScores[`imperial-${i}`] ?? '0') || 0,
      kills: parseInt(playerKills[`imperial-${i}`] ?? '0') || 0,
      deaths: parseInt(playerDeaths[`imperial-${i}`] ?? '0') || 0,
      faction: 'Imperial',
      outcome: imperialOutcome,
      partnerless: partnerlessImperial === i,
    }))

    let rebelResults: PlayerCalcResult[]
    let imperialResults: PlayerCalcResult[]

    if (rebelOutcome === 'Won') {
      const delta = Math.round(40 * (1 - expRebel))
      rebelResults = calcWin(rebelPlayers, rebelBR, delta)
      imperialResults = calcLoss(imperialPlayers, imperialBR, -delta, scoreDiff)
    } else if (imperialOutcome === 'Won') {
      const delta = Math.round(40 * (1 - expImperial))
      rebelResults = calcLoss(rebelPlayers, rebelBR, -delta, scoreDiff)
      imperialResults = calcWin(imperialPlayers, imperialBR, delta)
    } else {
      const rebelDelta = Math.round(40 * (0.5 - expRebel))
      const imperialDelta = -rebelDelta
      rebelResults = calcDraw(rebelPlayers, rebelBR, rebelDelta)
      imperialResults = calcDraw(imperialPlayers, imperialBR, imperialDelta)
    }

    // Determine MVP
    let highestRebel = 0, highestImperial = 0
    let mvpRebelId = 0, mvpImperialId = 0
    let deltaRebel = 0, deltaImperial = 0
    for (const r of rebelResults) {
      if (r.score > highestRebel) { highestRebel = r.score; mvpRebelId = r.id; deltaRebel = r.change }
    }
    for (const r of imperialResults) {
      if (r.score > highestImperial) { highestImperial = r.score; mvpImperialId = r.id; deltaImperial = r.change }
    }
    let mvpId: number
    if (highestImperial > highestRebel) mvpId = mvpImperialId
    else if (highestRebel > highestImperial) mvpId = mvpRebelId
    else {
      if (rebelOutcome === 'Won') mvpId = mvpRebelId
      else if (imperialOutcome === 'Won') mvpId = mvpImperialId
      else mvpId = deltaRebel > deltaImperial ? mvpRebelId : mvpImperialId
    }

    setCalcResults({ rebels: rebelResults, imperials: imperialResults })
    setCalculated(true)
    pendingMatchRef.current = { map: currentMap, teamSize, mvpId, rebelResult, imperialResult, rule: currentRule }
  }

  async function handleSubmit() {
    if (!calculated) {
      setError('Calculate scores first')
      return
    }
    const md = pendingMatchRef.current
    if (!md || !calcResults) return

    setSubmitting(true)
    setError('')
    try {
      const toStat = (r: PlayerCalcResult): PlayerMatchStat => ({
        id: r.id,
        faction: r.faction,
        outcome: r.outcome,
        score: r.score,
        perf: r.perf,
        change: r.change,
        NewBR: r.NewBR,
        kills: r.kills,
        deaths: r.deaths,
      })

      const { nextMap, nextRule } = await submitMatch({
        matchData: [md.map, md.teamSize, md.mvpId, md.rebelResult, md.imperialResult, md.rule],
        rebels: calcResults.rebels.map(toStat),
        imperials: calcResults.imperials.map(toStat),
      })

      setCurrentMap(nextMap)
      setCurrentRule(nextRule)

      // Re-fetch players to get updated BR values
      const freshPlayers = await getPlayers()
      const fresh = Array.isArray(freshPlayers) ? freshPlayers : []
      const byId = new Map(fresh.map((p) => [p.id, p]))
      setAllPlayers(fresh)
      setSelected((prev) => prev.map((p) => byId.get(p.id) ?? p))
      setRebels([])
      setImperials([])
      setRebelAvg(0)
      setImperialAvg(0)
      resetScoreState()
      setTeamsShown(false)

      setToast(true)
      setTimeout(() => setToast(false), 4000)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Submit failed')
    } finally {
      setSubmitting(false)
    }
  }

  function availableRow(player: PlayerWithStats, index: number) {
    const iconName = getRankIcon(player)
    const iconSrc = iconName ? RANK_ICON_URLS[iconName] : null
    return (
      <tr key={player.id} className="sound__hover sound__click" style={{ cursor: 'pointer' }} onClick={() => handleSelect(player)}>
        <td className="table__cell table__player table__xxl">
          <ul className="list list__player">
            <li>{index + 1}</li>
            <li><img src={`${FLAG_BASE}/${player.nation || 'aq'}.svg`} alt={player.nation} /></li>
            <li>
              {player.nickname}
              {iconSrc && <img height={27} src={iconSrc} alt="" style={{ verticalAlign: 'middle', marginLeft: 4 }} />}
            </li>
          </ul>
        </td>
        <td className="table__cell table__xxs">{player.br}</td>
      </tr>
    )
  }

  function rebelRow(player: PlayerWithStats, index: number) {
    if (canSubmit) {
      return (
        <tr key={player.id}>
          <td className="table__cell table__player table__xxl">
            <div className="partnerless">
              <input
                className="partnerless__input"
                type="checkbox"
                id={`rebel${index}`}
                checked={partnerlessRebel === index}
                onChange={() => handlePartnerlessRebel(index)}
                tabIndex={-1}
              />
              <label htmlFor={`rebel${index}`} className="partnerless__content">
                <span className="partnerless__text">{player.nickname}</span>
                <span className="partnerless__icon" />
              </label>
            </div>
          </td>
          <td className="table__cell table__md">
            <input
              type="number"
              min={0}
              max={999999}
              className="table__input"
              value={playerScores[`rebel-${index}`] ?? ''}
              onChange={(e) => handlePlayerScoreChange(`rebel-${index}`, e.target.value)}
            />
          </td>
          <td className="table__cell table__xs">
            <input
              type="number"
              min={0}
              max={999999}
              className="table__input"
              value={playerKills[`rebel-${index}`] ?? ''}
              onChange={(e) => handlePlayerKillsChange(`rebel-${index}`, e.target.value)}
            />
          </td>
          <td className="table__cell table__xs">
            <input
              type="number"
              min={0}
              max={999999}
              className="table__input"
              value={playerDeaths[`rebel-${index}`] ?? ''}
              onChange={(e) => handlePlayerDeathsChange(`rebel-${index}`, e.target.value)}
            />
          </td>
          <td className="table__cell table__xs">{calcResults ? calcResults.rebels[index]?.change ?? 0 : 0}</td>
          <td className="table__cell table__md">{calcResults ? calcResults.rebels[index]?.NewBR ?? player.br : player.br}</td>
        </tr>
      )
    }
    return (
      <tr key={player.id}>
        <td className="table__cell table__player table__xxl">
          <ul className="list list__player">
            <li><img src={`${FLAG_BASE}/${player.nation || 'aq'}.svg`} alt={player.nation} /></li>
            <li>{player.nickname}</li>
          </ul>
        </td>
        <td className="table__cell table__xxs">{player.br}</td>
      </tr>
    )
  }

  function imperialRow(player: PlayerWithStats, index: number) {
    if (canSubmit) {
      return (
        <tr key={player.id}>
          <td className="table__cell table__player table__xxl">
            <div className="partnerless">
              <input
                className="partnerless__input"
                type="checkbox"
                id={`imperial${index}`}
                checked={partnerlessImperial === index}
                onChange={() => handlePartnerlessImperial(index)}
                tabIndex={-1}
              />
              <label htmlFor={`imperial${index}`} className="partnerless__content">
                <span className="partnerless__text">{player.nickname}</span>
                <span className="partnerless__icon" />
              </label>
            </div>
          </td>
          <td className="table__cell table__md">
            <input
              type="number"
              min={0}
              max={999999}
              className="table__input"
              value={playerScores[`imperial-${index}`] ?? ''}
              onChange={(e) => handlePlayerScoreChange(`imperial-${index}`, e.target.value)}
            />
          </td>
          <td className="table__cell table__xs">
            <input
              type="number"
              min={0}
              max={999999}
              className="table__input"
              value={playerKills[`imperial-${index}`] ?? ''}
              onChange={(e) => handlePlayerKillsChange(`imperial-${index}`, e.target.value)}
            />
          </td>
          <td className="table__cell table__xs">
            <input
              type="number"
              min={0}
              max={999999}
              className="table__input"
              value={playerDeaths[`imperial-${index}`] ?? ''}
              onChange={(e) => handlePlayerDeathsChange(`imperial-${index}`, e.target.value)}
            />
          </td>
          <td className="table__cell table__xs">{calcResults ? calcResults.imperials[index]?.change ?? 0 : 0}</td>
          <td className="table__cell table__md">{calcResults ? calcResults.imperials[index]?.NewBR ?? player.br : player.br}</td>
        </tr>
      )
    }
    return (
      <tr key={player.id}>
        <td className="table__cell table__player table__xxl">
          <ul className="list list__player">
            <li><img src={`${FLAG_BASE}/${player.nation || 'aq'}.svg`} alt={player.nation} /></li>
            <li>{player.nickname}</li>
          </ul>
        </td>
        <td className="table__cell table__xxs">{player.br}</td>
      </tr>
    )
  }

  return (
    <>
      {toast && (
        <div className="toast toast--success">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12" />
          </svg>
          Match submitted
        </div>
      )}
      <main>
        <section className="section section--balance">
          <div className="container">
            <div>
              <nav className="breadcrumb">
                <NavLink end className="link nav__element--link sound__hover sound__click" to="/">Play</NavLink>
                <span className="breadcrumb__separator">/</span>
                <span className="breadcrumb__current">Ranked</span>
              </nav>
              <h1 className="title title--large" style={{ whiteSpace: 'nowrap' }}>Ranked pug</h1>
            </div>

            <div className="settings">
              <img className="ranked" src={rankedImg} alt="ranked" />
              <div className="table">
                <input
                  type="text"
                  className="input searchbar__table sound__hover"
                  placeholder="Search for names, br ..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
                <div className="table__list">
                  <div className="table__info">
                    <h2 className="title title--small">Players</h2>
                    <span className="table__counter">{filteredAvailable.length}</span>
                  </div>
                  <div className="table__hidden table__scroll">
                    <table className="table__content table__search">
                      <thead className="table__head table__sticky">
                        <tr>
                          <th className="head__cell head__player head__xxl">Name</th>
                          <th className="head__cell head__xxs">BR</th>
                        </tr>
                      </thead>
                      <tbody className="table__body table__dynamic">
                        {loading ? (
                          <tr><td colSpan={2} className="table__cell">Loading…</td></tr>
                        ) : filteredAvailable.length === 0 ? (
                          <tr><td colSpan={2} className="table__cell">No players found.</td></tr>
                        ) : (
                          filteredAvailable.map((p, i) => availableRow(p, i))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
              <div className="settings__console">
                <div className="settings__mode">
                  <div className="settings__button">
                    <h4 className="title title--extrasmall">Create&nbsp;teams</h4>
                    <div className="button__teams">
                      <button
                        type="button"
                        className="btn btn--grey sound__hover sound__click"
                        onClick={handleBalance}
                      >
                        Balanced
                      </button>
                      <button
                        type="button"
                        className="btn btn--grey sound__hover sound__click"
                        onClick={handleLoadLastMatch}
                      >
                        <span>Last&nbsp;match</span>
                      </button>
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn--width btn--reset sound__hover sound__click"
                  onClick={handleReset}
                >
                  <span>Reset</span>
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 29 29">
                    <path fill="#000" d="M25.5,16.58c0,6.07-4.93,11-11,11S3.5,22.65,3.5,16.58c0-.55,.45-1,1-1s1,.45,1,1c0,4.97,4.04,9,9,9s9-4.03,9-9c0-4.64-3.54-8.47-8.05-8.95l2.56,2.56c.39,.39,.39,1.03,0,1.42-.19,.19-.45,.29-.71,.29s-.5-.1-.7-.29l-4.25-4.24c-.39-.4-.39-1.03,0-1.42L16.6,1.71c.39-.39,1.02-.39,1.41,0,.39,.39,.39,1.02,0,1.41l-2.5,2.51c5.59,.5,9.99,5.23,9.99,10.95Z" />
                  </svg>
                </button>
              </div>
            </div>

            {/* Selected players */}
            <div className="player">
              <div className="player__play">
                <div className="player__info">
                  <h3 className="title title--small">Players</h3>
                  <span className="player__counter">
                    <span className="player__counter--total">{selected.length}</span>/12
                  </span>
                </div>
                <div className="table__hidden">
                  <table className="table__content">
                    <thead className="table__head">
                      <tr>
                        <th className="head__cell head__player">Name</th>
                        <th className="head__cell head__rating">BR</th>
                      </tr>
                    </thead>
                    <tbody className="table__body player__list">
                      {selected.map((player) => (
                        <tr
                          key={player.id}
                          className="sound__hover sound__delete"
                          style={{ cursor: 'pointer' }}
                          onClick={() => handleRemove(player)}
                        >
                          <td className="table__cell table__player table__xxl">
                            <ul className="list list__player">
                              <li><img src={`${FLAG_BASE}/${player.nation || 'aq'}.svg`} alt={player.nation} /></li>
                              <li>
                                {player.nickname}
                                {(() => { const n = getRankIcon(player); const s = n ? RANK_ICON_URLS[n] : null; return s ? <img height={27} src={s} alt="" style={{ verticalAlign: 'middle', marginLeft: 4 }} /> : null })()}
                              </li>
                            </ul>
                          </td>
                          <td className="table__cell table__xxs">{player.br}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
              {error && (
                <div className="error">
                  <span className="error__message">{error}</span>
                </div>
              )}
            </div>

            {/* Teams */}
            <div className="team__gamemode">
              <div className="team">
                <div className="team__1">
                  <div className="team__rebel">
                    <div className="team__info">
                      <h3 className="title title--small">Rebel</h3>
                      <span className="team__counter">
                        <span className="team__counter--rebel">{rebels.length}</span>/6
                      </span>
                    </div>
                    <div className="table__hidden">
                      <table className="table__content">
                        <thead className="table__head">
                          <tr>
                            <th className="head__cell head__player head__xxl">Name</th>
                            {canSubmit ? (
                              <>
                                <th className="head__cell head__md">Score</th>
                                <th className="head__cell head__xs">Kills</th>
                                <th className="head__cell head__xs">Deaths</th>
                                <th className="head__cell head__xs">&Delta;BR</th>
                                <th className="head__cell head__md">NBR</th>
                              </>
                            ) : (
                              <th className="head__cell head__xxs">BR</th>
                            )}
                          </tr>
                        </thead>
                        <tbody className="table__body">
                          {rebels.map((p, i) => rebelRow(p, i))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                  <div className="team__rebel--score">
                    <h3 className="title title--extrasmall">Avg. BR</h3>
                    <span className="team__score">{rebels.length > 0 ? formatAvg(rebelAvg) : '0'}</span>
                  </div>
                </div>
                <div className="team__2">
                  <div className="team__card">
                    <div className="team__imperial">
                      <div className="team__info">
                        <h3 className="title title--small">Imperial</h3>
                        <span className="team__counter">
                          <span className="team__counter--imperial">{imperials.length}</span>/6
                        </span>
                      </div>
                      <div className="table__hidden">
                        <table className="table__content">
                          <thead className="table__head">
                            <tr>
                              <th className="head__cell head__player head__xxl">Name</th>
                              {canSubmit ? (
                                <>
                                  <th className="head__cell head__md">Score</th>
                                  <th className="head__cell head__xs">Kills</th>
                                  <th className="head__cell head__xs">Deaths</th>
                                  <th className="head__cell head__xs">&Delta;BR</th>
                                  <th className="head__cell head__md">NBR</th>
                                </>
                              ) : (
                                <th className="head__cell head__xxs">BR</th>
                              )}
                            </tr>
                          </thead>
                          <tbody className="table__body">
                            {imperials.map((p, i) => imperialRow(p, i))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                    <div className="team__imperial--score">
                      <h3 className="title title--extrasmall">Avg. BR</h3>
                      <span className="team__score">{imperials.length > 0 ? formatAvg(imperialAvg) : '0'}</span>
                    </div>
                  </div>
                </div>

                {canSubmit && (
                  <div className="center">
                    <div className="vertical">
                      <div className="result">
                        <div className="result--rebel">
                          <input
                            className="score score--rebel"
                            type="number"
                            min={0}
                            max={5}
                            value={rebelScore}
                            onChange={(e) => handleRebelScoreChange(e.target.value)}
                          />
                          <svg width="40px" height="40px" viewBox="0 0 20 20">
                            <circle r="10" cx="10" cy="10" fill="#0f98d380" />
                            <circle
                              ref={rebelCircleRef}
                              className="circle"
                              r="5"
                              cx="10"
                              cy="10"
                              fill="transparent"
                              stroke="#0f98d3"
                              strokeWidth="10"
                              strokeDasharray="calc(0 * 31.4 / 100) 31.4"
                              transform="rotate(-90) translate(-20)"
                            />
                          </svg>
                        </div>
                        <div className="result--imperial">
                          <input
                            className="score score--imperial"
                            type="number"
                            min={0}
                            max={5}
                            value={imperialScore}
                            onChange={(e) => handleImperialScoreChange(e.target.value)}
                          />
                          <svg width="40px" height="40px" viewBox="0 0 20 20">
                            <circle r="10" cx="10" cy="10" fill="#da180f80" />
                            <circle
                              ref={imperialCircleRef}
                              className="circle"
                              r="5"
                              cx="10"
                              cy="10"
                              fill="transparent"
                              stroke="#da180f"
                              strokeWidth="10"
                              strokeDasharray="calc(0 * 31.4 / 100) 31.4"
                              transform="rotate(-90) translate(-20)"
                            />
                          </svg>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              <div className="game">
                <div className="game__settings">
                  <h3 className="title title--extrasmall">Map</h3>
                  <span className="maps">{currentMap}</span>
                </div>
                <div className="game__settings">
                  <h3 className="title title--extrasmall">Rule</h3>
                  <span className="rules">{currentRule}</span>
                </div>
                {canSubmit && (
                  <>
                    <button
                      type="button"
                      className="btn btn--grey sound__hover sound__click"
                      onClick={handleCalculate}
                      disabled={rebels.length === 0}
                    >
                      Calculate
                    </button>
                    <button
                      type="button"
                      className="btn btn--grey sound__hover sound__click"
                      onClick={handleSubmit}
                      disabled={!calculated || submitting}
                    >
                      Submit
                    </button>
                  </>
                )}
              </div>
            </div>

            <div className="back">
              <Link className="link btn btn--transparent sound__hover sound__delete" to="/">
                Back
              </Link>
            </div>
          </div>
        </section>
      </main>
      <Footer />
    </>
  )
}
