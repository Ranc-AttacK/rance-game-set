# P4 论坛（NodeBB-lite）实施大纲

> 状态：任务级大纲（2026-11-16）
> 设计依据：`docs/superpowers/specs/2026-11-16-p4-forum-design-v2.1.md`
> 说明：本文件锁定 P4 的任务拆分与验收标准；进入 P4 时再展开为逐文件、逐测试的 TDD 计划（writing-plans）。
> 前置：P1–P3 完成；使用当时的下一个可用 Flyway 版本（预计 V5+）。

## 目标

把 v2 的「posts + comments 两层嵌套」替换为 NodeBB-lite 论坛：分区树 → 主题 → 扁平楼层 + 标签 + 关注 + upvote/声望 + 分区版主 + 举报工单 + 通知聚合 + 多范围搜索，并配套 NodeBB 式三栏 UI。

## 模块边界

- `community`：categories/topics/posts/tags/votes/follows/moderators/user_stats/edits/events
- `moderation`：flags/reports/notes + actions（复用）
- `notification`：扩展类型、merge_key、30 天清理
- `catalog`/`identity`：只读作品/用户摘要；不新增跨模块写
- `search`：社区侧 `SearchService` 实现 topics/posts/tags 范围

## 任务拆分与验收

### Task P4-1: 社区数据模型与迁移

- 新增 categories/topics/posts/tags/topic_tags/post_votes/topic_follows/category_follows/category_moderators/user_stats/post_edits/topic_events。
- 修改 moderation：flags/reports/notes；notification 增加 `merge_key` 与 `(created_at)` 索引。
- 按 v2.1 §4 建立全部索引与 FULLTEXT（ngram）。
- 验收：Flyway 全绿；Hibernate validate 通过；`EXPLAIN` 覆盖列表/详情/搜索 SQL，无全表扫。

### Task P4-2: 分区树与统计

- `GET /categories` 返回树、子分区、topic/post 计数、最后回复摘要。
- 分类计数原子更新；最后回复批量装配（禁止 N+1）。
- 验收：种子 10 个分区，父子层级与计数正确；API P95 < 100ms。

### Task P4-3: 发主题与标签

- `POST /topics`：校验分区启用、标签 min/max、标题/正文、可选 work 关联与 kind。
- 事务内写 topic + 首楼 post（floor=1），更新分区计数/最后回复与 user_stats。
- 验收：主题出现在列表顶部；计数正确；标签数量违规返回 40001；重复请求不产生重复主题（幂等键可选）。

### Task P4-4: 主题列表与游标分页

- `GET /categories/{slug}/topics?sort=latest|popular&tag=&cursor=`；`GET /topics?sort=latest|popular`。
- 分区主题：`pinned DESC, last_post_at DESC, id DESC` 游标；最新同理；热门近 7 天按 `post_count*3 + view_count`，offset 最多 20 页。
- 验收：并发新增下无重复/漏项；`EXPLAIN` 命中索引；10 万主题 P95 < 300ms。

### Task P4-5: 主题详情与楼层分页

- `GET /topics/{id}?page=&size=`：主题头 + 扁平楼层（floor、引用、作者摘要、upvote 状态、最佳答案标记）+ 版主权限标记。
- 每页 20，最大页限制；批量装配作者/被引用楼层。
- 验收：楼层顺序与数量正确；隐藏/删除楼层不展示；匿名/登录视图一致（个人状态客户端补）。

### Task P4-6: 回复、引用与楼层分配

- `POST /topics/{id}/posts`：锁定/删除校验；原子分配下一个 floor；可带 `reply_to_post_id`。
- 更新 topic/分区/用户统计；通知主题作者、关注者、被引用者（排除自己、去重）。
- 验收：并发回复 floor 不重复；三类通知各一条；同一目标通知按 merge_key 聚合。

### Task P4-7: 编辑历史、删除/恢复与主题事件

- `PATCH /posts/{id}`：作者/版主；写 `post_edits`（old/new/patch），`edit_count++`。
- `DELETE /posts/{id}`、`DELETE/PATCH /topics/{id}`：软删除/恢复；写 `topic_events`。
- `GET /posts/{id}/history`：作者看自己的，版主看任意。
- 验收：编辑后历史可查；删除后列表/详情不可见；事件日志完整。

### Task P4-8: Upvote、声望与用户统计

- `POST/DELETE /posts/{id}/upvote`：唯一键幂等；不可自投；最低声望 0；作者 reputation ±1。
- `community_user_stats` 原子增减；每日重算兜底。
- 验收：重复 upvote 不重复计数；取消回退；自投 403/422；声望与统计一致。

### Task P4-9: 关注与 Feed

- 主题/分区 follow/unfollow/ignore；`follower_count` 同步。
- `GET /feed`：关注的分区/主题 + 关注用户的新主题/回复，游标分页。
- 验收：关注后新回复/新主题进 feed；忽略后不再出现；并发无重复。

### Task P4-10: 分区版主与主题工具

- `community_category_moderators` 权限判定；置顶/取消、锁定/取消、移动、隐藏/恢复/删除。
- 移动主题时原子调整源/目标分区计数与最后回复；写 topic_events。
- 验收：普通用户 403；版主仅本分区；移动后两分区计数与列表正确。

### Task P4-11: 举报工单

- `POST /reports` 按目标 find-or-create flag、追加 report、通知版主。
- 版主分派/备注/处置（隐藏/删除/封禁/驳回）；写 actions；更新 flag 状态；通知举报人。
- 验收：同一目标多次举报聚合一个 flag；状态机 OPEN/WIP/RESOLVED/REJECTED 正确；举报人收到结果。

### Task P4-12: 通知聚合与清理

- 扩展通知类型；增加 merge_key；读取聚合展示；保留 30 天定时清理。
- 未读数与列表分页；排除自己。
- 验收：批量 upvote/回复聚合；30 天前记录被清理；未读数准确。

### Task P4-13: 社区搜索

- `GET /search?type=topics|posts|tags|users&q=&cid=&tag=&cursor=`。
- topics FULLTEXT(title) + posts FULLTEXT(content) + tags 匹配；分类/标签过滤；部分失败标记。
- 验收：中文 ngram 命中；过滤正确；游标无重复；`EXPLAIN` 无全表扫。

### Task P4-14: 前端论坛三栏与分区页

- `/forum` 三栏；左导航（版块/最新/热门/标签/用户）；分区卡片（图标/描述/子分区/计数/最后回复）；右栏（搜索/通知/用户）；移动端抽屉。
- 验收：视觉与 v2.1 §2/§8 一致；SSR 输出；个人状态客户端补。

### Task P4-15: 主题列表页

- 排序 tabs、标签筛选、置顶/锁定标识、回复/浏览数、最后回复、游标加载、空/错/骨架。
- 验收：切换排序/标签正确；深翻不重复；移动端可用。

### Task P4-16: 主题详情与发帖器

- 楼层渲染、引用、upvote、关注、编辑、举报、版主菜单、最佳答案标记、编辑历史弹层。
- Markdown 编辑器 + 预览；标签校验；作品关联；回复吸底。
- 验收：发布/回复/引用/投票/关注/最佳答案全通；XSS 过滤生效。

### Task P4-17: 标签/用户/最新/热门页

- `/tags`、`/tags/:slug`、`/users`、`/users/:id`、`/latest`、`/popular`。
- 用户主页显示主题/帖子/声望；标签页按主题数排序。
- 验收：分页/游标正确；用户统计准确。

### Task P4-18: 版务后台与验收

- `/moderation/flags`：筛选、分派、备注、处置；版主主题工具 UI。
- 种子数据：10 万主题/50 万楼层压测数据；E2E 10 条黄金路径；性能报告。
- 验收：v2.1 §10 全通过；P95 达标；计数重算对齐；无 P1–P3 回归。

## Definition of Done

- 所有列表/详情/搜索 `EXPLAIN` 命中索引，无全表扫。
- 10 万主题/50 万楼层下，分区列表与主题详情 P95 < 300ms。
- 计数（topic/post/follower/reputation/user_stats）写时原子更新 + 每日重算一致。
- 并发回复 floor 不重复；游标分页并发新增不重不漏。
- 通知聚合、30 天清理、举报工单状态机、分区版主权限全部有测试覆盖。
- v2.1 §10 的 10 条 E2E 全绿；P1–P3 测试不回退。

## 风险

| 风险 | 应对 |
|---|---|
| 计数漂移 | 原子 SQL + 每日重算 + 差异告警 |
| floor 并发 | 事务内 `SELECT ... FOR UPDATE` 或用 topic 版本号 CAS |
| 通知风暴 | merge_key 聚合 + 30 天清理 + 排除自己 |
| 版主越权 | 每个写端点校验 cid 版主/管理员；ArchUnit 禁止跨模块访表 |
| 游标不稳定 | `(pinned,last_post_at,id)` 复合游标 + 索引 |
| 热门榜 query 变慢 | 近 7 天窗口 + 计数索引；后续按天分桶 |

## 迁移说明

v2 的 `community_posts`/`community_comments` 尚未实现（P1–P3 不涉及），P4 直接按 v2.1 建表，无历史数据迁移。若届时已有数据，再补迁移脚本。
