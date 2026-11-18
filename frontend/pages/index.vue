<script setup lang="ts">
interface Entry {
  slug: string
  name: string
  to: string
}

const entries: Entry[] = [
  { slug: 'forum', name: '论坛', to: '/forum' },
  { slug: 'latest', name: '最新讨论', to: '/latest' },
  { slug: 'general', name: '综合讨论', to: '/forum/general' },
  { slug: 'news', name: '新作情报', to: '/forum/news' },
  { slug: 'translation', name: '汉化交流', to: '/forum/translation' },
  { slug: 'resources', name: '补丁与资料', to: '/forum/resources' },
  { slug: 'help', name: '求助问答', to: '/forum/help' },
  { slug: 'chat', name: '水区闲聊', to: '/forum/chat' }
]
</script>

<template>
  <div class="space-y-12 sm:space-y-16">
    <section class="flex flex-col items-center px-2 pt-4 text-center sm:pt-12">
      <p class="text-[11px] font-medium uppercase tracking-[0.35em] text-muted">GALGAME COMMUNITY</p>
      <h1 class="mt-4 text-4xl font-semibold tracking-tight text-foreground sm:text-5xl">
        <span class="text-sakura-500">Galgame</span> 社区
      </h1>
      <p class="mt-5 max-w-xl text-sm leading-7 text-muted sm:text-base">
        一个 galgame 交流社区：作品讨论、新作情报、汉化协作与合规资源索引。
        资源仅限补丁、语音、设定集、攻略与官方商店信息。
      </p>
      <div class="mt-8 flex flex-wrap justify-center gap-3">
        <NuxtLink to="/forum" class="btn">进入论坛</NuxtLink>
        <NuxtLink to="/register" class="btn-ghost">创建账号</NuxtLink>
        <NuxtLink to="/login" class="btn-ghost">已有账号</NuxtLink>
      </div>
    </section>

    <section aria-label="社区入口">
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-4">
        <NuxtLink
          v-for="entry in entries"
          :key="entry.slug"
          :to="entry.to"
          class="card card-hover flex flex-col items-center justify-center gap-3 px-3 py-8 text-center sm:py-9"
        >
          <span class="icon-tile h-12 w-12">
            <CategoryIcon :slug="entry.slug" class="h-7 w-7" />
          </span>
          <span class="text-sm font-medium text-foreground">{{ entry.name }}</span>
        </NuxtLink>
      </div>
    </section>
  </div>
</template>
