<script setup lang="ts">
import type { PostView } from '~/lib/forum'

const props = defineProps<{
  post: PostView
  currentUserId: number | null
  authenticated: boolean
}>()

const emit = defineEmits<{
  upvote: [post: PostView]
  edit: [post: PostView]
  delete: [post: PostView]
}>()

const isOwn = computed(() => props.currentUserId !== null && props.currentUserId === props.post.author.id)
</script>

<template>
  <article class="card p-4 sm:p-5">
    <header class="flex flex-wrap items-center justify-between gap-2 text-xs">
      <div class="flex flex-wrap items-center gap-2">
        <span class="rounded-md bg-neutral-100 px-1.5 py-0.5 font-mono text-[11px] font-medium text-muted">#{{ post.floor }}</span>
        <span class="font-medium text-foreground">{{ post.author.nickname }}</span>
        <span v-if="post.replyToPostId" class="badge">引用楼层</span>
      </div>
      <span class="text-muted">
        <LocalTime :value="post.createdAt" />
      </span>
    </header>
    <div class="my-3 h-px bg-border" aria-hidden="true" />
    <p class="whitespace-pre-wrap break-words text-[15px] leading-7 text-neutral-800">{{ post.content }}</p>

    <footer class="mt-3 flex flex-wrap items-center gap-2 border-t border-border/70 pt-3 text-xs">
      <button
        v-if="authenticated && !isOwn"
        type="button"
        class="inline-flex items-center gap-1 rounded-full border px-2.5 py-1 transition"
        :class="post.upvoted
          ? 'border-sakura-500 bg-sakura-500/10 text-sakura-600'
          : 'border-border text-muted hover:border-neutral-300 hover:text-foreground'"
        :aria-pressed="post.upvoted"
        @click="emit('upvote', post)"
      >
        <svg viewBox="0 0 24 24" class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 20V6M6 12l6-6 6 6" />
        </svg>
        {{ post.upvoted ? '已赞' : '赞' }} {{ post.upvoteCount }}
      </button>
      <span v-else class="text-muted">赞 {{ post.upvoteCount }}</span>

      <span v-if="isOwn" class="ml-auto flex items-center gap-2">
        <button type="button" class="btn-ghost px-2.5 py-1 text-xs" @click="emit('edit', post)">编辑</button>
        <button type="button" class="btn-danger px-2.5 py-1 text-xs" @click="emit('delete', post)">删除</button>
      </span>
    </footer>
  </article>
</template>
