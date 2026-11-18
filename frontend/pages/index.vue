<template>
  <section class="flex min-h-[60vh] flex-col items-center justify-center text-center">
    <h1 class="text-4xl font-bold tracking-tight">
      <span class="text-brand-400">Galgame</span> 社区
    </h1>
    <p class="mt-4 max-w-xl text-slate-400">
      作品词条、评测与资源索引、分区讨论。先注册一个账号，验证邮箱后即可开始。
    </p>
    <div class="mt-8 flex flex-wrap justify-center gap-3">
      <NuxtLink to="/forum" class="btn">进入论坛</NuxtLink>
      <NuxtLink to="/register" class="btn-ghost">创建账号</NuxtLink>
      <NuxtLink to="/login" class="btn-ghost">已有账号</NuxtLink>
    </div>
  </section>
</template>
