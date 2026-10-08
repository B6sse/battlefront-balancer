import { useState, useEffect, useMemo, useRef } from 'react'
import { getPlayers, createPlayer, updatePlayer, deletePlayer } from '../api/players'
import type { PlayerCreateRequest, PlayerUpdateRequest } from '../api/players'
import { getRandomizerWeights, updateRandomizerWeights } from '../api/ranked'
import type { RandomizerWeightEntry, RandomizerWeightsResponse } from '../api/ranked'
import { getCurrentSeason, startNextSeason, cleanupSeason, getUsers } from '../api/admin'
import type { UserDto } from '../api/admin'
import { useAuth } from '../context/AuthContext'
import { HostTokensPanel } from '../components/HostTokensPanel'
import { UsersPanel } from '../components/UsersPanel'
import { PlayerRequestsPanel } from '../components/PlayerRequestsPanel'
import type { PlayerWithStats } from '../types'

const FLAG_BASE = 'https://cdnjs.cloudflare.com/ajax/libs/flag-icon-css/2.8.0/flags/4x3'

// Only printable ASCII, no angle brackets (< >) or quotes that could be misused
const NICKNAME_RE = /^[^\x00-\x1f<>"'`]{1,16}$/
const NATION_RE = /^[a-z]{2}$/

function validateNickname(v: string): string | null {
  const t = v.trim()
  if (!t) return 'Nickname is required'
  if (t.length > 16) return 'Nickname max 16 characters'
  if (!NICKNAME_RE.test(t)) return 'Nickname contains invalid characters'
  return null
}

function validateNation(v: string): string | null {
  const t = v.trim().toLowerCase()
  if (!NATION_RE.test(t)) return 'Nation must be exactly 2 letters (a–z)'
  return null
}

function validateRating(v: string): string | null {
  if (!/^\d+$/.test(v.trim())) return 'Rating must be a whole number'
  const n = parseInt(v, 10)
  if (n < 1 || n > 99) return 'Rating must be 1–99'
  return null
}

function validateDzrating(v: string): string | null {
  if (!/^\d+$/.test(v.trim())) return 'DZ rating must be a whole number'
  const n = parseInt(v, 10)
  if (n < 1 || n > 99) return 'DZ rating must be 1–99'
  return null
}

function validateBr(v: string): string | null {
  if (!/^\d+$/.test(v.trim())) return 'BR must be a whole number'
  const n = parseInt(v, 10)
  if (n < 1 || n > 9999) return 'BR must be 1–9999'
  return null
}

/** Persona ID is optional; EA persona IDs are 13 digits, so 15 keeps them within JS safe integers. */
function validatePersonaId(v: string, players: PlayerWithStats[], playerId: number | null): string | null {
  const t = v.trim()
  if (!t) return null
  if (!/^\d{1,15}$/.test(t) || parseInt(t, 10) < 1) return 'Persona ID must be a positive whole number'
  const owner = players.find((p) => p.personaId === parseInt(t, 10) && p.id !== playerId)
  if (owner) return `Persona ID is already used by ${owner.nickname}`
  return null
}

function parsePersonaId(v: string): number | null {
  const t = v.trim()
  return t ? parseInt(t, 10) : null
}

interface EditState {
  nickname: string
  nation: string
  rating: string
  dzrating: string
  br: string
  personaId: string
}

function toEditState(p: PlayerWithStats): EditState {
  return {
    nickname: p.nickname,
    nation: p.nation,
    rating: String(p.rating),
    dzrating: String(p.dzrating),
    br: String(p.br),
    personaId: p.personaId === null ? '' : String(p.personaId),
  }
}

export function AdminPage() {
  const { user: currentUser } = useAuth()
  const [players, setPlayers] = useState<PlayerWithStats[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editState, setEditState] = useState<EditState | null>(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null)
  const [savingId, setSavingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const [addForm, setAddForm] = useState({ nickname: '', nation: '', rating: '', personaId: '' })
  const [addError, setAddError] = useState<string | null>(null)
  const [addLoading, setAddLoading] = useState(false)

  const [weights, setWeights] = useState<RandomizerWeightsResponse | null>(null)
  const [weightDraft, setWeightDraft] = useState<Record<number, string>>({})
  const [weightsSaving, setWeightsSaving] = useState(false)
  const [weightsError, setWeightsError] = useState<string | null>(null)

  const [currentSeason, setCurrentSeason] = useState<number | null>(null)
  const [seasonLoading, setSeasonLoading] = useState(false)
  const [seasonError, setSeasonError] = useState<string | null>(null)
  const [cleanupConfirm, setCleanupConfirm] = useState(false)
  const [cleanupSelectedSeason, setCleanupSelectedSeason] = useState<number | null>(null)

  const [users, setUsers] = useState<UserDto[] | null>(null)

  const successTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    getPlayers()
      .then(setPlayers)
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    getRandomizerWeights()
      .then((w) => {
        setWeights(w)
        const draft: Record<number, string> = {}
        ;[...w.maps, ...w.rules].forEach((e) => { draft[e.id] = String(e.weight) })
        setWeightDraft(draft)
      })
      .catch(() => {/* not admin — silently skip */})
    getCurrentSeason()
      .then((r) => {
        setCurrentSeason(r.season)
        setCleanupSelectedSeason(Math.max(1, r.season - 1))
      })
      .catch(() => {/* not admin — silently skip */})
    getUsers()
      .then(setUsers)
      .catch(() => {/* not admin — silently skip */})
  }, [])

  function handleWeightChange(id: number, value: string) {
    setWeightDraft((prev) => ({ ...prev, [id]: value }))
  }

  async function handleWeightsSave() {
    setWeightsError(null)
    const updates: { id: number; weight: number }[] = []
    for (const [idStr, valStr] of Object.entries(weightDraft)) {
      const w = parseInt(valStr, 10)
      if (!/^\d+$/.test(valStr.trim()) || w < 0) {
        setWeightsError('All weights must be whole numbers >= 0')
        return
      }
      updates.push({ id: parseInt(idStr, 10), weight: w })
    }
    setWeightsSaving(true)
    try {
      const updated = await updateRandomizerWeights(updates)
      setWeights(updated)
      showSuccess('Weights saved')
    } catch (err) {
      setWeightsError(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setWeightsSaving(false)
    }
  }

  async function handleStartSeason() {
    setSeasonError(null)
    setSeasonLoading(true)
    try {
      const result = await startNextSeason()
      setCurrentSeason(result.newSeason)
      setCleanupSelectedSeason(result.newSeason - 1)
      setCleanupConfirm(false)
      showSuccess(`Season ${result.newSeason} started — ${result.playersInitialized} players initialized`)
    } catch (err) {
      setSeasonError(err instanceof Error ? err.message : 'Failed to start season')
    } finally {
      setSeasonLoading(false)
    }
  }

  async function handleCleanup() {
    if (cleanupSelectedSeason === null) return
    if (!cleanupConfirm) { setCleanupConfirm(true); return }
    setSeasonError(null)
    setSeasonLoading(true)
    try {
      const result = await cleanupSeason(cleanupSelectedSeason)
      setCleanupConfirm(false)
      showSuccess(`Cleaned up season ${result.season} — ${result.deletedRows} rows deleted`)
    } catch (err) {
      setSeasonError(err instanceof Error ? err.message : 'Failed to cleanup')
    } finally {
      setSeasonLoading(false)
    }
  }

  function reloadUsers() {
    getUsers().then(setUsers).catch(() => {})
  }

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return players
    return players.filter((p) => p.nickname.toLowerCase().includes(q))
  }, [players, search])

  function showSuccess(msg: string) {
    if (successTimer.current) clearTimeout(successTimer.current)
    setSuccessMsg(msg)
    successTimer.current = setTimeout(() => setSuccessMsg(null), 3000)
  }

  function startEdit(p: PlayerWithStats) {
    setEditingId(p.id)
    setEditState(toEditState(p))
    setDeleteConfirmId(null)
    setError(null)
  }

  function cancelEdit() {
    setEditingId(null)
    setEditState(null)
    setDeleteConfirmId(null)
    setError(null)
  }

  function handleEditChange(field: keyof EditState, value: string) {
    setEditState((prev) => prev ? { ...prev, [field]: value } : prev)
  }

  async function handleSave(id: number) {
    if (!editState) return
    setError(null)

    const errNickname = validateNickname(editState.nickname)
    if (errNickname) { setError(errNickname); return }
    const errNation = validateNation(editState.nation)
    if (errNation) { setError(errNation); return }
    const errRating = validateRating(editState.rating)
    if (errRating) { setError(errRating); return }
    const errDz = validateDzrating(editState.dzrating)
    if (errDz) { setError(errDz); return }
    const errBr = validateBr(editState.br)
    if (errBr) { setError(errBr); return }
    const errPersona = validatePersonaId(editState.personaId, players, id)
    if (errPersona) { setError(errPersona); return }

    const rating = parseInt(editState.rating, 10)
    const dzrating = parseInt(editState.dzrating, 10)
    const br = parseInt(editState.br, 10)
    const nickname = editState.nickname.trim()
    const nation = editState.nation.trim().toLowerCase()
    const personaId = parsePersonaId(editState.personaId)

    setSavingId(id)
    try {
      const req: PlayerUpdateRequest = { nickname, nation, rating, dzrating, br, personaId }
      await updatePlayer(id, req)
      setPlayers((prev) => prev.map((p) => p.id === id ? { ...p, nickname, nation, rating, dzrating, br, personaId } : p))
      setEditingId(null)
      setEditState(null)
      showSuccess(`${nickname} updated`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setSavingId(null)
    }
  }

  async function handleDelete(id: number, nickname: string) {
    if (deleteConfirmId !== id) {
      setDeleteConfirmId(id)
      return
    }
    try {
      await deletePlayer(id)
      setPlayers((prev) => prev.filter((p) => p.id !== id))
      if (editingId === id) cancelEdit()
      showSuccess(`${nickname} deleted`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed')
    } finally {
      setDeleteConfirmId(null)
    }
  }

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault()
    setAddError(null)

    const errNickname = validateNickname(addForm.nickname)
    if (errNickname) { setAddError(errNickname); return }
    const errNation = validateNation(addForm.nation)
    if (errNation) { setAddError(errNation); return }
    const errRating = validateRating(addForm.rating)
    if (errRating) { setAddError(errRating); return }
    const errPersona = validatePersonaId(addForm.personaId, players, null)
    if (errPersona) { setAddError(errPersona); return }

    const rating = parseInt(addForm.rating, 10)
    const nickname = addForm.nickname.trim()
    const nation = addForm.nation.trim().toLowerCase()
    const personaId = parsePersonaId(addForm.personaId)

    setAddLoading(true)
    try {
      const req: PlayerCreateRequest = { nickname, nation, rating, personaId }
      const created = await createPlayer(req)
      const fresh = await getPlayers()
      setPlayers(fresh)
      setAddForm({ nickname: '', nation: '', rating: '', personaId: '' })
      showSuccess(`${created.nickname} added`)
    } catch (err) {
      setAddError(err instanceof Error ? err.message : 'Add failed')
    } finally {
      setAddLoading(false)
    }
  }

  return (
    <section className="section section--menu page-edit">
      {successMsg && (
        <div className="toast toast--success">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <path d="M20 6L9 17l-5-5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          {successMsg}
        </div>
      )}
      <div className="container">
        <div className="table">
          <input
            type="text"
            className="input searchbar__table sound__hover"
            placeholder="Search for names..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <div className="table__list">
            <div className="table__info">
              <h2 className="title title--small">Players</h2>
              <span className="table__counter">{players.length}</span>
            </div>
            {error && <p style={{ padding: '0 20px 10px', color: 'var(--color-red)', fontSize: '1.4rem' }}>{error}</p>}
            <div className="table__hidden" style={{ overflow: 'auto', maxHeight: '40vh' }}>
              <table className="table__content table__search" style={{ minWidth: '100%' }}>
                <thead className="table__head table__sticky">
                  <tr>
                    <th className="head__cell head__player" style={{ width: '30%' }}>Name</th>
                    <th className="head__cell" style={{ width: '8%' }}>Nation</th>
                    <th className="head__cell" style={{ width: '8%' }}>Rating</th>
                    <th className="head__cell" style={{ width: '8%' }}>DZ</th>
                    <th className="head__cell" style={{ width: '8%' }}>BR</th>
                    <th className="head__cell" style={{ width: '18%' }}>Persona ID</th>
                    <th className="head__cell" style={{ width: '12%' }}>Action</th>
                  </tr>
                </thead>
                <tbody className="table__body">
                  {loading ? (
                    <tr><td colSpan={7} style={{ padding: '20px', textAlign: 'center' }}>Loading...</td></tr>
                  ) : filtered.map((p, i) => {
                    const isEditing = editingId === p.id
                    const isSaving = savingId === p.id
                    return (
                      <tr key={p.id}>
                        {isEditing && editState ? (
                          <>
                            <td className="table__cell table__player">
                              <input
                                type="text"
                                maxLength={16}
                                value={editState.nickname}
                                onChange={(e) => handleEditChange('nickname', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell">
                              <input
                                type="text"
                                maxLength={2}
                                value={editState.nation}
                                onChange={(e) => handleEditChange('nation', e.target.value.replace(/[^a-zA-Z]/g, ''))}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell">
                              <input
                                type="number"
                                min={1}
                                max={99}
                                value={editState.rating}
                                onChange={(e) => handleEditChange('rating', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell">
                              <input
                                type="number"
                                min={1}
                                max={99}
                                value={editState.dzrating}
                                onChange={(e) => handleEditChange('dzrating', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell">
                              <input
                                type="number"
                                min={1}
                                max={9999}
                                value={editState.br}
                                onChange={(e) => handleEditChange('br', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell">
                              <input
                                type="text"
                                inputMode="numeric"
                                maxLength={15}
                                placeholder="None"
                                value={editState.personaId}
                                onChange={(e) => handleEditChange('personaId', e.target.value.replace(/\D/g, ''))}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell table__action">
                              <ul className="list">
                                <li>
                                  <button
                                    type="button"
                                    className="btn--save sound__hover sound__click"
                                    onClick={() => handleSave(p.id)}
                                    disabled={isSaving}
                                    aria-label="Save"
                                  />
                                </li>
                                <li>
                                  <button
                                    type="button"
                                    className={`delete sound__hover sound__delete${deleteConfirmId === p.id ? ' delete--confirm' : ''}`}
                                    onClick={() => handleDelete(p.id, p.nickname)}
                                    disabled={isSaving}
                                    title={deleteConfirmId === p.id ? 'Click again to confirm' : `Delete ${p.nickname}`}
                                    aria-label={`Delete ${p.nickname}`}
                                  />
                                </li>
                              </ul>
                            </td>
                          </>
                        ) : (
                          <>
                            <td className="table__cell table__player">
                              <ul className="list list__player">
                                <li>{i + 1}</li>
                                <li><img src={`${FLAG_BASE}/${p.nation || 'aq'}.svg`} alt={p.nation} /></li>
                                <li>
                                  {p.nickname}
                                  {p.lastSeenName && p.lastSeenName !== p.nickname && (
                                    <span
                                      title="Last seen in game as"
                                      style={{ display: 'block', color: 'var(--color-text-muted)', fontSize: '1.2rem' }}
                                    >
                                      {p.lastSeenName}
                                    </span>
                                  )}
                                </li>
                              </ul>
                            </td>
                            <td className="table__cell">{p.nation}</td>
                            <td className="table__cell">{p.rating}</td>
                            <td className="table__cell">{p.dzrating}</td>
                            <td className="table__cell">{p.br}</td>
                            <td className="table__cell">{p.personaId ?? '–'}</td>
                            <td className="table__cell table__action">
                              <ul className="list">
                                <li>
                                  <button
                                    type="button"
                                    className="edit sound__hover sound__click"
                                    onClick={() => startEdit(p)}
                                    aria-label={`Edit ${p.nickname}`}
                                  />
                                </li>
                                <li>
                                  <button
                                    type="button"
                                    className={`delete sound__hover sound__delete${deleteConfirmId === p.id ? ' delete--confirm' : ''}`}
                                    onClick={() => handleDelete(p.id, p.nickname)}
                                    title={deleteConfirmId === p.id ? 'Click again to confirm' : `Delete ${p.nickname}`}
                                    aria-label={`Delete ${p.nickname}`}
                                  />
                                </li>
                              </ul>
                            </td>
                          </>
                        )}
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <form className="add" onSubmit={handleAdd}>
            <div className="add__input">
              <input
                className="input sound__hover"
                type="text"
                maxLength={16}
                placeholder="Name"
                value={addForm.nickname}
                onChange={(e) => setAddForm((f) => ({ ...f, nickname: e.target.value }))}
                disabled={addLoading}
                autoComplete="off"
              />
              <input
                className="input sound__hover"
                type="text"
                maxLength={2}
                placeholder="Nation"
                value={addForm.nation}
                onChange={(e) => setAddForm((f) => ({ ...f, nation: e.target.value.replace(/[^a-zA-Z]/g, '') }))}
                disabled={addLoading}
                autoComplete="off"
              />
              <input
                className="input sound__hover"
                type="number"
                min={1}
                max={99}
                placeholder="Rating"
                value={addForm.rating}
                onChange={(e) => setAddForm((f) => ({ ...f, rating: e.target.value }))}
                disabled={addLoading}
                autoComplete="off"
              />
              <input
                className="input sound__hover"
                type="text"
                inputMode="numeric"
                maxLength={15}
                placeholder="Persona ID"
                title="EA persona ID (optional)"
                value={addForm.personaId}
                onChange={(e) => setAddForm((f) => ({ ...f, personaId: e.target.value.replace(/\D/g, '') }))}
                disabled={addLoading}
                autoComplete="off"
              />
              {addError && <span style={{ color: 'var(--color-red)', fontSize: '1.3rem', alignSelf: 'center', whiteSpace: 'nowrap' }}>{addError}</span>}
            </div>
            <button className="btn btn--transparent sound__hover sound__click" type="submit" disabled={addLoading}>
              {addLoading ? '...' : 'Add'}
            </button>
          </form>
        </div>

        <PlayerRequestsPanel onPlayersChanged={() => getPlayers().then(setPlayers)} onSuccess={showSuccess} />

        {weights && (
          <div className="table randomizer-weights">
            <div className="table__list">
              <div className="table__info">
                <h2 className="title title--small">Randomizer Weights</h2>
              </div>
              <div>
                <div className="randomizer-weights__groups">
                  <WeightGroup label="Maps" entries={weights.maps} draft={weightDraft} onChange={handleWeightChange} />
                  <WeightGroup label="Rules" entries={weights.rules} draft={weightDraft} onChange={handleWeightChange} />
                </div>
                {weightsError && <p style={{ color: 'var(--color-red)', fontSize: '1.3rem', margin: '10px 20px 0' }}>{weightsError}</p>}
                <button
                  type="button"
                  className="btn btn--transparent sound__hover sound__click"
                  style={{ margin: '14px 20px 20px' }}
                  onClick={handleWeightsSave}
                  disabled={weightsSaving}
                >
                  {weightsSaving ? 'Saving...' : 'Save weights'}
                </button>
              </div>
            </div>
          </div>
        )}

        {users !== null && (
          <UsersPanel users={users} currentUserId={currentUser?.id ?? null} onUsersChanged={reloadUsers} onSuccess={showSuccess} />
        )}

        {users !== null && <HostTokensPanel users={users} onSuccess={showSuccess} />}

        {currentSeason !== null && cleanupSelectedSeason !== null && (
          <div className="table">
            <div className="table__list">
              <div className="table__info">
                <h2 className="title title--small">Season Management</h2>
                <span className="table__counter">{currentSeason}</span>
              </div>
              <div style={{ padding: '0 20px 20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <p style={{ fontSize: '1.4rem', margin: 0 }}>
                  Current season: <strong>{currentSeason}</strong>
                </p>
                <div>
                  <button
                    type="button"
                    className="btn btn--transparent sound__hover sound__click"
                    onClick={handleStartSeason}
                    disabled={seasonLoading}
                  >
                    {seasonLoading ? 'Working...' : `Start season ${currentSeason + 1}`}
                  </button>
                </div>
                {currentSeason > 1 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                      <label style={{ fontSize: '1.4rem' }}>
                        Cleanup season:
                        <select
                          value={cleanupSelectedSeason}
                          onChange={(e) => {
                            setCleanupSelectedSeason(Number(e.target.value))
                            setCleanupConfirm(false)
                            setSeasonError(null)
                          }}
                          disabled={seasonLoading}
                          style={{ marginLeft: '8px', fontSize: '1.4rem', background: 'transparent', color: 'inherit', border: '1px solid var(--color-grey)', borderRadius: '4px', padding: '2px 6px' }}
                        >
                          {Array.from({ length: currentSeason - 1 }, (_, i) => i + 1).map((s) => (
                            <option key={s} value={s}>{s}</option>
                          ))}
                        </select>
                      </label>
                      {!cleanupConfirm && (
                        <button
                          type="button"
                          className="btn btn--transparent sound__hover sound__click"
                          onClick={handleCleanup}
                          disabled={seasonLoading}
                        >
                          Cleanup season {cleanupSelectedSeason}
                        </button>
                      )}
                      {cleanupConfirm && (
                        <>
                          <button
                            type="button"
                            className="btn btn--transparent sound__hover sound__click"
                            style={{ outline: '2px solid var(--color-red)', color: 'var(--color-red)' }}
                            onClick={handleCleanup}
                            disabled={seasonLoading}
                          >
                            {seasonLoading ? 'Working...' : `Confirm: delete unplayed rows from season ${cleanupSelectedSeason}`}
                          </button>
                          <button
                            type="button"
                            className="btn btn--transparent sound__hover sound__click"
                            onClick={() => { setCleanupConfirm(false); setSeasonError(null) }}
                            disabled={seasonLoading}
                          >
                            Cancel
                          </button>
                        </>
                      )}
                    </div>
                    {cleanupSelectedSeason !== currentSeason - 1 && (
                      <p style={{ fontSize: '1.3rem', color: 'var(--color-yellow)', margin: 0 }}>
                        Warning: expected season {currentSeason - 1} (previous season). Are you sure you want to clean up season {cleanupSelectedSeason}?
                      </p>
                    )}
                  </div>
                )}
                {seasonError && <p style={{ color: 'var(--color-red)', fontSize: '1.3rem', margin: 0 }}>{seasonError}</p>}
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

function WeightGroup({
  label,
  entries,
  draft,
  onChange,
}: {
  label: string
  entries: RandomizerWeightEntry[]
  draft: Record<number, string>
  onChange: (id: number, value: string) => void
}) {
  const total = entries.reduce((s, e) => s + (parseInt(draft[e.id] ?? '0', 10) || 0), 0)
  return (
    <div className="randomizer-weights__group">
      <h3 className="randomizer-weights__group-title" style={{ paddingLeft: '20px' }}>{label}</h3>
      <table className="table__content">
        <thead className="table__head table__sticky">
          <tr>
            <th className="head__cell head__player" style={{ textAlign: 'left', paddingLeft: '10px' }}>Name</th>
            <th className="head__cell" style={{ width: '100px' }}>Weight</th>
            <th className="head__cell" style={{ width: '80px' }}>%</th>
          </tr>
        </thead>
        <tbody className="table__body">
          {entries.map((e) => {
            const w = parseInt(draft[e.id] ?? '0', 10) || 0
            const pct = total > 0 ? ((w / total) * 100).toFixed(1) : '0.0'
            return (
              <tr key={e.id}>
                <td className="table__cell table__player" style={{ textAlign: 'left' }}>{e.name}</td>
                <td className="table__cell">
                  <input
                    type="number"
                    min={0}
                    value={draft[e.id] ?? '0'}
                    onChange={(ev) => onChange(e.id, ev.target.value)}
                    style={{ width: '70px' }}
                  />
                </td>
                <td className="table__cell" style={{ color: 'var(--color-grey)', fontSize: '1.3rem' }}>{pct}%</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
