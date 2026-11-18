<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const status = ref<'loading' | 'success' | 'error'>('loading')
const message = ref('正在验证邮箱…')
const resending = ref(false)

async function resend() {
  resending.value = true
  try {
    await auth.resendVerification()
    message.value = '新的验证邮件已发送，请查收。'
  } catch (e) {
    message.value = apiErrorMessage(e)
  } finally {
    resending.value = false
  }
}

onMounted(async () => {
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    status.value = 'error'
    message.value = '缺少验证令牌，请从邮件中的链接重新打开。'
    return
  }
  try {
    await auth.verifyEmail(token)
    status.value = 'success'
    message.value = '邮箱验证成功，现在可以正常使用社区了。'
    if (auth.isAuthenticated) await auth.fetchMe()
  } catch (e) {
    status.value = 'error'
    message.value = apiErrorMessage(e)
  }
})
</script>

<template>
  <AuthShell title="邮箱验证">
    <div class="space-y-4">
      <Alert
        :type="status === 'success' ? 'success' : status === 'error' ? 'error' : 'info'"
        :message="message"
      />
      <div class="flex flex-col gap-3">
        <NuxtLink v-if="status === 'success'" to="/login" class="btn w-full">去登录</NuxtLink>
        <button
          v-if="status === 'error' && auth.isAuthenticated && !auth.user?.emailVerified"
          class="btn-ghost w-full"
          :disabled="resending"
          @click="resend"
        >
          {{ resending ? '发送中…' : '重新发送验证邮件' }}
        </button>
        <NuxtLink to="/" class="text-center text-sm text-muted transition hover:text-foreground">返回首页</NuxtLink>
      </div>
    </div>
  </AuthShell>
</template>
