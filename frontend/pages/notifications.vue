<script setup lang="ts">
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationView
} from '~/lib/forum'
import { apiErrorMessage } from '~/lib/api'

definePageMeta({ middleware: 'auth' })

const items = ref<NotificationView[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const error = ref('')

const typeText: Record<string, string> = {
  POST_REPLY: '回复了你的主题',
  POST_QUOTE: '引用了你的楼层',
  POST_UPVOTE: '赞了你的楼层',
  TOPIC_FOLLOW_REPLY: '回复了你关注的主题'
}

function description(notification: NotificationView) {
  return `${notification.actorNickname} ${typeText[notification.type] ?? '有新动态'}`
}

async function load(target = 1) {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchNotifications(target)
    items.value = result.items
    page.value = result.page
    total.value = result.total
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => void load(1))

async function open(notification: NotificationView) {
  if (!notification.read) {
    try {
      await markNotificationRead(notification.id)
      notification.read = true
    } catch {
      // 未读状态更新失败不阻塞跳转
    }
  }
  if (notification.topicId) {
    await navigateTo(`/topics/${notification.topicId}`)
  }
}

async function readAll() {
  try {
    await markAllNotificationsRead()
    items.value = items.value.map(item => ({ ...item, read: true }))
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
</script>

<template>
  <section class="mx-auto max-w-3xl space-y-5">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="text-xs uppercase tracking-[0.22em] text-muted">NOTIFICATIONS</p>
        <h1 class="page-title mt-2">通知</h1>
      </div>
      <button class="btn-ghost px-3 py-1.5 text-sm" type="button" @click="readAll">全部已读</button>
    </header>

    <p v-if="error" class="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{{ error }}</p>

    <div v-if="items.length === 0 && !loading" class="card p-10 text-center text-sm text-muted">
      暂时没有通知。
    </div>

    <ul v-else class="divide-y divide-border overflow-hidden rounded-xl border border-border bg-card">
      <li v-for="notification in items" :key="notification.id">
        <button
          type="button"
          class="flex w-full items-start gap-3 px-4 py-3 text-left transition hover:bg-neutral-50"
          @click="open(notification)"
        >
          <span
            class="mt-1.5 h-2 w-2 shrink-0 rounded-full"
            :class="notification.read ? 'bg-neutral-200' : 'bg-sakura-500'"
            aria-hidden="true"
          />
          <span class="min-w-0 flex-1">
            <span class="block text-sm text-foreground">{{ description(notification) }}</span>
            <span v-if="notification.topicTitle" class="mt-0.5 block truncate text-xs text-muted">
              {{ notification.topicTitle }}
            </span>
          </span>
          <span class="shrink-0 text-xs text-muted">
            <LocalTime :value="notification.createdAt" />
          </span>
        </button>
      </li>
    </ul>

    <nav v-if="totalPages > 1" class="flex items-center justify-center gap-2">
      <button class="btn-ghost px-3 py-1.5 text-sm" type="button" :disabled="page <= 1 || loading" @click="load(page - 1)">
        上一页
      </button>
      <span class="px-2 text-sm text-muted">第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <button class="btn-ghost px-3 py-1.5 text-sm" type="button" :disabled="page >= totalPages || loading" @click="load(page + 1)">
        下一页
      </button>
    </nav>
  </section>
</template>
