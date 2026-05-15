import { useEffect, useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { getSeasons } from '../api/matches'
import { getPlayersWithSeason, getPlayerMatchHistory } from '../api/players'
import type { PlayerWithStats, PlayerMatchHistoryEntry } from '../types'
import { getRankByDistributionFraction } from '../utils/rankIcons'
import kyberSvg from '../assets/images/SVG/kyber.svg'
import beskarSvg from '../assets/images/SVG/beskar.svg'
import diamondSvg from '../assets/images/SVG/diamond.svg'
import platinumSvg from '../assets/images/SVG/platinum.svg'
import aurodiumSvg from '../assets/images/SVG/aurodium.svg'
import chromiumSvg from '../assets/images/SVG/chromium.svg'
import bronziumSvg from '../assets/images/SVG/bronzium.svg'

const RANK_ICON_URLS: Record<string, string> = {
  kyber: kyberSvg,
  beskar: beskarSvg,
  diamond: diamondSvg,
  platinum: platinumSvg,
  aurodium: aurodiumSvg,
  chromium: chromiumSvg,
  bronzium: bronziumSvg,
}

const FLAG_BASE = 'https://cdnjs.cloudflare.com/ajax/libs/flag-icon-css/2.8.0/flags/4x3'

function formatDate(isoDate: string): string {
  const d = new Date(isoDate)
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}

function formatTime(isoDate: string): string {
  const d = new Date(isoDate)
  return d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

function formatScore(score: number): string {
  return score.toLocaleString('de-DE')
}

function BrChart({ entries }: { entries: PlayerMatchHistoryEntry[] }) {
  const brData = [...entries].reverse().map((e) => e.newBr)
  if (brData.length === 0) return <div style={{ minHeight: 80 }} />

  const W = 400
  const H = 180
  const padL = 5
  const padR = 25
  const padT = 10
  const padB = 10

  const rawMin = Math.min(...brData)
  const rawMax = Math.max(...brData)
  const yRange = 10
  const yMin = Math.round(rawMin / 10) * 10 - yRange
  const yMax = Math.round(rawMax / 10) * 10 + yRange
  const yExtent = yMax - yMin || 1
  const xStep = (W - padL - padR) / Math.max(brData.length - 1, 1)

  const pts = brData.map((br, i) => ({
    x: padL + i * xStep,
    y: padT + ((yMax - br) / yExtent) * (H - padT - padB),
  }))

  const linePath = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
  const fillPath = `${linePath} L${pts[pts.length - 1].x.toFixed(1)},${H - padB} L${pts[0].x.toFixed(1)},${H - padB} Z`

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      style={{ width: '100%', display: 'block' }}
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="brGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="rgba(234,195,66,0.3)" />
          <stop offset="60%" stopColor="rgba(234,195,66,0)" />
        </linearGradient>
      </defs>
      <path d={fillPath} fill="url(#brGrad)" />
      <path
        d={linePath}
        stroke="#eac342"
        strokeWidth="3"
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function StatsPage() {
  const [seasons, setSeasons] = useState<number[]>([])
  const [activeSeason, setActiveSeason] = useState<string | null>(null)
  const [players, setPlayers] = useState<PlayerWithStats[]>([])
  const [selectedPlayerId, setSelectedPlayerId] = useState<number | null>(null)
  const [matches, setMatches] = useState<PlayerMatchHistoryEntry[]>([])
  const [search, setSearch] = useState('')
  const [loadingPlayers, setLoadingPlayers] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    getSeasons()
      .then(setSeasons)
      .catch(() => setSeasons([]))
  }, [])

  useEffect(() => {
    setLoadingPlayers(true)
    getPlayersWithSeason(activeSeason ?? undefined)
      .then((data) => {
        setPlayers(data)
        setSelectedPlayerId(data.length > 0 ? data[0].id : null)
      })
      .catch(() => setPlayers([]))
      .finally(() => setLoadingPlayers(false))
  }, [activeSeason])

  useEffect(() => {
    if (selectedPlayerId === null) {
      setMatches([])
      return
    }
    getPlayerMatchHistory(selectedPlayerId, activeSeason ?? undefined)
      .then(setMatches)
      .catch(() => setMatches([]))
  }, [selectedPlayerId, activeSeason])

  const rankFractionMap = useMemo(() => {
    const ranked = players.filter((p) => p.played >= 5)
    const total = ranked.length || 1
    return new Map(ranked.map((p, i) => [p.id, (i + 1) / total]))
  }, [players])

  const filteredPlayers = useMemo(() => {
    if (!search.trim()) return players
    const upper = search.toUpperCase()
    return players.filter((p) =>
      [p.nickname, p.played, p.won, p.lost, p.draw].join(' ').toUpperCase().includes(upper),
    )
  }, [players, search])

  const selectedPlayer = useMemo(
    () => players.find((p) => p.id === selectedPlayerId) ?? null,
    [players, selectedPlayerId],
  )

  const avgScore =
    selectedPlayer && selectedPlayer.played > 0
      ? Math.floor(selectedPlayer.score / selectedPlayer.played)
      : 0
  const percentWon =
    selectedPlayer && selectedPlayer.played > 0
      ? Math.round((selectedPlayer.won / selectedPlayer.played) * 100)
      : 0
  const percentLost =
    selectedPlayer && selectedPlayer.played > 0
      ? Math.round((selectedPlayer.lost / selectedPlayer.played) * 100)
      : 0
  const percentDraw =
    selectedPlayer && selectedPlayer.played > 0
      ? Math.round((selectedPlayer.draw / selectedPlayer.played) * 100)
      : 0

  return (
    <main>
      <section className="section section--stats">
        <div className="container">
          {/* Left: player list */}
          <div className="tabs">
            <div className="season__selector">
              <ul className="list season__list">
                <li>
                  <button
                    type="button"
                    className={`btn sound__hover sound__click${activeSeason === 'all' ? ' btn--active' : ''}`}
                    onClick={() => setActiveSeason('all')}
                  >
                    All
                  </button>
                </li>
                {seasons.map((s) => (
                  <li key={s}>
                    <button
                      type="button"
                      className={`btn sound__hover sound__click${
                        activeSeason === String(s) ||
                        (activeSeason === null && s === seasons[seasons.length - 1])
                          ? ' btn--active'
                          : ''
                      }`}
                      onClick={() => setActiveSeason(String(s))}
                    >
                      S{s}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
            <input
              type="text"
              className="input searchbar__table sound__hover"
              placeholder="Search for names, stats ..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <div className="table__list">
              <div className="table__info">
                <h2 className="title title--small">Players</h2>
                <span className="table__counter">{filteredPlayers.length}</span>
              </div>
              <div className="table__hidden table__scroll">
                <table className="table__content table__search">
                  <thead className="table__head table__sticky">
                    <tr>
                      <th className="head__cell head__player head__xxl">Name</th>
                      <th className="head__cell head__xxs">Games</th>
                      <th className="head__cell head__xxs">Won</th>
                      <th className="head__cell head__xxs">Lost</th>
                      <th className="head__cell head__xxs">Draw</th>
                    </tr>
                  </thead>
                  <tbody className="table__body table__select">
                    {loadingPlayers ? (
                      <tr>
                        <td colSpan={5} className="table__cell">Loading…</td>
                      </tr>
                    ) : filteredPlayers.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="table__cell">No players found.</td>
                      </tr>
                    ) : (
                      filteredPlayers.map((player, index) => {
                        const rf = rankFractionMap.get(player.id)
                        const iconName = rf != null ? getRankByDistributionFraction(rf) : null
                        const iconSrc = iconName ? RANK_ICON_URLS[iconName] : null
                        return (
                          <tr
                            key={player.id}
                            className={`sound__hover sound__click${selectedPlayerId === player.id ? ' match__selected' : ''}`}
                            onClick={() => setSelectedPlayerId(player.id)}
                            style={{ cursor: 'pointer' }}
                          >
                            <td className="table__cell table__player table__xxl">
                              <ul className="list list__player">
                                <li>{index + 1}</li>
                                <li>
                                  <img
                                    src={`${FLAG_BASE}/${player.nation || 'aq'}.svg`}
                                    alt={player.nation}
                                  />
                                </li>
                                <li>
                                  {player.nickname}
                                  {iconSrc && (
                                    <img
                                      height={27}
                                      src={iconSrc}
                                      alt=""
                                      style={{ verticalAlign: 'middle', marginLeft: 4 }}
                                    />
                                  )}
                                </li>
                              </ul>
                            </td>
                            <td className="table__cell table__xxs">{player.played}</td>
                            <td className="table__cell table__xxs">{player.won}</td>
                            <td className="table__cell table__xxs">{player.lost}</td>
                            <td className="table__cell table__xxs">{player.draw}</td>
                          </tr>
                        )
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Middle: player stats */}
          <div className="stats">
            <div className="stats__player">
              <h1 className="title title--large">
                <ul className="list">
                  <li>
                    <img
                      className="stats__nation"
                      src={`${FLAG_BASE}/${selectedPlayer?.nation || 'aq'}.svg`}
                      alt={selectedPlayer?.nation ?? ''}
                    />
                  </li>
                  <li>{selectedPlayer?.nickname ?? 'John Doe'}</li>
                </ul>
              </h1>
              <div className="bar" />
            </div>
            <div className="stats__info">
              <ul className="list list__stats">
                <li>
                  <h3 className="title title--extrasmall">Rating</h3>
                  <small className="stat">{selectedPlayer?.rating ?? 0}</small>
                </li>
                <li>
                  <h3 className="title title--extrasmall">Br</h3>
                  <small className="stat">{selectedPlayer?.br ?? 0}</small>
                </li>
                <li>
                  <h3 className="title title--extrasmall">Best Br</h3>
                  <small className="stat">{selectedPlayer?.best ?? 0}</small>
                </li>
                <li>
                  <h3 className="title title--extrasmall">MVP</h3>
                  <small className="stat">{selectedPlayer?.mvp ?? 0}</small>
                </li>
                <li>
                  <h3 className="title title--extrasmall">Score</h3>
                  <small className="stat">{formatScore(selectedPlayer?.score ?? 0)}</small>
                </li>
                <li>
                  <h3 className="title title--extrasmall">Avg. Score</h3>
                  <small className="stat">{formatScore(avgScore)}</small>
                </li>
              </ul>
            </div>
            <div className="stats__games">
              <div className="stats__won">
                <div className="stats__bar">
                  <div className="stats__progress" style={{ height: `${percentWon}%` }} />
                  <small className="stats__percent">{percentWon}&thinsp;%</small>
                </div>
                <h3 className="title title--extrasmall">Won</h3>
              </div>
              <div className="stats__lost">
                <div className="stats__bar">
                  <div className="stats__progress" style={{ height: `${percentLost}%` }} />
                  <small className="stats__percent">{percentLost}&thinsp;%</small>
                </div>
                <h3 className="title title--extrasmall">Lost</h3>
              </div>
              <div className="stats__draw">
                <div className="stats__bar">
                  <div className="stats__progress" style={{ height: `${percentDraw}%` }} />
                  <small className="stats__percent">{percentDraw}&thinsp;%</small>
                </div>
                <h3 className="title title--extrasmall">Draw</h3>
              </div>
            </div>
          </div>

          {/* Right: BR chart + match history */}
          <div className="matches">
            <div className="graph">
              <BrChart entries={matches} />
            </div>
            <div className="table__list">
              <div className="table__info">
                <h2 className="title title--small">Games</h2>
                <span className="table__counter">{matches.length}</span>
              </div>
              <div className="table__hidden table__scroll">
                <table className="table__content table__search table__stats">
                  <thead className="table__head">
                    <tr>
                      <th className="head__cell head__sm">Date</th>
                      <th className="head__cell head__xxs">Time</th>
                      <th className="head__cell head__lg">Map</th>
                      <th className="head__cell head__lg">Rule</th>
                      <th className="head__cell head__xxs">Score</th>
                      <th className="head__cell head__xxs">ΔBR</th>
                    </tr>
                  </thead>
                  <tbody className="table__body table__match">
                    {matches.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="table__cell">No games found.</td>
                      </tr>
                    ) : (
                      matches.map((m) => (
                        <tr
                          key={m.matchId}
                          className={`sound__hover sound__click${m.result === 'Won' ? ' green' : m.result === 'Lost' ? ' red' : ' blue'}`}
                          onClick={() => {
                            const seasonParam = activeSeason ? `&season=${activeSeason}` : ''
                            navigate(`/matches?match=${m.matchId}${seasonParam}`)
                          }}
                          style={{ cursor: 'pointer' }}
                        >
                          <td className="table__cell table__date table__sm">{formatDate(m.date)}</td>
                          <td className="table__cell table__xxs">{formatTime(m.date)}</td>
                          <td className="table__cell table__map table__lg">{m.map}</td>
                          <td className="table__cell table__lg">{m.rule}</td>
                          <td className="table__cell table__xxs">{m.score}</td>
                          <td className="table__cell table__xxs">{m.updateBr}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}
