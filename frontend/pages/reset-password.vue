<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const token = typeof route.query.token === 'string' ? route.query.token : ''
const password = ref('')
const confirm = ref('')
const error = ref('')
const loading = ref(false)
const done = ref(false)

async function submit() {
  error.value = ''
  if (!token) {
    error.value = '缺少重置令牌，请从邮件中的链接重新打开。'
    return
  }
  if (password.value !== confirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await auth.resetPassword(token, password.value)
    done.value = true
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="重置密码">
    <div v-if="done" class="space-y-4">
      <Alert type="success" message="密码已重置，所有已登录会话已失效，请重新登录。" />
      <NuxtLink to="/login" class="btn w-full">去登录</NuxtLink>
    </div>
    <form v-else class="space-y-4" @submit.prevent="submit">
      <label class="block text-sm font-medium text-neutral-700">
        新密码
        <input v-model="password" type="password" required autocomplete="new-password" class="input mt-1.5" placeholder="8-72 字节，含字母和数字" />
      </label>
      <label class="block text-sm font-medium text-neutral-700">
        确认新密码
        <input v-model="confirm" type="password" required autocomplete="new-password" class="input mt-1.5" />
      </label>
      <Alert v-if="error" type="error" :message="error" />
      <button class="btn w-full" type="submit" :disabled="loading">
        {{ loading ? '重置中…' : '重置密码' }}
      </button>
    </form>
  </AuthShell>
</template>
