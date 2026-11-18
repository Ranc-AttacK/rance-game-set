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
  <form class="mx-auto max-w-2xl space-y-4" @submit.prevent="submit">
    <header>
      <p class="kicker">POST A QUEST</p>
      <h1 class="section-title mt-1.5">发主题</h1>
      <p class="mt-1.5 text-sm text-parchment-100/70">正文按纯文本展示，保留换行。</p>
    </header>

    <div class="divider-gold" aria-hidden="true" />

    <div class="panel space-y-4 p-5 sm:p-6">
      <p v-if="categoriesError" class="rounded-lg border border-wine-500/50 bg-wine-600/15 p-3 text-sm text-wine-100">
        分区加载失败，请刷新重试。
      </p>

      <label class="block text-sm text-parchment-100/80">
        分区
        <select v-model="categoryId" class="input mt-1">
          <option :value="null" disabled>选择分区</option>
          <option v-for="option in options" :key="option.id" :value="option.id">
            {{ option.label }}
          </option>
        </select>
      </label>

      <input v-model="title" class="input" maxlength="160" placeholder="标题（4–160 字）" />
      <textarea v-model="content" rows="12" maxlength="20000" class="input" placeholder="正文（纯文本，保留换行）" />
      <input v-model="tagsInput" class="input" placeholder="标签，用逗号或空格分隔，例如：汉化, 补丁" />

      <p v-if="selected" class="text-xs text-parchment-100/60">
        该分区标签数：{{ selected.minTags }}–{{ selected.maxTags }}，当前 {{ tagList.length }}
      </p>
      <p v-if="error" class="text-sm text-wine-200">{{ error }}</p>

      <div class="flex justify-end gap-3 pt-1">
        <NuxtLink to="/forum" class="btn-ghost">取消</NuxtLink>
        <button class="btn" type="submit" :disabled="submitting">
          {{ submitting ? '发布中…' : '发布主题' }}
        </button>
      </div>
    </div>
  </form>
</template>
