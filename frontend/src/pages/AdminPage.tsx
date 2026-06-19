import { useState, useEffect, useMemo, useRef } from 'react'
import { getPlayers, createPlayer, updatePlayer, deletePlayer } from '../api/players'
import type { PlayerCreateRequest, PlayerUpdateRequest } from '../api/players'
import type { PlayerWithStats } from '../types'
const FLAG_BASE = 'https://cdnjs.cloudflare.com/ajax/libs/flag-icon-css/2.8.0/flags/4x3'

interface EditState {
  nickname: string
  nation: string
  rating: string
  dzrating: string
  br: string
}

function toEditState(p: PlayerWithStats): EditState {
  return {
    nickname: p.nickname,
    nation: p.nation,
    rating: String(p.rating),
    dzrating: String(p.dzrating),
    br: String(p.br),
  }
}

export function AdminPage() {
  const [players, setPlayers] = useState<PlayerWithStats[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editState, setEditState] = useState<EditState | null>(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null)
  const [savingId, setSavingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const [addForm, setAddForm] = useState({ nickname: '', nation: '', rating: '' })
  const [addError, setAddError] = useState<string | null>(null)
  const [addLoading, setAddLoading] = useState(false)

  const successTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    getPlayers()
      .then(setPlayers)
      .finally(() => setLoading(false))
  }, [])

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
    const rating = parseInt(editState.rating)
    const dzrating = parseInt(editState.dzrating)
    const br = parseInt(editState.br)
    if (!editState.nickname.trim()) { setError('Nickname required'); return }
    if (!editState.nation.trim() || editState.nation.trim().length !== 2) { setError('Nation must be 2 letters'); return }
    if (isNaN(rating) || rating < 1 || rating > 99) { setError('Rating must be 1–99'); return }
    if (isNaN(dzrating) || dzrating < 1 || dzrating > 99) { setError('DZ must be 1–99'); return }
    if (isNaN(br) || br < 1 || br > 9999) { setError('BR must be 1–9999'); return }

    setSavingId(id)
    try {
      const req: PlayerUpdateRequest = {
        nickname: editState.nickname.trim(),
        nation: editState.nation.trim().toLowerCase(),
        rating,
        dzrating,
        br,
      }
      const updated = await updatePlayer(id, req)
      setPlayers((prev) => prev.map((p) => p.id === id ? { ...p, ...updated } : p))
      setEditingId(null)
      setEditState(null)
      showSuccess(`${updated.nickname} updated`)
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
    const rating = parseInt(addForm.rating)
    if (!addForm.nickname.trim()) { setAddError('Nickname required'); return }
    if (!addForm.nation.trim() || addForm.nation.trim().length !== 2) { setAddError('Nation must be 2 letters'); return }
    if (isNaN(rating) || rating < 1 || rating > 99) { setAddError('Rating must be 1–99'); return }

    setAddLoading(true)
    try {
      const req: PlayerCreateRequest = {
        nickname: addForm.nickname.trim(),
        nation: addForm.nation.trim().toLowerCase(),
        rating,
      }
      const created = await createPlayer(req)
      setPlayers((prev) => {
        const next = [...prev, { ...created, br: created.br ?? 0, dzrating: created.dzrating ?? rating, played: 0, best: 0, won: 0, lost: 0, draw: 0, score: 0, mvp: 0 }]
        return next.sort((a, b) => b.rating - a.rating || a.nickname.localeCompare(b.nickname))
      })
      setAddForm({ nickname: '', nation: '', rating: '' })
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
            <div className="table__hidden table__scroll" style={{ height: 'auto', maxHeight: '65vh' }}>
              <table className="table__content table__search">
                <thead className="table__head table__sticky">
                  <tr>
                    <th className="head__cell head__player head__xxl">Name</th>
                    <th className="head__cell head__xxs">Nation</th>
                    <th className="head__cell head__xxs">Rating</th>
                    <th className="head__cell head__xxs">DZ</th>
                    <th className="head__cell head__xxs">BR</th>
                    <th className="head__cell head__xxs">Action</th>
                  </tr>
                </thead>
                <tbody className="table__body">
                  {loading ? (
                    <tr><td colSpan={6} style={{ padding: '20px', textAlign: 'center' }}>Loading...</td></tr>
                  ) : filtered.map((p, i) => {
                    const isEditing = editingId === p.id
                    const isSaving = savingId === p.id
                    return (
                      <tr key={p.id}>
                        {isEditing && editState ? (
                          <>
                            <td className="table__cell table__player table__xxl">
                              <input
                                type="text"
                                maxLength={16}
                                value={editState.nickname}
                                onChange={(e) => handleEditChange('nickname', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell table__xxs">
                              <input
                                type="text"
                                maxLength={2}
                                value={editState.nation}
                                onChange={(e) => handleEditChange('nation', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell table__xxs">
                              <input
                                type="number"
                                min={1}
                                max={99}
                                value={editState.rating}
                                onChange={(e) => handleEditChange('rating', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell table__xxs">
                              <input
                                type="number"
                                min={1}
                                max={99}
                                value={editState.dzrating}
                                onChange={(e) => handleEditChange('dzrating', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell table__xxs">
                              <input
                                type="number"
                                min={1}
                                max={9999}
                                value={editState.br}
                                onChange={(e) => handleEditChange('br', e.target.value)}
                                disabled={isSaving}
                              />
                            </td>
                            <td className="table__cell table__action table__xxs">
                              <ul className="list">
                                <li>
                                  <button
                                    type="button"
                                    className="btn btn--save sound__hover sound__click"
                                    onClick={() => handleSave(p.id)}
                                    disabled={isSaving}
                                    aria-label="Save"
                                  />
                                </li>
                                <li>
                                  <button
                                    type="button"
                                    className={`sound__hover sound__delete delete${deleteConfirmId === p.id ? ' delete--confirm' : ''}`}
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
                            <td className="table__cell table__player table__xxl">
                              <ul className="list list__player">
                                <li>{i + 1}</li>
                                <li><img src={`${FLAG_BASE}/${p.nation || 'aq'}.svg`} alt={p.nation} /></li>
                                <li>{p.nickname}</li>
                              </ul>
                            </td>
                            <td className="table__cell table__xxs">{p.nation}</td>
                            <td className="table__cell table__xxs">{p.rating}</td>
                            <td className="table__cell table__xxs">{p.dzrating}</td>
                            <td className="table__cell table__xxs">{p.br}</td>
                            <td className="table__cell table__action table__xxs">
                              <ul className="list">
                                <li>
                                  <button
                                    type="button"
                                    className="sound__hover sound__click edit"
                                    onClick={() => startEdit(p)}
                                    aria-label={`Edit ${p.nickname}`}
                                  />
                                </li>
                                <li>
                                  <button
                                    type="button"
                                    className={`sound__hover sound__delete delete${deleteConfirmId === p.id ? ' delete--confirm' : ''}`}
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
            <div className="add__input" style={{ flexDirection: 'row' }}>
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
                onChange={(e) => setAddForm((f) => ({ ...f, nation: e.target.value }))}
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
              {addError && <span style={{ color: 'var(--color-red)', fontSize: '1.3rem', alignSelf: 'center' }}>{addError}</span>}
            </div>
            <button className="btn btn--transparent sound__hover sound__click" type="submit" disabled={addLoading}>
              {addLoading ? '...' : 'Add'}
            </button>
          </form>
        </div>
      </div>
    </section>
  )
}
