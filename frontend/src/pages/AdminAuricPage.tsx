import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAuricVersion, saveAuricVersion } from '../api/admin'

interface FormState {
  latestVersion: string
  minimumVersion: string
  downloadUrl: string
  sha256: string
  releaseNotes: string
}

const fieldStyle = { width: 'calc(100% - 42px)', fontFamily: 'inherit' }
const monoStyle = { ...fieldStyle, fontFamily: 'monospace' }
const labelStyle = { display: 'flex', flexDirection: 'column' as const, gap: '4px', fontSize: '1.3rem' }

function formatDate(iso: string | null): string {
  return iso ? iso.slice(0, 16).replace('T', ' ') : 'never'
}

/**
 * Admin page for the Auric version info published at GET /api/auric/version: the latest and minimum version, and an
 * optional download link, SHA-256 and release notes for the latest public DLL.
 */
export function AdminAuricPage() {
  const [form, setForm] = useState<FormState | null>(null)
  const [updatedAt, setUpdatedAt] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const successTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    getAuricVersion()
      .then((v) => {
        setForm({
          latestVersion: v.latestVersion ?? '',
          minimumVersion: v.minimumVersion ?? '',
          downloadUrl: v.downloadUrl ?? '',
          sha256: v.sha256 ?? '',
          releaseNotes: v.releaseNotes ?? '',
        })
        setUpdatedAt(v.updatedAt)
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load Auric version'))
    return () => { if (successTimer.current) clearTimeout(successTimer.current) }
  }, [])

  function setField(key: keyof FormState, value: string) {
    setForm((f) => (f ? { ...f, [key]: value } : f))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!form) return
    setError(null)
    setSaving(true)
    try {
      const saved = await saveAuricVersion({
        latestVersion: form.latestVersion,
        minimumVersion: form.minimumVersion,
        downloadUrl: form.downloadUrl.trim() || null,
        sha256: form.sha256.trim() || null,
        releaseNotes: form.releaseNotes.trim() || null,
      })
      setUpdatedAt(saved.updatedAt)
      setSuccessMsg('Auric version saved')
      if (successTimer.current) clearTimeout(successTimer.current)
      successTimer.current = setTimeout(() => setSuccessMsg(null), 3000)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save Auric version')
    } finally {
      setSaving(false)
    }
  }

  function input(key: Exclude<keyof FormState, 'releaseNotes'>, label: string, opts: { mono?: boolean; maxLength?: number; placeholder?: string }) {
    return (
      <label style={labelStyle}>
        {label}
        <input
          className="input"
          type="text"
          value={form?.[key] ?? ''}
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
          <p style={{ margin: 0, fontSize: '1.4rem' }}>
            <Link to="/admin" className="sound__hover sound__click" style={{ color: 'inherit' }}>← Admin</Link>
          </p>
          <div className="table__list">
            <div className="table__info">
              <h2 className="title title--small">Auric Version</h2>
            </div>
            <p style={{ padding: '0 20px 10px', fontSize: '1.3rem', color: 'var(--color-text-muted)', margin: 0 }}>
              Published at <code>/api/auric/version</code>. Versions are three numbers, e.g. 1.4.0; the minimum version can
              not be higher than the latest. Last saved: {formatDate(updatedAt)}.
            </p>
            {form === null ? (
              <p style={{ padding: '0 20px 20px', fontSize: '1.4rem', margin: 0, color: error ? 'var(--color-red)' : 'inherit' }}>
                {error ?? 'Loading...'}
              </p>
            ) : (
              <form onSubmit={handleSubmit} style={{ padding: '0 20px 20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px 20px' }}>
                  {input('latestVersion', 'Latest version', { maxLength: 20, placeholder: '1.4.0' })}
                  {input('minimumVersion', 'Minimum version', { maxLength: 20, placeholder: '1.4.0' })}
                </div>
                {input('downloadUrl', 'Download URL of the latest DLL (https, optional)', { maxLength: 2000, placeholder: 'https://...' })}
                {input('sha256', 'SHA-256 of the latest DLL (optional)', { mono: true, maxLength: 64 })}
                <label style={labelStyle}>
                  Release notes (optional)
                  <textarea
                    className="input"
                    rows={4}
                    maxLength={2000}
                    value={form.releaseNotes}
                    onChange={(e) => setField('releaseNotes', e.target.value)}
                    disabled={saving}
                    style={{ ...fieldStyle, resize: 'vertical' }}
                  />
                </label>
                {error && <p style={{ margin: 0, color: 'var(--color-red)', fontSize: '1.4rem' }}>{error}</p>}
                <div>
                  <button className="btn btn--transparent sound__hover sound__click" type="submit" disabled={saving}>
                    {saving ? 'Saving...' : 'Save'}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      </div>
    </section>
  )
}
