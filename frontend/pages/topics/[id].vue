<script setup lang="ts">
import { fetchTopic, replyToTopic } from '~/lib/forum'
import { apiErrorMessage } from '~/lib/api'

const route = useRoute()
const auth = useAuthStore()
const id = computed(() => Number(route.params.id))
const page = computed(() => {
  const raw = Number(route.query.page ?? 1)
  return Number.isFinite(raw) && raw >= 1 ? Math.floor(raw) : 1
})

const { data: topic, error, refresh } = await useAsyncData(
  () => `topic-${id.value}-${page.value}`,
  () => fetchTopic(id.value, page.value)
)

const content = ref('')
const replyError = ref('')
const submitting = ref(false)

async function submitReply() {
  if (!content.value.trim()) return
  replyError.value = ''
  submitting.value = true
  try {
    await replyToTopic(id.value, { content: content.value.trim() })
    content.value = ''
    await refresh()
  } catch (e) {
    replyError.value = apiErrorMessage(e)
  } finally {
    submitting.value = false
  }
}

const totalPages = computed(() => {
  if (!topic.value) return 1
  return Math.max(1, Math.ceil(topic.value.posts.total / topic.value.posts.size))
})
</script>

<template>
  <article v-if="topic" class="space-y-6">
    <header class="space-y-2">
      <div class="flex flex-wrap items-center gap-2 text-xs text-slate-400">
        <NuxtLink :to="`/forum/${topic.categorySlug}`" class="text-brand-300">
          {{ topic.categoryName }}
        </NuxtLink>
        <span v-if="topic.pinned" class="rounded bg-brand-500/20 px-2 py-0.5 text-brand-200">置顶</span>
        <span v-if="topic.locked" class="rounded bg-amber-500/20 px-2 py-0.5 text-amber-200">已锁定</span>
        <span>{{ topic.viewCount }} 浏览</span>
      </div>
      <h1 class="text-2xl font-bold">{{ topic.title }}</h1>
      <p class="text-sm text-slate-400">
        由 {{ topic.author.nickname }} 发布于 <LocalTime :value="topic.createdAt" />
      </p>
      <ul v-if="topic.tags.length" class="flex flex-wrap gap-2">
        <li v-for="tag in topic.tags" :key="tag.id" class="rounded bg-white/10 px-2 py-0.5 text-xs">
          #{{ tag.name }}
        </li>
      </ul>
    </header>

    <ul class="space-y-3">
      <PostCard v-for="post in topic.posts.items" :key="post.id" :post="post" />
    </ul>

    <nav v-if="totalPages > 1" class="flex flex-wrap items-center justify-center gap-2">
      <NuxtLink
        v-if="page > 1"
        :to="`/topics/${topic.id}?page=${page - 1}`"
        class="btn-ghost"
      >
        上一页
      </NuxtLink>
      <span class="px-3 py-2 text-sm text-slate-400">第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <NuxtLink
        v-if="page < totalPages"
        :to="`/topics/${topic.id}?page=${page + 1}`"
        class="btn-ghost"
      >
        下一页
      </NuxtLink>
    </nav>

    <section
      v-if="auth.isAuthenticated && !topic.locked"
      class="space-y-3 rounded-lg border border-white/10 bg-white/5 p-4"
    >
      <h2 class="font-semibold">回复</h2>
      <p v-if="!auth.user?.emailVerified" class="text-sm text-amber-300">
        请先验证邮箱后再发言。
      </p>
      <template v-else>
        <textarea
          v-model="content"
          rows="5"
          maxlength="20000"
          class="input"
          placeholder="写下你的回复…"
        />
        <p v-if="replyError" class="text-sm text-red-300">{{ replyError }}</p>
        <button class="btn" type="button" :disabled="submitting" @click="submitReply">
          {{ submitting ? '发送中…' : '发布回复' }}
        </button>
      </template>
    </section>
    <p v-else-if="!auth.isAuthenticated" class="text-sm text-slate-400">
      <NuxtLink to="/login" class="text-brand-300">登录</NuxtLink> 后参与讨论。
    </p>
    <p v-else class="text-sm text-slate-400">主题已锁定，暂不能回复。</p>
  </article>

  <p v-else-if="error" class="rounded border border-white/10 bg-white/5 p-6 text-slate-400">
    主题不存在或已删除。
  </p>
  <p v-else class="text-slate-400">加载中…</p>
</template>
