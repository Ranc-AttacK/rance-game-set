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
