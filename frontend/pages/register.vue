<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

definePageMeta({ middleware: 'guest' })

const auth = useAuthStore()
const form = reactive({ email: '', nickname: '', password: '', confirm: '' })
const error = ref('')
const loading = ref(false)
const done = ref(false)

async function submit() {
  error.value = ''
  if (form.password !== form.confirm) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await auth.register(form.email.trim(), form.nickname.trim(), form.password)
    done.value = true
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="创建账号" subtitle="注册后需要点击邮箱里的链接完成验证">
    <div v-if="done" class="space-y-4">
      <Alert type="success" message="注册成功。验证邮件已发送，请到 Mailpit（http://localhost:8025）查看并点击验证链接。" />
      <NuxtLink to="/login" class="btn w-full">去登录</NuxtLink>
    </div>
    <form v-else class="space-y-4" @submit.prevent="submit">
      <label class="block text-sm font-medium text-neutral-700">
        邮箱
        <input v-model="form.email" type="email" required autocomplete="email" class="input mt-1.5" placeholder="you@example.com" />
      </label>
      <label class="block text-sm font-medium text-neutral-700">
        昵称
        <input v-model="form.nickname" type="text" required minlength="2" maxlength="40" class="input mt-1.5" placeholder="2-40 个字符" />
      </label>
      <label class="block text-sm font-medium text-neutral-700">
        密码
        <input v-model="form.password" type="password" required autocomplete="new-password" class="input mt-1.5" placeholder="8-72 字节，含字母和数字" />
      </label>
      <label class="block text-sm font-medium text-neutral-700">
        确认密码
        <input v-model="form.confirm" type="password" required autocomplete="new-password" class="input mt-1.5" />
      </label>
      <Alert v-if="error" type="error" :message="error" />
      <button class="btn w-full" type="submit" :disabled="loading">
        {{ loading ? '注册中…' : '注册' }}
      </button>
    </form>
    <p class="mt-4 text-sm text-muted">
      已有账号？
      <NuxtLink to="/login" class="font-medium text-foreground transition hover:text-sakura-600">直接登录</NuxtLink>
    </p>
  </AuthShell>
</template>
