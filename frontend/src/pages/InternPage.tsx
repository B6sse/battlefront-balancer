import { useEffect, useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { getPlayers } from '../api/players'
import type { PlayerWithStats } from '../types'
import { RatingBadge } from '../components/RatingBadge'
import internImg from '../assets/images/intern.jpg'

const FLAG_BASE = 'https://cdnjs.cloudflare.com/ajax/libs/flag-icon-css/2.8.0/flags/4x3'
const MIN_PLAYERS = 4

type RatingKey = 'rating' | 'dzrating'

// --- Balance algorithm (ported from script.js) ---

type Subset = { team: PlayerWithStats[]; sum: number }

function getSubsetsOfExactSize(players: PlayerWithStats[], key: RatingKey, size: number): Subset[] {
  const result: Subset[] = []
  function backtrack(start: number, current: PlayerWithStats[], sum: number) {
    if (current.length === size) {
      result.push({ team: [...current], sum })
      return
    }
    for (let i = start; i < players.length; i++) {
      backtrack(i + 1, [...current, players[i]], sum + players[i][key])
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

function generateCombinations(players: PlayerWithStats[], key: RatingKey) {
  const sorted = [...players].sort((a, b) => b[key] - a[key] || a.nickname.localeCompare(b.nickname))
  const teamSize = sorted.length / 2
  const totalRating = sorted.reduce((sum, p) => sum + p[key], 0)
  const halfTotal = totalRating / 2
  const left = sorted.slice(0, teamSize)
  const right = sorted.slice(teamSize)

  let bestDiff = Infinity
  let bestTeam1: PlayerWithStats[] = []

  for (let k = 0; k <= teamSize; k++) {
    const leftSubsets = getSubsetsOfExactSize(left, key, k)
    const rightSubsets = getSubsetsOfExactSize(right, key, teamSize - k)
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
  const byRating = (a: PlayerWithStats, b: PlayerWithStats) => b[key] - a[key] || a.nickname.localeCompare(b.nickname)
  return { team1: [...bestTeam1].sort(byRating), team2: [...bestTeam2].sort(byRating) }
}

function avgRating(team: PlayerWithStats[], key: RatingKey): number {
  return team.reduce((sum, p) => sum + p[key], 0) / team.length
}

function formatAvg(avg: number): string {
  return avg === Math.floor(avg) ? String(avg) : avg.toFixed(1)
}

// --- Component ---

export function InternPage() {
  const [allPlayers, setAllPlayers] = useState<PlayerWithStats[]>([])
  const [selected, setSelected] = useState<PlayerWithStats[]>([])
  const [rebels, setRebels] = useState<PlayerWithStats[]>([])
  const [imperials, setImperials] = useState<PlayerWithStats[]>([])
  const [rebelAvg, setRebelAvg] = useState(0)
  const [imperialAvg, setImperialAvg] = useState(0)
  const [teamsShown, setTeamsShown] = useState(false)
  const [isDZ, setIsDZ] = useState(false)
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const ratingKey: RatingKey = isDZ ? 'dzrating' : 'rating'

  useEffect(() => {
    getPlayers()
      .then((data) => setAllPlayers(Array.isArray(data) ? data : []))
      .catch(() => setError('Could not load players'))
      .finally(() => setLoading(false))
  }, [])

  const available = useMemo(() => {
    const selectedIds = new Set(selected.map((p) => p.id))
    return [...allPlayers]
      .filter((p) => !selectedIds.has(p.id))
      .sort((a, b) => b[ratingKey] - a[ratingKey] || a.nickname.localeCompare(b.nickname))
  }, [allPlayers, selected, ratingKey])

  const filteredAvailable = useMemo(() => {
    if (!search.trim()) return available
    const upper = search.toUpperCase()
    return available.filter((p) =>
      [p.nickname, p.rating, p.dzrating].join(' ').toUpperCase().includes(upper),
    )
  }, [available, search])

  function runBalance(players: PlayerWithStats[], key: RatingKey) {
    const { team1, team2 } = generateCombinations(players, key)
    setRebels(team1)
    setImperials(team2)
    setRebelAvg(avgRating(team1, key))
    setImperialAvg(avgRating(team2, key))
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
      runBalance(next, ratingKey)
    }
  }

  function handleRemove(player: PlayerWithStats) {
    setError('')
    const next = selected.filter((p) => p.id !== player.id)
    setSelected(next)
    if (teamsShown && next.length >= MIN_PLAYERS && next.length % 2 === 0) {
      runBalance(next, ratingKey)
    } else if (next.length < MIN_PLAYERS) {
      setRebels([])
      setImperials([])
      setRebelAvg(0)
      setImperialAvg(0)
    }
  }

  function handleBalance() {
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
    runBalance(selected, ratingKey)
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
  }

  function handleToggle() {
    setIsDZ((prev) => !prev)
    handleReset()
  }

  function playerRow(player: PlayerWithStats, levelKey: RatingKey, index: number) {
    return (
      <tr key={player.id} className="sound__hover sound__click" style={{ cursor: 'pointer' }} onClick={() => handleSelect(player)}>
        <td className="table__cell table__player table__xxl">
          <ul className="list list__player">
            <li>{index + 1}</li>
            <li>
              <img src={`${FLAG_BASE}/${player.nation || 'aq'}.svg`} alt={player.nation} />
            </li>
            <li>{player.nickname}</li>
          </ul>
        </td>
        <td className="table__cell table__xxs"><RatingBadge rating={player[levelKey]} /></td>
      </tr>
    )
  }

  function teamRow(player: PlayerWithStats, levelKey: RatingKey) {
    return (
      <tr key={player.id}>
        <td className="table__cell table__player table__xxl">
          <ul className="list list__player">
            <li>
              <img src={`${FLAG_BASE}/${player.nation || 'aq'}.svg`} alt={player.nation} />
            </li>
            <li>{player.nickname}</li>
          </ul>
        </td>
        <td className="table__cell table__xxs"><RatingBadge rating={player[levelKey]} /></td>
      </tr>
    )
  }

  return (
    <main>
      <section className="section section--balance">
        <div className="container">
          <h1 className="title title--large" style={{ whiteSpace: 'nowrap' }}>Private Match</h1>

          {/* Settings: image + player table + controls */}
          <div className="settings">
            <img className="ranked" src={internImg} alt="intern" />
            <div className="table">
              <input
                type="text"
                className="input searchbar__table sound__hover"
                placeholder="Search for names, ratings ..."
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
                        <th className="head__cell head__xxs">Rating</th>
                      </tr>
                    </thead>
                    <tbody className="table__body table__dynamic">
                      {loading ? (
                        <tr><td colSpan={2} className="table__cell">Loading…</td></tr>
                      ) : filteredAvailable.length === 0 ? (
                        <tr><td colSpan={2} className="table__cell">No players found.</td></tr>
                      ) : (
                        filteredAvailable.map((p, i) => playerRow(p, ratingKey, i))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
            <div className="settings__console">
              <div className="settings__mode">
                <div className="settings__hardcore">
                  <h4 className="title title--extrasmall">Gamemode</h4>
                  <div className="hardcore__switch btn--switch sound__hover sound__click">
                    <input
                      type="checkbox"
                      id="gamemode-switch"
                      className="switch"
                      checked={isDZ}
                      onChange={handleToggle}
                    />
                    <div className="label">
                      <label
                        className={`label--status label--off${!isDZ ? ' label--active' : ''}`}
                        htmlFor="gamemode-switch"
                      >
                        Cargo
                      </label>
                      <label
                        className={`label--status label--on${isDZ ? ' label--active' : ''}`}
                        htmlFor="gamemode-switch"
                      >
                        Dropzone
                      </label>
                    </div>
                  </div>
                </div>
                <div className="settings__button">
                  <h4 className="title title--extrasmall">Create&nbsp;teams</h4>
                  <button
                    type="button"
                    className="btn btn--grey sound__hover sound__click"
                    onClick={handleBalance}
                  >
                    Balanced
                  </button>
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
                      <th className="head__cell head__player head__xxl">Name</th>
                      <th className="head__cell head__xxs">Rating</th>
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
                            <li>
                              <img src={`${FLAG_BASE}/${player.nation || 'aq'}.svg`} alt={player.nation} />
                            </li>
                            <li>{player.nickname}</li>
                          </ul>
                        </td>
                        <td className="table__cell table__xxs"><RatingBadge rating={player[ratingKey]} /></td>
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
          <div className="team team__gamemode">
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
                        <th className="head__cell head__xxs">Rating</th>
                      </tr>
                    </thead>
                    <tbody className="table__body">
                      {rebels.map((p) => teamRow(p, ratingKey))}
                    </tbody>
                  </table>
                </div>
              </div>
              <div className="team__rebel--score">
                <h3 className="title title--extrasmall">Avg. Rating</h3>
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
                          <th className="head__cell head__xxs">Rating</th>
                        </tr>
                      </thead>
                      <tbody className="table__body">
                        {imperials.map((p) => teamRow(p, ratingKey))}
                      </tbody>
                    </table>
                  </div>
                </div>
                <div className="team__imperial--score">
                  <h3 className="title title--extrasmall">Avg. Rating</h3>
                  <span className="team__score">{imperials.length > 0 ? formatAvg(imperialAvg) : '0'}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Back */}
          <div className="back">
            <Link className="link btn btn--transparent sound__hover sound__delete" to="/">
              Back
            </Link>
          </div>
        </div>
      </section>
    </main>
  )
}
