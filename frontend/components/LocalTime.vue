<script setup lang="ts">
const props = defineProps<{ value: string | null | undefined }>()

const text = computed(() => {
  if (!props.value) return ''
  return new Date(props.value).toLocaleString('zh-CN')
})
</script>

<template>
  <ClientOnly>
    <time v-if="value" :datetime="value">{{ text }}</time>
    <span v-else>—</span>
    <template #fallback><span class="text-muted">—</span></template>
  </ClientOnly>
</template>
