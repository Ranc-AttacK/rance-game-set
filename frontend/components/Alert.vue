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
      'border-wine-500/50 bg-wine-600/15 text-wine-100': type === 'error',
      'border-emerald-600/40 bg-emerald-500/10 text-emerald-200': type === 'success',
      'border-gold-700/40 bg-gold-500/10 text-gold-100': type === 'info'
    }"
  >
    <span class="mt-0.5 shrink-0 text-xs" aria-hidden="true">
      {{ type === 'error' ? '✕' : type === 'success' ? '✓' : '◆' }}
    </span>
    <p class="min-w-0 flex-1 leading-relaxed">{{ message }}</p>
  </div>
</template>
