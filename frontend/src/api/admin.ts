import { fetchApi } from './client'

export interface UserDto {
  id: number
  username: string
  role: string
}

export function getUsers(): Promise<UserDto[]> {
  return fetchApi<UserDto[]>('/admin/users')
}

export function updateUserRole(id: number, role: string): Promise<UserDto> {
  return fetchApi<UserDto>(`/admin/users/${id}/role`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  })
}

export function deleteUser(id: number): Promise<void> {
  return fetchApi<void>(`/admin/users/${id}`, { method: 'DELETE' })
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
