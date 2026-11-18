<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

definePageMeta({ middleware: 'guest' })

const auth = useAuthStore()
const email = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(email.value.trim(), password.value)
    await navigateTo('/me')
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="登录" subtitle="使用邮箱和密码登录你的账号">
    <form class="space-y-4" @submit.prevent="submit">
      <label class="block text-sm text-slate-300">
        邮箱
        <input v-model="email" type="email" required autocomplete="email" class="input mt-1" placeholder="you@example.com" />
      </label>
      <label class="block text-sm text-slate-300">
        密码
        <input v-model="password" type="password" required autocomplete="current-password" class="input mt-1" placeholder="至少 8 位，含字母和数字" />
      </label>
      <Alert v-if="error" type="error" :message="error" />
      <button class="btn w-full" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>
    <div class="mt-4 flex justify-between text-sm">
      <NuxtLink to="/forgot-password" class="text-slate-400 hover:text-white">忘记密码</NuxtLink>
      <NuxtLink to="/register" class="text-brand-300 hover:text-brand-200">注册新账号</NuxtLink>
    </div>
  </AuthShell>
</template>
