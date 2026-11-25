<script setup lang="ts">
import { createTopic, fetchCategories, type CategoryNode } from '~/lib/forum'
import { apiErrorMessage } from '~/lib/api'

definePageMeta({ middleware: 'auth' })

const { data: categories, error: categoriesError } = await useAsyncData<CategoryNode[]>(
  'new-topic-categories',
  () => fetchCategories()
)

const options = computed(() => {
  const roots = categories.value ?? []
  return roots.flatMap(root => [
    { id: root.id, label: root.name, minTags: root.minTags, maxTags: root.maxTags },
    ...root.children.map(child => ({
      id: child.id,
      label: `└ ${child.name}`,
      minTags: child.minTags,
      maxTags: child.maxTags
    }))
  ])
})

const categoryId = ref<number | null>(null)
const title = ref('')
const content = ref('')
const tagsInput = ref('')
const error = ref('')
const submitting = ref(false)

const selected = computed(() => options.value.find(option => option.id === categoryId.value))
const tagList = computed(() =>
  tagsInput.value
    .split(/[,，\s]+/)
    .map(tag => tag.trim())
    .filter(Boolean)
)
const tagCountValid = computed(() => {
  const option = selected.value
  if (!option) return true
  return tagList.value.length >= option.minTags && tagList.value.length <= option.maxTags
})

async function submit() {
  error.value = ''
  if (!categoryId.value || !title.value.trim() || !content.value.trim()) {
    error.value = '请选择分区并填写标题和正文'
    return
  }
  if (!tagCountValid.value) {
    error.value = `标签数量需在 ${selected.value?.minTags}–${selected.value?.maxTags} 之间`
    return
  }
  submitting.value = true
  try {
    const topic = await createTopic({
      categoryId: categoryId.value,
      title: title.value.trim(),
      content: content.value.trim(),
      tags: [...new Set(tagList.value)]
    })
    await navigateTo(`/topics/${topic.id}`)
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="mx-auto max-w-2xl space-y-5" @submit.prevent="submit">
    <header class="text-center">
      <p class="text-[11px] font-medium uppercase tracking-[0.35em] text-muted">NEW TOPIC</p>
      <h1 class="mt-2 text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">发主题</h1>
      <p class="mt-2 text-sm text-muted">正文按纯文本展示，保留换行。</p>
    </header>

    <div class="card space-y-4 p-5 sm:p-6">
      <p v-if="categoriesError" class="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
        分区加载失败，请刷新重试。
      </p>

      <label class="block text-sm font-medium text-neutral-700">
        分区
        <select v-model="categoryId" class="input mt-1.5">
          <option :value="null" disabled>选择分区</option>
          <option v-for="option in options" :key="option.id" :value="option.id">
            {{ option.label }}
          </option>
        </select>
      </label>

      <label class="block text-sm font-medium text-neutral-700">
        标题
        <input v-model="title" class="input mt-1.5" maxlength="160" placeholder="标题（4–160 字）" />
      </label>
      <label class="block text-sm font-medium text-neutral-700">
        正文
        <textarea v-model="content" rows="12" maxlength="20000" class="input mt-1.5" placeholder="正文（纯文本，保留换行）" />
      </label>
      <p class="!mt-1 text-xs text-muted">支持图片语法：![说明](https://example.com/a.png)，其余内容按纯文本展示。</p>
      <label class="block text-sm font-medium text-neutral-700">
        标签
        <input v-model="tagsInput" class="input mt-1.5" placeholder="标签，用逗号或空格分隔，例如：汉化, 补丁" />
      </label>

      <p v-if="selected" class="text-xs text-muted">
        该分区标签数：{{ selected.minTags }}–{{ selected.maxTags }}，当前 {{ tagList.length }}
      </p>
      <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

      <div class="flex justify-end gap-3 pt-1">
        <NuxtLink to="/forum" class="btn-ghost">取消</NuxtLink>
        <button class="btn" type="submit" :disabled="submitting">
          {{ submitting ? '发布中…' : '发布主题' }}
        </button>
      </div>
    </div>
  </form>
</template>
