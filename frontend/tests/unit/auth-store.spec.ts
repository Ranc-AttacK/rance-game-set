import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn()
  },
  setAccessToken: vi.fn(),
  refreshAccessToken: vi.fn()
}))

vi.mock('~/lib/api', () => mocks)

import { useAuthStore } from '~/stores/auth'

const user = {
  id: 1,
  email: 'player@example.com',
  nickname: 'Player',
  avatarUrl: null,
  bio: null,
  emailVerified: true,
  role: 'USER',
  createdAt: '2026-01-01T00:00:00Z'
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('auth store', () => {
  it('bootstrap loads the current user when refresh succeeds', async () => {
    mocks.refreshAccessToken.mockResolvedValue('token')
    mocks.api.get.mockResolvedValue({ data: { data: user } })

    const auth = useAuthStore()
    await auth.bootstrap()

    expect(auth.user).toEqual(user)
    expect(auth.isAuthenticated).toBe(true)
    expect(mocks.api.get).toHaveBeenCalledWith('/users/me')
  })

  it('login stores the token and user', async () => {
    mocks.api.post.mockResolvedValue({
      data: { data: { accessToken: 'access', expiresIn: 1800, user } }
    })
    const auth = useAuthStore()

    await auth.login('player@example.com', 'secret123')

    expect(mocks.setAccessToken).toHaveBeenCalledWith('access')
    expect(auth.user).toEqual(user)
  })

  it('logout posts and clears state even on success', async () => {
    mocks.api.post.mockResolvedValue({ data: { data: null } })
    const auth = useAuthStore()
    auth.user = user

    await auth.logout()

    expect(mocks.api.post).toHaveBeenCalledWith('/auth/logout')
    expect(auth.user).toBeNull()
    expect(mocks.setAccessToken).toHaveBeenCalledWith(null)
  })

  it('updateProfile replaces the user summary', async () => {
    mocks.api.patch.mockResolvedValue({ data: { data: { ...user, nickname: 'New' } } })
    const auth = useAuthStore()
    auth.user = user

    await auth.updateProfile({ nickname: 'New' })

    expect(auth.user?.nickname).toBe('New')
  })
})
