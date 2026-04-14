import { useEffect, useState, useMemo } from 'react'
import { getSeasons, getMatchList, getMatchDetail } from '../api/matches'
import type { MatchDetail, MatchSummary } from '../types'

const FLAG_BASE = 'https://cdnjs.cloudflare.com/ajax/libs/flag-icon-css/2.8.0/flags/4x3'

function formatDate(isoDate: string): string {
  const d = new Date(isoDate)
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}

function formatTime(isoDate: string): string {
  const d = new Date(isoDate)
  return d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

function scoreDasharray(score: number): string {
  const clamped = Math.min(Math.max(score, 0), 5)
  return `${(clamped * 20 * 31.4) / 100} 31.4`
}

interface ScoreCircleProps {
  score: number
  color: string
  bgColor: string
}

function ScoreCircle({ score, color, bgColor }: ScoreCircleProps) {
  return (
    <div style={{ position: 'relative', display: 'flex' }}>
      <span
        className="score"
        style={{ alignItems: 'center', color: '#fff', fontSize: 'var(--title-sm)', fontWeight: 600 }}
      >
        {score}
      </span>
      <svg width="40px" height="40px" viewBox="0 0 20 20">
        <circle r="10" cx="10" cy="10" fill={bgColor} />
        <circle
          className="circle"
          r="5"
          cx="10"
          cy="10"
          fill="transparent"
          stroke={color}
          strokeWidth="10"
          strokeDasharray={scoreDasharray(score)}
          transform="rotate(-90) translate(-20)"
        />
      </svg>
    </div>
  )
}

interface MatchStatTableProps {
  title: string
  teamSize: number
  players: MatchDetail['rebels']
  colorClass: 'team__rebel' | 'team__imperial'
}

function MatchStatTable({ title, teamSize, players, colorClass }: MatchStatTableProps) {
  return (
    <div className={colorClass}>
      <div className="team__info">
        <h3 className="title title--small">{title}</h3>
        <span className="team__counter">{teamSize}/6</span>
      </div>
      <div className="table__hidden">
        <table className="table__content">
          <thead className="table__head">
            <tr>
              <th className="head__cell head__player head__xxl">Name</th>
              <th className="head__cell head__sm">Score</th>
              <th className="head__cell head__xs">ΔBR</th>
              <th className="head__cell head__xs">NBR</th>
              <th className="head__cell head__xs">Perf</th>
            </tr>
          </thead>
          <tbody className="table__body">
            {players.map((p) => (
              <tr key={p.nickname}>
                <td className="table__cell table__player table__xxl">
                  <ul className="list list__player">
                    <li>
                      <img
                        src={`${FLAG_BASE}/${p.nation || 'aq'}.svg`}
                        alt={p.nation}
                      />
                    </li>
                    <li>{p.nickname}</li>
                  </ul>
                </td>
                <td className="table__cell table__sm">{p.score}</td>
                <td className="table__cell table__xs">{p.updateBr}</td>
                <td className="table__cell table__xs">{p.newBr}</td>
                <td className="table__cell table__xs">{p.perf}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export function MatchesPage() {
  const [seasons, setSeasons] = useState<number[]>([])
  const [activeSeason, setActiveSeason] = useState<string | null>(null)
  const [matches, setMatches] = useState<MatchSummary[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<MatchDetail | null>(null)
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Load seasons once
  useEffect(() => {
    getSeasons()
      .then((s) => setSeasons(s))
      .catch(() => setSeasons([]))
  }, [])

  // Load match list whenever activeSeason changes
  useEffect(() => {
    setLoading(true)
    setError(null)
    setSelectedId(null)
    setDetail(null)
    getMatchList(activeSeason ?? undefined)
      .then((data) => {
        setMatches(data)
        // Auto-select first match
        if (data.length > 0) {
          setSelectedId(data[0].id)
        }
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'Could not load matches'))
      .finally(() => setLoading(false))
  }, [activeSeason])

  // Load detail whenever selectedId changes
  useEffect(() => {
    if (selectedId === null) {
      setDetail(null)
      return
    }
    getMatchDetail(selectedId, activeSeason ?? undefined)
      .then(setDetail)
      .catch(() => setDetail(null))
  }, [selectedId, activeSeason])

  const filteredMatches = useMemo(() => {
    if (!search.trim()) return matches
    const upper = search.toUpperCase()
    return matches.filter((m) =>
      [m.map, m.rule, m.mvpName ?? '', m.supervisorName ?? '', formatDate(m.date)].join(' ')
        .toUpperCase()
        .includes(upper),
    )
  }, [matches, search])

  return (
    <main>
      <section className="section section--match">
        <div className="container">
          {/* Match list */}
          <div className="table">
            {/* Season selector */}
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
                      className={`btn sound__hover sound__click${activeSeason === String(s) || (activeSeason === null && s === seasons[seasons.length - 1]) ? ' btn--active' : ''}`}
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
                <h2 className="title title--small">Matches</h2>
                <span className="table__counter">{filteredMatches.length}</span>
              </div>
              <div className="table__hidden table__scroll">
                <table className="table__content table__search">
                  <thead className="table__head table__sticky">
                    <tr>
                      <th className="head__cell head__sm">Date</th>
                      <th className="head__cell head__xxs">Time</th>
                      <th className="head__cell head__md">Map</th>
                      <th className="head__cell head__xs">Rule</th>
                      <th className="head__cell head__xxs">Size</th>
                      <th className="head__cell head__sm">Mvp</th>
                      <th className="head__cell head__sm">Head</th>
                    </tr>
                  </thead>
                  <tbody className="table__body">
                    {loading ? (
                      <tr>
                        <td colSpan={7} className="table__cell">Loading…</td>
                      </tr>
                    ) : error ? (
                      <tr>
                        <td colSpan={7} className="table__cell">{error}</td>
                      </tr>
                    ) : filteredMatches.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="table__cell">No matches found.</td>
                      </tr>
                    ) : (
                      filteredMatches.map((m) => (
                        <tr
                          key={m.id}
                          className={`sound__hover${selectedId === m.id ? ' match__selected' : ''}`}
                          onClick={() => setSelectedId(m.id)}
                          style={{ cursor: 'pointer' }}
                        >
                          <td className="table__cell table__date table__sm">{formatDate(m.date)}</td>
                          <td className="table__cell table__xxs">{formatTime(m.date)}</td>
                          <td className="table__cell table__map table__md">{m.map}</td>
                          <td className="table__cell table__xs">{m.rule}</td>
                          <td className="table__cell table__size table__xxs">
                            {m.teamSize} V {m.teamSize}
                          </td>
                          <td className="table__cell table__sm">{m.mvpName ?? '?'}</td>
                          <td className="table__cell table__sm">{m.supervisorName ?? '?'}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Match detail */}
          <div className="team team__match">
            <div className="team__1">
              <MatchStatTable
                title="Rebel"
                teamSize={detail?.teamSize ?? 0}
                players={detail?.rebels ?? []}
                colorClass="team__rebel"
              />
            </div>
            <div className="team__2">
              <MatchStatTable
                title="Imperial"
                teamSize={detail?.teamSize ?? 0}
                players={detail?.imperials ?? []}
                colorClass="team__imperial"
              />
            </div>
            <div className="center">
              <div className="vertical">
                <div className="result">
                  <ScoreCircle
                    score={detail?.rebelScore ?? 0}
                    color="#0f98d3"
                    bgColor="#0f98d380"
                  />
                  <ScoreCircle
                    score={detail?.imperialScore ?? 0}
                    color="#da180f"
                    bgColor="#da180f80"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}
