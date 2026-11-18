# 生产部署手册（单机 Docker Compose）

面向一台 Linux 服务器：Nginx 终止 TLS 并反代 Nuxt 与后端，MySQL / Redis / MinIO 仅在内网，邮件走外部 SMTP。

## 前置条件

- 已安装 Docker Engine 与 Compose v2（`docker compose version`）
- 域名 A/AAAA 记录指向服务器；公网开放 80/443
- 服务器能拉取 Docker Hub 镜像（国内可配置镜像加速）
- 服务器时间同步（TLS 与 JWT 依赖正确时间）

## 首次部署

### 1. 准备环境变量

```bash
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env，填写域名、数据库/Redis 密码、SMTP 账号
```

### 2. 生成 JWT 密钥

后端通过 Docker secrets 读取密钥文件，私钥不会出现在环境变量或日志里。

```bash
cd deploy
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt-private.pem
openssl rsa -pubout -in secrets/jwt-private.pem -out secrets/jwt-public.pem
# 容器以 UID/GID 10001 运行，密钥需对该用户可读
sudo chown 10001:10001 secrets/jwt-private.pem secrets/jwt-public.pem
chmod 400 secrets/jwt-private.pem secrets/jwt-public.pem
cd ..
```

生产环境设置了 `JWT_REQUIRE_CONFIGURED_KEYS=true`：密钥缺失时后端会直接启动失败，而不是生成重启即失效的临时密钥。

### 3. 申请 TLS 证书（首次，standalone）

此时 Nginx 尚未启动，80 端口空闲：

```bash
docker run --rm -p 80:80 \
  -v "$PWD/deploy/certbot/conf:/etc/letsencrypt" \
  certbot/certbot certonly --standalone \
  -d gal.example.com --agree-tos -m you@example.com --no-eff-email
```

> 将 `gal.example.com` 换成 `deploy/.env` 中的 `DOMAIN`。

### 4. 构建并启动

```bash
docker compose -f deploy/docker-compose.prod.yml up -d --build
docker compose -f deploy/docker-compose.prod.yml ps
```

- 首次启动 MySQL 后 Flyway 自动建表（V0–V3，含社区分区种子）。
- Nuxt SSR 通过 `data-net` 之外的 edge-net 直连 `http://backend:8080`（`NUXT_API_INTERNAL_BASE`，compose 已默认配置）。
- 后端健康检查使用 `/actuator/health/liveness`，就绪后 Nginx 才会启动。
- 验证：`curl -I https://gal.example.com` 返回 200。
- 后端镜像默认使用阿里云 Maven 镜像。若服务器在海外，可改用官方源：
  `docker compose -f deploy/docker-compose.prod.yml build --build-arg MAVEN_SETTINGS=docker/maven-settings-central.xml`。

### 5. 创建管理员

1. 在站点注册账号并**完成邮箱验证**（未验证账号不会被提升）。
2. 在 `deploy/.env` 设置 `APP_BOOTSTRAP_ADMIN_EMAIL=你的邮箱`。
3. 应用配置：

```bash
docker compose -f deploy/docker-compose.prod.yml up -d backend
docker compose -f deploy/docker-compose.prod.yml logs backend | grep -i admin
```

启动日志出现 `Promoted bootstrap admin account ...` 即生效。引导逻辑只在系统中还没有管理员时提升一次；完成后请**立即从 `deploy/.env` 删除该变量**并再次 `up -d backend`，避免长期保留提权入口。

## 日常运维

```bash
# 查看状态 / 日志
docker compose -f deploy/docker-compose.prod.yml ps
docker compose -f deploy/docker-compose.prod.yml logs -f backend

# 升级（拉取代码后）
git pull
docker compose -f deploy/docker-compose.prod.yml up -d --build

# 回滚：切回上一个 tag 后重新构建
git checkout <previous-tag>
docker compose -f deploy/docker-compose.prod.yml up -d --build
```

### 备份

```bash
# MySQL（逻辑备份，可安全在线执行）
docker compose -f deploy/docker-compose.prod.yml exec -T mysql \
  sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers gal' \
  | gzip > "gal-$(date +%F).sql.gz"
```

> MinIO/媒体存储在引入上传功能（P2）后再加入编排，届时需要一并纳入备份。

建议：每日备份并保留 14 天，定期做一次恢复演练。恢复 MySQL 示例：

```bash
gunzip -c gal-2026-11-18.sql.gz | docker compose -f deploy/docker-compose.prod.yml exec -T mysql \
  sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" gal'
```

### 证书续期

`certbot` 服务每 12 小时执行一次 `certbot renew`，通过 Nginx 的 `/.well-known/acme-challenge/`（webroot 模式）完成校验。续期后需让 Nginx 重新加载证书，建议加一条主机 cron：

```cron
0 4 * * 1 docker compose -f /opt/galgame/rance_game_set/deploy/docker-compose.prod.yml exec -T nginx nginx -s reload
```

验证续期流程：

```bash
docker compose -f deploy/docker-compose.prod.yml run --rm --entrypoint certbot certbot renew --dry-run
```

## 安全基线

- 只有 80/443 对公网开放；MySQL/Redis 不映射宿主端口，且位于 `internal: true` 的 `data-net`，边缘容器无法直接访问数据层。
- `APP_TRUSTED_PROXIES` 只包含 Nginx 的内网地址 `172.28.0.10`，其他来源伪造的 `X-Real-IP` 会被忽略。
- 后端只信任 `/run/secrets` 下的 JWT 密钥；`deploy/.env`、`deploy/secrets/`、证书目录已加入 `.gitignore`。
- 所有应用容器启用 `no-new-privileges`，后端以 UID 10001 非 root 运行。
- 密码类变量仍通过环境变量注入，`docker inspect` 可见；单机部署下依赖宿主权限与网络隔离缓解，后续可改用 secret 文件。
- 上线中国大陆需完成 ICP 备案与实名制合规确认。

## 已知待办（P2 之前）

- 邮箱未验证用户目前可登录浏览，但不能发布内容（写入限制随论坛模块落地）。
- 管理员 2FA、Nginx 微缓存、CSP 细化、监控告警按设计文档 P7 实施。
