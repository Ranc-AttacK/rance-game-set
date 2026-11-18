<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <section class="mx-auto mt-10 w-full max-w-md rounded-2xl border border-white/10 bg-white/5 p-8 shadow-2xl">
    <h1 class="text-2xl font-semibold tracking-tight">{{ title }}</h1>
    <p v-if="subtitle" class="mt-2 text-sm text-slate-400">{{ subtitle }}</p>
    <div class="mt-6">
      <slot />
    </div>
  </section>
</template>
