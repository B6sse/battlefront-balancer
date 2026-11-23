import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { createRelay, deleteRelay, getRelays, updateRelay } from '../api/admin'
import type { RelayAdminDto, RelayRequest } from '../api/admin'

interface FormState {
  id: string
  region: string
  name: string
  host: string
  port: string
  active: boolean
}

const EMPTY_FORM: FormState = { id: '', region: '', name: '', host: '', port: '25200', active: true }

const fieldStyle = { width: 'calc(100% - 42px)', fontFamily: 'inherit' }
const labelStyle = { display: 'flex', flexDirection: 'column' as const, gap: '4px', fontSize: '1.3rem' }
const mutedStyle = { fontSize: '1.3rem', color: 'var(--color-text-muted)', margin: 0 }

function toForm(r: RelayAdminDto): FormState {
  return { id: r.id, region: r.region, name: r.name, host: r.host, port: String(r.port), active: r.active }
}

function toRequest(f: FormState, active?: boolean): RelayRequest {
  const port = f.port.trim()
  return {
    id: f.id.trim(), region: f.region, name: f.name, host: f.host.trim(),
    port: port === '' ? null : /^\d+$/.test(port) ? Number(port) : -1, active,
  }
}

/**
 * Admin page for the relay servers that forward the game's packets between hosts and players. Active relays are
 * published at GET /api/relays; deactivating one hides it and the game servers running through it.
 */
export function AdminRelaysPage() {
  const [relays, setRelays] = useState<RelayAdminDto[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [editing, setEditing] = useState<RelayAdminDto | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null)
  const [tableError, setTableError] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const successTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    getRelays()
      .then(setRelays)
      .catch((err) => setLoadError(err instanceof Error ? err.message : 'Failed to load relays'))
    return () => { if (successTimer.current) clearTimeout(successTimer.current) }
  }, [])

  function showSuccess(msg: string) {
    setSuccessMsg(msg)
    if (successTimer.current) clearTimeout(successTimer.current)
    successTimer.current = setTimeout(() => setSuccessMsg(null), 3000)
  }

  async function reload() {
    setRelays(await getRelays())
  }

  function setField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function resetForm() {
    setForm(EMPTY_FORM)
    setEditing(null)
    setFormError(null)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    setSaving(true)
    try {
      if (editing) {
        const saved = await updateRelay(editing.id, toRequest(form, form.active))
        showSuccess(`${saved.id} saved`)
      } else {
        const saved = await createRelay(toRequest(form))
        showSuccess(`${saved.id} added`)
      }
      resetForm()
      await reload()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to save relay')
    } finally {
      setSaving(false)
    }
  }

  async function handleToggle(r: RelayAdminDto) {
    setTableError(null)
    setBusyId(r.id)
    try {
      await updateRelay(r.id, toRequest(toForm(r), !r.active))
      await reload()
      showSuccess(`${r.id} ${r.active ? 'deactivated' : 'reactivated'}`)
    } catch (err) {
      setTableError(err instanceof Error ? err.message : 'Failed to change the relay')
    } finally {
      setBusyId(null)
    }
  }

  async function handleDelete(r: RelayAdminDto) {
    if (deleteConfirmId !== r.id) { setDeleteConfirmId(r.id); return }
    setTableError(null)
    setBusyId(r.id)
    try {
      await deleteRelay(r.id)
      if (editing?.id === r.id) resetForm()
      await reload()
      showSuccess(`${r.id} deleted`)
    } catch (err) {
      setTableError(err instanceof Error ? err.message : 'Failed to delete relay')
    } finally {
      setBusyId(null)
      setDeleteConfirmId(null)
    }
  }

  function textInput(key: Exclude<keyof FormState, 'active'>, label: string, opts: { maxLength?: number; placeholder?: string; disabled?: boolean } = {}) {
    return (
      <label style={labelStyle}>
        {label}
        <input
          className="input"
          type="text"
          value={form[key]}
          maxLength={opts.maxLength}
          placeholder={opts.placeholder}
          onChange={(e) => setField(key, e.target.value)}
          disabled={saving || opts.disabled}
          autoComplete="off"
          spellCheck={false}
          style={fieldStyle}
        />
      </label>
    )
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
        <div className="table" style={{ gridColumn: '1 / -1' }}>
          <p style={{ margin: 0, fontSize: '1.4rem' }}>
            <Link to="/admin" className="sound__hover sound__click" style={{ color: 'inherit' }}>← Admin</Link>
          </p>

          <div className="table__list">
            <div className="table__info">
              <h2 className="title title--small">Relays</h2>
              <span className="table__counter" title="Active relays">{relays?.filter((r) => r.active).length ?? 0}</span>
            </div>
            <p style={{ ...mutedStyle, padding: '0 20px 10px' }}>
              Relay servers forward the game's packets, so hosts do not have to open a port. Active relays are published at{' '}
              <code>/api/relays</code>. Deactivating a relay hides it and the game servers running through it; deleting it ends them.
            </p>
            {(loadError || tableError) && (
              <p style={{ padding: '0 20px 10px', color: 'var(--color-red)', fontSize: '1.4rem', margin: 0 }}>{loadError ?? tableError}</p>
            )}
            <div className="table__hidden" style={{ overflow: 'auto', maxHeight: '50vh' }}>
              <table className="table__content" style={{ minWidth: '100%' }}>
                <thead className="table__head table__sticky">
                  <tr>
                    <th className="head__cell head__player">ID</th>
                    <th className="head__cell">Region</th>
                    <th className="head__cell">Name</th>
                    <th className="head__cell">Host</th>
                    <th className="head__cell">Port</th>
                    <th className="head__cell">Live servers</th>
                    <th className="head__cell">Status</th>
                    <th className="head__cell">Action</th>
                  </tr>
                </thead>
                <tbody className="table__body">
                  {relays === null ? (
                    <tr><td colSpan={8} style={{ padding: '20px', textAlign: 'center' }}>{loadError ? '–' : 'Loading...'}</td></tr>
                  ) : relays.length === 0 ? (
                    <tr><td colSpan={8} className="table__cell">No relays yet</td></tr>
                  ) : relays.map((r) => (
                    <tr key={r.id} style={{ opacity: r.active ? 1 : 0.5 }}>
                      <td className="table__cell table__player" style={{ fontFamily: 'monospace' }}>{r.id}</td>
                      <td className="table__cell">{r.region}</td>
                      <td className="table__cell">{r.name}</td>
                      <td className="table__cell" style={{ fontFamily: 'monospace' }}>{r.host}</td>
                      <td className="table__cell">{r.port}</td>
                      <td className="table__cell">{r.liveSessions}</td>
                      <td className="table__cell" style={{ fontWeight: 500 }}>{r.active ? 'Active' : 'Inactive'}</td>
                      <td className="table__cell table__action">
                        <div style={{ display: 'flex', gap: '6px', alignItems: 'center', justifyContent: 'center' }}>
                          <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={() => { setEditing(r); setForm(toForm(r)); setFormError(null) }} disabled={busyId === r.id}>
                            Edit
                          </button>
                          <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={() => handleToggle(r)} disabled={busyId === r.id}>
                            {r.active ? 'Deactivate' : 'Activate'}
                          </button>
                          <button
                            type="button"
                            className={`delete sound__hover sound__delete${deleteConfirmId === r.id ? ' delete--confirm' : ''}`}
                            onClick={() => handleDelete(r)}
                            disabled={busyId === r.id}
                            title={deleteConfirmId === r.id ? 'Click again to delete for good' : `Delete ${r.id}`}
                            aria-label={`Delete ${r.id}`}
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="table__list">
            <div className="table__info">
              <h2 className="title title--small">{editing ? `Edit ${editing.id}` : 'Add Relay'}</h2>
            </div>
            <form onSubmit={handleSubmit} style={{ padding: '0 20px 20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '12px 20px' }}>
                {textInput('id', editing ? 'ID (cannot be changed)' : 'ID (a-z, 0-9 and -)', { maxLength: 20, placeholder: 'eu1', disabled: editing !== null })}
                {textInput('region', 'Region', { maxLength: 10, placeholder: 'EU' })}
                {textInput('name', 'Name', { maxLength: 60, placeholder: 'Europe' })}
                {textInput('host', 'Host (DNS name or IP)', { maxLength: 253, placeholder: 'relay-eu.example.com' })}
                {textInput('port', 'Port', { maxLength: 5, placeholder: '25200' })}
              </div>
              {editing && (
                <label style={{ ...labelStyle, flexDirection: 'row', alignItems: 'center', gap: '8px' }}>
                  <input type="checkbox" checked={form.active} onChange={(e) => setField('active', e.target.checked)} disabled={saving} />
                  Active
                </label>
              )}
              {formError && <p style={{ margin: 0, color: 'var(--color-red)', fontSize: '1.4rem' }}>{formError}</p>}
              <div style={{ display: 'flex', gap: '10px' }}>
                <button className="btn btn--transparent sound__hover sound__click" type="submit" disabled={saving}>
                  {saving ? 'Saving...' : editing ? 'Save' : 'Add relay'}
                </button>
                {editing && (
                  <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={resetForm} disabled={saving}>
                    Cancel
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      </div>
    </section>
  )
}
