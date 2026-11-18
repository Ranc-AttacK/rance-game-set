<script setup lang="ts">
withDefaults(defineProps<{ label?: string }>(), { label: '' })
</script>

<template>
  <svg
    viewBox="0 0 48 56"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :role="label ? 'img' : undefined"
    :aria-label="label || undefined"
    :aria-hidden="label ? undefined : 'true'"
    focusable="false"
  >
    <!-- 外盾：酒红盾面 + 古金描边 -->
    <path
      d="M24 2.5 L43.5 9.5 V27 C43.5 39.5 34.6 47.8 24 53 C13.4 47.8 4.5 39.5 4.5 27 V9.5 Z"
      fill="#651426"
      stroke="#d4ac45"
      stroke-width="2"
      stroke-linejoin="round"
    />
    <!-- 内盾细线 -->
    <path
      d="M24 6.5 L39.5 12.5 V27 C39.5 37.2 32.2 43.5 24 47.8 C15.8 43.5 8.5 37.2 8.5 27 V12.5 Z"
      stroke="#e2c46a"
      stroke-width="1"
      stroke-linejoin="round"
      opacity="0.65"
    />
    <!-- 竖剑 -->
    <path d="M24 14.5 V39" stroke="#f0dda8" stroke-width="2.4" stroke-linecap="round" />
    <path d="M19.5 34 H28.5" stroke="#f0dda8" stroke-width="2.4" stroke-linecap="round" />
    <path d="M24 14.5 L21.7 19.5 H26.3 Z" fill="#f0dda8" />
    <!-- 剑柄圆头 -->
    <circle cx="24" cy="41.5" r="1.6" fill="#f0dda8" />
    <!-- 上方菱形星 -->
    <path d="M24 6.8 L25.5 9.3 L24 11.8 L22.5 9.3 Z" fill="#e2c46a" />
  </svg>
</template>
