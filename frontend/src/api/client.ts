const API_BASE = '/api'

export async function fetchApi<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, { credentials: 'include', ...init })
  if (!res.ok) throw new Error(`API error: ${res.status}`)
  if (res.status === 204 || res.headers.get('content-length') === '0') return undefined as T
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text)
}
