import { api } from '~/lib/api'

export interface UserBrief {
  id: number
  nickname: string
  avatarUrl: string | null
}

export interface LastPostView {
  postId: number
  topicId: number
  topicTitle: string
  authorId: number
  authorNickname: string
  at: string
}

export interface CategoryNode {
  id: number
  parentId: number | null
  slug: string
  name: string
  description: string | null
  icon: string | null
  color: string | null
  minTags: number
  maxTags: number
  topicCount: number
  postCount: number
  lastPost: LastPostView | null
  children: CategoryNode[]
}

export interface TopicListItem {
  id: number
  title: string
  categorySlug: string
  categoryName: string
  pinned: boolean
  locked: boolean
  postCount: number
  viewCount: number
  author: UserBrief
  createdAt: string
  lastPostAt: string
}

export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

export interface TagView {
  id: number
  name: string
  slug: string
}

export interface PostView {
  id: number
  floor: number
  content: string
  author: UserBrief
  replyToPostId: number | null
  createdAt: string
}

export interface PageData<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface TopicDetail {
  id: number
  title: string
  categorySlug: string
  categoryName: string
  pinned: boolean
  locked: boolean
  viewCount: number
  author: UserBrief
  createdAt: string
  tags: TagView[]
  posts: PageData<PostView>
}

interface Envelope<T> {
  code: number
  message: string
  data: T
}

function readBase(): string {
  if (import.meta.server) {
    return String(useRuntimeConfig().apiInternalBase || '')
  }
  return ''
}

async function readData<T>(path: string, query?: Record<string, string | number>): Promise<T> {
  const response = await $fetch<Envelope<T>>(`${readBase()}${path}`, { query })
  return response.data
}

export function fetchCategories(): Promise<CategoryNode[]> {
  return readData<CategoryNode[]>('/api/v1/categories')
}

export function fetchCategoryTopics(
  slug: string,
  cursor?: string | null
): Promise<CursorPage<TopicListItem>> {
  return readData<CursorPage<TopicListItem>>(
    `/api/v1/categories/${encodeURIComponent(slug)}/topics`,
    cursor ? { cursor } : undefined
  )
}

export function fetchLatestTopics(cursor?: string | null): Promise<CursorPage<TopicListItem>> {
  return readData<CursorPage<TopicListItem>>('/api/v1/topics', cursor ? { cursor } : undefined)
}

export function fetchTopic(id: number, page = 1): Promise<TopicDetail> {
  return readData<TopicDetail>(`/api/v1/topics/${id}`, { page })
}

export async function createTopic(payload: {
  categoryId: number
  title: string
  content: string
  tags: string[]
}): Promise<TopicDetail> {
  const response = await api.post('/topics', payload)
  return response.data.data as TopicDetail
}

export async function replyToTopic(
  topicId: number,
  payload: { content: string; replyToPostId?: number | null }
): Promise<PostView> {
  const response = await api.post(`/topics/${topicId}/posts`, payload)
  return response.data.data as PostView
}
