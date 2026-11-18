<script setup lang="ts">
import type { CategoryNode } from '~/lib/forum'

const props = defineProps<{
  categories: CategoryNode[]
  active: string
}>()

const activeRoot = computed(() => {
  if (props.active === 'latest') return 'latest'
  const root = props.categories.find(
    category =>
      category.slug === props.active ||
      category.children.some(child => child.slug === props.active)
  )
  return root?.slug ?? props.active
})
</script>

<template>
  <!-- 移动端：横向 chips；桌面端：左侧白色圆角菜单卡 -->
  <div class="min-w-0 lg:contents">
    <div class="-mx-4 overflow-x-auto px-4 pb-1 lg:hidden">
      <div class="flex w-max items-center gap-2">
        <NuxtLink
          to="/latest"
          class="chip whitespace-nowrap"
          :class="{ active: activeRoot === 'latest' }"
        >
          <CategoryIcon slug="latest" class="h-3.5 w-3.5" />
          最新
        </NuxtLink>
        <NuxtLink
          v-for="category in categories"
          :key="category.id"
          :to="`/forum/${category.slug}`"
          class="chip whitespace-nowrap"
          :class="{ active: activeRoot === category.slug }"
        >
          <CategoryIcon :slug="category.slug" class="h-3.5 w-3.5" />
          {{ category.name }}
        </NuxtLink>
      </div>
    </div>

    <aside class="hidden lg:block">
      <nav
        class="card sticky top-20 p-2"
        aria-label="分区导航"
      >
        <NuxtLink
          to="/latest"
          class="menu-link"
          :class="{ active: activeRoot === 'latest' }"
        >
          <CategoryIcon slug="latest" class="h-4 w-4 shrink-0" />
          最新讨论
        </NuxtLink>
        <div class="my-2 h-px bg-border" aria-hidden="true" />
        <NuxtLink
          v-for="category in categories"
          :key="category.id"
          :to="`/forum/${category.slug}`"
          class="menu-link"
          :class="{ active: activeRoot === category.slug }"
        >
          <CategoryIcon :slug="category.slug" class="h-4 w-4 shrink-0" />
          <span class="truncate">{{ category.name }}</span>
        </NuxtLink>
      </nav>
    </aside>
  </div>
</template>
