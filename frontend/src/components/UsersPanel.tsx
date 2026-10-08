import { useState } from 'react'
import { changeOwnPassword, createUser, deleteUser, resetUserTwoFactor, setUserPassword, updateUserRole } from '../api/admin'
import type { UserDto } from '../api/admin'

const selectStyle = { fontSize: '1.4rem', background: 'transparent', color: 'inherit', border: '1px solid var(--color-grey)', borderRadius: '4px', padding: '2px 6px' }
const smallButton = { fontSize: '1.2rem', padding: '4px 8px' }

/** Same rules as the backend's PasswordPolicy, for an early and clear message. */
function passwordError(password: string): string | null {
  const ok =
    password.length >= 10 &&
    /\p{Lu}/u.test(password) &&
    /\p{Ll}/u.test(password) &&
    /[^\p{L}\p{N}\s]/u.test(password)
  return ok ? null : 'Password must be at least 10 characters and include an uppercase letter, a lowercase letter and a special character'
}

/**
 * Users table for admins: change roles, set passwords and reset two-factor login for supervisors and editors,
 * delete users, add new supervisors/editors, and change the admin's own password. Admin accounts other than your
 * own cannot be changed.
 */
export function UsersPanel({
  users,
  currentUserId,
  onUsersChanged,
  onSuccess,
}: {
  users: UserDto[]
  currentUserId: number | null
  onUsersChanged: () => void
  onSuccess: (msg: string) => void
}) {
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null)
  const [resetConfirmId, setResetConfirmId] = useState<number | null>(null)
  const [passwordEditId, setPasswordEditId] = useState<number | null>(null)
  const [passwordDraft, setPasswordDraft] = useState('')
  const [addForm, setAddForm] = useState({ username: '', password: '', role: 'supervisor' as 'supervisor' | 'editor' })
  const [adding, setAdding] = useState(false)
  const [ownForm, setOwnForm] = useState({ current: '', next: '', code: '' })
  const [ownOpen, setOwnOpen] = useState(false)
  const [ownSaving, setOwnSaving] = useState(false)

  async function run(id: number | null, action: () => Promise<unknown>, message: string) {
    setError(null)
    setBusyId(id)
    try {
      await action()
      onUsersChanged()
      onSuccess(message)
      return true
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
      return false
    } finally {
      setBusyId(null)
      setDeleteConfirmId(null)
      setResetConfirmId(null)
    }
  }

  function handleRoleChange(u: UserDto, role: string) {
    run(u.id, () => updateUserRole(u.id, role), `${u.username} is now ${role}`)
  }

  function handleDelete(u: UserDto) {
    if (deleteConfirmId !== u.id) { setDeleteConfirmId(u.id); return }
    run(u.id, () => deleteUser(u.id), `${u.username} removed`)
  }

  function handleResetTwoFactor(u: UserDto) {
    if (resetConfirmId !== u.id) { setResetConfirmId(u.id); return }
    run(u.id, () => resetUserTwoFactor(u.id), `Two-factor login reset for ${u.username}`)
  }

  async function handleSetPassword(u: UserDto) {
    const err = passwordError(passwordDraft)
    if (err) { setError(err); return }
    if (await run(u.id, () => setUserPassword(u.id, passwordDraft), `Password set for ${u.username}`)) {
      setPasswordEditId(null)
      setPasswordDraft('')
    }
  }

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault()
    const username = addForm.username.trim()
    if (!/^[A-Za-z0-9_.-]{3,32}$/.test(username)) { setError("Username must be 3–32 characters: letters, digits, '.', '_' or '-'"); return }
    const err = passwordError(addForm.password)
    if (err) { setError(err); return }
    setAdding(true)
    if (await run(null, () => createUser(username, addForm.password, addForm.role), `${username} added as ${addForm.role}`)) {
      setAddForm({ username: '', password: '', role: addForm.role })
    }
    setAdding(false)
  }

  async function handleOwnPassword(e: React.FormEvent) {
    e.preventDefault()
    const err = passwordError(ownForm.next)
    if (err) { setError(err); return }
    if (!/^\d{6}$/.test(ownForm.code.trim())) { setError('Enter the 6-digit code from your authenticator app'); return }
    setOwnSaving(true)
    if (await run(null, () => changeOwnPassword(ownForm.current, ownForm.next, ownForm.code.trim()), 'Your password was changed')) {
      setOwnForm({ current: '', next: '', code: '' })
      setOwnOpen(false)
    }
    setOwnSaving(false)
  }

  return (
    <div className="table">
      <div className="table__list">
        <div className="table__info">
          <h2 className="title title--small">Users</h2>
          <span className="table__counter">{users.length}</span>
        </div>
        {error && <p style={{ padding: '0 20px 10px', color: 'var(--color-red)', fontSize: '1.4rem' }}>{error}</p>}
        <div className="table__hidden" style={{ overflow: 'auto', maxHeight: '40vh' }}>
          <table className="table__content table__search" style={{ minWidth: '100%' }}>
            <thead className="table__head table__sticky">
              <tr>
                <th className="head__cell head__player" style={{ width: '28%' }}>Username</th>
                <th className="head__cell" style={{ width: '20%' }}>Role</th>
                <th className="head__cell" style={{ width: '14%' }}>2FA</th>
                <th className="head__cell" style={{ width: '38%' }}>Action</th>
              </tr>
            </thead>
            <tbody className="table__body">
              {users.map((u) => {
                const isCurrentUser = currentUserId === u.id
                const isAdmin = u.role === 'admin'
                const canEdit = !isAdmin && !isCurrentUser
                const busy = busyId === u.id
                const needsTwoFactor = u.role === 'admin' || u.role === 'editor'
                return (
                  <tr key={u.id}>
                    <td className="table__cell table__player">
                      {isCurrentUser && <span style={{ color: 'var(--color-yellow)', marginRight: '6px' }}>▶</span>}{u.username}
                    </td>
                    <td className="table__cell">
                      {canEdit ? (
                        <select value={u.role} disabled={busy} onChange={(e) => handleRoleChange(u, e.target.value)} style={{ ...selectStyle, opacity: busy ? 0.5 : 1 }}>
                          <option value="editor">editor</option>
                          <option value="supervisor">supervisor</option>
                        </select>
                      ) : (
                        <span style={{ color: isAdmin ? 'inherit' : 'var(--color-grey)' }}>{u.role}</span>
                      )}
                    </td>
                    <td className="table__cell" style={{ fontSize: '1.3rem' }}>
                      {!needsTwoFactor ? (
                        <span style={{ color: 'var(--color-text-muted)' }}>–</span>
                      ) : u.twoFactorEnabled ? (
                        <span>✓ On</span>
                      ) : (
                        <span style={{ fontStyle: 'italic' }} title="Will be asked to set up at next login">Not set up</span>
                      )}
                    </td>
                    <td className="table__cell table__action">
                      {canEdit && passwordEditId === u.id ? (
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                          <input
                            type="password"
                            placeholder="New password"
                            value={passwordDraft}
                            onChange={(e) => setPasswordDraft(e.target.value)}
                            disabled={busy}
                            autoComplete="new-password"
                            style={{ minWidth: 0, flex: 1 }}
                          />
                          <button type="button" className="btn btn--transparent sound__hover sound__click" style={smallButton} disabled={busy} onClick={() => handleSetPassword(u)}>Save</button>
                          <button type="button" className="btn btn--transparent sound__hover sound__click" style={smallButton} disabled={busy} onClick={() => { setPasswordEditId(null); setPasswordDraft('') }}>Cancel</button>
                        </div>
                      ) : canEdit && (
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center', justifyContent: 'flex-end' }}>
                          <button type="button" className="btn btn--transparent sound__hover sound__click" style={smallButton} disabled={busy} onClick={() => { setPasswordEditId(u.id); setPasswordDraft(''); setError(null) }}>
                            Set password
                          </button>
                          {u.twoFactorEnabled && (
                            <button
                              type="button"
                              className="btn btn--transparent sound__hover sound__click"
                              style={resetConfirmId === u.id ? { ...smallButton, outline: '2px solid var(--color-red)', color: 'var(--color-red)' } : smallButton}
                              disabled={busy}
                              onClick={() => handleResetTwoFactor(u)}
                              title="For a lost phone: they set up two-factor again at next login"
                            >
                              {resetConfirmId === u.id ? 'Confirm reset' : 'Reset 2FA'}
                            </button>
                          )}
                          <button
                            type="button"
                            className={`delete sound__hover sound__delete${deleteConfirmId === u.id ? ' delete--confirm' : ''}`}
                            onClick={() => handleDelete(u)}
                            disabled={busy}
                            title={deleteConfirmId === u.id ? 'Click again to confirm' : `Remove ${u.username}`}
                            aria-label={`Remove ${u.username}`}
                          />
                        </div>
                      )}
                      {isCurrentUser && !ownOpen && (
                        <button type="button" className="btn btn--transparent sound__hover sound__click" style={smallButton} onClick={() => { setOwnOpen(true); setError(null) }}>
                          Change my password
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {ownOpen && (
          <form onSubmit={handleOwnPassword} style={{ padding: '14px 20px', display: 'flex', flexDirection: 'column', gap: 10, borderTop: '1px solid var(--color-border)' }}>
            <h3 className="title title--small" style={{ margin: 0 }}>Change my password</h3>
            <div className="add__input">
              <input className="input" type="password" placeholder="Current password" autoComplete="current-password" value={ownForm.current} onChange={(e) => setOwnForm((f) => ({ ...f, current: e.target.value }))} disabled={ownSaving} />
              <input className="input" type="password" placeholder="New password" autoComplete="new-password" value={ownForm.next} onChange={(e) => setOwnForm((f) => ({ ...f, next: e.target.value }))} disabled={ownSaving} />
              <input className="input" type="text" inputMode="numeric" placeholder="App code" autoComplete="one-time-code" maxLength={6} value={ownForm.code} onChange={(e) => setOwnForm((f) => ({ ...f, code: e.target.value.replace(/\D/g, '') }))} disabled={ownSaving} />
            </div>
            <div style={{ display: 'flex', gap: 10 }}>
              <button type="submit" className="btn btn--transparent sound__hover sound__click" disabled={ownSaving}>{ownSaving ? 'Saving...' : 'Save'}</button>
              <button type="button" className="btn btn--transparent sound__hover sound__click" disabled={ownSaving} onClick={() => { setOwnOpen(false); setOwnForm({ current: '', next: '', code: '' }) }}>Cancel</button>
            </div>
          </form>
        )}
      </div>

      <form className="add" onSubmit={handleAdd}>
        <div className="add__input">
          <input className="input sound__hover" type="text" maxLength={32} placeholder="New username" autoComplete="off" value={addForm.username} onChange={(e) => setAddForm((f) => ({ ...f, username: e.target.value }))} disabled={adding} />
          <input className="input sound__hover" type="password" placeholder="Password" autoComplete="new-password" value={addForm.password} onChange={(e) => setAddForm((f) => ({ ...f, password: e.target.value }))} disabled={adding} />
          <select value={addForm.role} onChange={(e) => setAddForm((f) => ({ ...f, role: e.target.value as 'supervisor' | 'editor' }))} disabled={adding} style={selectStyle}>
            <option value="supervisor">supervisor</option>
            <option value="editor">editor</option>
          </select>
        </div>
        <button className="btn btn--transparent sound__hover sound__click" type="submit" disabled={adding} style={{ whiteSpace: 'nowrap' }}>
          {adding ? '...' : 'Add user'}
        </button>
      </form>
    </div>
  )
}
