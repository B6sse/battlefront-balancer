import { useEffect, useMemo, useState } from 'react'
import { approvePlayerRequest, getPlayerRequests, linkPlayerRequest, rejectPlayerRequest } from '../api/playerRequests'
import type { PlayerRequestDto } from '../api/playerRequests'
import { getInternPlayers } from '../api/players'
import type { PlayerWithStats } from '../types'

const selectStyle = { fontSize: '1.4rem', background: 'transparent', color: 'inherit', border: '1px solid var(--color-grey)', borderRadius: '4px', padding: '2px 6px' }

interface Draft {
  nickname: string
  nation: string
  rating: string
  linkTo: string
}

function toDraft(r: PlayerRequestDto): Draft {
  return { nickname: r.nickname, nation: r.nation, rating: String(r.rating), linkTo: '' }
}

/**
 * Pending player requests from Auric hosts, for admins and editors. Each request can be approved (after correcting
 * the fields), linked to an existing player that has no persona ID yet, or rejected. Hidden for other roles.
 */
export function PlayerRequestsPanel({ onPlayersChanged, onSuccess }: { onPlayersChanged: () => void; onSuccess: (msg: string) => void }) {
  const [requests, setRequests] = useState<PlayerRequestDto[] | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [allPlayers, setAllPlayers] = useState<PlayerWithStats[]>([])
  const [drafts, setDrafts] = useState<Record<number, Draft>>({})
  const [busyId, setBusyId] = useState<number | null>(null)
  const [rejectConfirmId, setRejectConfirmId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  function load() {
    return getPlayerRequests()
      .then((list) => {
        setRequests(list)
        setDrafts(Object.fromEntries(list.map((r) => [r.id, toDraft(r)])))
      })
      .catch(() => setForbidden(true))
  }

  useEffect(() => {
    load()
    getInternPlayers().then(setAllPlayers).catch(() => {})
  }, [])

  const linkable = useMemo(
    () => allPlayers.filter((p) => p.personaId === null).sort((a, b) => a.nickname.localeCompare(b.nickname)),
    [allPlayers],
  )

  if (forbidden) return null

  function setDraft(id: number, field: keyof Draft, value: string) {
    setDrafts((prev) => ({ ...prev, [id]: { ...prev[id], [field]: value } }))
  }

  async function run(r: PlayerRequestDto, action: () => Promise<unknown>, message: string, playersChanged: boolean) {
    setError(null)
    setBusyId(r.id)
    try {
      await action()
      await load()
      if (playersChanged) {
        onPlayersChanged()
        getInternPlayers().then(setAllPlayers).catch(() => {})
      }
      onSuccess(message)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed')
    } finally {
      setBusyId(null)
      setRejectConfirmId(null)
    }
  }

  function handleApprove(r: PlayerRequestDto) {
    const d = drafts[r.id]
    const nickname = d.nickname.trim()
    const nation = d.nation.trim().toLowerCase()
    const rating = parseInt(d.rating, 10)
    if (!nickname || nickname.length > 16) { setError('Nickname must be 1–16 characters'); return }
    if (!/^[a-z]{2}$/.test(nation)) { setError('Nation must be exactly 2 letters (a–z)'); return }
    if (!(rating >= 1 && rating <= 99)) { setError('Rating must be 1–99'); return }
    run(r, () => approvePlayerRequest(r.id, { nickname, nation, rating }), `${nickname} added`, true)
  }

  function handleLink(r: PlayerRequestDto) {
    const playerId = parseInt(drafts[r.id].linkTo, 10)
    const target = linkable.find((p) => p.id === playerId)
    if (!target) { setError('Choose a player to link to'); return }
    run(r, () => linkPlayerRequest(r.id, playerId), `Persona ID linked to ${target.nickname}`, true)
  }

  function handleReject(r: PlayerRequestDto) {
    if (rejectConfirmId !== r.id) { setRejectConfirmId(r.id); return }
    run(r, () => rejectPlayerRequest(r.id), `Request for ${r.nickname} rejected`, false)
  }

  return (
    <div className="table">
      <div className="table__list">
        <div className="table__info">
          <h2 className="title title--small">Player Requests</h2>
          <span className="table__counter">{requests?.length ?? 0}</span>
        </div>
        {error && <p style={{ padding: '0 20px 10px', color: 'var(--color-red)', fontSize: '1.4rem' }}>{error}</p>}
        {requests === null ? (
          <p style={{ padding: '0 20px 20px', fontSize: '1.4rem' }}>Loading...</p>
        ) : requests.length === 0 ? (
          <p style={{ padding: '0 20px 20px', fontSize: '1.4rem', color: 'var(--color-text-muted)' }}>No pending requests</p>
        ) : (
          <div style={{ padding: '0 20px 20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {requests.map((r) => {
              const d = drafts[r.id] ?? toDraft(r)
              const busy = busyId === r.id
              return (
                <div key={r.id} style={{ border: '1px solid var(--color-border)', borderRadius: '4px', padding: '12px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  <p style={{ margin: 0, fontSize: '1.3rem' }}>
                    Persona ID <strong>{r.personaId}</strong>
                    {r.inGameName && <> · in game as <strong>{r.inGameName}</strong></>}
                    <span style={{ color: 'var(--color-text-muted)' }}>
                      {' · '}requested {r.requestedAt.slice(0, 16).replace('T', ' ')}
                      {r.requestedBy && <> by {r.requestedBy}</>}
                    </span>
                  </p>
                  <div className="add__input">
                    <input className="input" type="text" maxLength={16} placeholder="Name" value={d.nickname} disabled={busy}
                      onChange={(e) => setDraft(r.id, 'nickname', e.target.value)} />
                    <input className="input" type="text" maxLength={2} placeholder="Nation" value={d.nation} disabled={busy}
                      onChange={(e) => setDraft(r.id, 'nation', e.target.value.replace(/[^a-zA-Z]/g, ''))} />
                    <input className="input" type="number" min={1} max={99} placeholder="Rating" value={d.rating} disabled={busy}
                      onChange={(e) => setDraft(r.id, 'rating', e.target.value)} />
                    <button type="button" className="btn btn--transparent sound__hover sound__click" disabled={busy} onClick={() => handleApprove(r)}>
                      Approve
                    </button>
                  </div>
                  <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
                    <select value={d.linkTo} disabled={busy} onChange={(e) => setDraft(r.id, 'linkTo', e.target.value)} style={selectStyle}>
                      <option value="">Or link to existing player...</option>
                      {linkable.map((p) => (
                        <option key={p.id} value={p.id}>{p.nickname} ({p.nation})</option>
                      ))}
                    </select>
                    <button type="button" className="btn btn--transparent sound__hover sound__click" disabled={busy || !d.linkTo} onClick={() => handleLink(r)}>
                      Link
                    </button>
                    <button
                      type="button"
                      className="btn btn--transparent sound__hover sound__click"
                      style={rejectConfirmId === r.id ? { outline: '2px solid var(--color-red)', color: 'var(--color-red)' } : undefined}
                      disabled={busy}
                      onClick={() => handleReject(r)}
                    >
                      {rejectConfirmId === r.id ? 'Confirm reject' : 'Reject'}
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
