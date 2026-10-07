import { useEffect, useState, useMemo, useCallback } from 'react'
import { usePageTitle } from '../hooks/usePageTitle'
import { Link } from 'react-router-dom'
import { RatingBadge } from '../components/RatingBadge'
import { getPlayers, getInternPlayers } from '../api/players'
import type { PlayerWithStats } from '../types'
import { getRankByDistributionFraction } from '../utils/rankIcons'
import arrowSvg from '../assets/images/SVG/arrow.svg'
import kyberSvg from '../assets/images/SVG/kyber.svg'
import beskarSvg from '../assets/images/SVG/beskar.svg'
import diamondSvg from '../assets/images/SVG/diamond.svg'
import platinumSvg from '../assets/images/SVG/platinum.svg'
import aurodiumSvg from '../assets/images/SVG/aurodium.svg'
import chromiumSvg from '../assets/images/SVG/chromium.svg'
import bronziumSvg from '../assets/images/SVG/bronzium.svg'
import internImg from '../assets/images/intern.jpg'
import rankedImg from '../assets/images/ranked.jpg'
import superLeagueImg from '../assets/images/super-league.png'

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

type ViewMode = 'intern' | 'ranked'

function getPlayerRankIcon(player: PlayerWithStats, viewMode: ViewMode): string | null {
  if (viewMode === 'ranked' && player.played >= 5 && 'rankFraction' in player) {
    return getRankByDistributionFraction((player as { rankFraction: number }).rankFraction)
  }
  return null
}

export function HomePage() {
  usePageTitle('Home')
  const [internPlayers, setInternPlayers] = useState<PlayerWithStats[]>([])
  const [rankedPlayers, setRankedPlayers] = useState<PlayerWithStats[]>([])
  const [loading, setLoading] = useState(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [viewMode, setViewMode] = useState<ViewMode>('intern')
  const [sortColumn, setSortColumn] = useState(1)
  const [sortAscending, setSortAscending] = useState(false)
  const [search, setSearch] = useState('')

  const allPlayers = useMemo(() => {
    if (viewMode === 'ranked') {
      const qualified = [...rankedPlayers]
        .filter((p) => p.played >= 5)
        .sort((a, b) => b.br - a.br || a.nickname.localeCompare(b.nickname))
      const total = qualified.length || 1
      const qualifiedWithFraction = qualified.map((p, i) => ({ ...p, rankFraction: (i + 1) / total }))
      const unqualified = [...rankedPlayers]
        .filter((p) => p.played < 5)
        .sort((a, b) => b.br - a.br || a.nickname.localeCompare(b.nickname))
      return [...qualifiedWithFraction, ...unqualified]
    }
    return internPlayers
  }, [internPlayers, rankedPlayers, viewMode])

  const sortedPlayers = useMemo(() => {
    const list = [...allPlayers]
    const dir = sortAscending ? 1 : -1
    if (viewMode === 'intern') {
      if (sortColumn === 1) {
        list.sort((a, b) => {
          const avgA = (a.rating + a.dzrating) / 2
          const avgB = (b.rating + b.dzrating) / 2
          const cmp = dir * (avgA - avgB)
          return cmp !== 0 ? cmp : a.nickname.localeCompare(b.nickname)
        })
      } else {
        const prop = sortColumn === 0 ? 'nickname' : sortColumn === 2 ? 'rating' : 'dzrating'
        list.sort((a, b) => {
          const av = a[prop as keyof PlayerWithStats]
          const bv = b[prop as keyof PlayerWithStats]
          const cmp = typeof av === 'number' && typeof bv === 'number'
            ? dir * ((av as number) - (bv as number))
            : dir * String(av).localeCompare(String(bv))
          return cmp !== 0 ? cmp : a.nickname.localeCompare(b.nickname)
        })
      }
    } else {
      if (sortColumn === 1) {
        list.sort((a, b) => {
          if (a.played >= 5 && b.played < 5) return -1
          if (a.played < 5 && b.played >= 5) return 1
          if (b.br !== a.br) return b.br - a.br
          return a.nickname.localeCompare(b.nickname)
        })
        if (sortAscending) list.reverse()
      } else {
        const prop = sortColumn === 0 ? 'nickname' : sortColumn === 2 ? 'played' : sortColumn === 3 ? 'won' : sortColumn === 4 ? 'lost' : 'draw'
        list.sort((a, b) => {
          const av = a[prop as keyof PlayerWithStats]
          const bv = b[prop as keyof PlayerWithStats]
          const cmp = typeof av === 'number' && typeof bv === 'number'
            ? dir * ((av as number) - (bv as number))
            : dir * String(av).localeCompare(String(bv))
          return cmp !== 0 ? cmp : a.nickname.localeCompare(b.nickname)
        })
      }
    }
    return list
  }, [allPlayers, viewMode, sortColumn, sortAscending])

  const filteredPlayers = useMemo(() => {
    if (!search.trim()) return sortedPlayers
    const upper = search.toUpperCase()
    return sortedPlayers.filter((p) => {
      const str = [p.nickname, p.rating, p.dzrating, p.br, p.played, p.won, p.lost, p.draw].join(' ')
      return str.toUpperCase().includes(upper)
    })
  }, [sortedPlayers, search])

  useEffect(() => {
    setPlayersError(null)
    Promise.all([getInternPlayers(), getPlayers()])
      .then(([intern, ranked]) => {
        setInternPlayers(Array.isArray(intern) ? intern : [])
        setRankedPlayers(Array.isArray(ranked) ? ranked : [])
      })
      .catch(() => {
        setPlayersError('Failed to load players. Please try again later.')
      })
      .finally(() => setLoading(false))
  }, [])

  const onSort = useCallback((columnIndex: number) => {
    if (sortColumn === columnIndex) {
      setSortAscending((a) => !a)
    } else {
      setSortColumn(columnIndex)
      setSortAscending(columnIndex === 0)
    }
  }, [sortColumn])

  const onToggleMode = useCallback(() => {
    setViewMode((m) => (m === 'intern' ? 'ranked' : 'intern'))
    setSortColumn(1)
    setSortAscending(false)
  }, [])

  return (
    <main>
      <section className="section section--menu">
        <div className="container">
          <div className="table">
            <input
              type="text"
              className="input searchbar__table sound__hover"
              placeholder="Search for names, stats ..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Search players"
            />
            <div className="table__list">
              <div className="table__info">
                <h2 className="title title--small">Players</h2>
                <div className="table__gamemode">
                  <div className="btn--mode btn--switch sound__hover sound__click">
                    <input
                      type="checkbox"
                      id="switch"
                      className="switch"
                      checked={viewMode === 'ranked'}
                      onChange={onToggleMode}
                      aria-label="Toggle Intern / Ranked"
                    />
                    <div className="label">
                      <label
                        className={`label--status label--off ${viewMode === 'intern' ? 'label--active' : ''}`}
                        htmlFor="switch"
                      >
                        Intern
                      </label>
                      <label
                        className={`label--status label--on ${viewMode === 'ranked' ? 'label--active' : ''}`}
                        htmlFor="switch"
                      >
                        Ranked
                      </label>
                    </div>
                  </div>
                  <span className="table__counter">{filteredPlayers.length}</span>
                </div>
              </div>
              <div className="table__hidden table__scroll">
                <table className="table__content table__search">
                  <thead className="table__head table__sticky">
                    {viewMode === 'intern' ? (
                      <tr>
                        <th
                          className="head__cell head__cell--sorting head__player head__xxl sound__hover sound__click"
                          onClick={() => onSort(0)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(0)}
                        >
                          <div className="head__content">
                            Name <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                        <th
                          className={`head__cell head__cell--sorting head__xxs sound__hover sound__click ${sortColumn === 1 ? (sortAscending ? 'asc' : 'desc') + ' active' : ''}`}
                          onClick={() => onSort(1)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(1)}
                        >
                          <div className="head__content">
                            OVR <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                        <th
                          className={`head__cell head__cell--sorting head__xxs sound__hover sound__click ${sortColumn === 2 ? (sortAscending ? 'asc' : 'desc') + ' active' : ''}`}
                          onClick={() => onSort(2)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(2)}
                        >
                          <div className="head__content">
                            CGO <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                        <th
                          className={`head__cell head__cell--sorting head__xxs sound__hover sound__click ${sortColumn === 3 ? (sortAscending ? 'asc' : 'desc') + ' active' : ''}`}
                          onClick={() => onSort(3)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(3)}
                        >
                          <span className="head__content">
                            DZ <img className="sortingIcon" src={arrowSvg} alt="" />
                          </span>
                        </th>
                      </tr>
                    ) : (
                      <tr>
                        <th
                          className="head__cell head__cell--sorting head__player head__xxl sound__hover sound__click"
                          onClick={() => onSort(0)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(0)}
                        >
                          <div className="head__content">
                            Name <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                        <th
                          className={`head__cell head__cell--sorting head__xxs sound__hover sound__click ${sortColumn === 1 ? (sortAscending ? 'asc' : 'desc') + ' active' : ''}`}
                          onClick={() => onSort(1)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(1)}
                        >
                          <div className="head__content">
                            BR <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                        <th
                          className={`head__cell head__cell--sorting head__xxs sound__hover sound__click ${sortColumn === 2 ? (sortAscending ? 'asc' : 'desc') + ' active' : ''}`}
                          onClick={() => onSort(2)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(2)}
                        >
                          <div className="head__content">
                            Games <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                        <th
                          className={`head__cell head__cell--sorting head__xxs sound__hover sound__click ${sortColumn === 3 ? (sortAscending ? 'asc' : 'desc') + ' active' : ''}`}
                          onClick={() => onSort(3)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(3)}
                        >
                          <div className="head__content">
                            Won <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                        <th
                          className={`head__cell head__cell--sorting head__xxs sound__hover sound__click ${sortColumn === 4 ? (sortAscending ? 'asc' : 'desc') + ' active' : ''}`}
                          onClick={() => onSort(4)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(4)}
                        >
                          <div className="head__content">
                            Lost <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                        <th
                          className={`head__cell head__cell--sorting head__xxs sound__hover sound__click ${sortColumn === 5 ? (sortAscending ? 'asc' : 'desc') + ' active' : ''}`}
                          onClick={() => onSort(5)}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => e.key === 'Enter' && onSort(5)}
                        >
                          <div className="head__content">
                            Draw <img className="sortingIcon" src={arrowSvg} alt="" />
                          </div>
                        </th>
                      </tr>
                    )}
                  </thead>
                  <tbody className="table__body">
                    {loading ? (
                      <tr>
                        <td colSpan={viewMode === 'intern' ? 4 : 6} className="table__cell">
                          Loading...
                        </td>
                      </tr>
                    ) : playersError ? (
                      <tr>
                        <td colSpan={viewMode === 'intern' ? 4 : 6} className="table__cell">
                          {playersError}
                        </td>
                      </tr>
                    ) : filteredPlayers.length === 0 ? (
                      <tr>
                        <td colSpan={viewMode === 'intern' ? 4 : 6} className="table__cell">
                          No players found
                        </td>
                      </tr>
                    ) : (
                      filteredPlayers.map((player, index) => {
                        const displayNum = sortColumn === 0
                          ? (sortAscending ? index + 1 : filteredPlayers.length - index)
                          : (sortAscending ? filteredPlayers.length - index : index + 1)
                        const iconName = getPlayerRankIcon(player, viewMode)
                        const iconSrc = iconName ? RANK_ICON_URLS[iconName] : null
                        return viewMode === 'intern' ? (
                          <tr key={player.id}>
                            <td className="table__cell table__player table__xxl">
                              <ul className="list list__player">
                                <li>{displayNum}</li>
                                <li>
                                  <img src={`${FLAG_BASE}/${player.nation}.svg`} alt={`${player.nation} flag`} />
                                </li>
                                <li>
                                  {player.nickname}
                                  {iconSrc && (
                                    <img height={27} src={iconSrc} alt="" style={{ verticalAlign: 'middle', marginLeft: 4 }} />
                                  )}
                                </li>
                              </ul>
                            </td>
                            <td className="table__cell table__xxs"><RatingBadge rating={Math.round((player.rating + player.dzrating) / 2)} /></td>
                            <td className="table__cell table__xxs">{player.rating}</td>
                            <td className="table__cell table__xxs">{player.dzrating}</td>
                          </tr>
                        ) : (
                          <tr key={player.id}>
                            <td className="table__cell table__player table__xxl">
                              <ul className="list list__player">
                                <li>{displayNum}</li>
                                <li>
                                  <img src={`${FLAG_BASE}/${player.nation}.svg`} alt={`${player.nation} flag`} />
                                </li>
                                <li>
                                  {player.nickname}
                                  {iconSrc && (
                                    <img height={27} src={iconSrc} alt="" style={{ verticalAlign: 'middle', marginLeft: 4 }} />
                                  )}
                                </li>
                              </ul>
                            </td>
                            <td className="table__cell table__xxs">{player.br}</td>
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
          <div className="gamemode">
            <Link className="link sound__hover sound__click" to="/intern">
              <div className="gamemode__intern">
                <div className="gamemode__info">
                  <h3 className="title title--medium">Intern</h3>
                  <span>Team balancer based on player ratings</span>
                </div>
                <img
                  className="img"
                  src={internImg}
                  alt="intern"
                />
              </div>
            </Link>
            <Link className="link sound__hover sound__click" to="/ranked">
              <div className="gamemode__ranked">
                <div className="gamemode__info">
                  <h3 className="title title--medium">Ranked</h3>
                  <span>Competitive battlefront games</span>
                </div>
                <img
                  className="img"
                  src={rankedImg}
                  alt="ranked"
                />
              </div>
            </Link>
          </div>
          <a
            className="btn btn--grey btn--partner sound__hover sound__click"
            href="https://discord.gg/SzKvdReAMH"
            target="_blank"
            rel="noopener noreferrer"
          >
            <div className="partner__info">
              <h3 className="title title--small">TSL Battlefront</h3>
              <span>Discord Partner for ranked pugs, tournaments and more</span>
            </div>
            <img src={superLeagueImg} alt="super-league" />
          </a>
        </div>
      </section>
    </main>
  )
}
