<template>
  <div class="space-y-10">
    <!-- 公会大厅招牌 -->
    <section class="panel relative overflow-hidden px-6 py-14 text-center sm:px-10 sm:py-16">
      <div class="pointer-events-none absolute -left-10 -top-16 h-48 w-48 rounded-full bg-wine-600/20 blur-3xl" aria-hidden="true" />
      <div class="pointer-events-none absolute -right-12 top-10 h-40 w-40 rounded-full bg-gold-500/15 blur-3xl" aria-hidden="true" />

      <div class="relative">
        <Crest class="mx-auto h-16 w-16 drop-shadow-[0_6px_18px_rgba(0,0,0,0.7)]" />
        <p class="kicker mt-5">ADVENTURERS' GUILD · GAL COMMUNITY</p>
        <h1 class="mt-3 font-display text-4xl font-bold tracking-wide sm:text-5xl">
          <span class="bg-gradient-to-b from-gold-100 via-gold-300 to-gold-500 bg-clip-text text-transparent">
            冒险者公会
          </span>
        </h1>
        <p class="mx-auto mt-4 max-w-xl text-sm leading-relaxed text-parchment-100/70 sm:text-base">
          作品词条、评测与合规资源索引、分区讨论。先注册一个账号，验证邮箱后即可领取你的第一份委托。
        </p>
        <div class="mt-8 flex flex-wrap justify-center gap-3">
          <NuxtLink to="/forum" class="btn">进入论坛</NuxtLink>
          <NuxtLink to="/register" class="btn-ghost">创建账号</NuxtLink>
          <NuxtLink to="/login" class="btn-ghost">已有账号</NuxtLink>
        </div>
      </div>
    </section>

    <!-- 任务告示板 -->
    <section aria-labelledby="guild-notices" class="space-y-4">
      <div class="flex items-center gap-3">
        <h2 id="guild-notices" class="font-display text-sm font-semibold tracking-[0.2em] text-gold-300/90">
          今日告示
        </h2>
        <span class="h-px flex-1 bg-gradient-to-r from-gold-700/50 to-transparent" aria-hidden="true" />
      </div>
      <div class="grid gap-4 sm:grid-cols-3">
        <article class="panel-soft group p-5 transition duration-200 hover:-translate-y-0.5 hover:border-gold-500/40">
          <div class="medallion h-11 w-11 text-xl">📖</div>
          <h3 class="mt-3 font-display text-base font-semibold text-parchment-50">作品词条</h3>
          <p class="mt-1.5 text-sm leading-relaxed text-parchment-100/70">
            按作品归档的评测与讨论入口，方便冒险者查阅与交流。
          </p>
        </article>
        <article class="panel-soft group p-5 transition duration-200 hover:-translate-y-0.5 hover:border-gold-500/40">
          <div class="medallion h-11 w-11 text-xl">🧭</div>
          <h3 class="mt-3 font-display text-base font-semibold text-parchment-50">合规资源索引</h3>
          <p class="mt-1.5 text-sm leading-relaxed text-parchment-100/70">
            仅索引补丁、语音、设定集、攻略与官方商店信息等合规内容。
          </p>
        </article>
        <article class="panel-soft group p-5 transition duration-200 hover:-translate-y-0.5 hover:border-gold-500/40">
          <div class="medallion h-11 w-11 text-xl">🛡️</div>
          <h3 class="mt-3 font-display text-base font-semibold text-parchment-50">分区论坛</h3>
          <p class="mt-1.5 text-sm leading-relaxed text-parchment-100/70">
            加入各分区讨论，登录并验证邮箱后即可发布主题与回复。
          </p>
        </article>
      </div>
    </section>
  </div>
</template>
