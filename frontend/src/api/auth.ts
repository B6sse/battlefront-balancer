export interface User {
  id: number
  username: string
  role: string
}

export async function apiLogin(username: string, password: string): Promise<User> {
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

export async function apiLogout(): Promise<void> {
  await fetch('/api/logout', { method: 'POST', credentials: 'include' })
}

export async function apiMe(): Promise<User | null> {
  const res = await fetch('/api/me', { credentials: 'include' })
  if (!res.ok) return null
  return res.json()
}
