<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <section class="mx-auto mt-4 w-full max-w-2xl">
    <div class="card overflow-hidden">
      <div class="border-b border-border px-6 py-5 text-center">
        <span class="mx-auto grid h-10 w-10 place-items-center rounded-lg bg-accent text-accent-foreground" aria-hidden="true">
          <svg viewBox="0 0 24 24" class="h-5 w-5" fill="currentColor">
            <path d="M12 2.4l2.1 7.5 7.5 2.1-7.5 2.1L12 21.6l-2.1-7.5L2.4 12l7.5-2.1L12 2.4Z" />
          </svg>
        </span>
        <h1 class="mt-3 text-xl font-semibold tracking-tight text-foreground">{{ title }}</h1>
        <p v-if="subtitle" class="mt-1 text-xs leading-relaxed text-muted">{{ subtitle }}</p>
      </div>
      <div class="p-6">
        <slot />
      </div>
    </div>
  </section>
</template>
