<script setup lang="ts">
import {
  fetchTopic,
  fetchTopicAuthed,
  removePost,
  removeTopic,
  replyToTopic,
  setPostUpvote,
  setTopicFollow,
  updatePost,
  updateTopic,
  type PostView
} from '~/lib/forum'
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

const currentUserId = computed(() => auth.user?.id ?? null)
const isTopicAuthor = computed(() => currentUserId.value !== null && currentUserId.value === topic.value?.author.id)

const content = ref('')
const replyError = ref('')
const submitting = ref(false)
const actionError = ref('')
const busy = ref(false)
const editingPostId = ref<number | null>(null)
const editDraft = ref('')
const editingTitle = ref(false)
const titleDraft = ref('')

async function reload() {
  if (auth.isAuthenticated) {
    topic.value = await fetchTopicAuthed(id.value, page.value)
  } else {
    await refresh()
  }
}

onMounted(async () => {
  if (!auth.isAuthenticated) return
  try {
    topic.value = await fetchTopicAuthed(id.value, page.value)
  } catch {
    // SSR 数据仍可用；交互状态稍后由操作刷新
  }
})

async function submitReply() {
  if (!content.value.trim()) return
  replyError.value = ''
  submitting.value = true
  try {
    await replyToTopic(id.value, { content: content.value.trim() })
    content.value = ''
    await reload()
  } catch (e) {
    replyError.value = apiErrorMessage(e)
  } finally {
    submitting.value = false
  }
}

async function toggleFollow() {
  if (!topic.value || busy.value) return
  actionError.value = ''
  busy.value = true
  try {
    const result = await setTopicFollow(topic.value.id, !topic.value.following)
    topic.value.following = result.following
    topic.value.followerCount = result.followerCount
  } catch (e) {
    actionError.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

async function toggleUpvote(post: PostView) {
  if (busy.value) return
  actionError.value = ''
  busy.value = true
  try {
    const result = await setPostUpvote(post.id, !post.upvoted)
    post.upvoted = result.upvoted
    post.upvoteCount = result.upvoteCount
  } catch (e) {
    actionError.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

function startEdit(post: PostView) {
  editingPostId.value = post.id
  editDraft.value = post.content
}

async function saveEdit(post: PostView) {
  if (!editDraft.value.trim() || busy.value) return
  actionError.value = ''
  busy.value = true
  try {
    await updatePost(post.id, editDraft.value.trim())
    editingPostId.value = null
    await reload()
  } catch (e) {
    actionError.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

async function deletePost(post: PostView) {
  if (busy.value) return
  if (!window.confirm('确定删除这个楼层？')) return
  actionError.value = ''
  busy.value = true
  try {
    await removePost(post.id)
    await reload()
  } catch (e) {
    actionError.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

function startTitleEdit() {
  if (!topic.value) return
  titleDraft.value = topic.value.title
  editingTitle.value = true
}

async function saveTitle() {
  if (!titleDraft.value.trim() || busy.value) return
  actionError.value = ''
  busy.value = true
  try {
    topic.value = await updateTopic(id.value, titleDraft.value.trim())
    editingTitle.value = false
  } catch (e) {
    actionError.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

async function deleteTopic() {
  if (!topic.value || busy.value) return
  if (!window.confirm('确定删除整个主题？此操作会隐藏主题和所有楼层。')) return
  actionError.value = ''
  busy.value = true
  try {
    const slug = topic.value.categorySlug
    await removeTopic(id.value)
    await navigateTo(`/forum/${slug}`)
  } catch (e) {
    actionError.value = apiErrorMessage(e)
    busy.value = false
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
        <span>{{ topic.followerCount }} 关注</span>
        <button
          v-if="auth.isAuthenticated"
          type="button"
          class="ml-auto rounded-full border px-2.5 py-1 transition"
          :class="topic.following
            ? 'border-accent bg-accent text-accent-foreground'
            : 'border-border text-muted hover:border-neutral-300 hover:text-foreground'"
          :disabled="busy"
          @click="toggleFollow"
        >
          {{ topic.following ? '已关注' : '关注' }}
        </button>
      </div>

      <template v-if="editingTitle">
        <div class="mt-3 space-y-2">
          <input v-model="titleDraft" class="input" maxlength="160" />
          <div class="flex justify-end gap-2">
            <button class="btn-ghost px-3 py-1.5 text-sm" type="button" @click="editingTitle = false">取消</button>
            <button class="btn px-3 py-1.5 text-sm" type="button" :disabled="busy" @click="saveTitle">保存标题</button>
          </div>
        </div>
      </template>
      <template v-else>
        <h1 class="mt-3 text-2xl font-semibold leading-snug tracking-tight text-foreground sm:text-3xl">{{ topic.title }}</h1>
        <div v-if="isTopicAuthor" class="mt-2 flex gap-2 text-xs">
          <button type="button" class="btn-ghost px-2.5 py-1 text-xs" @click="startTitleEdit">编辑标题</button>
          <button type="button" class="btn-danger px-2.5 py-1 text-xs" @click="deleteTopic">删除主题</button>
        </div>
      </template>

      <p class="mt-2 text-sm text-muted">
        由 {{ topic.author.nickname }} 发布于 <LocalTime :value="topic.createdAt" />
      </p>
      <ul v-if="topic.tags.length" class="mt-3 flex flex-wrap gap-1.5">
        <li v-for="tag in topic.tags" :key="tag.id" class="chip px-2 py-0.5 text-[11px]">
          #{{ tag.name }}
        </li>
      </ul>
    </header>

    <p v-if="actionError" class="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
      {{ actionError }}
    </p>

    <div class="space-y-3">
      <template v-for="post in topic.posts.items" :key="post.id">
        <div v-if="editingPostId === post.id" class="card space-y-3 p-4 sm:p-5">
          <p class="text-xs text-muted">编辑 #{{ post.floor }} 楼层</p>
          <textarea v-model="editDraft" rows="6" maxlength="20000" class="input" />
          <div class="flex justify-end gap-2">
            <button class="btn-ghost px-3 py-1.5 text-sm" type="button" @click="editingPostId = null">取消</button>
            <button class="btn px-3 py-1.5 text-sm" type="button" :disabled="busy" @click="saveEdit(post)">保存修改</button>
          </div>
        </div>
        <PostCard
          v-else
          :post="post"
          :current-user-id="currentUserId"
          :authenticated="auth.isAuthenticated"
          @upvote="toggleUpvote"
          @edit="startEdit"
          @delete="deletePost"
        />
      </template>
    </div>

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
