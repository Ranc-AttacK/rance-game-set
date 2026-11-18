import { expect, type APIRequestContext, type Page } from '@playwright/test'

export async function fetchVerificationToken(
  request: APIRequestContext,
  email: string
): Promise<string> {
  for (let attempt = 0; attempt < 30; attempt++) {
    const listResponse = await request.get('http://localhost:8025/api/v1/messages')
    if (listResponse.ok()) {
      const list = await listResponse.json()
      const messages: Array<{ ID: string; To?: Array<{ Address: string }> }> = list.messages ?? []
      for (const message of messages) {
        const recipients = message.To ?? []
        if (!recipients.some((to) => to.Address === email)) continue
        const detailResponse = await request.get(
          `http://localhost:8025/api/v1/message/${message.ID}`
        )
        if (!detailResponse.ok()) continue
        const detail = await detailResponse.json()
        const text = `${detail.Text ?? ''}\n${detail.HTML ?? ''}\n${detail.Snippet ?? ''}`
        const match = text.match(/token=([A-Za-z0-9_-]+)/)
        if (match) return match[1]
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`verification email for ${email} not found in Mailpit`)
}

export async function registerVerifyLogin(
  page: Page,
  request: APIRequestContext,
  options: { prefix?: string; nickname?: string } = {}
): Promise<string> {
  const email = `${options.prefix ?? 'e2e'}-${Date.now()}@example.com`
  const nickname = options.nickname ?? 'E2E'

  await page.goto('/register')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('昵称').fill(nickname)
  await page.getByLabel('密码', { exact: true }).fill('secret123')
  await page.getByLabel('确认密码').fill('secret123')
  await page.getByRole('button', { name: '注册' }).click()
  await expect(page.getByText('注册成功')).toBeVisible()

  const token = await fetchVerificationToken(request, email)
  await page.goto(`/verify-email?token=${token}`)
  await expect(page.getByText('邮箱验证成功')).toBeVisible()

  await page.goto('/login')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('密码').fill('secret123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/me$/)

  return email
}
