# P4 论坛最小垂直切片实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让论坛端到端可用：分区列表 → 主题列表（游标）→ 主题详情（楼层分页）→ 发主题/回帖，并把结果接进现有 Nuxt 前端。

**Architecture:** 在模块化单体内新增 `community` 模块，仅通过 `identity :: api` 读取用户摘要；先落 categories/topics/posts/tags 四组表与种子分区，写入用事务 + 原子计数，读取用游标分页；前端沿用 Nuxt 3 + Pinia + Tailwind，公共页 SSR。

**Tech Stack:** Spring Boot 3.3.6 / Java 21 / Spring Modulith / JPA / Flyway / MySQL 8 / MockMvc + Testcontainers / Nuxt 3.21 / Vitest / Playwright。

**本切片包含：** 分区树（计数 + 最后回复）、全站/分区主题列表（latest 游标）、主题详情（楼层分页、浏览数）、发主题（标签校验、首楼）、回帖（楼层原子分配、引用）、邮箱验证写权限、基础限流、前端 5 个页面、E2E 黄金路径。

**本切片明确不做：** 热门榜、标签页、用户页、upvote/声望、关注/feed、版主操作、举报、通知、编辑历史、Markdown 渲染（正文按纯文本 + 换行展示，禁止输出 HTML）、搜索。均为后续任务，数据模型已预留字段。

---

## API 契约（本切片）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/api/v1/categories` | 匿名 | 启用分区树 + 计数 + 最后回复 |
| GET | `/api/v1/categories/{slug}/topics?cursor=&limit=` | 匿名 | 分区主题流，latest 游标 |
| GET | `/api/v1/topics?cursor=&limit=` | 匿名 | 全站主题流，latest 游标 |
| POST | `/api/v1/topics` | 登录 + 已验证 | 发主题 |
| GET | `/api/v1/topics/{id}?page=&size=` | 匿名 | 主题头 + 楼层分页 |
| POST | `/api/v1/topics/{id}/posts` | 登录 + 已验证 | 回帖/引用 |

统一响应体沿用 `ApiResponse`；游标响应 `{ items, nextCursor }`，`nextCursor=null` 表示结束。

---

### Task 1: 社区表结构与分区种子

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__community_forum.sql`
- Create: `backend/src/main/resources/db/migration/V3__community_seed.sql`
- Test: `backend/src/test/java/com/rance/galcommunity/community/CommunitySchemaIT.java`
- Modify: `backend/src/test/java/com/rance/galcommunity/IntegrationTestBase.java`（truncate 新表）

- [ ] **Step 1: 写 schema 测试（先失败）**

```java
class CommunitySchemaIT extends IntegrationTestBase {

    @Test
    void communityTablesAndSeedCategoriesExist() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_categories", Integer.class)).isEqualTo(13);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_categories WHERE parent_id IS NOT NULL",
                Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM community_categories WHERE slug='general'", String.class))
                .isEqualTo("综合讨论");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=CommunitySchemaIT test`
Expected: FAIL，`Table 'gal.community_categories' doesn't exist`.

- [ ] **Step 3: 写迁移 V2**

```sql
CREATE TABLE community_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    slug VARCHAR(80) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NULL,
    icon VARCHAR(80) NULL,
    color VARCHAR(16) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    min_tags INT NOT NULL DEFAULT 0,
    max_tags INT NOT NULL DEFAULT 5,
    topic_count BIGINT NOT NULL DEFAULT 0,
    post_count BIGINT NOT NULL DEFAULT 0,
    last_post_id BIGINT NULL,
    last_post_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_slug (slug),
    KEY idx_category_parent (parent_id, sort_order, id),
    KEY idx_category_enabled (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'DISCUSSION',
    title VARCHAR(160) NOT NULL,
    pinned BIT(1) NOT NULL DEFAULT b'0',
    locked BIT(1) NOT NULL DEFAULT b'0',
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    deleted_at DATETIME(6) NULL,
    post_count INT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    last_post_id BIGINT NULL,
    last_post_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_topic_category_list (category_id, visibility, deleted_at, pinned, last_post_at, id),
    KEY idx_topic_latest (last_post_at, id),
    KEY idx_topic_author (author_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    reply_to_post_id BIGINT NULL,
    floor INT NOT NULL,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    deleted_at DATETIME(6) NULL,
    edit_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_topic_floor (topic_id, floor),
    KEY idx_post_topic_page (topic_id, visibility, deleted_at, floor),
    KEY idx_post_author (author_id, created_at),
    KEY idx_post_reply (reply_to_post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    slug VARCHAR(60) NOT NULL,
    color VARCHAR(16) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name),
    UNIQUE KEY uk_tag_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE community_topic_tags (
    topic_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (topic_id, tag_id),
    KEY idx_topic_tag_tag (tag_id, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 4: 写种子 V3（10 个分区，5 个子分区）**

```sql
INSERT INTO community_categories
    (parent_id, slug, name, description, icon, color, sort_order, enabled, min_tags, max_tags)
VALUES
    (NULL, 'general', '综合讨论', 'Galgame 日常与综合话题', '💬', '#6366f1', 10, b'1', 0, 5),
    (NULL, 'news', '新作情报', '新作、移植与发售消息', '📰', '#0ea5e9', 20, b'1', 0, 5),
    (NULL, 'translation', '汉化交流', '汉化进度、开坑与协作', '🈯', '#22c55e', 30, b'1', 0, 5),
    (NULL, 'resources', '补丁与资料', '合规资源索引：仅补丁/语音/设定集/攻略/官方商店', '🧩', '#f59e0b', 40, b'1', 1, 5),
    (NULL, 'reviews', '攻略与感想', '攻略、评测与通关感想', '📝', '#ec4899', 50, b'1', 0, 5),
    (NULL, 'help', '求助问答', '提问与解答', '❓', '#14b8a6', 60, b'1', 0, 5),
    (NULL, 'creative', '原创与同人', '原创内容与同人创作', '🎨', '#a855f7', 70, b'1', 0, 5),
    (NULL, 'chat', '水区 / 闲聊', '轻松闲聊', '☕', '#64748b', 80, b'1', 0, 5);

INSERT INTO community_categories
    (parent_id, slug, name, description, icon, color, sort_order, enabled, min_tags, max_tags)
SELECT id, 'patch', '汉化补丁', '汉化补丁发布与反馈', '🔧', '#f59e0b', 41, b'1', 1, 5
FROM community_categories WHERE slug = 'resources';

INSERT INTO community_categories
    (parent_id, slug, name, description, icon, color, sort_order, enabled, min_tags, max_tags)
SELECT id, 'voice', '语音包', '语音包与音声资料', '🎙️', '#f59e0b', 42, b'1', 1, 5
FROM community_categories WHERE slug = 'resources';

INSERT INTO community_categories
    (parent_id, slug, name, description, icon, color, sort_order, enabled, min_tags, max_tags)
SELECT id, 'artbook', '设定集', '设定集与画集索引', '📚', '#f59e0b', 43, b'1', 1, 5
FROM community_categories WHERE slug = 'resources';

INSERT INTO community_categories
    (parent_id, slug, name, description, icon, color, sort_order, enabled, min_tags, max_tags)
SELECT id, 'guide', '攻略', '攻略与流程资料', '🗺️', '#f59e0b', 44, b'1', 1, 5
FROM community_categories WHERE slug = 'resources';

INSERT INTO community_categories
    (parent_id, slug, name, description, icon, color, sort_order, enabled, min_tags, max_tags)
SELECT id, 'store', '官方商店', '官方购买渠道与商店信息', '🛒', '#f59e0b', 45, b'1', 1, 5
FROM community_categories WHERE slug = 'resources';
```

- [ ] **Step 5: 更新测试基类 truncate**

在 `resetState()` 的 `truncate("user_accounts");` 之前加入（子表在前）：

```java
truncate("community_topic_tags");
truncate("community_tags");
truncate("community_posts");
truncate("community_topics");
truncate("community_categories");
```

- [ ] **Step 6: 跑测试**

Run: `mvn -q -Dtest=CommunitySchemaIT test`
Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/resources/db/migration backend/src/test/java/com/rance/galcommunity/community/CommunitySchemaIT.java backend/src/test/java/com/rance/galcommunity/IntegrationTestBase.java
git commit -m "feat(community): add forum schema and seed categories"
```

---

### Task 2: 游标分页基础件

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/shared/api/CursorPage.java`
- Create: `backend/src/main/java/com/rance/galcommunity/community/web/TopicCursor.java`
- Test: `backend/src/test/java/com/rance/galcommunity/community/TopicCursorTest.java`

- [ ] **Step 1: 写单元测试**

```java
class TopicCursorTest {

    @Test
    void roundTripsMicrosecondPrecision() {
        Instant at = Instant.parse("2026-11-18T06:00:00.123456Z");
        TopicCursor cursor = new TopicCursor(true, at, 42L);
        TopicCursor decoded = TopicCursor.decode(cursor.encode());
        assertThat(decoded.pinned()).isTrue();
        assertThat(decoded.lastPostAt()).isEqualTo(at);
        assertThat(decoded.id()).isEqualTo(42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!!", "v1|abc|1", "v1|1|2|x"})
    void rejectsMalformed(String raw) {
        assertThatThrownBy(() -> TopicCursor.decode(raw))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -Dtest=TopicCursorTest test`
Expected: FAIL，类不存在。

- [ ] **Step 3: 实现**

```java
package com.rance.galcommunity.shared.api;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor) {
      public CursorPage {
        items = List.copyOf(items);
    }
}
```

```java
package com.rance.galcommunity.community.web;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/** Cursor for pinned-first topic lists: (pinned, lastPostAt, id) descending. */
public record TopicCursor(boolean pinned, Instant lastPostAt, long id) {

    private static final String VERSION = "v1";

    public String encode() {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, lastPostAt);
        String raw = VERSION + "|" + (pinned ? 1 : 0) + "|" + micros + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static TopicCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                throw invalid();
            }
            Instant at = Instant.EPOCH.plus(Long.parseLong(parts[2]), ChronoUnit.MICROS);
            return new TopicCursor("1".equals(parts[1]), at, Long.parseLong(parts[3]));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_CURSOR, "分页游标无效");
    }
}
```

- [ ] **Step 4: 跑测试**

Run: `mvn -q -Dtest=TopicCursorTest test`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/rance/galcommunity/shared/api/CursorPage.java backend/src/main/java/com/rance/galcommunity/community/web/TopicCursor.java backend/src/test/java/com/rance/galcommunity/community/TopicCursorTest.java
git commit -m "feat(community): add cursor pagination primitives"
```

---

### Task 3: community 模块、实体、仓库与身份读取契约

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/community/package-info.java`
- Create: `.../community/domain/{Category,Topic,Post,Tag,TopicTag,TopicTagId}.java`
- Create: `.../community/repo/{CategoryRepository,TopicRepository,PostRepository,TagRepository,TopicTagRepository}.java`
- Modify: `.../identity/api/IdentityApi.java`（新增 `isEmailVerified`）
- Modify: `.../identity/service/UserService.java`（实现）
- Modify: `backend/src/test/java/com/rance/galcommunity/architecture/ModuleStructureTest.java`
- Test: `backend/src/test/java/com/rance/galcommunity/identity/IdentityApiIT.java`（补验证状态用例）

- [ ] **Step 1: 部署模块边界**

```java
@ApplicationModule(allowedDependencies = {"shared", "identity :: api"})
package com.rance.galcommunity.community;

import org.springframework.modulith.ApplicationModule;
```

`ModuleStructureTest.detectsExpectedModules` 改为：

```java
.containsExactlyInAnyOrder("shared", "identity", "community");
```

- [ ] **Step 2: 扩展 IdentityApi（先写测试）**

在 `IdentityApiIT` 加：

```java
@Test
void reportsEmailVerificationState() {
    long id = registerUnverified("verifystate@example.com");
    assertThat(identityApi.isEmailVerified(id)).isFalse();
    // 通过 JDBC 置为已验证，避免依赖邮件流程
    jdbcTemplate.update("UPDATE user_accounts SET email_verified = b'1' WHERE id = ?", id);
    assertThat(identityApi.isEmailVerified(id)).isTrue();
}
```

（`registerUnverified` 用现有注册辅助方法即可；identityApi 字段注入 `IdentityApi`。）

- [ ] **Step 3: 实现接口**

```java
/** True only for an ACTIVE user whose email is verified. */
boolean isEmailVerified(long id);
```

`UserService`：

```java
@Override
@Transactional(readOnly = true)
public boolean isEmailVerified(long id) {
    return users.findById(id)
            .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
            .map(UserAccount::isEmailVerified)
            .orElse(false);
}
```

- [ ] **Step 4: 实体与仓库**

实体均为 `@Entity` + Lombok `@Getter @Setter @NoArgsConstructor`，时间字段 `Instant`，布尔字段 `boolean`（BIT(1)），枚举 `@Enumerated(STRING)`。

- `Category`：映射 V2 全部列；`@Column(name="enabled", nullable=false) boolean enabled`。
- `Topic`：字段 `id, categoryId, authorId, kind(String), title, pinned, locked, visibility(String), deletedAt, postCount, viewCount, lastPostId, lastPostAt, createdAt, updatedAt`；`@PrePersist/@PreUpdate` 维护时间。
- `Post`：`id, topicId, authorId, content, replyToPostId, floor, visibility, deletedAt, editCount, createdAt, updatedAt`。
- `Tag`：`id, name, slug, color`。
- `TopicTag`：`@EmbeddedId TopicTagId(topicId, tagId)`，`@MapsId` 无额外列。

仓库关键方法：

```java
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlugAndEnabledTrue(String slug);
    List<Category> findByEnabledTrueOrderBySortOrderAscIdAsc();
}

public interface TopicRepository extends JpaRepository<Topic, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Topic t where t.id = :id")
    Optional<Topic> findByIdForUpdate(@Param("id") long id);

    @Query("""
            select t from Topic t
            where t.categoryId = :categoryId and t.visibility = 'PUBLIC' and t.deletedAt is null
              and (:cursorAt is null
                   or t.pinned < :cursorPinned
                   or (t.pinned = :cursorPinned
                       and (t.lastPostAt < :cursorAt
                            or (t.lastPostAt = :cursorAt and t.id < :cursorId))))
            order by t.pinned desc, t.lastPostAt desc, t.id desc
            """)
    List<Topic> findCategoryPage(@Param("categoryId") long categoryId,
                                 @Param("cursorPinned") boolean cursorPinned,
                                 @Param("cursorAt") Instant cursorAt,
                                 @Param("cursorId") long cursorId,
                                 Pageable pageable);

    @Query("""
            select t from Topic t
            where t.visibility = 'PUBLIC' and t.deletedAt is null
              and (:cursorAt is null
                   or t.lastPostAt < :cursorAt
                   or (t.lastPostAt = :cursorAt and t.id < :cursorId))
            order by t.lastPostAt desc, t.id desc
            """)
    List<Topic> findLatestPage(@Param("cursorAt") Instant cursorAt,
                               @Param("cursorId") long cursorId, Pageable pageable);
}

public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("""
            select p from Post p
            where p.topicId = :topicId and p.visibility = 'PUBLIC' and p.deletedAt is null
            order by p.floor asc
            """)
    Page<Post> findVisiblePage(@Param("topicId") long topicId, Pageable pageable);

    List<Post> findByIdIn(Collection<Long> ids);
}
```

注意：`findCategoryPage`/`findLatestPage` 使用 `:cursorAt is null` 时，首屏调用方传 `null, true(pinned), 0L`，游标条件整体短路。JPQL 中参数为 null 时必须显式传 `Instant` 类型（`@Param` 已声明）。

- [ ] **Step 5: 跑模块与身份测试**

Run: `mvn -q -Dtest=ModuleStructureTest,IdentityApiIT test`
Expected: PASS（含模块边界校验）。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/rance/galcommunity/community backend/src/main/java/com/rance/galcommunity/identity/api/IdentityApi.java backend/src/main/java/com/rance/galcommunity/identity/service/UserService.java backend/src/test/java/com/rance/galcommunity/architecture/ModuleStructureTest.java backend/src/test/java/com/rance/galcommunity/identity/IdentityApiIT.java
git commit -m "feat(community): add domain model and identity verification contract"
```

---

### Task 4: 分区树 API

**Files:**
- Create: `.../community/web/dto/CategoryNode.java`、`LastPostView.java`、`UserBrief.java`
- Create: `.../community/service/CategoryQueryService.java`
- Create: `.../community/web/CategoryController.java`
- Test: `backend/src/test/java/com/rance/galcommunity/community/CategoryApiIT.java`

- [ ] **Step 1: 写接口测试**

```java
@Test
void listsCategoryTreeWithCountsAndLastPost() throws Exception {
    long generalId = idOf("general");
    long topicId = jdbcInsertTopic(generalId, "最后一帖主题");
    jdbcTemplate.update("UPDATE community_categories SET topic_count=1, post_count=3, "
            + "last_post_id=?, last_post_at=? WHERE id=?", lastPostId, now, generalId);

    mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(8))
            .andExpect(jsonPath("$.data[0].slug").value("general"))
            .andExpect(jsonPath("$.data[0].topicCount").value(1))
            .andExpect(jsonPath("$.data[0].lastPost.topicTitle").value("最后一帖主题"));

    mockMvc.perform(get("/api/v1/categories"))
            .andExpect(jsonPath("$.data[?(@.slug=='resources')].children.length()").value(5));
}
```

辅助方法（测试类内）：`idOf(slug)`、`jdbcInsertTopic`、插入一条 post 作为最后回复、`now = Instant.now().truncatedTo(ChronoUnit.MICROS)`。

- [ ] **Step 2: 实现 DTO + 服务 + 控制器**

```java
public record UserBrief(long id, String nickname, String avatarUrl) {}

public record LastPostView(long postId, long topicId, String topicTitle,
                           long authorId, String authorNickname, Instant at) {}

public record CategoryNode(long id, Long parentId, String slug, String name, String description,
                           String icon, String color, int minTags, int maxTags,
                           long topicCount, long postCount,
                           LastPostView lastPost, List<CategoryNode> children) {}
```

`CategoryQueryService.tree()`：

1. 取全部启用分区，按 `sortOrder, id` 排序。
2. 收集非空 `lastPostId`，一次 `PostRepository.findByIdIn` → topic ids → `TopicRepository.findAllById` → author ids + `IdentityApi.findUsers`（批量，禁止循环查询）。
3. 按 `parentId` 组装一层子节点（种子只有两级）；`lastPost` 组装失败（数据被删）时置 `null`。
4. 返回根节点列表。

`CategoryController`：

```java
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryQueryService categories;

    @GetMapping
    public ApiResponse<List<CategoryNode>> tree() {
        return ApiResponse.ok(categories.tree());
    }
}
```

- [ ] **Step 3: 安全放行（匿名可读）**

`SecurityConfig` 在 permitAll 段加：

```java
.requestMatchers(HttpMethod.GET, "/api/v1/categories/**", "/api/v1/topics/**").permitAll()
```

- [ ] **Step 4: 跑测试**

Run: `mvn -q -Dtest=CategoryApiIT test`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git commit -m "feat(community): expose category tree api"
```

---

### Task 5: 主题游标列表 API

**Files:**
- Create: `.../community/web/dto/TopicListItem.java`
- Create: `.../community/service/TopicQueryService.java`（列表部分）
- Create: `.../community/web/TopicController.java`（列表端点）
- Test: `backend/src/test/java/com/rance/galcommunity/community/TopicListIT.java`

- [ ] **Step 1: 写测试**

```java
@Test
void categoryTopicsAreCursorPaginatedWithoutDuplicates() throws Exception {
    for (int i = 1; i <= 5; i++) {
        insertTopic(generalId, "主题 " + i, BASE.plusSeconds(i));
    }
    String first = mockMvc.perform(get("/api/v1/categories/general/topics?limit=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].title").value("主题 5"))
            .andReturn().getResponse().getContentAsString();
    String cursor = JsonPath.read(first, "$.data.nextCursor");

    mockMvc.perform(get("/api/v1/categories/general/topics?limit=2&cursor=" + cursor))
            .andExpect(jsonPath("$.data.items[0].title").value("主题 3"))
            .andExpect(jsonPath("$.data.items[1].title").value("主题 2"));
}

@Test
void pinnedTopicsComeFirst() throws Exception {
    insertTopic(generalId, "普通", BASE, false);
    insertTopic(generalId, "置顶", BASE.minusSeconds(60), true);
    mockMvc.perform(get("/api/v1/categories/general/topics"))
            .andExpect(jsonPath("$.data.items[0].title").value("置顶"));
}

@Test
void rejectsBadCursor() throws Exception {
    mockMvc.perform(get("/api/v1/categories/general/topics?cursor=bad"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40002));
}

@Test
void unknownCategoryReturns404() throws Exception {
    mockMvc.perform(get("/api/v1/categories/nope/topics"))
            .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: 实现查询与组装**

```java
public record TopicListItem(long id, String title, String categorySlug, String categoryName,
                            boolean pinned, boolean locked, int postCount, long viewCount,
                            UserBrief author, Instant createdAt, Instant lastPostAt) {}
```

`TopicQueryService.listByCategory(slug, cursor, limit)`：

- `limit = clamp(1..50, default 20)`；游标 null → 首屏；否则 `TopicCursor.decode`。
- 校验分区存在且启用，否则 `NOT_FOUND`。
- 查询 `limit + 1` 条判断是否还有下一页；返回前 `limit` 条。
- 批量装配作者（`IdentityApi.findUsers`）与分类名；分类名从已加载的 category 对象取。
- `nextCursor` 用最后一条 item 的 `(pinned, lastPostAt, id)` 编码；无更多则为 `null`。

`listLatest(cursor, limit)` 同理，`pinned` 恒为 `false`（不入游标语义，编码时传 false）。

控制器：

```java
@GetMapping("/{slug}/topics")
public ApiResponse<CursorPage<TopicListItem>> byCategory(
        @PathVariable String slug,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int limit) {
    return ApiResponse.ok(topics.listByCategory(slug, cursor, limit));
}
```

全站流放在同一控制器的 `@GetMapping`（`/api/v1/topics`）。

- [ ] **Step 3: 跑测试**

Run: `mvn -q -Dtest=TopicListIT test`
Expected: PASS（含 40002/404）。

- [ ] **Step 4: 提交**

```bash
git commit -m "feat(community): add cursor-paginated topic lists"
```

---

### Task 6: 主题详情与楼层分页

**Files:**
- Create: `.../community/web/dto/{TopicDetailResponse,PostView,TagView}.java`
- Modify: `.../community/service/TopicQueryService.java`
- Modify: `.../community/web/TopicController.java`
- Test: `backend/src/test/java/com/rance/galcommunity/community/TopicDetailIT.java`

- [ ] **Step 1: 写测试**

```java
@Test
void returnsTopicHeaderAndPostsPaged() throws Exception {
    long topicId = insertTopicWithPosts(generalId, 25);
    mockMvc.perform(get("/api/v1/topics/" + topicId + "?page=2&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("主题标题"))
            .andExpect(jsonPath("$.data.posts.items.length()").value(5))
            .andExpect(jsonPath("$.data.posts.items[0].floor").value(21))
            .andExpect(jsonPath("$.data.posts.total").value(25))
            .andExpect(jsonPath("$.data.posts.items[0].author.nickname").isNotEmpty());
}

@Test
void incrementsViewCount() throws Exception {
    long topicId = insertTopic(generalId, "浏览数", BASE);
    mockMvc.perform(get("/api/v1/topics/" + topicId)).andExpect(status().isOk());
    assertThat(jdbcTemplate.queryForObject(
            "SELECT view_count FROM community_topics WHERE id=?", Long.class, topicId)).isEqualTo(1L);
}

@Test
void deletedOrMissingTopicIs404() throws Exception {
    mockMvc.perform(get("/api/v1/topics/999999")).andExpect(status().isNotFound());
}
```

- [ ] **Step 2: 实现**

```java
public record TagView(long id, String name, String slug) {}

public record PostView(long id, int floor, String content, UserBrief author,
                       Long replyToPostId, Instant createdAt) {}

public record TopicDetailResponse(long id, String title, String categorySlug, String categoryName,
                                  boolean pinned, boolean locked, long viewCount,
                                  UserBrief author, Instant createdAt,
                                  List<TagView> tags, PageResponse<PostView> posts) {}
```

`detail(id, page, size)` 流程：

1. 查 topic，过滤 `visibility=PUBLIC && deletedAt==null`，否则 `NOT_FOUND`。
2. `size=clamp(1..50,20)`，`page>=1`；`PageRequest.of(page-1,size)`。
3. `postRepository.findVisiblePage` → 批量作者 → 映射 `PostView`。
4. 标签：`topicTagRepository.findTagIdsByTopicId` → `tagRepository.findAllById`。
5. `jdbcTemplate.update("UPDATE community_topics SET view_count = view_count + 1 WHERE id = ?", id)`（独立于事务的简单计数；读请求不失败于计数）。
6. 组装 `TopicDetailResponse`。

- [ ] **Step 3: 跑测试**

Run: `mvn -q -Dtest=TopicDetailIT test`
Expected: PASS。

- [ ] **Step 4: 提交**

```bash
git commit -m "feat(community): add topic detail with paged floors"
```

---

### Task 7: 发主题（首楼、标签、计数）

**Files:**
- Create: `.../community/web/dto/CreateTopicRequest.java`
- Create: `.../community/service/TopicWriteService.java`
- Modify: `.../community/web/TopicController.java`
- Test: `backend/src/test/java/com/rance/galcommunity/community/CreateTopicIT.java`

- [ ] **Step 1: 写测试**

```java
@Test
void verifiedUserCreatesTopicWithFirstFloorAndCounters() throws Exception {
    String token = verifiedUser("poster@example.com", "Poster");
    MvcResult result = mockMvc.perform(post("/api/v1/topics")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"categoryId": %d, "title": "我的第一个主题",
                             "content": "正文内容", "tags": ["汉化", "补丁"]}
                            """.formatted(generalId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("我的第一个主题"))
            .andReturn();

    long topicId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    assertThat(jdbcTemplate.queryForObject(
            "SELECT floor FROM community_posts WHERE topic_id=?", Integer.class, topicId)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
            "SELECT topic_count FROM community_categories WHERE id=?", Long.class, generalId)).isEqualTo(1L);
    assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM community_topic_tags WHERE topic_id=?", Integer.class, topicId)).isEqualTo(2);
}

@Test
void unverifiedUserCannotCreateTopic() throws Exception {
    String token = unverifiedUser("unverified@example.com", "Unverified");
    mockMvc.perform(post("/api/v1/topics")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(40302));
}

@Test
void enforcesCategoryTagRules() throws Exception {
    // resources 分区 min_tags=1
    String token = verifiedUser("tagger@example.com", "Tagger");
    mockMvc.perform(post("/api/v1/topics")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"categoryId": %d, "title": "无标签主题", "content": "正文"}
                            """.formatted(resourcesId)))
            .andExpect(status().isUnprocessableEntity());
}

@Test
void rejectsBlankAndOverlongInput() throws Exception { /* 40001，两条断言 */ }
```

测试内辅助：`verifiedUser` 复用 `registerAndGetVerificationToken` + 调验证端点 + 登录拿 token；`unverifiedUser` 注册后直接登录。

- [ ] **Step 2: 实现请求校验**

```java
public record CreateTopicRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(min = 4, max = 160) String title,
        @NotBlank @Size(max = 20000) String content,
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> tags) {}
```

- [ ] **Step 3: 写服务（事务内）**

```java
@Transactional
public TopicDetailResponse create(long userId, CreateTopicRequest request) {
    if (!identityApi.isEmailVerified(userId)) {
        throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "请先验证邮箱");
    }
    if (!rateLimit.tryAcquire("rl:topic:user:" + userId, 10, Duration.ofHours(1))) {
        throw new BusinessException(ErrorCode.RATE_LIMITED, "发帖过于频繁");
    }
    Category category = categories.findById(request.categoryId())
            .filter(Category::isEnabled)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分区不存在"));
    List<String> tagNames = normalizeTags(request.tags());
    if (tagNames.size() < category.getMinTags() || tagNames.size() > category.getMaxTags()) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "标签数量需在 " + category.getMinTags() + "–" + category.getMaxTags() + " 之间");
    }

    Instant now = Instant.now();
    Topic topic = new Topic();
    topic.setCategoryId(category.getId());
    topic.setAuthorId(userId);
    topic.setKind("DISCUSSION");
    topic.setTitle(request.title().trim());
    topic.setPostCount(1);
    topic.setVisibility("PUBLIC");
    topic.setLastPostAt(now);
    topics.save(topic);

    Post post = new Post();
    post.setTopicId(topic.getId());
    post.setAuthorId(userId);
    post.setContent(request.content().trim());
    post.setFloor(1);
    post.setVisibility("PUBLIC");
    posts.save(post);

    topic.setLastPostId(post.getId());
    categories.incrementAfterTopic(category.getId(), now);
    categories.updateLastPost(category.getId(), post.getId(), now);
    attachTags(topic.getId(), tagNames);
    return detail(userId, topic.getId(), 1, 20);
}
```

`CategoryRepository` 增加：

```java
@Modifying
@Query("update Category c set c.topicCount = c.topicCount + 1, c.postCount = c.postCount + 1 where c.id = :id")
void incrementAfterTopic(@Param("id") long id);

@Modifying
@Query("update Category c set c.lastPostId = :postId, c.lastPostAt = :at where c.id = :id")
void updateLastPost(@Param("id") long id, @Param("postId") long postId, @Param("at") Instant at);
```

标签 `normalizeTags`：trim、去空、`LinkedHashSet` 去重、保留顺序；`attachTags` 用 `tagRepository.findByNameIn` 找已有，缺失则创建（`name=slug=trimmed`，slug 冲突时追加 `-<id>` 由数据库唯一键兜底——MVP 标签只含中文/英文时 `name` 唯一即可）；写 `TopicTag`。

- [ ] **Step 4: 控制器**

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<TopicDetailResponse> create(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody CreateTopicRequest request) {
    return ApiResponse.ok(writes.create(CurrentUser.of(jwt).id(), request));
}
```

- [ ] **Step 5: 跑测试**

Run: `mvn -q -Dtest=CreateTopicIT test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git commit -m "feat(community): create topics with tags and atomic counters"
```

---

### Task 8: 回帖、楼层分配与引用

**Files:**
- Create: `.../community/web/dto/CreatePostRequest.java`
- Modify: `.../community/service/TopicWriteService.java`
- Modify: `.../community/web/TopicController.java`
- Test: `backend/src/test/java/com/rance/galcommunity/community/CreatePostIT.java`

- [ ] **Step 1: 写测试**

```java
@Test
void replyAllocatesNextFloorAndUpdatesCounters() throws Exception {
    String token = verifiedUser("replier@example.com", "Replier");
    long topicId = insertTopic(generalId, "回复测试", BASE);

    mockMvc.perform(post("/api/v1/topics/" + topicId + "/posts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"第一层回复\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.floor").value(2));

    assertThat(jdbcTemplate.queryForObject(
            "SELECT post_count FROM community_topics WHERE id=?", Integer.class, topicId)).isEqualTo(2);
    assertThat(jdbcTemplate.queryForObject(
            "SELECT last_post_id FROM community_topics WHERE id=?", Long.class, topicId)).isNotNull();
}

@Test
void quoteMustBelongToSameTopic() throws Exception { /* 42201 */ }

@Test
void lockedTopicRejectsReply() throws Exception {
    jdbcTemplate.update("UPDATE community_topics SET locked=b'1' WHERE id=?", topicId);
    /* 42201，message 含“已锁定” */
}
```

- [ ] **Step 2: 实现**

```java
public record CreatePostRequest(@NotBlank @Size(max = 20000) String content,
                                Long replyToPostId) {}
```

服务逻辑：

```java
@Transactional
public PostView reply(long userId, long topicId, CreatePostRequest request) {
    if (!identityApi.isEmailVerified(userId)) {
        throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "请先验证邮箱");
    }
    if (!rateLimit.tryAcquire("rl:post:user:" + userId, 60, Duration.ofHours(1))) {
        throw new BusinessException(ErrorCode.RATE_LIMITED, "回帖过于频繁");
    }
    Topic topic = topics.findByIdForUpdate(topicId)
            .filter(t -> t.getDeletedAt() == null && "PUBLIC".equals(t.getVisibility()))
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "主题不存在"));
    if (topic.isLocked()) {
        throw new BusinessException(ErrorCode.BUSINESS_RULE, "主题已锁定");
    }
    if (request.replyToPostId() != null) {
        Post quoted = posts.findById(request.replyToPostId())
                .filter(p -> p.getTopicId() == topicId && p.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE, "引用楼层无效"));
    }

    int floor = topic.getPostCount() + 1;
    Post post = new Post();
    post.setTopicId(topicId);
    post.setAuthorId(userId);
    post.setContent(request.content().trim());
    post.setReplyToPostId(request.replyToPostId());
    post.setFloor(floor);
    post.setVisibility("PUBLIC");
    posts.save(post);

    Instant now = Instant.now();
    topic.setPostCount(floor);
    topic.setLastPostId(post.getId());
    topic.setLastPostAt(now);
    categories.incrementPost(topic.getCategoryId());
    categories.updateLastPost(topic.getCategoryId(), post.getId(), now);
    return new PostView(post.getId(), floor, post.getContent(),
            identityApi.findUser(userId).map(u -> new UserBrief(u.id(), u.nickname(), u.avatarUrl()))
                    .orElse(new UserBrief(userId, "已注销用户", null)),
            post.getReplyToPostId(), post.getCreatedAt());
}
```

`CategoryRepository.incrementPost`：`update Category c set c.postCount = c.postCount + 1 where c.id = :id`。

并发正确性由 `findByIdForUpdate`（`SELECT ... FOR UPDATE`）+ `(topic_id, floor)` 唯一键双保险。

- [ ] **Step 3: 并发测试（关键）**

```java
@Test
void concurrentRepliesProduceUniqueFloors() throws Exception {
    long topicId = insertTopic(generalId, "并发", BASE);
    String token = verifiedUser("race@example.com", "Race");
    int n = 8;
    var pool = Executors.newFixedThreadPool(n);
    var futures = new ArrayList<Future<?>>();
    for (int i = 0; i < n; i++) {
        futures.add(pool.submit(() -> {
            mockMvc.perform(post("/api/v1/topics/" + topicId + "/posts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"race\"}"));
            return null;
        }));
    }
    for (var f : futures) f.get();
    pool.shutdown();

    Integer distinct = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT floor) FROM community_posts WHERE topic_id=?", Integer.class, topicId);
    Integer total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM community_posts WHERE topic_id=?", Integer.class, topicId);
    assertThat(distinct).isEqualTo(total).isEqualTo(9);
}
```

- [ ] **Step 4: 跑测试**

Run: `mvn -q -Dtest=CreatePostIT test`
Expected: PASS，无重复 floor。

- [ ] **Step 5: 提交**

```bash
git commit -m "feat(community): reply with atomic floor allocation"
```

---

### Task 9: 写接口限流与文档对齐

**Files:**
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/security/SecurityConfig.java`（若非 Task 4 已完成）
- Modify: `README.md`（API 概览加论坛端点）
- Test: `backend/src/test/java/com/rance/galcommunity/community/ForumSecurityIT.java`

- [ ] **Step 1: 写测试**

```java
@Test
void anonymousCanReadButNotWrite() throws Exception {
    mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/topics")).andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/topics")
                    .contentType(MediaType.APPLICATION_JSON).content(validBody()))
            .andExpect(status().isUnauthorized());
}

@Test
void openApiDocumentsForumEndpoints() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/categories']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/topics/{id}/posts']").exists());
}
```

- [ ] **Step 2: 确认限流接在服务层**（Task 7/8 已实现），跑测试

Run: `mvn -q -Dtest=ForumSecurityIT test`
Expected: PASS。

- [ ] **Step 3: 提交**

```bash
git commit -m "test(community): lock down forum read/write access"
```

---

## 前端

### Task 10: 论坛 API 客户端与类型

**Files:**
- Create: `frontend/lib/forum.ts`
- Test: `frontend/lib/forum.test.ts`（游标拼接与参数构造的纯函数）

- [ ] **Step 1: 写类型与函数**

```ts
import { api } from '~/lib/api'

export interface UserBrief { id: number; nickname: string; avatarUrl: string | null }
export interface LastPostView {
  postId: number; topicId: number; topicTitle: string
  authorId: number; authorNickname: string; at: string
}
export interface CategoryNode {
  id: number; parentId: number | null
  slug: string; name: string; description: string | null
  icon: string | null; color: string | null
  minTags: number; maxTags: number
  topicCount: number; postCount: number
  lastPost: LastPostView | null
  children: CategoryNode[]
}
export interface TopicListItem {
  id: number; title: string; categorySlug: string; categoryName: string
  pinned: boolean; locked: boolean; postCount: number; viewCount: number
  author: UserBrief; createdAt: string; lastPostAt: string
}
export interface CursorPage<T> { items: T[]; nextCursor: string | null }
export interface TagView { id: number; name: string; slug: string }
export interface PostView {
  id: number; floor: number; content: string
  author: UserBrief; replyToPostId: number | null; createdAt: string
}
export interface PageData<T> { items: T[]; total: number; page: number; size: number }
export interface TopicDetail {
  id: number; title: string; categorySlug: string; categoryName: string
  pinned: boolean; locked: boolean; viewCount: number
  author: UserBrief; createdAt: string
  tags: TagView[]; posts: PageData<PostView>
}

export const forumApi = {
  categories: async (): Promise<CategoryNode[]> =>
    (await api.get('/categories')).data.data,
  categoryTopics: async (slug: string, cursor?: string | null): Promise<CursorPage<TopicListItem>> =>
    (await api.get(`/categories/${encodeURIComponent(slug)}/topics`, { params: { cursor: cursor ?? undefined } })).data.data,
  latestTopics: async (cursor?: string | null): Promise<CursorPage<TopicListItem>> =>
    (await api.get('/topics', { params: { cursor: cursor ?? undefined } })).data.data,
  topic: async (id: number, page = 1): Promise<TopicDetail> =>
    (await api.get(`/topics/${id}`, { params: { page } })).data.data,
  createTopic: async (payload: { categoryId: number; title: string; content: string; tags: string[] }): Promise<TopicDetail> =>
    (await api.post('/topics', payload)).data.data,
  reply: async (topicId: number, payload: { content: string; replyToPostId?: number | null }): Promise<PostView> =>
    (await api.post(`/topics/${topicId}/posts`, payload)).data.data
}
```

- [ ] **Step 2: 单测**：断言 `forumApi.categoryTopics` 用 axios mock（`vi.mock('~/lib/api')`）时 `cursor` 为 `undefined` 而非 `null`；`encodeURIComponent` 处理 slug。

- [ ] **Step 3: 跑测试**

Run: `cd frontend && npm run test:unit`
Expected: PASS（现有 7 个 + 新增）。

- [ ] **Step 4: 提交**

```bash
git commit -m "feat(web): add forum api client and types"
```

---

### Task 11: `/forum` 分区页与全局导航

**Files:**
- Create: `frontend/components/forum/CategoryCard.vue`
- Create: `frontend/pages/forum/index.vue`
- Modify: `frontend/layouts/default.vue`（导航加入 论坛 / 最新）

- [ ] **Step 1: 页面（SSR）**

```vue
<script setup lang="ts">
import { forumApi, type CategoryNode } from '~/lib/forum'

const { data, error } = await useAsyncData<CategoryNode[]>('categories', () => forumApi.categories())
const roots = computed(() => data.value ?? [])
</script>

<template>
  <section class="space-y-6">
    <header class="flex items-end justify-between">
      <div>
        <h1 class="text-2xl font-bold">论坛</h1>
        <p class="mt-1 text-sm text-slate-400">按分区浏览讨论；登录并验证邮箱后可发帖。</p>
      </div>
      <NuxtLink v-if="useAuthStore().isAuthenticated" to="/topics/new" class="btn">发主题</NuxtLink>
    </header>

    <p v-if="error" class="rounded border border-red-500/40 bg-red-500/10 p-4 text-red-300">
      分区加载失败，请稍后重试。
    </p>

    <div v-else class="space-y-4">
      <CategoryCard v-for="category in roots" :key="category.id" :category="category" />
    </div>
  </section>
</template>
```

`CategoryCard.vue`：展示图标、名称（链接 `/forum/{slug}`）、描述、子分区 chips（链接同样到 `/forum/{slug}`）、`{topicCount} 主题 · {postCount} 帖子`，右下角最后回复 `topicTitle · authorNickname`（无则“暂无讨论”）。样式沿用 `btn`、`border-white/10`、`bg-white/5` 等现有 token。

导航 `default.vue` 在标题与登录区之间加入：

```vue
<nav class="flex items-center gap-4 text-sm text-slate-300">
  <NuxtLink to="/forum" class="hover:text-white">论坛</NuxtLink>
  <NuxtLink to="/latest" class="hover:text-white">最新</NuxtLink>
</nav>
```

（窄屏允许换行，不做抽屉。）

- [ ] **Step 2: 手工验证**

打开 `http://localhost:3001/forum`：看到 8 个根分区，`补丁与资料` 展开 5 个子分区；卡片计数为 0、显示“暂无讨论”。

- [ ] **Step 3: typecheck + 提交**

Run: `cd frontend && npm run typecheck`
Expected: 无错误。

```bash
git commit -m "feat(web): add forum category page"
```

---

### Task 12: 分区主题列表与全站最新

**Files:**
- Create: `frontend/components/forum/TopicRow.vue`
- Create: `frontend/pages/forum/[slug].vue`
- Create: `frontend/pages/latest.vue`

- [ ] **Step 1: 实现分区页**

```vue
<script setup lang="ts">
import { forumApi, type CursorPage, type TopicListItem } from '~/lib/forum'

const route = useRoute()
const slug = computed(() => String(route.params.slug))
const { data, error } = await useAsyncData(`topics-${slug.value}`, () => forumApi.categoryTopics(slug.value))

const items = ref<TopicListItem[]>(data.value?.items ?? [])
const nextCursor = ref<string | null>(data.value?.nextCursor ?? null)
const loading = ref(false)

watch(slug, async () => {
  const page = await forumApi.categoryTopics(slug.value)
  items.value = page.items
  nextCursor.value = page.nextCursor
})

async function loadMore() {
  if (!nextCursor.value || loading.value) return
  loading.value = true
  try {
    const page = await forumApi.categoryTopics(slug.value, nextCursor.value)
    items.value = [...items.value, ...page.items]
    nextCursor.value = page.nextCursor
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="space-y-5">
    <header class="flex items-center justify-between">
      <h1 class="text-2xl font-bold">分区讨论</h1>
      <NuxtLink v-if="useAuthStore().isAuthenticated" to="/topics/new" class="btn">发主题</NuxtLink>
    </header>
    <p v-if="error" class="text-red-300">加载失败，请稍后重试。</p>
    <div v-else-if="items.length === 0" class="rounded border border-white/10 bg-white/5 p-8 text-center text-slate-400">
      这里还没有主题，来发第一帖吧。
    </div>
    <ul v-else class="divide-y divide-white/10 overflow-hidden rounded border border-white/10 bg-white/5">
      <TopicRow v-for="topic in items" :key="topic.id" :topic="topic" />
    </ul>
    <div v-if="nextCursor" class="flex justify-center">
      <button class="btn-ghost" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </div>
  </section>
</template>
```

`TopicRow.vue`：标题链接 `/topics/{id}`、置顶/锁定徽标、作者昵称、`{postCount} 回复 · {viewCount} 浏览`、最后回复时间（`new Date(x).toLocaleString('zh-CN')`）。

`latest.vue` 复用同一结构，数据源 `forumApi.latestTopics`，`useAsyncData('latest-topics', ...)`。

- [ ] **Step 2: 手工验证**

`/forum/general` 显示空态；发帖后出现；`/latest` 显示同一主题。

- [ ] **Step 3: typecheck + 提交**

```bash
git commit -m "feat(web): add category topic list and latest feed"
```

---

### Task 13: 主题详情与回帖

**Files:**
- Create: `frontend/components/forum/PostCard.vue`
- Create: `frontend/pages/topics/[id].vue`

- [ ] **Step 1: 页面**

```vue
<script setup lang="ts">
import { forumApi, type TopicDetail } from '~/lib/forum'
import { apiErrorMessage } from '~/lib/api'

const route = useRoute()
const auth = useAuthStore()
const id = computed(() => Number(route.params.id))
const page = computed(() => Number(route.query.page ?? 1))

const { data: topic, error, refresh } = await useAsyncData(
  () => `topic-${id.value}-${page.value}`,
  () => forumApi.topic(id.value, page.value)
)

const content = ref('')
const replyError = ref('')
const submitting = ref(false)

async function submitReply() {
  if (!content.value.trim()) return
  replyError.value = ''
  submitting.value = true
  try {
    await forumApi.reply(id.value, { content: content.value.trim() })
    content.value = ''
    await refresh()
  } catch (e) {
    replyError.value = apiErrorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <article v-if="topic" class="space-y-6">
    <header class="space-y-2">
      <div class="flex flex-wrap items-center gap-2 text-xs text-slate-400">
        <NuxtLink :to="`/forum/${topic.categorySlug}`" class="text-brand-300">{{ topic.categoryName }}</NuxtLink>
        <span v-if="topic.pinned" class="rounded bg-brand-500/20 px-2 py-0.5 text-brand-200">置顶</span>
        <span v-if="topic.locked" class="rounded bg-amber-500/20 px-2 py-0.5 text-amber-200">已锁定</span>
        <span>{{ topic.viewCount }} 浏览</span>
      </div>
      <h1 class="text-2xl font-bold">{{ topic.title }}</h1>
      <p class="text-sm text-slate-400">
        由 {{ topic.author.nickname }} 发布于 {{ new Date(topic.createdAt).toLocaleString('zh-CN') }}
      </p>
      <ul v-if="topic.tags.length" class="flex flex-wrap gap-2">
        <li v-for="tag in topic.tags" :key="tag.id" class="rounded bg-white/10 px-2 py-0.5 text-xs">#{{ tag.name }}</li>
      </ul>
    </header>

    <ul class="space-y-3">
      <PostCard v-for="post in topic.posts.items" :key="post.id" :post="post" />
    </ul>

    <nav v-if="topic.posts.total > topic.posts.size" class="flex justify-center gap-2">
      <NuxtLink v-if="page > 1" :to="{ query: { page: page - 1 } }" class="btn-ghost">上一页</NuxtLink>
      <span class="px-3 py-2 text-sm text-slate-400">第 {{ page }} 页 / 共 {{ Math.ceil(topic.posts.total / topic.posts.size) }} 页</span>
      <NuxtLink v-if="page * topic.posts.size < topic.posts.total" :to="{ query: { page: page + 1 } }" class="btn-ghost">下一页</NuxtLink>
    </nav>

    <section v-if="auth.isAuthenticated && !topic.locked" class="space-y-3 rounded border border-white/10 bg-white/5 p-4">
      <h2 class="font-semibold">回复</h2>
      <p v-if="!auth.user?.emailVerified" class="text-sm text-amber-300">请先验证邮箱后再发言。</p>
      <template v-else>
        <textarea v-model="content" rows="5" class="input" maxlength="20000" placeholder="写下你的回复…" />
        <p v-if="replyError" class="text-sm text-red-300">{{ replyError }}</p>
        <button class="btn" :disabled="submitting" @click="submitReply">{{ submitting ? '发送中…' : '发布回复' }}</button>
      </template>
    </section>
    <p v-else-if="!auth.isAuthenticated" class="text-sm text-slate-400">
      <NuxtLink to="/login" class="text-brand-300">登录</NuxtLink> 后参与讨论。
    </p>
  </article>
  <p v-else class="text-slate-400">{{ error ? '主题不存在或已删除。' : '加载中…' }}</p>
</template>
```

`PostCard.vue`：楼层号 `#{{ floor }}`、作者、时间、`<p class="whitespace-pre-wrap break-words">{{ content }}</p>`（绝不使用 `v-html`）。若 `replyToPostId` 非空显示“回复 #楼层”（MVP 不反查楼层号时显示“引用了某楼层”）。

- [ ] **Step 2: 手工验证**

回帖后楼层 +1、计数刷新；未验证账号看到提示。

- [ ] **Step 3: typecheck + 提交**

```bash
git commit -m "feat(web): add topic detail and reply"
```

---

### Task 14: 发主题页

**Files:**
- Create: `frontend/pages/topics/new.vue`
- Modify: `frontend/middleware/auth.ts`（无改动则复用）

- [ ] **Step 1: 页面**

```vue
<script setup lang="ts">
definePageMeta({ middleware: 'auth' })

import { forumApi, type CategoryNode } from '~/lib/forum'
import { apiErrorMessage } from '~/lib/api'

const { data: categories } = await useAsyncData('new-topic-categories', () => forumApi.categories())
const flat = computed(() => (categories.value ?? []).flatMap(c => [c, ...c.children]))

const categoryId = ref<number | null>(null)
const title = ref('')
const content = ref('')
const tags = ref('')
const error = ref('')
const submitting = ref(false)

const selected = computed(() => flat.value.find(c => c.id === categoryId.value))
const tagList = computed(() => tags.value.split(/[,，\s]+/).map(t => t.trim()).filter(Boolean))
const tagCountValid = computed(() => {
  const c = selected.value
  if (!c) return true
  return tagList.value.length >= c.minTags && tagList.value.length <= c.maxTags
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
    const topic = await forumApi.createTopic({
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
    <h1 class="text-2xl font-bold">发主题</h1>
    <label class="block text-sm">分区
      <select v-model.number="categoryId" class="input mt-1">
        <option :value="null" disabled>选择分区</option>
        <option v-for="c in flat" :key="c.id" :value="c.id">
          {{ c.parentId ? `　└ ${c.name}` : c.name }}
        </option>
      </select>
    </label>
    <input v-model="title" class="input" maxlength="160" placeholder="标题（4–160 字）" />
    <textarea v-model="content" rows="12" class="input" maxlength="20000" placeholder="正文（纯文本，保留换行）" />
    <input v-model="tags" class="input" placeholder="标签，用逗号或空格分隔" />
    <p v-if="selected" class="text-xs text-slate-400">
      该分区标签数：{{ selected.minTags }}–{{ selected.maxTags }}，当前 {{ tagList.length }}
    </p>
    <p v-if="error" class="text-sm text-red-300">{{ error }}</p>
    <button class="btn" :disabled="submitting">{{ submitting ? '发布中…' : '发布主题' }}</button>
  </form>
</template>
```

注意：`CategoryNode` 前端类型补 `parentId` 字段（后端返回时可加 `parentId`；若不加，前端 `flat` 用现有 children 结构即可，模板里 `c.parentId` 改为子分区判断——实现时统一：`CategoryNode` 增加 `parentId: number | null`，后端 DTO 同步返回）。

- [ ] **Step 2: typecheck + 手工验证**

发布后跳转主题页；分区计数 +1；`/forum/{slug}` 顶部出现新主题。

- [ ] **Step 3: 提交**

```bash
git commit -m "feat(web): add topic composer"
```

---

### Task 15: E2E 黄金路径与全量验证

**Files:**
- Create: `frontend/e2e/forum.spec.ts`
- Create: `frontend/e2e/helpers.ts`（复用 Mailpit 流程）
- Modify: `frontend/e2e/auth.spec.ts`（改为引用 helper，行为不变）

- [ ] **Step 1: 抽取 helper**

```ts
import { expect, type Page, type APIRequestContext } from '@playwright/test'

export async function registerVerifyLogin(page: Page, request: APIRequestContext, prefix: string) {
  const email = `${prefix}-${Date.now()}@example.com`
  const password = 'secret123'
  await page.goto('/register')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('昵称').fill('E2E')
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '注册' }).click()

  const list = await request.get('http://localhost:8025/api/v1/messages')
  const messages = (await list.json()).messages as Array<{ ID: string; To: Array<{ Address: string }> }>
  const message = messages.find(m => m.To.some(t => t.Address === email))!
  const detail = await request.get(`http://localhost:8025/api/v1/message/${message.ID}`)
  const body = JSON.stringify(await detail.json())
  const token = /token=([A-Za-z0-9_-]+)/.exec(body)![1]

  await page.goto(`/verify-email?token=${token}`)
  await expect(page.getByText('邮箱验证成功')).toBeVisible()

  await page.goto('/login')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/me$/)
  return email
}
```

（若现有页面 label 文案不同，以页面实际 `getByLabel` 可访问名称为准；`auth.spec.ts` 现有选择器即权威来源。）

- [ ] **Step 2: 写 forum E2E**

```ts
test('verified user posts a topic and replies', async ({ page, request }) => {
  await registerVerifyLogin(page, request, 'forum')

  await page.goto('/forum')
  await expect(page.getByRole('heading', { name: '论坛' })).toBeVisible()

  await page.goto('/topics/new')
  await page.getByLabel('分区').selectOption({ label: '综合讨论' })
  await page.getByPlaceholder('标题（4–160 字）').fill(`E2E 主题 ${Date.now()}`)
  await page.getByPlaceholder('正文（纯文本，保留换行）').fill('这是 E2E 正文')
  await page.getByPlaceholder('标签，用逗号或空格分隔').fill('E2E, 测试')
  await page.getByRole('button', { name: '发布主题' }).click()

  await expect(page).toHaveURL(/\/topics\/\d+$/)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('E2E 主题')
  await expect(page.getByText('这是 E2E 正文')).toBeVisible()

  await page.getByPlaceholder('写下你的回复…').fill('E2E 回复')
  await page.getByRole('button', { name: '发布回复' }).click()
  await expect(page.getByText('E2E 回复')).toBeVisible()
  await expect(page.getByText('#2')).toBeVisible()
})
```

- [ ] **Step 3: 运行后端与前端，跑 E2E**

确保后端 8080、前端 3001、Mailpit 8025 在跑；若 Playwright 配置固定 3000，用环境变量或临时改 `baseURL`：

Run: `cd frontend && PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test`
（若 `playwright.config.ts` 未读取该变量，改为直接把 `baseURL` 设为 `http://localhost:3001`，并在 `webServer.command` 使用 `npx nuxt dev --port 3001`、`reuseExistingServer: true`。）

Expected: `auth.spec.ts` + `forum.spec.ts` 全部 PASS。

- [ ] **Step 4: 全量验证**

```bash
cd backend && mvn verify
cd ../frontend && npm run test:unit && npm run typecheck && npm run build
```

Expected: 后端全绿（原 101 + 新增社区测试）；前端单测/类型/构建通过。

- [ ] **Step 5: 提交**

```bash
git commit -m "test(web): cover forum golden path end to end"
```

---

## 完成定义

- 匿名可读分区/主题/楼层；未验证用户写入返回 40302；已验证用户可发主题/回帖。
- 分页无重复漏项；楼层号唯一（并发测试覆盖）；分区/主题计数与最后回复正确。
- 正文按纯文本渲染，无 `v-html`，杜绝 XSS。
- 后端 `mvn verify`、前端单测/typecheck/build、Playwright 全绿。
- 不提交任何密钥；不引入游戏本体/磁力链接相关字段。

## 后续（不在本切片）

热门榜与标签/用户页、upvote/声望、关注/feed、版主工具、举报工单、通知、编辑历史、Markdown 渲染、搜索、媒体上传。
