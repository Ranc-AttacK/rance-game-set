import { defineStore } from 'pinia'
import { api, refreshAccessToken, setAccessToken } from '~/lib/api'

export interface AuthUser {
  id: number
  email: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
  emailVerified: boolean
  role: string
  createdAt: string
}

interface AuthState {
  user: AuthUser | null
  ready: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    ready: false
  }),
  getters: {
    isAuthenticated: (state) => state.user !== null
  },
  actions: {
    async bootstrap() {
      if (this.ready) return
      this.ready = true
      const token = await refreshAccessToken()
      if (!token) return
      try {
        await this.fetchMe()
      } catch {
        setAccessToken(null)
        this.user = null
      }
    },
    async login(email: string, password: string) {
      const { data } = await api.post('/auth/login', { email, password })
      setAccessToken(data.data.accessToken)
      this.user = data.data.user
    },
    async register(email: string, nickname: string, password: string) {
      const { data } = await api.post('/auth/register', { email, nickname, password })
      return data.data as AuthUser
    },
    async verifyEmail(token: string) {
      await api.post('/auth/verify-email', { token })
    },
    async resendVerification() {
      await api.post('/auth/resend-verification')
    },
    async forgotPassword(email: string) {
      await api.post('/auth/forgot-password', { email })
    },
    async resetPassword(token: string, newPassword: string) {
      await api.post('/auth/reset-password', { token, newPassword })
    },
    async fetchMe() {
      const { data } = await api.get('/users/me')
      this.user = data.data as AuthUser
    },
    async updateProfile(payload: { nickname?: string; avatarUrl?: string; bio?: string }) {
      const { data } = await api.patch('/users/me', payload)
      this.user = data.data as AuthUser
      return this.user
    },
    async deleteAccount() {
      await api.delete('/users/me')
      setAccessToken(null)
      this.user = null
    },
    async logout() {
      try {
        await api.post('/auth/logout')
      } finally {
        setAccessToken(null)
        this.user = null
      }
    }
  }
})
