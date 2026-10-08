import { useState, useRef, useEffect } from 'react'
import { usePageTitle } from '../hooks/usePageTitle'
import { useNavigate, Link } from 'react-router-dom'
import QRCode from 'qrcode'
import { useAuth } from '../context/AuthContext'
import { apiConfirmTwoFactorSetup, apiStartTwoFactorSetup, apiVerifyCode } from '../api/auth'
import type { TwoFactorSetup, User } from '../api/auth'

/** credentials → (admins/editors) code, or setup → recovery codes on the first login */
type Step = 'credentials' | 'code' | 'setup' | 'recovery'

const hintStyle = { fontSize: '1.4rem', margin: 0, lineHeight: 1.5 }

export function LoginPage() {
  usePageTitle('Login')
  const { login, completeLogin } = useAuth()
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('credentials')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [setup, setSetup] = useState<TwoFactorSetup | null>(null)
  const [qrDataUrl, setQrDataUrl] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([])
  const [loggedInUser, setLoggedInUser] = useState<User | null>(null)
  const [savedCodes, setSavedCodes] = useState(false)
  const usernameRef = useRef<HTMLInputElement>(null)
  const codeRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (step === 'code' || step === 'setup') codeRef.current?.focus()
  }, [step, setup])

  /** A pending login that expired or had too many wrong codes has to start over. */
  function handleStepError(err: unknown) {
    const message = err instanceof Error ? err.message : 'Something went wrong.'
    setError(message)
    if (message.includes('Log in again')) {
      setStep('credentials')
      setSetup(null)
    }
  }

  async function handleCredentials(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = e.currentTarget
    const username = (form.elements.namedItem('username') as HTMLInputElement).value.trim()
    const password = (form.elements.namedItem('password') as HTMLInputElement).value

    if (!username || !password) {
      setError('Please fill in all fields.')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      const result = await login(username, password)
      if (result.status === 'OK') {
        navigate('/')
      } else if (result.status === 'TOTP_REQUIRED') {
        setStep('code')
      } else {
        const s = await apiStartTwoFactorSetup()
        setSetup(s)
        setQrDataUrl(await QRCode.toDataURL(s.otpauthUri, { width: 220, margin: 1 }))
        setStep('setup')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed.')
      usernameRef.current?.focus()
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCode(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const code = codeRef.current?.value.trim() ?? ''
    if (!code) { setError('Enter the code.'); return }
    setSubmitting(true)
    setError('')
    try {
      if (step === 'code') {
        const result = await apiVerifyCode(code)
        if (result.user) completeLogin(result.user)
        navigate('/')
      } else {
        const result = await apiConfirmTwoFactorSetup(code)
        setLoggedInUser(result.user ?? null)
        setRecoveryCodes(result.recoveryCodes ?? [])
        setStep('recovery')
      }
    } catch (err) {
      handleStepError(err)
      if (codeRef.current) codeRef.current.value = ''
    } finally {
      setSubmitting(false)
    }
  }

  function handleRecoveryDone() {
    if (loggedInUser) completeLogin(loggedInUser)
    navigate('/')
  }

  function downloadRecoveryCodes() {
    const text = `Battlefront Balancer recovery codes (${loggedInUser?.username ?? ''})\nEach code works once.\n\n${recoveryCodes.join('\n')}\n`
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }))
    const a = document.createElement('a')
    a.href = url
    a.download = 'battlefront-balancer-recovery-codes.txt'
    a.click()
    URL.revokeObjectURL(url)
  }

  const codeInput = (placeholder: string) => (
    <input
      ref={codeRef}
      className="input sound__hover"
      type="text"
      name="code"
      placeholder={placeholder}
      autoComplete="one-time-code"
      inputMode={step === 'setup' ? 'numeric' : 'text'}
      disabled={submitting}
    />
  )

  return (
    <section className="section section--login">
      <div className="container">
        <div className="login">
          {error && (
            <div className="error">
              <span className="error__message">{error}</span>
            </div>
          )}

          {step === 'credentials' && (
            <>
              <h2 className="title title--medium">Log in</h2>
              <form className="login__form" onSubmit={handleCredentials} noValidate>
                <input
                  ref={usernameRef}
                  className="input sound__hover"
                  type="text"
                  name="username"
                  placeholder="Username"
                  autoComplete="username"
                  required
                  disabled={submitting}
                />
                <input
                  className="input sound__hover"
                  type="password"
                  name="password"
                  placeholder="Password"
                  autoComplete="current-password"
                  required
                  disabled={submitting}
                />
                <div className="login__button">
                  <Link className="link btn btn--transparent sound__hover sound__delete" to="/">
                    Back
                  </Link>
                  <button className="btn btn--grey sound__hover sound__click" type="submit" disabled={submitting}>
                    {submitting ? 'Logging in…' : 'Log in'}
                  </button>
                </div>
              </form>
            </>
          )}

          {step === 'code' && (
            <>
              <h2 className="title title--medium">Two-factor code</h2>
              <form className="login__form" onSubmit={handleCode} noValidate>
                <p style={hintStyle}>Enter the 6-digit code from your authenticator app, or one of your recovery codes.</p>
                {codeInput('123456')}
                <div className="login__button">
                  <button
                    type="button"
                    className="btn btn--transparent sound__hover sound__delete"
                    onClick={() => { setStep('credentials'); setError('') }}
                    disabled={submitting}
                  >
                    Back
                  </button>
                  <button className="btn btn--grey sound__hover sound__click" type="submit" disabled={submitting}>
                    {submitting ? 'Checking…' : 'Verify'}
                  </button>
                </div>
              </form>
            </>
          )}

          {step === 'setup' && setup && (
            <>
              <h2 className="title title--medium">Set up two-factor login</h2>
              <form className="login__form" onSubmit={handleCode} noValidate>
                <p style={hintStyle}>
                  Your account needs a code from an authenticator app (for example Google Authenticator, Microsoft
                  Authenticator or 1Password) every time you log in.
                </p>
                <p style={hintStyle}>1. Scan this QR code with the app:</p>
                {qrDataUrl && (
                  <img src={qrDataUrl} alt="QR code for the authenticator app" width={220} height={220} style={{ alignSelf: 'center', background: '#fff', padding: 8, borderRadius: 4 }} />
                )}
                <p style={{ ...hintStyle, fontSize: '1.3rem' }}>
                  Can't scan it? Enter this key in the app instead:{' '}
                  <code style={{ wordBreak: 'break-all', userSelect: 'all' }}>{setup.secret.match(/.{1,4}/g)?.join(' ')}</code>
                </p>
                <p style={hintStyle}>2. Enter the 6-digit code the app shows:</p>
                {codeInput('123456')}
                <div className="login__button">
                  <button
                    type="button"
                    className="btn btn--transparent sound__hover sound__delete"
                    onClick={() => { setStep('credentials'); setSetup(null); setError('') }}
                    disabled={submitting}
                  >
                    Cancel
                  </button>
                  <button className="btn btn--grey sound__hover sound__click" type="submit" disabled={submitting}>
                    {submitting ? 'Checking…' : 'Confirm'}
                  </button>
                </div>
              </form>
            </>
          )}

          {step === 'recovery' && (
            <>
              <h2 className="title title--medium">Save your recovery codes</h2>
              <div className="login__form">
                <p style={hintStyle}>
                  If you lose your phone, each of these codes lets you log in once instead of an app code.{' '}
                  <strong>They are shown only now.</strong> Keep them somewhere safe, like a password manager.
                </p>
                <ul style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '6px 20px', listStyle: 'none', padding: 0, margin: 0, fontFamily: 'monospace', fontSize: '1.6rem' }}>
                  {recoveryCodes.map((c) => <li key={c}>{c}</li>)}
                </ul>
                <button type="button" className="btn btn--transparent sound__hover sound__click" onClick={downloadRecoveryCodes}>
                  Download as text file
                </button>
                <label style={{ ...hintStyle, display: 'flex', gap: 8, alignItems: 'center' }}>
                  <input type="checkbox" checked={savedCodes} onChange={(e) => setSavedCodes(e.target.checked)} />
                  I have saved my recovery codes
                </label>
                <div className="login__button">
                  <button type="button" className="btn btn--grey sound__hover sound__click" onClick={handleRecoveryDone} disabled={!savedCodes}>
                    Continue
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  )
}
