<script setup lang="ts">
withDefaults(
  defineProps<{ slug?: string | null; label?: string }>(),
  { slug: '', label: '' }
)
</script>

<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
    focusable="false"
  >
    <g v-if="slug === 'forum'">
      <path d="M21 15a2 2 0 0 1-2 2H8l-4 4V5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2Z" />
      <path d="M8 9h8M8 12.5h5" />
    </g>
    <g v-else-if="slug === 'latest'">
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </g>
    <g v-else-if="slug === 'general'">
      <path d="M20.5 11.5a8.5 8.5 0 1 0-3.3 6.7L21 19l-.8-3.8a8.4 8.4 0 0 0 .3-3.7Z" />
      <path d="M8.5 11.5h.01M12 11.5h.01M15.5 11.5h.01" />
    </g>
    <g v-else-if="slug === 'news'">
      <path d="M4.5 5.5h12a2 2 0 0 1 2 2V18a2.5 2.5 0 0 0 2.5 2.5H7A2.5 2.5 0 0 1 4.5 18V5.5Z" />
      <path d="M18.5 9H21v8.5a3 3 0 0 1-3 3" />
      <path d="M7.5 9h6M7.5 12.5h6M7.5 16h4" />
    </g>
    <g v-else-if="slug === 'translation'">
      <path d="M4 5h8M8 3v2c0 4-2 7-5 9" />
      <path d="M4.5 9c1 2.5 3 4.5 5.5 5.5" />
      <path d="m13 21 4-10 4 10" />
      <path d="M14.5 17.5h5" />
    </g>
    <g v-else-if="slug === 'resources'">
      <path d="m12 3 9 5-9 5-9-5 9-5Z" />
      <path d="m3 12 9 5 9-5" />
      <path d="m3 16 9 5 9-5" />
    </g>
    <g v-else-if="slug === 'reviews'">
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
    </g>
    <g v-else-if="slug === 'help'">
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9a2.5 2.5 0 1 1 4 2c-.9.7-1.5 1.2-1.5 2.5" />
      <path d="M12 17h.01" />
    </g>
    <g v-else-if="slug === 'creative'">
      <path d="M12 3a9 9 0 1 0 1.5 17.9 2 2 0 0 0-.4-3.8H13a1.5 1.5 0 0 1 0-3h4a4 4 0 0 0 4-4C21 6.1 17 3 12 3Z" />
      <path d="M7.5 10.5h.01M10 7h.01M14 7h.01M16.5 10.5h.01" />
    </g>
    <g v-else-if="slug === 'chat'">
      <path d="M17 8h1a3 3 0 1 1 0 6h-1" />
      <path d="M3 8h14v7a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4Z" />
      <path d="M6 2v2M10 2v2M14 2v2" />
    </g>
    <g v-else-if="slug === 'patch'">
      <path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.1 2.1-3-3 2.1-2.1Z" />
    </g>
    <g v-else-if="slug === 'voice'">
      <rect x="9" y="3" width="6" height="11" rx="3" />
      <path d="M5 11a7 7 0 0 0 14 0" />
      <path d="M12 18v3M8 21h8" />
    </g>
    <g v-else-if="slug === 'artbook'">
      <path d="M12 6.5C10 5 7.5 4.5 4 5v14c3.5-.5 6 0 8 1.5 2-1.5 4.5-2 8-1.5V5c-3.5-.5-6 0-8 1.5Z" />
      <path d="M12 6.5V21" />
    </g>
    <g v-else-if="slug === 'guide'">
      <path d="m9 4-5 2v14l5-2 6 2 5-2V4l-5 2-6-2Z" />
      <path d="M9 4v14M15 6v14" />
    </g>
    <g v-else-if="slug === 'store'">
      <path d="M5 8h14l-1 12H6L5 8Z" />
      <path d="M9 8V6a3 3 0 0 1 6 0v2" />
    </g>
    <g v-else>
      <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />
    </g>
  </svg>
</template>
