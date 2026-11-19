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
  upvoteCount: number
  upvoted: boolean
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
  following: boolean
  followerCount: number
  author: UserBrief
  createdAt: string
  tags: TagView[]
  posts: PageData<PostView>
}

export interface NotificationView {
  id: number
  type: string
  actorId: number
  actorNickname: string
  actorAvatarUrl: string | null
  topicId: number | null
  topicTitle: string | null
  postId: number | null
  read: boolean
  createdAt: string
}

export interface VoteResult {
  upvoted: boolean
  upvoteCount: number
}

export interface FollowResult {
  following: boolean
  followerCount: number
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

export async function fetchTopicAuthed(id: number, page = 1): Promise<TopicDetail> {
  const response = await api.get(`/topics/${id}`, { params: { page } })
  return response.data.data as TopicDetail
}

export async function setPostUpvote(postId: number, upvote: boolean): Promise<VoteResult> {
  const response = upvote
    ? await api.post(`/posts/${postId}/upvote`)
    : await api.delete(`/posts/${postId}/upvote`)
  return response.data.data as VoteResult
}

export async function setTopicFollow(topicId: number, follow: boolean): Promise<FollowResult> {
  const response = follow
    ? await api.post(`/topics/${topicId}/follow`)
    : await api.delete(`/topics/${topicId}/follow`)
  return response.data.data as FollowResult
}

export async function updatePost(postId: number, content: string): Promise<PostView> {
  const response = await api.patch(`/posts/${postId}`, { content })
  return response.data.data as PostView
}

export async function removePost(postId: number): Promise<void> {
  await api.delete(`/posts/${postId}`)
}

export async function updateTopic(topicId: number, title: string): Promise<TopicDetail> {
  const response = await api.patch(`/topics/${topicId}`, { title })
  return response.data.data as TopicDetail
}

export async function removeTopic(topicId: number): Promise<void> {
  await api.delete(`/topics/${topicId}`)
}

export async function fetchNotifications(page = 1): Promise<PageData<NotificationView>> {
  const response = await api.get('/notifications', { params: { page } })
  return response.data.data as PageData<NotificationView>
}

export async function fetchUnreadCount(): Promise<number> {
  const response = await api.get('/notifications/unread-count')
  return Number(response.data.data.count ?? 0)
}

export async function markNotificationRead(id: number): Promise<void> {
  await api.post(`/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await api.post('/notifications/read-all')
}
