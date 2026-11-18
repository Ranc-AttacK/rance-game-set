import { expect, test } from '@playwright/test'
import { registerVerifyLogin } from './helpers'

test('verified user creates a topic and replies', async ({ page, request }) => {
  await registerVerifyLogin(page, request, { prefix: 'forum', nickname: '论坛E2E' })

  await page.getByRole('link', { name: '论坛', exact: true }).click()
  await expect(page).toHaveURL(/\/forum$/)
  await expect(page.getByRole('heading', { name: '论坛' })).toBeVisible()
  await expect(page.getByText('综合讨论').first()).toBeVisible()

  await page.getByRole('link', { name: '发主题' }).click()
  await expect(page).toHaveURL(/\/topics\/new$/)

  const title = `E2E 主题 ${Date.now()}`
  await page.getByRole('combobox').selectOption({ index: 1 })
  await page.getByPlaceholder('标题（4–160 字）').fill(title)
  await page.getByPlaceholder('正文（纯文本，保留换行）').fill('这是 E2E 正文')
  await page.getByPlaceholder('标签，用逗号或空格分隔，例如：汉化, 补丁').fill('E2E, 测试')
  await page.getByRole('button', { name: '发布主题' }).click()

  await expect(page).toHaveURL(/\/topics\/\d+$/)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('E2E 主题')
  await expect(page.getByText('这是 E2E 正文')).toBeVisible()

  await page.getByPlaceholder('写下你的回复…').fill('E2E 回复')
  await page.getByRole('button', { name: '发布回复' }).click()
  await expect(page.getByText('E2E 回复')).toBeVisible()
  await expect(page.getByText('#2')).toBeVisible()

  await page.goto('/forum/general')
  await expect(page.getByText(title)).toBeVisible()

  await page.goto('/latest')
  await expect(page.getByText(title)).toBeVisible()
})
