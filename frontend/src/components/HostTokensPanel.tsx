import { useEffect, useState } from 'react'
import { createHostToken, getHostTokens, revokeHostToken } from '../api/admin'
import type { HostTokenCreated, HostTokenDto, UserDto } from '../api/admin'

const selectStyle = { fontSize: '1.4rem', background: 'transparent', color: 'inherit', border: '1px solid var(--color-grey)', borderRadius: '4px', padding: '2px 6px' }

function formatDate(iso: string | null): string {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '–'
}

/**
 * Admin panel for Auric host tokens: create (token shown once), list and revoke.
 * Matches uploaded with a token get the token's owner as supervisor. Admins, supervisors and editors can own one.
 */
export function HostTokensPanel({ users, onSuccess }: { users: UserDto[]; onSuccess: (msg: string) => void }) {
  const [tokens, setTokens] = useState<HostTokenDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [ownerId, setOwnerId] = useState('')
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState<HostTokenCreated | null>(null)
  const [copied, setCopied] = useState(false)
  const [revokeConfirmId, setRevokeConfirmId] = useState<number | null>(null)

  const owners = users.filter((u) => u.role === 'admin' || u.role === 'supervisor' || u.role === 'editor')

  useEffect(() => {
    getHostTokens()
      .then(setTokens)
      .catch(() => setError('Failed to load host tokens'))
  }, [])

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    const trimmed = name.trim()
    if (!trimmed) { setError('Name is required'); return }
    if (!ownerId) { setError('Choose an owner'); return }

    setCreating(true)
    try {
      const result = await createHostToken(trimmed, parseInt(ownerId, 10))
      setCreated(result)
      setCopied(false)
      setName('')
      setOwnerId('')
      setTokens(await getHostTokens())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create token')
    } finally {
      setCreating(false)
    }
  }

  async function handleCopy() {
    if (!created) return
    try {
      await navigator.clipboard.writeText(created.token)
      setCopied(true)
    } catch {
      setError('Copy failed — select the token and copy it manually')
    }
  }

  async function handleRevoke(token: HostTokenDto) {
    if (revokeConfirmId !== token.id) {
      setRevokeConfirmId(token.id)
      return
    }
    setError(null)
    try {
      await revokeHostToken(token.id)
      setTokens(await getHostTokens())
      onSuccess(`${token.name} revoked`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to revoke token')
    } finally {
      setRevokeConfirmId(null)
    }
  }

  return (
    <div className="table">
      <div className="table__list">
        <div className="table__info">
          <h2 className="title title--small">Host Tokens</h2>
          <span className="table__counter">{tokens?.filter((t) => !t.revokedAt).length ?? 0}</span>
        </div>
        <p style={{ padding: '0 20px 10px', fontSize: '1.3rem', color: 'var(--color-text-muted)', margin: 0 }}>
          Tokens let an Auric host balance teams and upload matches. Matches uploaded with a token get its owner as supervisor.
        </p>
        {error && <p style={{ padding: '0 20px 10px', color: 'var(--color-red)', fontSize: '1.4rem' }}>{error}</p>}

        {created && (
          <div style={{ margin: '0 20px 14px', padding: '12px', border: '1px solid var(--color-yellow)', borderRadius: '4px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <p style={{ margin: 0, fontSize: '1.4rem' }}>
              Token for {created.name} ({created.username}). <strong>Copy it now — it will not be shown again.</strong>
            </p>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              <input
                className="input"
                type="text"
                readOnly
                value={created.token}
                onFocus={(e) => e.target.select()}
                style={{ flex: 1, fontFamily: 'monospace' }}
              />
              <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={handleCopy}>
                {copied ? 'Copied' : 'Copy'}
              </button>
              <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={() => setCreated(null)}>
                Done
              </button>
            </div>
          </div>
        )}

        <div className="table__hidden" style={{ overflow: 'auto', maxHeight: '40vh' }}>
          <table className="table__content" style={{ minWidth: '100%' }}>
            <thead className="table__head table__sticky">
              <tr>
                <th className="head__cell head__player" style={{ width: '25%' }}>Name</th>
                <th className="head__cell" style={{ width: '15%' }}>Owner</th>
                <th className="head__cell" style={{ width: '20%' }}>Created</th>
                <th className="head__cell" style={{ width: '20%' }}>Last used</th>
                <th className="head__cell" style={{ width: '10%' }}>Status</th>
                <th className="head__cell" style={{ width: '10%' }}>Action</th>
              </tr>
            </thead>
            <tbody className="table__body">
              {tokens === null ? (
                <tr><td colSpan={6} style={{ padding: '20px', textAlign: 'center' }}>Loading...</td></tr>
              ) : tokens.length === 0 ? (
                <tr><td colSpan={6} className="table__cell">No host tokens yet</td></tr>
              ) : tokens.map((t) => (
                <tr key={t.id} style={{ opacity: t.revokedAt ? 0.5 : 1 }}>
                  <td className="table__cell table__player">{t.name}</td>
                  <td className="table__cell">{t.username ?? '–'}</td>
                  <td className="table__cell">{formatDate(t.createdAt)}</td>
                  <td className="table__cell">{formatDate(t.lastUsedAt)}</td>
                  <td className="table__cell" style={{ color: t.revokedAt ? 'var(--color-red)' : 'var(--color-green)' }}>
                    {t.revokedAt ? 'Revoked' : 'Active'}
                  </td>
                  <td className="table__cell table__action">
                    {!t.revokedAt && (
                      <ul className="list">
                        <li>
                          <button
                            type="button"
                            className={`delete sound__hover sound__delete${revokeConfirmId === t.id ? ' delete--confirm' : ''}`}
                            onClick={() => handleRevoke(t)}
                            title={revokeConfirmId === t.id ? 'Click again to confirm' : `Revoke ${t.name}`}
                            aria-label={`Revoke ${t.name}`}
                          />
                        </li>
                      </ul>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <form className="add" onSubmit={handleCreate}>
        <div className="add__input">
          <input
            className="input sound__hover"
            type="text"
            maxLength={100}
            placeholder="Token name, e.g. host's PC"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={creating}
            autoComplete="off"
          />
          <select value={ownerId} onChange={(e) => setOwnerId(e.target.value)} disabled={creating} style={selectStyle}>
            <option value="">Owner...</option>
            {owners.map((u) => (
              <option key={u.id} value={u.id}>{u.username} ({u.role})</option>
            ))}
          </select>
        </div>
        <button className="btn btn--transparent sound__hover sound__click" type="submit" disabled={creating}>
          {creating ? '...' : 'Create'}
        </button>
      </form>
    </div>
  )
}
