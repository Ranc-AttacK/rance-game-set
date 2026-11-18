<script setup lang="ts">
withDefaults(defineProps<{ type?: 'error' | 'success' | 'info'; message: string }>(), {
  type: 'info'
})
</script>

<template>
  <div
    class="mt-4 flex items-start gap-2 rounded-lg border px-3 py-2 text-sm"
    :role="type === 'error' ? 'alert' : 'status'"
    :class="{
      'border-red-200 bg-red-50 text-red-700': type === 'error',
      'border-emerald-200 bg-emerald-50 text-emerald-700': type === 'success',
      'border-border bg-neutral-50 text-neutral-700': type === 'info'
    }"
  >
    <span class="mt-0.5 shrink-0 text-xs" aria-hidden="true">
      {{ type === 'error' ? '✕' : type === 'success' ? '✓' : '◆' }}
    </span>
    <p class="min-w-0 flex-1 leading-relaxed">{{ message }}</p>
  </div>
</template>
