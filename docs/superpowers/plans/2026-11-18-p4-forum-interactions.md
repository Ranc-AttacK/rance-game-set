# P4 论坛互动闭环实施计划

> 状态：执行中（2026-11-18）
> 前置：论坛垂直切片（分区/主题/楼层/发帖/回帖）已上线；本次补齐点赞、关注、通知、编辑与删除。

**Goal:** 让论坛具备反馈回路：用户能点赞、关注主题、收到回复/引用/点赞通知，并能编辑、删除自己的主题与楼层。

**Architecture:** 新增 `notification` 模块（对外暴露 `notification :: api`），community 通过该接口产生通知；点赞/关注/统计的计数与状态存社区表；编辑删除采用软删除 + 受影响范围计数重算。

**Tech Stack:** 既有 Spring Boot/Modulith/JPA/Flyway/MySQL + Nuxt 3。

---

## API 增量

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST/DELETE | `/api/v1/posts/{id}/upvote` | 登录+已验证 | 点赞/取消，禁止自赞，幂等 |
| POST/DELETE | `/api/v1/topics/{id}/follow` | 登录 | 关注/取消关注，维护 follower_count |
| PATCH | `/api/v1/posts/{id}` | 作者 | 编辑楼层正文，edit_count++ |
| DELETE | `/api/v1/posts/{id}` | 作者 | 软删除楼层并重算计数 |
| PATCH | `/api/v1/topics/{id}` | 作者 | 编辑主题标题 |
| DELETE | `/api/v1/topics/{id}` | 作者 | 软删除主题并重算分区计数 |
| GET | `/api/v1/notifications?page=&size=` | 登录 | 通知分页（含 actor 昵称、主题标题） |
| GET | `/api/v1/notifications/unread-count` | 登录 | 未读数 |
| POST | `/api/v1/notifications/{id}/read` | 本人 | 单条已读 |
| POST | `/api/v1/notifications/read-all` | 本人 | 全部已读 |

主题详情对登录用户额外返回 `following` / `followerCount`，楼层返回 `upvoteCount` / `upvoted`（匿名时 `upvoted=false`）。

通知类型：`POST_REPLY`、`POST_QUOTE`、`POST_UPVOTE`、`TOPIC_FOLLOW_REPLY`。保留 30 天，每日清理。

---

## Task 1: V4 迁移与实体

- `V4__community_interactions.sql`：
  - `ALTER community_topics ADD follower_count INT NOT NULL DEFAULT 0`
  - `ALTER community_posts ADD upvote_count INT NOT NULL DEFAULT 0`
  - `community_user_stats(user_id PK, topic_count, post_count, reputation, last_post_at)`
  - `community_post_votes(post_id, user_id, value TINYINT, created_at, PK(post_id,user_id), KEY(user_id,created_at))`
  - `community_topic_follows(topic_id, user_id, state, created_at, PK(topic_id,user_id), KEY(user_id))`
  - `user_notifications(id, recipient_id, actor_id, type, topic_id, topic_title, post_id, read_at, created_at, KEY(recipient_id,read_at,created_at))`
- 实体：Topic + followerCount；Post + upvoteCount；UserStat、PostVote(EmbeddedId)、TopicFollow(EmbeddedId)、Notification。
- 测试：`CommunityInteractionsSchemaIT` 验证表与列存在。
- 测试基类 truncate 追加新表（子表优先），注意不要清空分区种子。

## Task 2: notification 模块

- 包结构：`notification/{api,domain,repo,service,web}`，`package-info` 允许 `shared, identity :: api`，`api` 包 `@NamedInterface("api")`。
- `NotificationApi`：`reply/quote/followReply/upvote(...)`、`page(userId,page,size)`、`unreadCount`、`markRead`、`markAllRead`。
- 服务：插入通知（actor==recipient 时跳过）、分页按 `created_at DESC,id DESC`、批量加载 identity 摘要、已读只允许本人。
- 清理：每日删除 30 天前记录。
- 测试：NotificationApiIT + NotificationControllerIT（分页/未读/已读/越权）。

## Task 3: 点赞 + 声望

- `POST/DELETE /posts/{id}/upvote`：校验 post 可见、非本人、幂等；`upvote_count`、`user_stats.reputation` 原子增减；通知作者（取消不通知）。
- 详情组装 viewer 的投票状态（批量，禁止 N+1）。
- 测试：重复点赞只计 1、取消回退、自赞 42201、匿名 401、作者声望 ±1。

## Task 4: 关注主题

- `POST/DELETE /topics/{id}/follow`：WATCHING 记录；`follower_count` 同步；返回 `following/followerCount`。
- 测试：幂等关注/取消、计数正确、匿名 401。

## Task 5: 回复/引用/关注通知

- 回帖事务内：主题作者 `POST_REPLY`；被引用楼层作者 `POST_QUOTE`；WATCHING 关注者 `TOPIC_FOLLOW_REPLY`；排除操作者本人，按 (recipient,type) 去重。
- 测试：三类通知各自生成、不通知自己、重复关注者只一条。

## Task 6: 编辑与删除

- 编辑楼层：仅作者；正文非空 ≤20000；`edit_count++`。
- 编辑主题：仅作者；标题 4–160。
- 删除楼层：软删除；若删除的是最后楼层则回退 last_post；重算 topic.post_count 与 category.post_count/last_post。
- 删除主题：软删除；重算 category.topic_count/post_count/last_post。
- 用户统计随发主题/回帖/删除同步。
- 测试：非作者 403、幂等删除、计数与最后回复正确、删除后列表/详情不可见。

## Task 7: 前端

- `TopicDetail` 类型扩展 `following/followerCount`、`PostView.upvoteCount/upvoted`；新增 notifications 类型与 API。
- 主题详情：关注按钮；楼层点赞按钮（禁用自赞提示）；作者可见「编辑/删除」；主题作者可见编辑标题/删除主题；操作后刷新。
- 头部铃铛 + 未读红点（登录后 60s 轮询）；`/notifications` 页面（CSR + auth middleware）列表、单条已读、全部已读。
- 保持原型浅色视觉与 E2E 文案/可访问名称。
- 测试：单测/typecheck/build/E2E 全绿。

## Task 8: 文档与提交

- README 增补互动接口；`mvn verify` 全量通过。
- 分两个 commit：后端互动能力、前端互动 UI。

## 完成定义

- 点赞/关注幂等且计数一致；通知不通知自己、无重复轰炸；非作者不能编辑删除；删除后计数与最后回复正确。
- 后端全量测试、前端单测/类型/构建/E2E 全绿。
- 不新增外链资源，不触碰合规红线。
