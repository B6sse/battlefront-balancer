import { useState, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const usernameRef = useRef<HTMLInputElement>(null)

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
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
      await login(username, password)
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed.')
      usernameRef.current?.focus()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="section section--login">
      <div className="container">
        <div className="login">
          {error && (
            <div className="error">
              <span className="error__message">{error}</span>
            </div>
          )}
          <h2 className="title title--medium">Log in</h2>
          <form className="login__form" onSubmit={handleSubmit} noValidate>
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
              <Link
                className="link btn btn--transparent sound__hover sound__delete"
                to="/"
              >
                Back
              </Link>
              <button
                className="btn btn--grey sound__hover sound__click"
                type="submit"
                disabled={submitting}
              >
                {submitting ? 'Logging in…' : 'Log in'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </section>
  )
}
