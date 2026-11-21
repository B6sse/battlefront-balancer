import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { createApprovedMod, deleteApprovedMod, getApprovedMods, updateApprovedMod } from '../api/admin'
import type { ApprovedModDto, ApprovedModRequest } from '../api/admin'

/** The fields the signing tool prints, in its order. */
const SIGNED_FIELDS = ['title', 'version', 'author', 'category', 'contentSha256', 'contentSize', 'keyId', 'axSha256', 'axSize'] as const

interface FormState {
  title: string
  version: string
  author: string
  category: string
  contentSha256: string
  contentSize: string
  keyId: string
  axSha256: string
  axSize: string
  downloadUrl: string
  notes: string
  active: boolean
}

const EMPTY_FORM: FormState = {
  title: '', version: '', author: '', category: 'OPTIONAL', contentSha256: '', contentSize: '', keyId: '',
  axSha256: '', axSize: '', downloadUrl: '', notes: '', active: true,
}

const LABELS: Record<keyof FormState, string> = {
  title: 'Title', version: 'Version', author: 'Author', category: 'Category', contentSha256: 'Content SHA-256',
  contentSize: 'Content size (bytes)', keyId: 'Key ID', axSha256: 'Signed file SHA-256', axSize: 'Signed file size (bytes)',
  downloadUrl: 'Download URL (https, optional)', notes: 'Notes (admins only, optional)', active: 'Active',
}

const fieldStyle = { width: 'calc(100% - 42px)', fontFamily: 'inherit' }
const monoStyle = { ...fieldStyle, fontFamily: 'monospace' }
const labelStyle = { display: 'flex', flexDirection: 'column' as const, gap: '4px', fontSize: '1.3rem' }
const mutedStyle = { fontSize: '1.3rem', color: 'var(--color-text-muted)', margin: 0 }

function toForm(m: ApprovedModDto): FormState {
  return {
    title: m.title, version: m.version, author: m.author, category: m.category, contentSha256: m.contentSha256,
    contentSize: String(m.contentSize), keyId: m.keyId, axSha256: m.axSha256, axSize: String(m.axSize),
    downloadUrl: m.downloadUrl ?? '', notes: m.notes ?? '', active: m.active,
  }
}

function toNumber(v: string): number | null {
  const t = v.trim()
  return /^\d+$/.test(t) ? Number(t) : null
}

function toRequest(f: FormState, active?: boolean): ApprovedModRequest {
  return {
    title: f.title, version: f.version, author: f.author, category: f.category, contentSha256: f.contentSha256.trim(),
    contentSize: toNumber(f.contentSize), keyId: f.keyId, axSha256: f.axSha256.trim(), axSize: toNumber(f.axSize),
    downloadUrl: f.downloadUrl.trim() || null, notes: f.notes.trim() || null, active,
  }
}

/**
 * Reads one entry printed by the signing tool. Unknown keys are ignored.
 *
 * @returns the signed fields as form values, and the names of signed fields that were missing
 * @throws Error with a readable message if the text is not one JSON object
 */
function parseSignedEntry(text: string): { values: Partial<FormState>; missing: string[] } {
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('That is not valid JSON. Paste the whole entry the signing tool printed, including { and }.')
  }
  if (Array.isArray(parsed)) {
    if (parsed.length !== 1) throw new Error('Paste one entry at a time.')
    parsed = parsed[0]
  }
  if (parsed === null || typeof parsed !== 'object') throw new Error('Expected a JSON object like { "title": ... }.')
  const obj = parsed as Record<string, unknown>
  const values: Partial<FormState> = {}
  const missing: string[] = []
  for (const key of SIGNED_FIELDS) {
    const v = obj[key]
    if (v === undefined || v === null) missing.push(key)
    else values[key] = String(v)
  }
  return { values, missing }
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let v = n / 1024
  let i = 0
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++ }
  return `${v.toFixed(v < 10 ? 1 : 0)} ${units[i]}`
}

/**
 * Admin page for the approved mods list. Mods are approved and signed on the owner's own machine; here an admin pastes
 * the JSON the signing tool prints, adds a download link and notes, and can edit, deactivate or delete entries.
 * Active entries are published at GET /api/mods/approved for Auric hosts and the launcher.
 */
export function AdminModsPage() {
  const [mods, setMods] = useState<ApprovedModDto[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [editing, setEditing] = useState<ApprovedModDto | null>(null)
  const [pasted, setPasted] = useState('')
  const [pasteMessage, setPasteMessage] = useState<{ text: string; error: boolean } | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null)
  const [tableError, setTableError] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const successTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const formRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    getApprovedMods()
      .then(setMods)
      .catch((err) => setLoadError(err instanceof Error ? err.message : 'Failed to load mods'))
    return () => { if (successTimer.current) clearTimeout(successTimer.current) }
  }, [])

  function showSuccess(msg: string) {
    setSuccessMsg(msg)
    if (successTimer.current) clearTimeout(successTimer.current)
    successTimer.current = setTimeout(() => setSuccessMsg(null), 3000)
  }

  async function reload() {
    setMods(await getApprovedMods())
  }

  function setField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  function fillFromPaste(text: string) {
    if (!text.trim()) { setPasteMessage(null); return }
    try {
      const { values, missing } = parseSignedEntry(text)
      setForm((f) => ({ ...f, ...values }))
      setFormError(null)
      setPasteMessage(missing.length
        ? { text: `Filled in. Missing from the pasted entry: ${missing.join(', ')}`, error: true }
        : { text: 'Filled in from the signing tool. Add a download URL and notes if you like, then save.', error: false })
    } catch (err) {
      setPasteMessage({ text: err instanceof Error ? err.message : 'Could not read the pasted text', error: true })
    }
  }

  function resetForm() {
    setForm(EMPTY_FORM)
    setEditing(null)
    setPasted('')
    setPasteMessage(null)
    setFormError(null)
  }

  function startEdit(m: ApprovedModDto) {
    setEditing(m)
    setForm(toForm(m))
    setPasted('')
    setPasteMessage(null)
    setFormError(null)
    formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    if (toNumber(form.contentSize) === null || toNumber(form.axSize) === null) {
      setFormError('Sizes must be whole numbers of bytes')
      return
    }
    setSaving(true)
    try {
      if (editing) {
        const saved = await updateApprovedMod(editing.id, toRequest(form, form.active))
        showSuccess(`${saved.title} ${saved.version} saved`)
      } else {
        const saved = await createApprovedMod(toRequest(form))
        showSuccess(`${saved.title} ${saved.version} approved`)
      }
      resetForm()
      await reload()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Failed to save mod')
    } finally {
      setSaving(false)
    }
  }

  async function handleToggle(m: ApprovedModDto) {
    setTableError(null)
    setBusyId(m.id)
    try {
      await updateApprovedMod(m.id, toRequest(toForm(m), !m.active))
      await reload()
      showSuccess(`${m.title} ${m.version} ${m.active ? 'deactivated' : 'reactivated'}`)
    } catch (err) {
      setTableError(err instanceof Error ? err.message : 'Failed to change the mod')
    } finally {
      setBusyId(null)
    }
  }

  async function handleDelete(m: ApprovedModDto) {
    if (deleteConfirmId !== m.id) { setDeleteConfirmId(m.id); return }
    setTableError(null)
    setBusyId(m.id)
    try {
      await deleteApprovedMod(m.id)
      if (editing?.id === m.id) resetForm()
      await reload()
      showSuccess(`${m.title} ${m.version} deleted`)
    } catch (err) {
      setTableError(err instanceof Error ? err.message : 'Failed to delete mod')
    } finally {
      setBusyId(null)
      setDeleteConfirmId(null)
    }
  }

  function textInput(key: Exclude<keyof FormState, 'active' | 'category' | 'notes'>, opts: { mono?: boolean; maxLength?: number; placeholder?: string } = {}) {
    return (
      <label style={labelStyle}>
        {LABELS[key]}
        <input
          className="input"
          type="text"
          value={form[key]}
          maxLength={opts.maxLength}
          placeholder={opts.placeholder}
          onChange={(e) => setField(key, e.target.value)}
          disabled={saving}
          autoComplete="off"
          spellCheck={false}
          style={opts.mono ? monoStyle : fieldStyle}
        />
      </label>
    )
  }

  const activeCount = mods?.filter((m) => m.active).length ?? 0

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
              <h2 className="title title--small">Approved Mods</h2>
              <span className="table__counter" title="Active mods">{activeCount}</span>
            </div>
            <p style={{ ...mutedStyle, padding: '0 20px 10px' }}>
              Active mods are published at <code>/api/mods/approved</code> for Auric hosts and the launcher. An inactive mod
              counts as not approved. Notes are never published.
            </p>
            {(loadError || tableError) && (
              <p style={{ padding: '0 20px 10px', color: 'var(--color-red)', fontSize: '1.4rem', margin: 0 }}>{loadError ?? tableError}</p>
            )}
            <div className="table__hidden" style={{ overflow: 'auto', maxHeight: '50vh' }}>
              <table className="table__content" style={{ minWidth: '100%' }}>
                <thead className="table__head table__sticky">
                  <tr>
                    <th className="head__cell head__player">Title</th>
                    <th className="head__cell">Version</th>
                    <th className="head__cell">Author</th>
                    <th className="head__cell">Category</th>
                    <th className="head__cell">Content SHA-256</th>
                    <th className="head__cell">Size</th>
                    <th className="head__cell">Key</th>
                    <th className="head__cell">Download</th>
                    <th className="head__cell">Status</th>
                    <th className="head__cell">Action</th>
                  </tr>
                </thead>
                <tbody className="table__body">
                  {mods === null ? (
                    <tr><td colSpan={10} style={{ padding: '20px', textAlign: 'center' }}>{loadError ? '–' : 'Loading...'}</td></tr>
                  ) : mods.length === 0 ? (
                    <tr><td colSpan={10} className="table__cell">No approved mods yet</td></tr>
                  ) : mods.map((m) => (
                    <tr key={m.id} style={{ opacity: m.active ? 1 : 0.5 }}>
                      <td className="table__cell table__player" title={m.notes ?? undefined}>{m.title}</td>
                      <td className="table__cell">{m.version}</td>
                      <td className="table__cell">{m.author}</td>
                      <td className="table__cell">{m.category === 'MUST_MATCH_HOST' ? 'Must match host' : 'Optional'}</td>
                      <td className="table__cell" style={{ fontFamily: 'monospace' }} title={m.contentSha256}>{m.contentSha256.slice(0, 12)}…</td>
                      <td className="table__cell" title={`${m.contentSize} bytes (signed file: ${m.axSize} bytes)`}>{formatBytes(m.contentSize)}</td>
                      <td className="table__cell">{m.keyId}</td>
                      <td className="table__cell">
                        {m.downloadUrl ? <a href={m.downloadUrl} target="_blank" rel="noopener noreferrer" style={{ color: 'inherit' }}>Link</a> : '–'}
                      </td>
                      <td className="table__cell" style={{ fontWeight: 500 }}>{m.active ? 'Active' : 'Inactive'}</td>
                      <td className="table__cell table__action">
                        <div style={{ display: 'flex', gap: '6px', alignItems: 'center', justifyContent: 'center' }}>
                          <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={() => startEdit(m)} disabled={busyId === m.id}>
                            Edit
                          </button>
                          <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={() => handleToggle(m)} disabled={busyId === m.id}>
                            {m.active ? 'Deactivate' : 'Activate'}
                          </button>
                          <button
                            type="button"
                            className={`delete sound__hover sound__delete${deleteConfirmId === m.id ? ' delete--confirm' : ''}`}
                            onClick={() => handleDelete(m)}
                            disabled={busyId === m.id}
                            title={deleteConfirmId === m.id ? 'Click again to delete for good' : `Delete ${m.title}`}
                            aria-label={`Delete ${m.title}`}
                          />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="table__list" ref={formRef}>
            <div className="table__info">
              <h2 className="title title--small">{editing ? `Edit ${editing.title} ${editing.version}` : 'Add Mod'}</h2>
            </div>
            <form onSubmit={handleSubmit} style={{ padding: '0 20px 20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {!editing && (
                <label style={labelStyle}>
                  Paste the entry the signing tool printed
                  <textarea
                    className="input"
                    rows={5}
                    value={pasted}
                    onChange={(e) => { setPasted(e.target.value); fillFromPaste(e.target.value) }}
                    placeholder={'{ "title": "Improved Scoreboard", "version": "1.3", ... }'}
                    disabled={saving}
                    spellCheck={false}
                    style={{ ...monoStyle, resize: 'vertical' }}
                  />
                </label>
              )}
              {pasteMessage && (
                <p style={{ margin: 0, fontSize: '1.3rem', color: pasteMessage.error ? 'var(--color-red)' : 'inherit' }}>{pasteMessage.text}</p>
              )}

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '12px 20px' }}>
                {textInput('title', { maxLength: 200 })}
                {textInput('version', { maxLength: 50 })}
                {textInput('author', { maxLength: 200 })}
                <label style={labelStyle}>
                  {LABELS.category}
                  <select className="input" value={form.category} onChange={(e) => setField('category', e.target.value)} disabled={saving} style={{ width: '100%' }}>
                    <option value="OPTIONAL">OPTIONAL: any player may have it or not</option>
                    <option value="MUST_MATCH_HOST">MUST_MATCH_HOST: every player must have what the host has</option>
                  </select>
                </label>
                {textInput('contentSha256', { mono: true, maxLength: 64 })}
                {textInput('contentSize', { mono: true })}
                {textInput('keyId', { maxLength: 100 })}
                {textInput('axSha256', { mono: true, maxLength: 64 })}
                {textInput('axSize', { mono: true })}
                {textInput('downloadUrl', { maxLength: 2000, placeholder: 'https://...' })}
              </div>
              <label style={labelStyle}>
                {LABELS.notes}
                <textarea
                  className="input"
                  rows={3}
                  maxLength={5000}
                  value={form.notes}
                  onChange={(e) => setField('notes', e.target.value)}
                  disabled={saving}
                  style={{ ...fieldStyle, resize: 'vertical' }}
                />
              </label>
              {editing && (
                <label style={{ ...labelStyle, flexDirection: 'row', alignItems: 'center', gap: '8px' }}>
                  <input type="checkbox" checked={form.active} onChange={(e) => setField('active', e.target.checked)} disabled={saving} />
                  Active (unchecked: approval withdrawn)
                </label>
              )}
              {formError && <p style={{ margin: 0, color: 'var(--color-red)', fontSize: '1.4rem' }}>{formError}</p>}
              <div style={{ display: 'flex', gap: '10px' }}>
                <button className="btn btn--transparent sound__hover sound__click" type="submit" disabled={saving}>
                  {saving ? 'Saving...' : editing ? 'Save' : 'Add mod'}
                </button>
                {(editing || pasted || form !== EMPTY_FORM) && (
                  <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={resetForm} disabled={saving}>
                    {editing ? 'Cancel' : 'Clear'}
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
