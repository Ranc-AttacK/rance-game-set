<script setup lang="ts">
import type { PostView } from '~/lib/forum'

defineProps<{ post: PostView }>()
</script>

<template>
  <li class="card p-4 sm:p-5">
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
  </li>
</template>
