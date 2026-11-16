# Galgame 垂直社区 · 系统设计

> 状态：v2 冻结版（2026-11-16；含开工前补丁：索引清单、邮件送达、SSR 用户态/缓存、通知幂等、API 对齐）
> 项目目录：`rance_game_set`
> 交付目标：从原型界面到可部署上线的产品，分阶段完成

## 修订摘要（v1 → v2）

| 项 | v1 | v2 | 原因 |
|---|---|---|---|
| 架构 | 6 微服务 + Nacos + Gateway + Kafka | **模块化单体**（Spring Modulith） | MVP 冷启动无流量压力，微服务只增加成本；模块边界保留，未来可拆 |
| 资源类型 | 含 `GAME`（游戏本体外链） | 移除本体，仅补丁/语音/设定集/攻略/官方商店 | 索引分发盗版本体同样高风险 |
| 事件一致性 | 直接发 Kafka | Spring Modulith 事务性事件发布（Outbox 语义）+ 幂等消费 | 消除双写丢事件 |
| 排行榜 | 含 `period` 参数 | MVP 只做总榜 | 无时间窗口数据支撑 |
| 语言/平台 | JSON 字段 | 关联表 + 索引 | JSON 筛选全表扫 |
| 软删除/唯一键 | reviews 软删除 + 唯一键冲突 | reviews 用户删除走硬删除；管理员隐藏用 visibility | 避免用户永久无法重发 |
| 计数 | 仅"写时更新" | 原子增减 + 反向规则 + 每日重算 | 删除/取消/隐藏/并发一致性 |
| 动态流 | 偏移分页 | `(created_at,id)` 游标分页 | 边刷边更新时重复/漏帖 |
| 搜索 | 前端并行直连三域 | 单体统一 `/api/v1/search` 聚合 | 鉴权/CORS/部分失败易处理 |
| SEO | 纯 SPA | Nuxt 3 SSR 公共页面 | 内容社区需要搜索流量入口 |
| 账号闭环 | 无邮箱验证/找回 | 补齐验证、找回、刷新令牌复用检测 | 可真实上线 |

## 1. 产品定位

### 1.1 定位

面向中文 Galgame / 视觉小说爱好者的垂直社区，形态为 **作品词条 + 资源索引 + 分区论坛**。

- **用户**：玩家、汉化组、同人/独立社团、平台管理员
- **核心价值**：中文结构化作品资料（制作公司、引擎、汉化状态、评分）+ 合规资源索引（补丁/语音/设定集/攻略）+ 讨论
- **内容治理**：平台不托管游戏本体，也不索引本体下载；资源仅限非本体类型；举报、下架、操作审计
- **合规边界**：MVP 仅全年龄内容；R18 内容与年龄验证二期评估

### 1.2 竞品与差异化

| 竞品 | 强项 | 弱项 / 我们的机会 |
|---|---|---|
| Bangumi | ACGN 通用收藏与评分，词条生态成熟 | Galgame 资源/补丁索引弱；垂直筛选弱 |
| 2DFan | Galgame 补丁、汉化资源索引强 | 词条结构化与讨论体验较弱 |
| 月幕 / 相关资源社区 | 资源与汉化话题活跃 | 规范化词条与检索能力弱 |
| VNDB | 全球最全作品数据库，多语言元数据 | 中文社区形态弱、无本地讨论与资源索引 |
| shinnku.com | 资源目录 + 快速搜索（Tantivy） | 讨论与结构化词条弱 |

**差异化**：中文 Galgame 结构化词条 + **合规**资源索引（不含本体）+ 分区讨论一体化；投稿门槛低、审核与审计透明；后续用 AI 语义搜索（"描述找游戏"）做体验差异。

### 1.3 非目标（MVP 不做）

- 游戏本体文件托管、磁力/种子/网盘打包本体链接
- Elasticsearch / AI 语义搜索（预留 `SearchService` 接口）
- 角色词条、八维评分、协作编辑、发售日历订阅
- 私信、签到积分、原生 App（响应式 Web）
- R18 分区与年龄验证
- 多实例水平扩展与独立服务拆分

## 2. 需求范围

### 2.1 功能模块

| 模块 | 能力 |
|---|---|
| 作品词条 | 浏览、筛选、搜索、详情、投稿、编辑；元数据含制作公司、引擎、汉化状态、语言、平台、外链 |
| 资源索引 | 作品下投稿 **补丁/语音包/设定集/攻略/官方商店/其他**，维护版本、语言、平台、提供方、外链 |
| 评测评分 | 10 分制 + 文字评测；每用户每作品一条；聚合展示；点赞评测 |
| 讨论区 | 分区、帖子列表/详情、评论与回复、点赞；帖子可关联作品 |
| 用户 | 邮箱注册/验证/登录/找回密码、JWT、资料、关注、收藏 |
| 通知 | 站内通知（评论/回复/点赞/关注/评测/处置结果），前端 30s 轮询未读数 |
| 搜索 | 统一搜索接口聚合作品/帖子/用户；MySQL FULLTEXT（ngram）+ 标签匹配 |
| 管理后台 | 举报队列、作品/资源/帖子/评测/用户管理（隐藏/删除/封禁）、操作审计 |
| 附加页面 | 发售日历（按月）、排行榜（评分总榜/热度总榜） |

### 2.2 用户故事与验收标准

| ID | 用户故事 | 验收标准 |
|---|---|---|
| US-01 | 游客浏览作品库 | 可按标签/语言/平台/汉化状态/年份筛选，按最新/评分/热度排序，分页；隐藏与已删除作品不出现；空结果有提示 |
| US-02 | 游客查看作品详情 | 展示元数据、截图、平均分与人数、公开资源、可见评测、关联帖子；SSR 输出 title/description/OG/JSON-LD；隐藏作品返回 404 |
| US-03 | 新用户注册并验证邮箱 | 邮箱唯一冲突返回 409；1 分钟内收到验证邮件；未验证可登录但写操作返回 `40302`；重发验证受速率限制 |
| US-04 | 用户登录/找回密码 | 登录返回 access+refresh；密码错误限流；找回邮件 1 小时内有效、令牌单次使用；refresh 复用触发整族吊销 |
| US-05 | 用户投稿作品 | 必填标题/制作公司/发售日期/简介/语言/平台；封面可选；创建后立即公开；投稿者可编辑；非法字段 400；图片走安全管道 |
| US-06 | 用户投稿资源 | 类型仅限补丁/语音/设定集/攻略/官方商店/其他；链接仅 http/https，磁力/种子拒绝；资源出现在作品页；投稿者可编辑/删除；计数同步 |
| US-07 | 用户写评测 | 1–10 分；每用户每作品一条，重复 409；评分聚合原子更新；作品贡献者收通知；修改只调整分值差额；删除后释放唯一键 |
| US-08 | 用户发帖/评论/回复 | 帖子必选分区，可选关联作品；评论/回复成功通知帖子作者或被回复者；不通知自己；隐藏内容不可见 |
| US-09 | 用户关注/收藏 | 重复操作幂等且不重复计数；关注动态流包含被关注者与本人帖子；取消后不再出现 |
| US-10 | 用户搜索 | `q` 为空返回 400；返回分组结果；某一分组失败时返回其余分组并标记 `partial`；结果不含隐藏内容 |
| US-11 | 用户举报、管理员处置 | 举报可指定目标与原因；管理员可隐藏/删除/封禁；操作写审计；举报人收到处置通知；被隐藏内容从公开视图消失 |
| US-12 | 用户查看通知 | 未读数量正确；列表分页；支持标记已读；30s 轮询；不产生自我通知 |
| US-13 | 用户查看排行榜 | 评分榜要求评测数 ≥ 3；热度榜 = 收藏×3 + 评测×2 + 资源×1；仅总榜 |
| US-14 | 用户查看发售日历 | 按月查询，按发售日升序；隐藏作品不出现 |

## 3. 技术架构

### 3.1 架构决策：模块化单体（ADR）

**决策**：单个 Spring Boot 应用，按业务模块分包，使用 Spring Modulith 强制模块边界与模块间事件；单 MySQL、单 Redis、单 MinIO；Docker Compose 部署。

**理由**：单人开发、冷启动无流量压力；避免 6 套骨架/契约/注册中心/消息队列的固定成本；模块边界经测试验证，未来有真实理由（独立扩缩容、团队拆分）时再抽服务。

**被否决方案**：6 个 Spring Cloud Alibaba 微服务 + Nacos + Kafka（v1 方案）——对 MVP 过度设计；纯分层单体——模块依赖易腐化，缺少边界保护。

### 3.2 模块边界与依赖规则

```
identity     账号/认证/资料/关注
catalog      作品/标签/资源/评测/收藏/日历/排行
community    分区/帖子/评论/点赞/动态流
notification 通知/未读数
media        图片上传/处理/对象记录
moderation   举报/处置/审计
shared       通用响应/错误/安全/事件契约
```

依赖规则（由 Spring Modulith `ApplicationModules.verify()` 测试保证，禁止环）：

- `shared` 不依赖任何业务模块
- `identity` 只依赖 `shared`
- `catalog` 依赖 `shared`、`identity`（批量读用户摘要）、`media`
- `community` 依赖 `shared`、`identity`、`catalog`（作品摘要）、`media`
- `notification` 依赖 `shared`；只消费各模块事件生成通知，不向其他模块暴露内部 API（通知 REST 端点供前端直接调用）
- `media` 依赖 `shared`、`identity`
- `moderation` 依赖 `shared`，通过 `catalog`/`community`/`identity` 的模块 API 执行处置

模块间**禁止直接访问对方表**；同步读走模块 API（批量接口 + Redis 缓存），异步走模块事件。

**单体期特例**：moderation 处置时在同一本地事务内调用 catalog/community 模块 API 调整可见性与统计；这是单体带来的便利，未来拆分服务须改为 Saga/Outbox 补偿。

**查询过滤规范**：所有面向公众的查询统一强制 `visibility=PUBLIC AND deleted_at IS NULL`；提供共享查询构造器/Repository 基类，并用 ArchUnit 禁止跨模块直接访问仓储。

### 3.3 技术栈与版本基线

| 层 | 选型 |
|---|---|
| 后端 | Java 21、Spring Boot 3.3.x、Spring Security、Spring Data JPA、Flyway、Spring Modulith |
| 数据库 | MySQL 8.0（FULLTEXT ngram） |
| 缓存/限流 | Redis 7（缓存、Bucket4j 令牌桶） |
| 对象存储 | MinIO |
| 邮件 | SMTP；开发环境 MailHog |
| 前端 | Nuxt 3（Vue 3 + Vite + TypeScript + SSR）、Tailwind CSS、Naive UI、Pinia、Axios |
| 测试 | JUnit 5 + Mockito + Spring Modulith Test + Testcontainers（MySQL/Redis/MinIO）；Vitest；Playwright；k6 |
| 文档 | springdoc-openapi |
| 部署 | Docker Compose（app + web + MySQL + Redis + MinIO + MailHog(dev)） |

### 3.4 部署拓扑与端口

| 组件 | 端口 | 说明 |
|---|---|---|
| app（Spring Boot） | 8080 | 单体应用，含全部模块 |
| web（Nuxt SSR） | 3000 | 生产由 Nginx 反代；开发 5173 |
| MySQL | 3306 | 单库 `gal` |
| Redis | 6379 | 缓存/限流 |
| MinIO | 9000 / 9001 | API / 控制台 |
| MailHog | 1025 / 8025 | 仅开发 profile |
| Prometheus / Grafana | 9090 / 3001 | 可选 profile |

生产由 Nginx 终止 TLS、托管静态资源、反代 Nuxt 与 `/api`。

### 3.5 容量估算

| 指标 | 假设 |
|---|---|
| 注册用户 / DAU | 1 万 / 500 |
| 作品 / 帖子 / 评论 | 5 千 / 5 万 / 10 万 |
| 峰值请求 | 50 rps |
| 数据库体积 | ≤ 10 GB |
| 图片存储 | ≤ 50 GB |
| 单机规格 | 4C 8G 起步（4G 可跑但紧） |
| JVM 内存 | `-Xmx1536m`；MySQL 1G；Redis 256M；MinIO 512M；Nuxt 512M |
| 连接池 | Hikari 最大 20，MySQL `max_connections=200`；慢查询阈值 200ms |
| 图片 | 上传后生成 320/640/1280 三档缩略图；Nginx 缓存静态与媒体 |

### 3.6 可观测性

- Actuator `/actuator/health`、`/actuator/metrics`、`/actuator/prometheus`
- Micrometer + Prometheus + Grafana（Compose 可选 profile）
- 结构化日志（JSON）+ traceId（Micrometer Tracing）
- 关键告警：接口 5xx 比例、慢查询、事件投递积压、媒体孤儿数量

### 3.7 未来拆分路径

1. 模块边界已隔离；拆分时优先把 `media`、`notification` 抽为独立服务
2. 模块事件替换为消息队列（Kafka/RabbitMQ），事件契约已带 `eventType/version`
3. 数据库按模块拆库；跨模块读改远程调用 + 缓存
4. 若拆分，补充 DLQ、重试主题、事件版本兼容策略

## 4. 数据模型

### 4.1 通用约定

- 单库 `gal`，表按模块前缀：`user_`、`catalog_`、`community_`、`notification_`、`media_`、`moderation_`
- 主键 `BIGINT` 自增；时间 `created_at`/`updated_at`（UTC 存储）
- 软删除仅用于：`works`（`deleted_at`）、`posts`/`comments`（`deleted_at`）、`resources`（`deleted_at`）
- 隐藏用 `visibility`：`work`、`resource`、`review`、`post`、`comment`
- 点赞/收藏/关注为**硬删除**的幂等开关，不做软删除
- 迁移由 Flyway 管理，JPA `ddl-auto=validate`

### 4.2 identity

- `user_accounts`：id, email(唯一), password_hash(BCrypt), nickname, avatar_url, bio, role(USER/ADMIN), status(ACTIVE/BANNED/DELETED), email_verified(bool)
- `user_follows`：follower_id, followee_id, created_at；PK(follower_id, followee_id)
- `user_refresh_tokens`：id, user_id, family_id, token_hash, expires_at, revoked_at, replaced_by
- `user_email_verifications`：id, user_id, token_hash, expires_at, used_at
- `user_password_resets`：id, user_id, token_hash, expires_at, used_at
- `user_email_outbox`：id, to_email, template, payload(JSON), status(PENDING/SENT/FAILED), attempts, next_attempt_at, created_at；索引 (status, next_attempt_at)

### 4.3 catalog

- `catalog_works`：id, slug(唯一，永久不重用), title_localized, title_original, summary, description(Markdown), cover_url, developer_name, publisher_name, release_date, engine(RENPY/KRKR/ONS/UNITY/OTHER), localization_status(NONE/PATCHED/OFFICIAL), localization_group, age_rating(ALL_AGES), vndb_id, steam_url, official_url, visibility(PUBLIC/HIDDEN), created_by, deleted_at
- `catalog_languages`：code(主键), name
- `catalog_platforms`：code(主键), name
- `catalog_work_languages`：work_id, language_code；PK 复合，索引 `language_code`
- `catalog_work_platforms`：work_id, platform_code；PK 复合，索引 `platform_code`
- `catalog_tags`：id, name(唯一), slug(唯一)
- `catalog_work_tags`：work_id, tag_id；PK 复合
- `catalog_work_media`：id, work_id, url, sort_order
- `catalog_resources`：id, work_id, type(PATCH/VOICE/ARTBOOK/GUIDE/OFFICIAL_STORE/OTHER), title, version, language_code, platform_code, provider, links(JSON: [{label,url}]), note, uploader_id, visibility(PUBLIC/HIDDEN), deleted_at
- `catalog_reviews`：id, work_id, user_id, rating(1–10), title, content, visibility(PUBLIC/HIDDEN)；**硬删除**；唯一(work_id, user_id)
- `catalog_review_likes`：review_id, user_id, created_at；PK 复合
- `catalog_favorites`：work_id, user_id, created_at；PK 复合
- `catalog_work_stats`：work_id(主键), rating_sum, rating_count, review_count, favorite_count, resource_count

### 4.4 community

- `community_categories`：id, slug(唯一), name, description, sort_order, enabled
- `community_posts`：id, category_id, work_id(可空), author_id, type(DISCUSSION/NEWS/DEVLOG/GUIDE), title, body(Markdown), cover_url, visibility(PUBLIC/HIDDEN), deleted_at
- `community_comments`：id, post_id, author_id, parent_id(可空), body, visibility(PUBLIC/HIDDEN), deleted_at
- `community_post_likes`：post_id, user_id, created_at；PK 复合
- FULLTEXT：`community_posts(title, body)` with ngram parser

默认分区：综合讨论、新作情报、汉化交流、求助问答、资源分享、闲聊灌水。

### 4.5 notification

- `notification_notifications`：id, event_id, recipient_id, actor_id(系统通知为 0), type(COMMENT/REPLY/LIKE/FOLLOW/REVIEW/REPORT_RESULT), target_type(WORK/RESOURCE/POST/COMMENT/REVIEW/USER), target_id, content_snippet, is_read, created_at；索引 `(recipient_id, is_read, created_at)`、`(recipient_id, created_at)`；唯一 `(event_id, recipient_id)` 幂等（同一事件可通知多个接收人，不同评论事件 `event_id` 不同，不会被误吞）
- `notification_event_consumption`：event_id, consumer, processed_at；PK(event_id, consumer)

### 4.6 media

- `media_objects`：id, uploader_id, object_key(唯一), url, mime, size_bytes, width, height, status(ACTIVE/DELETED), created_at

### 4.7 moderation

- `moderation_reports`：id, reporter_id, target_type(RESOURCE/REVIEW/POST/COMMENT/USER), target_id, reason(COPYRIGHT/SPAM/ABUSE/MISLEADING/OTHER), detail, status(PENDING/RESOLVED/REJECTED), handled_by, handled_at, created_at；索引 `(status, created_at)`
- `moderation_actions`：id, report_id(可空), admin_id, action(HIDE/UNHIDE/DELETE/BAN/UNBAN), target_type, target_id, note, created_at

### 4.8 事务性事件（Outbox）

- `event_publication`：Spring Modulith 事件发布注册表，事件在业务事务内落库，提交后分发；启动时重投未完成事件
- 事件信封：`eventId(UUID)`、`eventType`、`version`、`occurredAt`、`actorId`、`payload`
- 监听器幂等：通知用 `(event_id, recipient_id)` 唯一键；其他消费者用 `notification_event_consumption`；失败记录并重试，持续失败进入告警
- `event_publication` 完成记录保留 30 天后由定时任务清理，避免无限增长
- 说明：v1 的 Kafka 在 MVP 移除；事件抽象保留，未来外化时补 DLQ/重试主题/版本兼容

### 4.9 软删除与唯一约束

| 表 | 删除语义 | 唯一约束处理 |
|---|---|---|
| works | 软删除（管理员） | slug 永久不重用 |
| resources | 用户删除软删除（`deleted_at`）；管理员隐藏 `visibility` | 无唯一键冲突 |
| reviews | 用户删除**硬删除**；管理员隐藏 `visibility` | 唯一(work_id,user_id) 在硬删除后释放；隐藏期间保留占位（用户不可重复投稿同一作品评测） |
| posts/comments | 软删除 | 无唯一键 |
| favorites/follows/likes | 取消即硬删除 | 唯一键正常支持幂等 |

> 隐藏评测保留唯一键占位是**产品意图**：防止用户绕过管理员处置重复发布；管理员可恢复或删除。

> 评论 `parent_id` 仅支持两级（评论 + 回复），服务端拒绝更深嵌套；回复统一指向顶层评论。

### 4.10 计数一致性

- 写时**原子 SQL 增减**（`UPDATE ... SET x = x ± ?`），不做读改写
- 反向规则：

| 操作 | rating_sum | rating_count | review_count | favorite_count | resource_count |
|---|---|---|---|---|---|
| 新增/恢复可见评测 | +rating | +1 | +1 | — | — |
| 修改评分 | +(new−old) | — | — | — | — |
| 删除/隐藏评测 | −rating | −1 | −1 | — | — |
| 收藏 / 取消收藏 | — | — | — | +1 / −1 | — |
| 新增/恢复可见资源 | — | — | — | — | +1 |
| 删除/隐藏资源 | — | — | — | — | −1 |

- 每日 03:00 全量重算 `catalog_work_stats`，与增量值差异超阈值告警
- 评测/资源/收藏写操作与统计更新在同一本地事务内完成

### 4.11 索引清单（随迁移建立）

| 表 | 索引 |
|---|---|
| user_accounts | UK(email) |
| user_follows | PK(follower_id, followee_id)、IDX(followee_id, created_at) |
| user_refresh_tokens | UK(token_hash)、IDX(user_id)、IDX(family_id) |
| user_email_verifications | UK(token_hash)、IDX(user_id) |
| user_password_resets | UK(token_hash)、IDX(user_id) |
| user_email_outbox | IDX(status, next_attempt_at) |
| catalog_works | UK(slug)、FULLTEXT(title_localized, title_original, summary) WITH PARSER ngram、IDX(visibility, deleted_at, release_date)、IDX(created_by) |
| catalog_work_tags | PK(work_id, tag_id)、IDX(tag_id) |
| catalog_work_languages | PK(work_id, language_code)、IDX(language_code) |
| catalog_work_platforms | PK(work_id, platform_code)、IDX(platform_code) |
| catalog_resources | IDX(work_id, visibility, deleted_at)、IDX(uploader_id) |
| catalog_reviews | UK(work_id, user_id)、IDX(work_id, visibility, created_at, id)、IDX(user_id) |
| catalog_review_likes | PK(review_id, user_id)、IDX(user_id) |
| catalog_favorites | PK(work_id, user_id)、IDX(user_id, created_at) |
| catalog_work_media | IDX(work_id, sort_order) |
| community_categories | UK(slug) |
| community_posts | FULLTEXT(title, body) WITH PARSER ngram、IDX(category_id, visibility, deleted_at, created_at, id)、IDX(author_id, created_at, id)、IDX(work_id, visibility, deleted_at)、IDX(created_at, id) |
| community_comments | IDX(post_id, visibility, deleted_at, created_at, id)、IDX(parent_id)、IDX(author_id) |
| community_post_likes | PK(post_id, user_id)、IDX(user_id) |
| notification_notifications | UK(event_id, recipient_id)、IDX(recipient_id, is_read, created_at)、IDX(recipient_id, created_at) |
| media_objects | UK(object_key)、IDX(uploader_id)、IDX(status, created_at) |
| moderation_reports | IDX(status, created_at)、IDX(target_type, target_id) |
| moderation_actions | IDX(target_type, target_id)、IDX(admin_id, created_at) |

搜索与筛选相关索引在 P2/P5 阶段用 `EXPLAIN` 验收，禁止全表扫。

## 5. 核心流程

1. **注册与验证**：校验邮箱唯一 → BCrypt 存密码 → 生成验证令牌（哈希存储，24h）→ 发信；未验证可登录但写操作返回 `40302`；重发接口限流。
2. **登录/刷新/找回**：登录返回 access JWT（RS256，30 分钟，带 `kid`）+ refresh（7 天，哈希存储，含 `family_id`）；刷新时旧 token 标记 `replaced_by`，若旧 token 再次使用则吊销整族；找回密码令牌 1 小时有效、单次使用。
3. **投稿作品**：上传封面/截图（media 安全管道）→ 创建作品（PUBLIC 立即可见）→ 发 `WorkPublished` 事件（事务性 Outbox）。
4. **投稿资源**：仅允许非本体类型；校验链接 `http/https`，拒绝 `magnet:` 与种子文件链接；创建后原子更新 `resource_count`。
5. **写评测**：唯一性校验 → 同一事务写评测、原子更新统计数据 → 发 `ReviewCreated` → 通知作品贡献者；修改只调整评分差额；删除（硬删除）反向更新并释放唯一键。
6. **发帖/评论**：写入后发 `PostPublished` / `CommentCreated`；通知帖子作者或被回复者（不通知自己）。
7. **点赞/关注/收藏**：唯一键幂等写入；仅首次成功发事件并计数；取消硬删除并反向计数。
8. **动态流**：`(created_at, id)` 游标分页；取关注列表（模块 API + Redis 60s 缓存）→ 查询这些作者与本人帖子；批量装配作者/作品摘要，避免 N+1。
9. **通知轮询**：前端 30s 拉未读数（页面隐藏时暂停）；通知列表分页与标记已读。
10. **举报处置**：举报入队 → 管理员后台处置 → `moderation` 通过模块 API 修改目标可见性/状态 → 同一事务内调整统计 → 写审计 → 发 `ReportResolved` → 通知举报人。
11. **媒体管道**：大小 ≤5MB、像素 ≤1000 万、真实格式嗅探 → 解码重编码（JPEG/WebP）→ 剥离 EXIF → 随机 key 存 MinIO → 返回 URL；每日清理 24h 未被引用的对象。

## 6. API 设计

### 6.1 约定

- 前缀 `/api/v1`；破坏性变更升 `/api/v2`，v1 保留至少 6 个月
- 统一响应：`{ code, message, data, traceId }`；HTTP 状态与业务码对应
- 列表分页：`page`(1 起)、`size`(默认 20，上限 50)，响应 `items/total/page/size`
- 动态流/时间线用游标：`cursor`（base64 `createdAtMillis:id`）、`limit`
- 公开读接口允许匿名；写接口需登录且邮箱已验证；`/admin/**` 需 ADMIN

### 6.2 错误码

| code | HTTP | 含义 |
|---|---|---|
| 40001 | 400 | 参数校验失败 |
| 40002 | 400 | 游标非法 |
| 40101 | 401 | 未认证 |
| 40102 | 401 | access 过期 |
| 40103 | 401 | refresh 复用/失效 |
| 40301 | 403 | 无权限 |
| 40302 | 403 | 邮箱未验证 |
| 40401 | 404 | 资源不存在 |
| 40901 | 409 | 唯一性冲突（如重复评测/收藏） |
| 41301 | 413 | 文件过大 |
| 41501 | 415 | 不支持的媒体类型 |
| 42201 | 422 | 业务规则违反（如禁止本体资源/磁力链接） |
| 42901 | 429 | 触发限流 |
| 50001 | 500 | 服务器内部错误 |
| 50301 | 503 | 依赖不可用 |

### 6.3 端点

| 域 | 端点 |
|---|---|
| 认证 | `POST /auth/register`、`POST /auth/verify-email`、`POST /auth/resend-verification`、`POST /auth/login`、`POST /auth/refresh`、`POST /auth/logout`、`POST /auth/forgot-password`、`POST /auth/reset-password` |
| 用户 | `GET /users/{id}`、`PATCH /users/me`、`DELETE /users/me`（注销）、`POST /users/{id}/follow`、`DELETE /users/{id}/follow`、`GET /users/{id}/followers`、`GET /users/{id}/following` |
| 作品 | `GET /works`、`GET /works/{slug}`、`POST /works`、`PATCH /works/{id}`、`DELETE /works/{id}`、`POST /works/{id}/favorite`、`DELETE /works/{id}/favorite` |
| 资源 | `GET /works/{id}/resources`、`POST /works/{id}/resources`、`PATCH /resources/{id}`、`DELETE /resources/{id}` |
| 评测 | `GET /works/{id}/reviews`、`POST /works/{id}/reviews`、`PATCH /reviews/{id}`、`DELETE /reviews/{id}`、`POST /reviews/{id}/like`、`DELETE /reviews/{id}/like` |
| 论坛 | `GET /categories`、`GET /posts`、`GET /posts/{id}`、`POST /posts`、`PATCH /posts/{id}`、`DELETE /posts/{id}`、`GET /posts/{id}/comments`、`POST /posts/{id}/comments`、`DELETE /comments/{id}`、`POST /posts/{id}/like`、`DELETE /posts/{id}/like` |
| 我的 | `GET /me/works`、`GET /me/resources`、`GET /me/reviews`、`GET /me/favorites`、`GET /me/follows`、`GET /me/posts` |
| 动态 | `GET /feed?cursor=&limit=`（关注动态） |
| 通知 | `GET /notifications`、`GET /notifications/unread-count`、`POST /notifications/read`（按 ids）、`POST /notifications/read-all` |
| 媒体 | `POST /media/upload`（multipart，≤5MB）、`GET /media/{id}`（私有桶代理，带缓存头）、`DELETE /media/{id}` |
| 搜索 | `GET /search?q=&type=all|works|posts|users`（聚合，支持 `partial` 标记） |
| 日历/排行 | `GET /calendar?month=YYYY-MM`、`GET /rankings?type=rating|popular` |
| 举报 | `POST /reports` |
| 管理 | `GET /admin/reports`、`POST /admin/reports/{id}/resolve`、`GET /admin/works`、`POST /admin/works/{id}/hide`、`POST /admin/resources/{id}/hide`、`POST /admin/posts/{id}/hide`、`POST /admin/reviews/{id}/hide`、`POST /admin/comments/{id}/hide`、`GET /admin/users`、`POST /admin/users/{id}/ban`、`POST /admin/users/{id}/unban`、`GET /admin/audit-logs` |

### 6.4 权限口径

- 作品：投稿者或管理员可编辑/删除
- 资源、帖子、评测、媒体：投稿者/作者或管理员可编辑/删除
- 评论：作者可删除；管理员可隐藏
- 所有隐藏/删除内容对普通用户不可见；管理员可见并标记状态
- 未验证邮箱用户可浏览，不能发帖/评测/投稿/举报

### 6.5 排行榜口径

- 评分榜：`rating_sum / rating_count` 降序，`review_count ≥ 3`
- 热度榜：`favorite_count×3 + review_count×2 + resource_count×1` 降序
- 仅总榜，不做时间窗口；榜单查询过滤 `visibility=PUBLIC` 且未删除的作品

## 7. 前端设计

### 7.1 设计语言

二次元风格：粉紫渐变点缀、圆角卡片、大封面、柔和阴影；亮/暗主题；响应式断点 640/768/1024/1280；中文界面（i18n 预留）。

### 7.2 渲染策略（SEO）

- Nuxt 3 SSR 渲染公开内容页：`/`、`/works`、`/works/:slug`、`/posts/:id`、`/u/:id`、`/calendar`、`/rankings`
- 需登录页面与管理后台用 CSR
- 每页输出 title/description/canonical/OG；作品页输出 `VideoGame` JSON-LD
- 生成 `sitemap.xml`、`robots.txt`；隐藏内容不进入 sitemap
- 首屏数据在服务端预取，客户端 hydration 复用

### 7.3 页面地图

| 路径 | 页面 | 渲染 |
|---|---|---|
| `/` | 首页（方案 A：推荐轮播 + 最新资源 + 最新讨论 + 侧栏日历/标签） | SSR |
| `/works` | 作品库（方案 B：左侧筛选 + 网格 + 右侧动态） | SSR |
| `/works/:slug` | 作品详情 | SSR |
| `/works/new`、`/works/:id/edit` | 投稿/编辑作品 | CSR |
| `/forum` | 分区列表 | SSR |
| `/forum/:categorySlug` | 帖子列表 | SSR |
| `/posts/:id` | 帖子详情 | SSR |
| `/posts/new` | 发帖 | CSR |
| `/calendar` | 发售日历 | SSR |
| `/rankings` | 排行榜 | SSR |
| `/search` | 搜索结果 | SSR |
| `/u/:id` | 个人主页 | SSR |
| `/me` | 我的（作品/资源/评测/收藏/关注/帖子） | CSR |
| `/notifications` | 通知中心 | CSR |
| `/settings` | 账号设置（含注销） | CSR |
| `/login`、`/register`、`/verify-email`、`/forgot-password`、`/reset-password` | 认证 | CSR |
| `/admin/*` | 管理后台 | CSR |

### 7.4 交互约定

- 未登录可浏览，写操作弹登录并回跳
- 骨架屏、空状态、错误重试
- 图片懒加载；Markdown 经 DOMPurify 白名单过滤
- 通知红点 30s 轮询，`document.hidden` 时暂停
- 举报入口在资源/评测/帖子/评论的更多菜单

### 7.5 SSR 用户态与缓存规则

- 公共页 SSR **只渲染公共数据**；「已收藏/已点赞/已关注/我的通知」等用户态一律客户端 hydration 后补，SSR HTML 不随登录态变化
- 匿名公共页允许 `Cache-Control: public, s-maxage=60, stale-while-revalidate=300`；任何含个人数据或用户态的响应必须 `Cache-Control: private, no-store`
- Nginx 对公共 GET 接口做 10 秒微缓存，缓存键不含 Cookie；带 `Authorization` 的请求不缓存
- Naive UI 的 SSR 使用 `@css-render/vue3-ssr` 收集并注入样式，关键 CSS 内联，避免首屏样式闪烁
- 作品页/帖子页输出 canonical、OG、JSON-LD；隐藏内容不进入 sitemap

## 8. 安全与合规

### 8.1 认证与密钥

- BCrypt（强度 10+）；登录/注册/找回接口限流
- JWT RS256，`kid` 支持密钥轮转；私钥仅认证模块持有，公钥全局可验
- refresh token 哈希存储，含 `family_id`；复用即吊销整族
- **令牌存储**：access token 仅存内存（不落 localStorage）；refresh token 放 `HttpOnly + Secure + SameSite=Strict` Cookie；刷新/登出接口校验 Origin；写接口用 Authorization 头，天然免疫 CSRF
- **管理员**：强制强密码策略并启用 TOTP 双因素；登录失败锁定与告警
- 邮箱验证与找回令牌单次使用、有有效期
- 密钥经环境变量注入，不入库、不入代码

### 8.2 输入/输出

- DTO 校验 + 长度上限；分页上限
- URL 仅 `http/https`；资源链接拒绝 `magnet:`、`.torrent`
- Markdown 白名单渲染；外链 `rel="noopener noreferrer"`
- 统一错误响应不泄露堆栈

### 8.3 上传安全

- 仅 JPEG/PNG/WebP；魔数嗅探而非仅 Content-Type
- ≤5MB、像素 ≤1000 万（防解压炸弹）
- 解码后重编码、剥离 EXIF；随机 object key
- 孤儿文件每日清理；存储 bucket 禁止目录遍历与执行
- MinIO bucket 私有；图片经 `GET /media/{id}` 代理输出并设置不可变长缓存；禁止公开列目录
- 上传后生成 320/640/1280 三档缩略图，列表页加载小图

### 8.4 越权与审计

- 每个写端点校验属主或管理员
- 管理操作全部写 `moderation_actions`
- 速率限制：登录、注册、重发验证、上传、举报分别限流
- Nginx/应用输出 CSP、HSTS、`X-Content-Type-Options`、`Referrer-Policy`、`Permissions-Policy`；透传真实客户端 IP，限流按真实 IP
- **反垃圾**：邮箱未验证不可写；注册 <24h 账号限频（发帖 3/日、评论 20/日）；发帖/评论/举报按 IP+账号双限流；可选接入 Turnstile（配置开关）

### 8.5 版权与内容合规

- **不托管、不索引游戏本体**：资源类型不含 `GAME`；禁止磁力/种子/整包本体链接；用户协议明确
- 通知-删除机制：版权投诉入口 → 管理员下架 → 记录审计；重复侵权者封禁
- 内容审核义务：用户生成内容需可举报、可下架；上线前准备审核流程与值班安排
- **ICP 备案**：若部署在中国大陆，域名与主机需备案
- **个人信息保护法**：隐私政策、最小化收集、账号注销与数据删除流程
- **未成年人保护**：MVP 全年龄内容；R18 需年龄验证与独立分区，二期评估
- 实名制要求：上线前按部署地法规确认（开发环境不受影响）
- 用户内容授权条款：投稿即授予平台展示/索引的非独占许可
- 数据保留：站内通知 180 天、操作审计 2 年、访问日志 90 天；注销后匿名化
- R18 口径：MVP 仅收录全年龄作品与补丁；疑似 R18 作品/资源按规则拒绝或下架

### 8.6 隐私

- 邮箱不出现在公开 API；日志脱敏
- 账号注销：置为 DELETED、匿名化昵称/邮箱、保留必要审计记录

### 8.7 邮件送达方案

- 生产使用托管邮件服务（阿里云 DirectMail / AWS SES 等），独立发信子域名（如 `mail.example.com`）
- 发信域名完成 **SPF / DKIM / DMARC** 配置并通过校验；设置退信与投诉回调处理
- 验证/找回邮件写入 `user_email_outbox`，定时发送 + 指数退避重试（最多 5 次），失败告警
- 开发环境用 MailHog；上线前在 Gmail / QQ / 163 做送达测试
- 单账号发信频率限制，防止被当成垃圾邮件源

## 9. 测试与验证

### 9.1 测试金字塔（核心域优先）

| 层 | 范围 |
|---|---|
| 单元 | 认证令牌、评分聚合、计数规则、权限判定、媒体校验 |
| 集成（Testcontainers） | MySQL/Redis/MinIO；核心用例：注册验证、评测增删改的统计、隐藏/恢复、举报处置、事件投递 |
| 模块测试 | `ApplicationModules.verify()` 无环依赖；事件发布/消费 |
| 前端单测 | Vitest：store、关键组件、表单校验 |
| E2E | Playwright 黄金路径 |
| 压测 | k6 场景：作品列表/详情、搜索、feed、发帖评论 |

### 9.2 E2E 黄金路径

1. 注册 → 验证邮箱 → 登录 → 浏览/筛选作品 → 收藏
2. 投稿作品（含封面）→ 出现在作品库与首页最新资源
3. 投稿资源（补丁外链）→ 资源列表可见；提交 `GAME` 类型被拒绝
4. 写评测 → 评分聚合更新 → 贡献者收通知 → 修改/删除后统计正确
5. 发帖 → 评论回复 → 楼主收到通知；feed 游标翻页不重复
6. 举报资源 → 管理员隐藏 → 内容不可见 → 统计更新 → 举报人收到通知
7. 刷新令牌复用 → 整族失效
8. 搜索 → 分组结果；模拟某分组失败返回 `partial`

### 9.3 性能目标与压测

- 目标：P95 API < 300ms（本地 4C8G）、首页首屏 < 2s、50 rps 持续 30 分钟错误率 < 0.1%
- k6 脚本纳入仓库；结果记录基线，回归对比

### 9.4 覆盖率

- 后端整体行覆盖 ≥80%，核心域（认证/评测聚合/举报处置）≥85%
- 前端核心 store/组件 ≥80%
- 覆盖率不达标不做"凑测试"，优先补核心域

## 10. 交付与运维

### 10.1 分阶段交付与工期

> 人力假设：单人开发。全职约 15–18 周（含 20–30% buffer）；兼职（每周 10–15 小时）约 8–11 个月。若需压缩，优先把 P6 的批量操作与管理员 2FA 后置。

| 阶段 | 内容 | 预估 | 验收 |
|---|---|---|---|
| P0 原型 | 高保真关键页（首页、作品详情、作品库、管理后台） | 2–3 天 | 原型评审通过 |
| P1 基座 | Compose、单体骨架、模块边界、统一响应/错误、认证（验证/找回/刷新）、邮件 outbox 与送达配置 | 2 周 | 注册验证登录闭环，Modulith verify 通过 |
| P2 作品与媒体 | 作品 CRUD、标签、语言/平台筛选、媒体管道与缩略图、作品详情 SSR 与缓存规则 | 2 周 | 投稿可见、筛选正确、SEO 输出、不泄露用户态 |
| P3 资源与评测 | 资源（非本体）、评测、收藏、统计与每日重算 | 1.5 周 | 计数正确、唯一约束、反向更新 |
| P4 论坛 | 分区、帖子、评论、点赞、feed 游标 | 2 周 | 发帖评论全通、feed 稳定分页 |
| P5 通知与检索 | 事件 Outbox、通知、统一搜索、日历、排行 | 1.5 周 | 通知不丢、搜索可用、榜单正确 |
| P6 治理 | 举报、管理后台、审计、封禁、隐藏 | 1.5 周 | 举报处置闭环 + 审计 |
| P7 上线 | 安全响应头、管理员 2FA、压测、备份演练、E2E、部署文档、合规文案 | 2–2.5 周 | DoD 全绿 |

### 10.2 CI/CD

- GitHub Actions：后端 `mvn verify`（含 Testcontainers）、前端 lint + vitest + build、镜像构建
- 发布：`docker compose pull && up -d`；回滚保留上一镜像 tag
- 数据库迁移随应用启动执行，失败阻断发布

### 10.3 备份与恢复

- MySQL：每日 `mysqldump` 到持久卷 + 异地副本；保留 7 日/30 日两档
- MinIO：每日 `mc mirror` 增量
- **RPO ≤ 24h，RTO ≤ 2h**；每季度恢复演练一次并记录

### 10.4 监控栈

- Prometheus + Grafana（可选 profile）：HTTP 指标、JVM、连接池、事件积压
- 日志：JSON 文件 + 轮转；可选 Loki
- 告警：5xx 比例、P95、事件失败、备份失败

## 11. 风险与应对

| 风险 | 应对 |
|---|---|
| 模块边界腐化回"大泥球" | Spring Modulith 架构测试 + 代码评审；禁止跨模块访表 |
| 事件投递失败丢通知 | 事务性 Outbox + 启动重投 + 幂等 + 告警 |
| 统计计数漂移 | 原子增减 + 每日重算 + 差异告警 |
| MySQL 中文检索效果有限 | ngram + 标签/前缀；`SearchService` 抽象，后期接 ES |
| 版权/合规风险 | 禁止本体与磁力；通知-删除；上线前完成备案/合规确认 |
| 单人开发工期风险 | 按阶段交付、每阶段可演示；砍功能优先于砍质量 |
| Nuxt SSR 复杂度 | 仅公共页 SSR，后台 CSR；预留降级为纯 SPA + 预渲染 |
| 验证邮件进垃圾箱 | 托管邮件服务 + SPF/DKIM/DMARC + 送达测试 + 退信监控 |
| SSR 缓存泄露用户态 | 公共页只渲染公共数据；个性化响应 `private`；缓存键不含 Cookie |
| 通知被误去重 | 幂等键改为 `(event_id, recipient_id)` |
| 索引缺失导致全表扫 | 4.11 索引清单 + `EXPLAIN` 验收 |
| 空社区冷启动 | 种子数据 + 投稿引导 + 首页推荐位 |

## 12. 验收标准（Definition of Done）

- `docker compose up` 一键启动 app/MySQL/Redis/MinIO/web/MailHog(dev)，健康检查全绿
- `ApplicationModules.verify()` 通过，无模块环依赖
- 第 9.2 节 8 条 E2E 黄金路径全部通过
- 后端整体覆盖率 ≥80%，核心域 ≥85%；前端核心 ≥80%
- 资源类型不含本体；磁力/种子链接被拒绝
- review 删除后可重新投稿；隐藏评测不计入统计；统计每日重算可对齐
- feed 游标分页在并发新增下无重复/漏帖
- 事件故障演练：提交后进程重启，未完成事件被重投且通知不重复
- 压测达到 9.3 目标；备份恢复演练完成（RPO/RTO 达标）
- 合规物料齐备：用户协议、隐私政策、版权投诉入口、账号注销流程
- 部署文档与回滚步骤可执行
- 4.11 索引清单全部建立，核心查询 `EXPLAIN` 无全表扫
- 邮件送达测试通过（SPF/DKIM/DMARC 校验通过，Gmail/QQ/163 收件正常）
- 匿名与登录用户访问同一 SSR 页面，HTML 不含个人状态；缓存头符合 7.5
- 通知幂等：同一事件重复投递只产生一条；多接收人各一条
- 安全响应头扫描通过；管理员 TOTP 可用
