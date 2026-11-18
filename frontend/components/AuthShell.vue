<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <section
    class="mx-auto mt-6 w-full max-w-md overflow-hidden rounded-guild border border-gold-700/40 bg-ink-900/85 shadow-panel"
  >
    <!-- 羊皮纸公告抬头 -->
    <div class="relative border-b border-gold-700/40 bg-gradient-to-b from-parchment-100 to-parchment-300 px-6 py-5 text-ink-950">
      <span class="rivet left-2.5 top-2.5" aria-hidden="true" />
      <span class="rivet right-2.5 top-2.5" aria-hidden="true" />
      <div class="flex items-center gap-3">
        <span class="grid h-11 w-11 shrink-0 place-items-center rounded-full border border-gold-700/60 bg-ink-950/5 shadow-emboss">
          <Crest class="h-7 w-7" />
        </span>
        <div class="min-w-0">
          <h1 class="font-display text-xl font-bold tracking-wide">{{ title }}</h1>
          <p v-if="subtitle" class="mt-0.5 text-xs leading-relaxed text-ink-800/70">{{ subtitle }}</p>
        </div>
      </div>
    </div>
    <div class="p-6">
      <slot />
    </div>
  </section>
</template>
