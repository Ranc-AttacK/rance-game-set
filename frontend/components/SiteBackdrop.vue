<template>
  <!-- 原创抽象装饰层：粉米色光斑 + 云朵 + 花瓣，全部内联，低透明度，不干扰文字。 -->
  <div class="pointer-events-none fixed inset-0 z-0 overflow-hidden" aria-hidden="true">
    <div class="absolute -left-24 -top-28 h-80 w-80 rounded-full bg-sakura-200/30 blur-3xl" />
    <div class="absolute -right-28 top-24 h-96 w-96 rounded-full bg-amber-100/35 blur-3xl" />
    <div class="absolute bottom-[-10rem] left-1/3 h-80 w-80 rounded-full bg-sky-100/30 blur-3xl" />

    <svg
      class="absolute inset-0 h-full w-full opacity-40"
      viewBox="0 0 1440 900"
      preserveAspectRatio="xMidYMid slice"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <!-- 云朵 -->
      <g fill="#ffffff" opacity="0.55">
        <path d="M148 210c0-20 16-36 36-36 6-22 26-38 50-38 27 0 50 20 54 46 20 1 36 17 36 37 0 21-17 38-38 38H186c-21 0-38-17-38-37Z" />
        <path d="M1080 108c0-16 13-29 29-29 5-18 21-31 40-31 22 0 40 16 44 37 16 1 29 14 29 30 0 17-14 30-31 30h-80c-17 0-31-13-31-30Z" />
        <path d="M1188 690c0-14 11-25 25-25 4-16 18-27 35-27 19 0 35 14 38 32 14 1 25 12 25 26 0 15-12 27-27 27h-69c-15 0-27-12-27-27Z"
          opacity="0.8" />
      </g>

      <!-- 花瓣 -->
      <g stroke="#e88cab" stroke-width="1.6" opacity="0.5">
        <path d="M360 340c-10-22 4-44 26-46 4 22-8 42-26 46Z" />
        <path d="M362 344c22-10 44 4 46 26-22 4-42-8-46-26Z" />
        <path d="M1046 470c-8-18 3-36 21-38 3 18-6 34-21 38Z" />
        <path d="M1048 473c18-8 36 3 38 21-18 3-34-6-38-21Z" />
        <path d="M760 760c-7-15 2-30 18-32 3 15-5 29-18 32Z" />
        <path d="M762 762c15-7 30 2 32 18-15 3-29-5-32-18Z" />
      </g>

      <!-- 细圆环光斑 -->
      <g stroke="#e9c4d2" stroke-width="1.2" opacity="0.45">
        <circle cx="120" cy="640" r="86" />
        <circle cx="1300" cy="320" r="58" />
        <circle cx="560" cy="120" r="42" />
      </g>
    </svg>
  </div>
</template>
