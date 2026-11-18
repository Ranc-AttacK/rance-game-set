<script setup lang="ts">
import { apiErrorMessage } from '~/lib/api'
import { useAuthStore } from '~/stores/auth'

definePageMeta({ middleware: 'auth' })

const auth = useAuthStore()
const form = reactive({ nickname: '', avatarUrl: '', bio: '' })
const message = ref('')
const messageType = ref<'success' | 'error' | 'info'>('info')
const saving = ref(false)
const resending = ref(false)

watch(
  () => auth.user,
  (user) => {
    if (!user) return
    form.nickname = user.nickname
    form.avatarUrl = user.avatarUrl ?? ''
    form.bio = user.bio ?? ''
  },
  { immediate: true }
)

function notify(type: 'success' | 'error' | 'info', text: string) {
  messageType.value = type
  message.value = text
}

async function save() {
  message.value = ''
  saving.value = true
  try {
    await auth.updateProfile({
      nickname: form.nickname.trim(),
      avatarUrl: form.avatarUrl.trim(),
      bio: form.bio.trim()
    })
    notify('success', '资料已保存')
  } catch (e) {
    notify('error', apiErrorMessage(e))
  } finally {
    saving.value = false
  }
}

async function resend() {
  resending.value = true
  try {
    await auth.resendVerification()
    notify('success', '验证邮件已重新发送')
  } catch (e) {
    notify('error', apiErrorMessage(e))
  } finally {
    resending.value = false
  }
}

async function removeAccount() {
  if (!window.confirm('确定要注销账号吗？该操作会匿名化资料并立即退出所有会话。')) return
  try {
    await auth.deleteAccount()
    await navigateTo('/login')
  } catch (e) {
    notify('error', apiErrorMessage(e))
  }
}

async function logout() {
  await auth.logout()
  await navigateTo('/login')
}
</script>

<template>
  <div v-if="auth.user" class="mx-auto max-w-2xl space-y-6">
    <section class="rounded-2xl border border-white/10 bg-white/5 p-6">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h1 class="text-2xl font-semibold">{{ auth.user.nickname }}</h1>
          <p class="mt-1 text-sm text-slate-400">{{ auth.user.email }} · {{ auth.user.role }}</p>
        </div>
        <span
          class="rounded-full px-3 py-1 text-xs"
          :class="auth.user.emailVerified ? 'bg-emerald-500/15 text-emerald-300' : 'bg-amber-500/15 text-amber-300'"
        >
          {{ auth.user.emailVerified ? '邮箱已验证' : '邮箱未验证' }}
        </span>
      </div>
      <button
        v-if="!auth.user.emailVerified"
        class="btn-ghost mt-4"
        :disabled="resending"
        @click="resend"
      >
        {{ resending ? '发送中…' : '重新发送验证邮件' }}
      </button>
    </section>

    <section class="rounded-2xl border border-white/10 bg-white/5 p-6">
      <h2 class="text-lg font-medium">编辑资料</h2>
      <form class="mt-4 space-y-4" @submit.prevent="save">
        <label class="block text-sm text-slate-300">
          昵称
          <input v-model="form.nickname" type="text" minlength="2" maxlength="40" required class="input mt-1" />
        </label>
        <label class="block text-sm text-slate-300">
          头像 URL
          <input v-model="form.avatarUrl" type="url" class="input mt-1" placeholder="https://example.com/avatar.png（留空清除）" />
        </label>
        <label class="block text-sm text-slate-300">
          简介
          <textarea v-model="form.bio" rows="3" maxlength="500" class="input mt-1" placeholder="介绍一下自己（留空清除）"></textarea>
        </label>
        <Alert v-if="message" :type="messageType" :message="message" />
        <div class="flex gap-3">
          <button class="btn" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存资料' }}</button>
          <button class="btn-ghost" type="button" @click="logout">退出登录</button>
        </div>
      </form>
    </section>

    <section class="rounded-2xl border border-red-500/20 bg-red-500/5 p-6">
      <h2 class="text-lg font-medium text-red-300">危险操作</h2>
      <p class="mt-2 text-sm text-slate-400">注销后邮箱会被匿名化，所有刷新会话立即失效。</p>
      <button class="mt-4 rounded-lg border border-red-500/40 px-4 py-2 text-sm text-red-300 hover:bg-red-500/10" type="button" @click="removeAccount">
        注销账号
      </button>
    </section>
  </div>
</template>
