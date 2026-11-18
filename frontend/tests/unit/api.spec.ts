import { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'
import { apiErrorMessage, getAccessToken, setAccessToken } from '~/lib/api'

describe('api token memory', () => {
  it('stores and clears the access token in memory', () => {
    setAccessToken('token-1')
    expect(getAccessToken()).toBe('token-1')
    setAccessToken(null)
    expect(getAccessToken()).toBeNull()
  })
})

describe('apiErrorMessage', () => {
  it('returns the server message from an Axios error', () => {
    const error = new AxiosError('failed', 'ERR', undefined, undefined, {
      data: { code: 40001, message: '参数校验失败' },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: {} as never
    })
    expect(apiErrorMessage(error)).toBe('参数校验失败')
  })

  it('falls back for network errors', () => {
    expect(apiErrorMessage(new Error('boom'))).toBe('网络错误，请稍后重试')
  })
})
