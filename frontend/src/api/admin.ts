import { fetchApi } from './client'

export interface UserDto {
  id: number
  username: string
  role: string
  /** Whether the user has set up the authenticator app (required for admins and editors) */
  twoFactorEnabled: boolean
}

/** Sends a JSON request and throws the server's message on failure. */
async function send<T>(path: string, method: string, body: unknown, fallbackError: string): Promise<T> {
  const res = await fetch(`/api${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.message ?? fallbackError)
  return data as T
}

/** Creates a supervisor or editor. */
export function createUser(username: string, password: string, role: 'supervisor' | 'editor'): Promise<UserDto> {
  return send<UserDto>('/admin/users', 'POST', { username, password, role }, 'Failed to create user')
}

/** Sets the password of a supervisor or editor. */
export function setUserPassword(id: number, password: string): Promise<void> {
  return send<void>(`/admin/users/${id}/password`, 'PUT', { password }, 'Failed to set password')
}

/** Removes a user's two-factor login so they set it up again at next login. */
export function resetUserTwoFactor(id: number): Promise<void> {
  return send<void>(`/admin/users/${id}/reset-2fa`, 'POST', undefined, 'Failed to reset two-factor login')
}

/** The logged-in admin changes their own password. */
export function changeOwnPassword(currentPassword: string, newPassword: string, code: string): Promise<void> {
  return send<void>('/admin/account/password', 'PUT', { currentPassword, newPassword, code }, 'Failed to change password')
}

export function getUsers(): Promise<UserDto[]> {
  return fetchApi<UserDto[]>('/admin/users')
}

export function updateUserRole(id: number, role: string): Promise<UserDto> {
  return send<UserDto>(`/admin/users/${id}/role`, 'PUT', { role }, 'Failed to update role')
}

export function deleteUser(id: number): Promise<void> {
  return send<void>(`/admin/users/${id}`, 'DELETE', undefined, 'Failed to delete user')
}

export interface HostTokenDto {
  id: number
  name: string
  userId: number
  username: string | null
  createdAt: string
  lastUsedAt: string | null
  revokedAt: string | null
}

/** Returned once when a token is created; `token` cannot be retrieved again. */
export interface HostTokenCreated {
  id: number
  name: string
  token: string
  userId: number
  username: string
}

export function getHostTokens(): Promise<HostTokenDto[]> {
  return fetchApi<HostTokenDto[]>('/admin/host-tokens')
}

export function createHostToken(name: string, userId: number): Promise<HostTokenCreated> {
  return fetchApi<HostTokenCreated>('/admin/host-tokens', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, userId }),
  })
}

export function revokeHostToken(id: number): Promise<void> {
  return fetchApi<void>(`/admin/host-tokens/${id}`, { method: 'DELETE' })
}

export function getCurrentSeason(): Promise<{ season: number }> {
  return fetchApi<{ season: number }>('/admin/season/current')
}

export function startNextSeason(): Promise<{ newSeason: number; playersInitialized: number }> {
  return fetchApi<{ newSeason: number; playersInitialized: number }>('/admin/season/start', {
    method: 'POST',
  })
}

export function cleanupSeason(season: number): Promise<{ season: number; deletedRows: number }> {
  return fetchApi<{ season: number; deletedRows: number }>('/admin/season/cleanup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ season }),
  })
}
