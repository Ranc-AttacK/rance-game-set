# P4 论坛设计 · v2.1（NodeBB-lite）

> 状态：已确认（2026-11-16）
> 关系：本文件**取代**主设计文档 v2 中 P4 相关章节（community / moderation / notification / search / 论坛 UI）。
> P1（认证）、P2（作品与媒体）、P3（资源与评测）**不受影响**。
> 参考：`galgame.dev`（真红论坛，NodeBB v4.4.4）+ 作者 fork 源码 + 截图 `C:\D\RaWorld\图\开发\真红论坛.png`。

## 1. 结论与边界

- 论坛采用 **NodeBB-lite** 模型：分区 → 主题 → 扁平楼层 + 标签 + 关注 + 投票 + 分区版主 + 举报工单。
- 不引入 NodeBB 的 Node.js 运行时、插件/主题体系、群组/聊天/草稿/多皮肤。
- 首页保留 v2 的 **A 发现型**；`/forum` 系列采用 NodeBB 式三栏布局。
- **合规红线**：不托管/不索引游戏本体；禁止磁力/种子；资源分区只允许补丁/语音包/设定集/攻略/官方商店；MVP 全年龄，CG 包/涩图/R18 二期再评估。

## 2. 信息架构（IA）

### 2.1 桌面三栏

```
┌──────── 左导航 ────────┬────────── 主内容 ──────────┬──── 右栏 ────┐
│ 版块                    │ 分区卡片列表                │ 搜索          │
│ 最新                    │ 图标 / 标题 / 描述           │ 通知          │
│ 热门                    │ 子分区 chips                │ 当前用户       │
│ 标签                    │ 主题数 / 帖子数              │               │
│ 用户                    │ 最后回复预览                 │               │
└─────────────────────────┴────────────────────────────┴───────────────┘
```

### 2.2 导航范围

| 导航 | MVP | 说明 |
|---|---|---|
| 版块 | ✅ | 分区树 + 统计 + 最后回复 |
| 最新 | ✅ | 全站主题流，游标分页 |
| 热门 | ✅ | 近 7 天高热主题 |
| 标签 | ✅ | 标签聚合与筛选 |
| 用户 | ✅ | 用户目录/搜索 |
| 未读 | ⏳ P5 | 需要 per-user 已读游标 |
| 群组 | ⏳ P5+ | MVP 用"分区版主"治理 |
| 聊天 / 草稿 / 多皮肤 | ❌ | 私信/草稿/皮肤后置；保留亮暗主题 |

右栏 MVP：搜索、通知、用户菜单；不做聊天/草稿。

### 2.3 页面地图（替换 v2 的 `/forum`、`/posts` 部分）

| 路径 | 页面 | 渲染 |
|---|---|---|
| `/forum` | 分区列表（NodeBB 式） | SSR |
| `/forum/:categorySlug` | 主题列表（最新/热门/标签筛选） | SSR |
| `/topics/:id` | 主题详情（扁平楼层、分页） | SSR |
| `/topics/new`、`/topics/:id/edit` | 发主题/编辑 | CSR |
| `/latest`、`/popular` | 全站主题流 | SSR |
| `/tags`、`/tags/:slug` | 标签页 | SSR |
| `/users`、`/users/:id` | 用户目录/主页 | SSR |
| `/moderation/flags` | 举报工单队列 | CSR |
| `/` | 首页（保持 A：推荐 + 最新资源 + 最新讨论） | SSR |

## 3. 分区种子（MVP）

```
综合讨论
新作情报
汉化交流
补丁与资料
  ├─ 汉化补丁
  ├─ 语音包
  ├─ 设定集
  ├─ 攻略
  └─ 官方商店
攻略与感想
求助问答（支持最佳答案）
原创与同人
水区 / 闲聊
```

不创建：资源本体、磁力/网盘整包、CG 包、涩图、R18 分区。

## 4. 数据模型（community 模块）

> 替换 v2 的 `community_posts` + `community_comments`。所有表带 `created_at`，可变对象带 `updated_at`。

### 4.1 categories

```sql
community_categories (
  id BIGINT PK AUTO_INCREMENT,
  parent_id BIGINT NULL,
  slug VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NULL,
  icon VARCHAR(80) NULL,
  color VARCHAR(16) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled BIT(1) NOT NULL DEFAULT b'1',
  external_link VARCHAR(500) NULL,
  min_tags INT NOT NULL DEFAULT 0,
  max_tags INT NOT NULL DEFAULT 5,
  topic_count BIGINT NOT NULL DEFAULT 0,
  post_count BIGINT NOT NULL DEFAULT 0,
  last_post_id BIGINT NULL,
  last_post_at DATETIME(6) NULL
)
```

索引：`(parent_id, sort_order, id)`、`(enabled, sort_order)`、`(last_post_at)`。

### 4.2 topics

```sql
community_topics (
  id BIGINT PK AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  work_id BIGINT NULL,
  author_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,              -- DISCUSSION/NEWS/DEVLOG/GUIDE
  title VARCHAR(160) NOT NULL,
  slug VARCHAR(180) NOT NULL UNIQUE,
  pinned BIT(1) NOT NULL DEFAULT b'0',
  pinned_until DATETIME(6) NULL,
  locked BIT(1) NOT NULL DEFAULT b'0',
  visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC', -- PUBLIC/HIDDEN
  deleted_at DATETIME(6) NULL,
  post_count INT NOT NULL DEFAULT 0,
  view_count BIGINT NOT NULL DEFAULT 0,
  follower_count INT NOT NULL DEFAULT 0,
  last_post_id BIGINT NULL,
  last_post_at DATETIME(6) NULL,
  accepted_post_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
)
```

索引：
- `(category_id, visibility, deleted_at, pinned, last_post_at, id)`
- `(category_id, visibility, deleted_at, last_post_at, id)`
- `(author_id, created_at, id)`
- `(work_id, last_post_at)`
- `(last_post_at, id)`
- `FULLTEXT(title) WITH PARSER ngram`

### 4.3 posts（扁平楼层）

```sql
community_posts (
  id BIGINT PK AUTO_INCREMENT,
  topic_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  content MEDIUMTEXT NOT NULL,
  reply_to_post_id BIGINT NULL,           -- 引用楼层，不嵌套
  floor INT NOT NULL,                     -- 1 = 首楼
  visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
  deleted_at DATETIME(6) NULL,
  edit_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
)
```

索引：`(topic_id, floor)`、`(topic_id, visibility, deleted_at, created_at, id)`、`(author_id, created_at)`、`(reply_to_post_id)`、`FULLTEXT(content) WITH PARSER ngram`。

### 4.4 tags

```sql
community_tags (id BIGINT PK, name VARCHAR(40) UNIQUE, slug VARCHAR(60) UNIQUE, color VARCHAR(16) NULL)
community_topic_tags (topic_id BIGINT, tag_id BIGINT, PRIMARY KEY(topic_id, tag_id))
```

索引：`community_topic_tags(tag_id, topic_id)`。

### 4.5 votes

```sql
community_post_votes (post_id BIGINT, user_id BIGINT, value TINYINT NOT NULL, created_at DATETIME(6),
                      PRIMARY KEY(post_id, user_id))
```

索引：`(user_id, created_at)`、`(post_id, value)`。MVP 只允许 `value=1`（upvote）；downvote 字段保留、默认关闭。

### 4.6 follows

```sql
community_topic_follows (topic_id BIGINT, user_id BIGINT, state VARCHAR(16) NOT NULL, created_at DATETIME(6),
                         PRIMARY KEY(topic_id, user_id))
community_category_follows (category_id BIGINT, user_id BIGINT, state VARCHAR(16) NOT NULL, created_at DATETIME(6),
                            PRIMARY KEY(category_id, user_id))
```

状态：`WATCHING / IGNORING`（topic），`WATCHING / NOT_WATCHING / IGNORING`（category）。标签关注后置。

### 4.7 治理

```sql
community_category_moderators (category_id BIGINT, user_id BIGINT, PRIMARY KEY(category_id, user_id))

community_user_stats (user_id BIGINT PK, topic_count INT, post_count INT, reputation INT, last_post_at DATETIME(6))

community_post_edits (id BIGINT PK, post_id BIGINT, editor_id BIGINT,
                      old_content MEDIUMTEXT, new_content MEDIUMTEXT, patch MEDIUMTEXT,
                      created_at DATETIME(6))

community_topic_events (id BIGINT PK, topic_id BIGINT, actor_id BIGINT,
                        type VARCHAR(24), meta JSON, created_at DATETIME(6))
```

索引：`category_moderators(user_id)`、`user_stats(reputation)`、`post_edits(post_id, created_at)`、`topic_events(topic_id, created_at)`。

### 4.8 举报工单（替换 v2 的 reports/actions）

```sql
moderation_flags (
  id BIGINT PK AUTO_INCREMENT,
  target_type VARCHAR(16) NOT NULL,       -- TOPIC/POST/USER/WORK/RESOURCE/REVIEW
  target_id BIGINT NOT NULL,
  state VARCHAR(16) NOT NULL,             -- OPEN/WIP/RESOLVED/REJECTED
  assignee_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  resolved_at DATETIME(6) NULL,
  UNIQUE KEY uk_flag_target (target_type, target_id)
)

moderation_reports (id BIGINT PK, flag_id BIGINT, reporter_id BIGINT,
                    reason VARCHAR(32), detail VARCHAR(1000), created_at DATETIME(6))

moderation_notes (id BIGINT PK, flag_id BIGINT, author_id BIGINT,
                  content VARCHAR(1000), created_at DATETIME(6))
```

索引：`flags(state, created_at)`、`flags(assignee_id, state)`、`reports(flag_id, created_at)`、`notes(flag_id, created_at)`。
`moderation_actions`（v2 已有）保留，作为最终处置审计。

### 4.9 通知（扩展 v2）

- 新增 `merge_key VARCHAR(120) NULL`；保留唯一 `(event_id, recipient_id)`。
- 类型：`POST_REPLY / POST_QUOTE / POST_UPVOTE / TOPIC_FOLLOW_REPLY / CATEGORY_WATCH_TOPIC / POST_EDIT / FLAG / FLAG_ASSIGNED / REPORT_RESULT / FOLLOW / ACCEPTED_ANSWER`。
- 读取时按 `merge_key` 聚合展示（如"3 人赞了你的帖子"）。
- 保留期 30 天，定时清理；索引 `(created_at)`。
- 标签关注、@提及后置 P5。

## 5. 权限矩阵

| 能力 | 游客 | 注册用户 | 分区版主 | 管理员 |
|---|---|---|---|---|
| 读公开分区/主题/帖子/搜索 | ✅ | ✅ | ✅ | ✅ |
| 发主题/回复/引用 | ❌ | ✅（邮箱已验证） | ✅ | ✅ |
| 编辑/删除自己的主题/帖子 | ❌ | ✅ | ✅ | ✅ |
| upvote（不可自投） | ❌ | ✅（声望 ≥ 0） | ✅ | ✅ |
| 关注主题/分区 | ❌ | ✅ | ✅ | ✅ |
| 举报 | ❌ | ✅ | ✅ | ✅ |
| 最佳答案（问答区） | ❌ | 仅主题作者 | ✅ | ✅ |
| 置顶/锁定/移动/隐藏/恢复 | ❌ | ❌ | 本分区 | ✅ |
| 查看编辑历史/已删除 | ❌ | 仅自己的 | ✅ | ✅ |
| 举报工单分派/备注/处置 | ❌ | ❌ | ✅ | ✅ |
| 封禁/禁言/用户管理 | ❌ | ❌ | ❌ | ✅ |

- downvote 默认关闭；如开启需单独权限与最低声望。
- 社区版主由 `community_category_moderators` 判定；管理员全局。

## 6. 关键流程

1. **发主题**：校验分区启用/锁定、标签数量（`min_tags/max_tags`）、标题与正文 → 同一事务插入 topic（`post_count=1`、`last_post_id`=首楼、`last_post_at=now`）与首楼 post（`floor=1`）→ 原子更新分区 `topic_count/post_count/last_post` 与 `user_stats` → 通知分区关注者（`CATEGORY_WATCH_TOPIC`）。
2. **回复/引用**：校验主题未锁定/未删除 → 取得下一个 `floor`（事务内原子分配）→ 插入 post（可带 `reply_to_post_id`）→ 更新 topic `post_count/last_post`、分区 `post_count/last_post`、`user_stats` → 通知主题作者/关注者（`POST_REPLY`/`TOPIC_FOLLOW_REPLY`）与被引用者（`POST_QUOTE`），排除自己并去重。
3. **编辑**：作者/版主 → 保存旧内容到 `post_edits`（含 diff patch）→ 更新正文、`edit_count` → 通知主题关注者（`POST_EDIT`，按 merge_key 聚合）。
4. **upvote**：唯一键幂等 → 新增/取消投票 → 作者声望 ±1（不可自投、最低声望）→ 通知作者（`POST_UPVOTE`）。
5. **关注**：主题/分区 WATCHING ↔ IGNORING；更新 `follower_count`。
6. **最佳答案**：仅问答区分区、主题作者/版主可设置；`accepted_post_id` 单值；通知答主（`ACCEPTED_ANSWER`）。
7. **版主操作**：置顶/取消、锁定/取消、移动分区、隐藏/恢复/删除 → 写 `topic_events`；移动时同时调整两个分区的计数与最后回复；通知主题作者（可选）。
8. **举报**：按 `(target_type,target_id)` 找到或创建 flag（状态 OPEN）→ 追加 report → 通知版主（`FLAG`）→ 版主分派/备注/处置 → 写 `moderation_actions` → 更新 flag 状态 → 通知举报人（`REPORT_RESULT`）。
9. **列表**：
   - 分区主题：`ORDER BY pinned DESC, last_post_at DESC, id DESC`，游标 `(pinned, last_post_at, id)`。
   - 最新：`ORDER BY last_post_at DESC, id DESC`，游标。
   - 热门：近 7 天 `last_post_at`，按 `post_count*3 + view_count` 降序，最多 20 页 offset（不做深翻）。
10. **通知轮询**：未读数 + 列表；按 `merge_key` 聚合；30 天清理。

## 7. API（替换 v2 forum 部分）

```
GET    /categories                          # 树 + 计数 + 最后回复摘要
GET    /categories/{slug}/topics?sort=latest|popular&tag=&cursor=&limit=
POST   /categories/{id}/follow
DELETE /categories/{id}/follow

GET    /topics?sort=latest|popular&cursor=&limit=
POST   /topics
GET    /topics/{id}?page=&size=
PATCH  /topics/{id}
DELETE /topics/{id}
POST   /topics/{id}/posts
POST   /topics/{id}/follow
DELETE /topics/{id}/follow
POST   /topics/{id}/accept        { postId }
POST   /topics/{id}/pin|unpin|lock|unlock|move   # MODERATOR/ADMIN
GET    /topics/{id}/events

PATCH  /posts/{id}
DELETE /posts/{id}
GET    /posts/{id}/history
POST   /posts/{id}/upvote
DELETE /posts/{id}/upvote

GET    /latest?cursor=
GET    /popular?page=
GET    /tags?q=
GET    /tags/{slug}/topics?cursor=
GET    /users?q=
GET    /users/{id}

GET    /moderation/flags?state=&assignee=
POST   /moderation/flags/{id}/assign
POST   /moderation/flags/{id}/notes
POST   /moderation/flags/{id}/resolve

GET    /search?type=topics|posts|tags|users&q=&cid=&tag=&cursor=
```

- 替换 v2 的 `GET/POST /posts`、`POST /posts/{id}/comments`、`POST /posts/{id}/like`。
- 通知端点沿用 v2，增加聚合字段。
- 公开读匿名可访问；写操作需登录+邮箱已验证；版主操作需分区版主或管理员。

## 8. 前端

### 8.1 布局

- `/forum`：三栏；左导航 MVP 5 项；分区卡片含图标/描述/子分区 chips/主题数/帖子数/最后回复；右栏搜索/通知/用户。
- `/forum/:slug`：排序 tabs（最新/热门）、标签筛选、主题行（置顶/锁定图标、标题、作者、回复/浏览、最后回复）；"加载更多"游标。
- `/topics/:id`：主题头（标题、标签、分区、作者、关注/版主菜单）+ 扁平楼层（楼层号、引用、upvote、编辑、举报、最佳答案标记）+ 底部回复框；编辑历史弹层。
- `/latest`、`/popular`、`/tags`、`/tags/:slug`、`/users`。
- `/moderation/flags`：工单列表、状态筛选、分派、备注、处置。
- 移动端：左导航抽屉；回复按钮吸底；右栏折叠。

### 8.2 交互

- 未登录可读；写操作弹登录并回跳。
- 发主题/回复：Markdown 编辑器 + 预览；标签数量即时校验；可选关联作品。
- 楼层分页每页 20；主题列表游标加载。
- 举报入口在主题/楼层/用户菜单。
- 通知红点轮询 30s；聚合展示。

## 9. 索引与性能验收

- 上述索引全部随迁移建立；列表/详情/搜索查询用 `EXPLAIN` 验收，禁止全表扫。
- 预计算 `topic_count/post_count/follower_count/user_stats`；写时原子增减 + 每日重算兜底。
- 主题/帖子列表用游标；主题内楼层用页码（有界）。
- 批量取用户摘要（IdentityApi）+ Redis 60s 缓存，禁止 N+1。
- 种子压测：10 万主题/50 万楼层，分区主题列表 P95 < 300ms；主题详情首屏 P95 < 300ms。

## 10. E2E 验收（P4）

1. 分区页展示子分区、主题/帖子数、最后回复；点击进入主题列表。
2. 发主题（含标签）→ 主题列表置顶/排序正确、分区计数更新。
3. 回复/引用 → 扁平楼层、楼层号正确；主题作者/关注者/被引用者收到通知（不通知自己、去重）。
4. upvote → 作者声望 +1；取消 → -1；自投被拒。
5. 编辑楼层 → 编辑历史可查；版主可查看他人历史。
6. 关注主题/分区 → 新回复/新主题产生通知；同目标多条通知按 merge_key 聚合。
7. 版主置顶/锁定/移动/隐藏/恢复 → 事件日志正确、移动后两分区计数正确。
8. 举报 → 同一目标多次举报聚合为一个 flag；分派/备注/处置 → 审计 + 举报人通知。
9. 问答区设置最佳答案 → 答主通知；同一主题只允许一个。
10. 最新/热门/标签/搜索游标分页无重复漏项；搜索按分区/标签过滤。

## 11. 后置清单

未读/已读游标、群组/徽章、私信/聊天、草稿、多皮肤、标签关注、@提及、发帖审核队列、downvote、投票贴、主题拆分/合并/迁移历史、实时 Socket/SSE、附件上传、R18 分区、CG/涩图区。

## 12. 对 P1–P3 的影响

无。P1–P3 的认证、作品、资源、评测、媒体、基础搜索保持不变；P4 仅新增/替换 community、moderation、notification、search 的论坛部分与对应 UI。
