import { expect, test } from '@playwright/test'
import { registerVerifyLogin } from './helpers'

test('register, verify email, login and logout', async ({ page, request }) => {
  await registerVerifyLogin(page, request, { prefix: 'e2e', nickname: 'E2E' })

  await expect(page.getByRole('heading', { name: 'E2E' })).toBeVisible()

  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login$/)
})
