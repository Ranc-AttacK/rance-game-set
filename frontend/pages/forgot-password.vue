<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

definePageMeta({ middleware: 'guest' })

const auth = useAuthStore()
const email = ref('')
const error = ref('')
const loading = ref(false)
const sent = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.forgotPassword(email.value.trim())
    sent.value = true
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="找回密码" subtitle="我们会向注册邮箱发送重置链接">
    <div v-if="sent" class="space-y-4">
      <Alert type="success" message="如果该邮箱已注册，重置邮件会发送到你的邮箱（开发环境请查看 Mailpit）。" />
      <NuxtLink to="/login" class="btn w-full">返回登录</NuxtLink>
    </div>
    <form v-else class="space-y-4" @submit.prevent="submit">
      <label class="block text-sm text-slate-300">
        注册邮箱
        <input v-model="email" type="email" required autocomplete="email" class="input mt-1" placeholder="you@example.com" />
      </label>
      <Alert v-if="error" type="error" :message="error" />
      <button class="btn w-full" type="submit" :disabled="loading">
        {{ loading ? '发送中…' : '发送重置邮件' }}
      </button>
    </form>
  </AuthShell>
</template>
