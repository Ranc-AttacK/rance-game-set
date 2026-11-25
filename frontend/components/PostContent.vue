<script setup lang="ts">
const props = defineProps<{ content: string }>()

interface TextSegment {
  type: 'text'
  text: string
}

interface ImageSegment {
  type: 'image'
  src: string
  alt: string
}

type Segment = TextSegment | ImageSegment

const IMAGE_PATTERN = /!\[([^\]]*)\]\(([^)\s]+)\)/g
const SAFE_SRC = /^(https?:\/\/|\/(?!\/))/

const segments = computed<Segment[]>(() => {
  const result: Segment[] = []
  let lastIndex = 0
  IMAGE_PATTERN.lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = IMAGE_PATTERN.exec(props.content)) !== null) {
    if (match.index > lastIndex) {
      result.push({ type: 'text', text: props.content.slice(lastIndex, match.index) })
    }
    if (SAFE_SRC.test(match[2])) {
      result.push({ type: 'image', src: match[2], alt: match[1] })
    } else {
      result.push({ type: 'text', text: match[0] })
    }
    lastIndex = match.index + match[0].length
  }
  if (lastIndex < props.content.length) {
    result.push({ type: 'text', text: props.content.slice(lastIndex) })
  }
  return result
})
</script>

<template>
  <div class="space-y-3">
    <template v-for="(segment, index) in segments" :key="index">
      <p
        v-if="segment.type === 'text' && segment.text.trim()"
        class="whitespace-pre-wrap break-words text-[15px] leading-7 text-neutral-800"
      >{{ segment.text }}</p>
      <figure
        v-else-if="segment.type === 'image'"
        class="overflow-hidden rounded-xl border border-border bg-neutral-50"
      >
        <img
          :src="segment.src"
          :alt="segment.alt"
          class="max-h-[420px] w-full object-cover"
          loading="lazy"
          referrerpolicy="no-referrer"
        />
        <figcaption v-if="segment.alt" class="border-t border-border/70 px-3 py-2 text-xs text-muted">
          {{ segment.alt }}
        </figcaption>
      </figure>
    </template>
  </div>
</template>
