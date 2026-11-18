import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

const API_BASE = '/api/v1'
const SKIP_REFRESH = [
  '/auth/login',
  '/auth/register',
  '/auth/verify-email',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/resend-verification',
  '/auth/refresh'
]

let accessToken: string | null = null
let refreshing: Promise<string | null> | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

export const api = axios.create({
  baseURL: API_BASE,
  withCredentials: true
})

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

async function requestRefresh(): Promise<string | null> {
  const response = await api.post('/auth/refresh')
  const token = response.data?.data?.accessToken as string | undefined
  setAccessToken(token ?? null)
  return token ?? null
}

export async function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    refreshing = requestRefresh()
      .catch(() => {
        setAccessToken(null)
        return null
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined
    const url = original?.url ?? ''
    const shouldRefresh = error.response?.status === 401
      && original
      && !original._retry
      && !SKIP_REFRESH.some((path) => url.includes(path))

    if (shouldRefresh) {
      original._retry = true
      const token = await refreshAccessToken()
      if (token) {
        original.headers = { ...(original.headers ?? {}), Authorization: `Bearer ${token}` }
        return api(original)
      }
    }
    return Promise.reject(error)
  }
)

export function apiErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const message = (error.response?.data as { message?: string } | undefined)?.message
    if (message) return message
    return '请求失败，请稍后重试'
  }
  return '网络错误，请稍后重试'
}
