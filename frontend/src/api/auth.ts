export interface User {
  id: number
  username: string
  role: string
}

/**
 * Result of a login step. OK = logged in; TOTP_REQUIRED = enter the code from the authenticator app;
 * TOTP_SETUP_REQUIRED = set up the authenticator app first (admins and editors).
 */
export interface LoginResult {
  status: 'OK' | 'TOTP_REQUIRED' | 'TOTP_SETUP_REQUIRED'
  user?: User
  /** Only right after two-factor setup; shown once */
  recoveryCodes?: string[]
}

export interface TwoFactorSetup {
  secret: string
  otpauthUri: string
}

async function post<T>(path: string, body: unknown, fallbackError: string): Promise<T> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'include',
  })
  if (res.status === 429) throw new Error('Too many attempts. Try again later.')
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.message ?? fallbackError)
  return data as T
}

export async function apiLogin(username: string, password: string): Promise<LoginResult> {
  const res = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
    credentials: 'include',
  })
  if (res.status === 429) throw new Error('Too many attempts. Try again later.')
  if (!res.ok) throw new Error('Invalid username or password.')
  return res.json()
}

/** Second login step: a code from the authenticator app, or a recovery code. */
export function apiVerifyCode(code: string): Promise<LoginResult> {
  return post<LoginResult>('/api/login/2fa', { code }, 'Invalid code')
}

/** First login of an admin or editor: get the secret to add to the authenticator app. */
export function apiStartTwoFactorSetup(): Promise<TwoFactorSetup> {
  return post<TwoFactorSetup>('/api/login/2fa/setup', undefined, 'Could not start two-factor setup')
}

/** Confirm the authenticator app with a code; logs in and returns recovery codes. */
export function apiConfirmTwoFactorSetup(code: string): Promise<LoginResult> {
  return post<LoginResult>('/api/login/2fa/setup/confirm', { code }, 'Invalid code')
}

export async function apiLogout(): Promise<void> {
  await fetch('/api/logout', { method: 'POST', credentials: 'include' })
}

export async function apiMe(): Promise<User | null> {
  const res = await fetch('/api/me', { credentials: 'include' })
  if (!res.ok) return null
  return res.json()
}
