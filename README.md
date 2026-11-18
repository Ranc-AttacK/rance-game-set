# Galgame 社区

中文 Galgame / 视觉小说垂直社区：作品词条 + 合规资源索引 + 分区论坛。

- 设计文档：`docs/superpowers/specs/2026-11-16-galgame-community-design.md`
- 实现计划：`docs/superpowers/plans/`

## 本地开发

前置：JDK 21、Maven 3.9+、Docker Desktop。

```bash
docker compose up -d
mvn -f backend/pom.xml spring-boot:run
```

停止服务：`docker compose down`；清空数据卷：`docker compose down -v`。

- API: http://localhost:8080/api/v1
- Swagger: http://localhost:8080/swagger-ui.html
- Mailpit（MailHog 兼容）: http://localhost:8025（SMTP: localhost:1025）
- MinIO Console: http://localhost:9001（minioadmin/minioadmin）

## 前端开发

```bash
cd frontend
npm install
npm run dev
```

- Web: http://localhost:3000
- API 通过 Nuxt 同源代理到 `http://localhost:8080/api/v1`
- 前端依赖后端运行；验证邮件在 Mailpit（http://localhost:8025）
- 单测：`npm run test:unit`
- E2E：`npm run test:e2e`（需 docker compose、后端、Mailpit 运行）

## 认证接口速查

```bash
# 注册（返回 201，并写入 Mailpit 验证邮件）
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"player@example.com","nickname":"Player","password":"secret123"}'

# 从 Mailpit (http://localhost:8025) 复制 token，然后：
curl -s -X POST http://localhost:8080/api/v1/auth/verify-email \
  -H 'Content-Type: application/json' \
  -d '{"token":"<mailpit-token>"}'

# 登录（响应含 accessToken，Set-Cookie 含 refresh_token）
curl -s -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"player@example.com","password":"secret123"}'

# 当前用户
curl -s http://localhost:8080/api/v1/users/me \
  -H 'Authorization: Bearer <accessToken>'
```

## 生产部署

单机 Docker Compose（Nginx + Nuxt + Spring Boot + MySQL/Redis/MinIO，外部 SMTP）：
见 [deploy/README.md](deploy/README.md)。

## 测试

```bash
mvn -f backend/pom.xml verify
```

测试使用 Testcontainers，需要 Docker 正常运行。
