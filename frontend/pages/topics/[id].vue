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
  <article v-if="topic" class="mx-auto max-w-3xl space-y-5">
    <header class="card p-5 sm:p-7">
      <div class="flex flex-wrap items-center gap-2 text-xs text-muted">
        <NuxtLink
          :to="`/forum/${topic.categorySlug}`"
          class="rounded-full border border-border bg-neutral-50 px-2 py-0.5 transition hover:border-neutral-300 hover:text-foreground"
        >
          {{ topic.categoryName }}
        </NuxtLink>
        <span v-if="topic.pinned" class="badge">置顶</span>
        <span v-if="topic.locked" class="badge">已锁定</span>
        <span>{{ topic.viewCount }} 浏览</span>
      </div>
      <h1 class="mt-3 text-2xl font-semibold leading-snug tracking-tight text-foreground sm:text-3xl">{{ topic.title }}</h1>
      <p class="mt-2 text-sm text-muted">
        由 {{ topic.author.nickname }} 发布于 <LocalTime :value="topic.createdAt" />
      </p>
      <ul v-if="topic.tags.length" class="mt-3 flex flex-wrap gap-1.5">
        <li v-for="tag in topic.tags" :key="tag.id" class="chip px-2 py-0.5 text-[11px]">
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
      <span class="px-3 py-2 text-sm text-muted">第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <NuxtLink
        v-if="page < totalPages"
        :to="`/topics/${topic.id}?page=${page + 1}`"
        class="btn-ghost"
      >
        下一页
      </NuxtLink>
    </nav>

    <section v-if="auth.isAuthenticated && !topic.locked" class="card space-y-3 p-5 sm:p-6">
      <h2 class="text-lg font-semibold text-foreground">回复</h2>
      <p v-if="!auth.user?.emailVerified" class="text-sm text-amber-700">
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
        <p v-if="replyError" class="text-sm text-red-600">{{ replyError }}</p>
        <button class="btn" type="button" :disabled="submitting" @click="submitReply">
          {{ submitting ? '发送中…' : '发布回复' }}
        </button>
      </template>
    </section>
    <div v-else-if="!auth.isAuthenticated" class="card p-5 text-center text-sm text-muted">
      <NuxtLink to="/login" class="font-medium text-foreground underline underline-offset-4 hover:text-sakura-600">登录</NuxtLink>
      后参与讨论。
    </div>
    <div v-else class="card p-5 text-center text-sm text-muted">主题已锁定，暂不能回复。</div>
  </article>

  <p v-else-if="error" class="card p-6 text-sm text-muted">
    主题不存在或已删除。
  </p>
  <p v-else class="text-sm text-muted">加载中…</p>
</template>
