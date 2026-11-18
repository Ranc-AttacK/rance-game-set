<script setup lang="ts">
withDefaults(defineProps<{ type?: 'error' | 'success' | 'info'; message: string }>(), {
  type: 'info'
})
</script>

<template>
  <p
    class="mt-4 rounded-lg border px-3 py-2 text-sm"
    :class="{
      'border-red-500/30 bg-red-500/10 text-red-300': type === 'error',
      'border-emerald-500/30 bg-emerald-500/10 text-emerald-300': type === 'success',
      'border-sky-500/30 bg-sky-500/10 text-sky-300': type === 'info'
    }"
  >
    {{ message }}
  </p>
</template>
