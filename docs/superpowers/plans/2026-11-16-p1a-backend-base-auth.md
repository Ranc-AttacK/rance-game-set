# P1-A 后端基座与认证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建成可运行的 Spring Boot 模块化单体基座，完成注册、邮箱验证、登录、刷新、找回密码、资料与注销的完整闭环。

**Architecture:** 单个 Spring Boot 3.3 应用，`shared` 为开放基础模块，`identity` 为第一个业务模块；Spring Modulith 校验模块边界；Spring Security + RS256 JWT（access 内存态、refresh httpOnly Cookie）；MySQL 存用户与令牌，Redis 限流，Mailpit（MailHog 兼容）承接开发邮件。

**Tech Stack:** Java 21、Spring Boot 3.3.6、Spring Modulith 1.2.6、Spring Data JPA、Flyway、Spring Security OAuth2 Resource Server、MySQL 8、Redis 7、Mailpit（MailHog 兼容）、Testcontainers、ArchUnit、springdoc-openapi。

> 本计划只覆盖 **P1-A 后端**。P1-B（Nuxt 前端认证页面与 E2E）在 backend 全绿后另写一份计划。

---

## 前置条件

- JDK 21、Maven 3.9+、Docker Desktop（Testcontainers 与 Compose 都需要）
- 当前仓库根目录：`rance_game_set`
- 约定：所有命令在仓库根目录执行；后端路径统一用 `backend/`

## 文件结构（本计划结束后）

```
backend/
├── pom.xml
├── src/main/java/com/rance/galcommunity/
│   ├── GalCommunityApplication.java
│   ├── shared/
│   │   ├── package-info.java
│   │   ├── api/{ApiResponse,PageResponse,ErrorCode,BusinessException,GlobalExceptionHandler}.java
│   │   ├── web/{TraceIdFilter,ClientIp}.java
│   │   ├── ratelimit/RateLimitService.java
│   │   ├── security/PasswordPolicy.java
│   │   └── util/Hashing.java
│   └── identity/
│       ├── package-info.java
│       ├── domain/{UserAccount,Role,AccountStatus,RefreshToken,EmailVerification,PasswordReset,EmailOutboxEntry,EmailOutboxStatus,EmailTemplate}.java
│       ├── repo/{UserAccountRepository,RefreshTokenRepository,EmailVerificationRepository,PasswordResetRepository,EmailOutboxRepository}.java
│       ├── api/{IdentityApi,UserSummary}.java
│       ├── service/{AuthService,TokenService,UserService,EmailOutboxService,EmailOutboxProcessor}.java
│       ├── mail/{EmailSender,SmtpEmailSender,EmailOutboxScheduler}.java
│       ├── security/{AppProperties,JwtKeyProvider,JwtConfig,SecurityConfig,RestAuthEntryPoint,RestAccessDeniedHandler,CurrentUser,OriginGuard,RefreshCookie}.java
│       └── web/
│           ├── {AuthController,UserController}.java
│           └── dto/{RegisterRequest,LoginRequest,VerifyEmailRequest,ForgotPasswordRequest,ResetPasswordRequest,UpdateProfileRequest,LoginResponse,UserProfileResponse,PublicUserProfile}.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/V1__identity.sql
└── src/test/
    ├── java/com/rance/galcommunity/
    │   ├── IntegrationTestBase.java
    │   ├── GalCommunityApplicationTests.java
    │   ├── architecture/{ModuleStructureTest,ArchitectureRulesTest}.java
    │   ├── support/{ProbeController,ProbeSecureController}.java
    │   └── identity/{SharedApiTest,RateLimitIT,IdentitySchemaIT,SecurityIT,RegistrationIT,EmailDispatchIT,VerifyEmailIT,LoginIT,RefreshIT,PasswordResetIT,ProfileIT,IdentityApiIT,ApiDocsIT}.java
    └── resources/application-test.yml
docker-compose.yml
README.md
.github/workflows/ci.yml
```

---

### Task 1: 后端骨架与测试基座

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/rance/galcommunity/GalCommunityApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/SecurityConfig.java`（临时放行版，Task 7 替换）
- Create: `backend/src/test/java/com/rance/galcommunity/IntegrationTestBase.java`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/java/com/rance/galcommunity/GalCommunityApplicationTests.java`

- [ ] **Step 1: 写失败的应用上下文测试**

`backend/src/test/java/com/rance/galcommunity/GalCommunityApplicationTests.java`
```java
package com.rance.galcommunity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class GalCommunityApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoadsAndHealthEndpointWorks() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=GalCommunityApplicationTests
```
Expected: BUILD FAILURE（`backend/pom.xml` 不存在）

- [ ] **Step 3: 写 pom.xml**

`backend/pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.6</version>
        <relativePath/>
    </parent>

    <groupId>com.rance</groupId>
    <artifactId>gal-community</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>gal-community</name>
    <description>Galgame vertical community</description>

    <properties>
        <java.version>21</java.version>
        <spring-modulith.version>1.2.6</spring-modulith.version>
        <springdoc.version>2.6.0</springdoc.version>
        <archunit.version>1.3.0</archunit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.modulith</groupId>
                <artifactId>spring-modulith-bom</artifactId>
                <version>${spring-modulith.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-mail</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <includes>
                        <include>**/*Test.java</include>
                        <include>**/*Tests.java</include>
                        <include>**/*IT.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: 写应用入口与配置**

`backend/src/main/java/com/rance/galcommunity/GalCommunityApplication.java`
```java
package com.rance.galcommunity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GalCommunityApplication {
    public static void main(String[] args) {
        SpringApplication.run(GalCommunityApplication.class, args);
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/security/SecurityConfig.java`（临时放行版，Task 7 替换）
```java
package com.rance.galcommunity.identity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain temporaryPermitAll(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

`backend/src/main/resources/application.yml`
```yaml
spring:
  application:
    name: gal-community
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:gal}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
    username: ${DB_USER:gal}
    password: ${DB_PASSWORD:galpass}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    properties:
      mail:
        smtp:
          auth: false
  modulith:
    events:
      jdbc:
        schema-initialization:
          enabled: true

server:
  port: 8080
  error:
    include-message: never

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: never

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

app:
  base-url: ${APP_BASE_URL:http://localhost:5173}
  allowed-origins: ${APP_ALLOWED_ORIGINS:http://localhost:5173}
  jwt:
    private-key: ${JWT_PRIVATE_KEY:}
    public-key: ${JWT_PUBLIC_KEY:}
    issuer: gal-community
    access-ttl: PT30M
    refresh-ttl: P7D
  cookie:
    secure: ${COOKIE_SECURE:false}
  email:
    dispatch-enabled: ${EMAIL_DISPATCH_ENABLED:true}
    poll-interval-ms: 5000

logging:
  pattern:
    level: "%5p [%X{traceId:-}]"
```

`backend/src/test/resources/application-test.yml`
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  mail:
    host: localhost
    port: 1025

app:
  base-url: http://localhost:5173
  allowed-origins:
    - http://localhost:5173
  jwt:
    private-key: ""
    public-key: ""
    issuer: gal-community
    access-ttl: PT30M
    refresh-ttl: P7D
  cookie:
    secure: false
  email:
    dispatch-enabled: false
    poll-interval-ms: 5000
```

- [ ] **Step 5: 写测试基座**

`backend/src/test/java/com/rance/galcommunity/IntegrationTestBase.java`
```java
package com.rance.galcommunity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("gal")
            .withUsername("gal")
            .withPassword("galpass");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;
}
```

- [ ] **Step 6: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=GalCommunityApplicationTests
```
Expected: BUILD SUCCESS，`Tests run: 1, Failures: 0`。（首次会启动 MySQL/Redis 容器，较慢）

- [ ] **Step 7: 提交**

```bash
git add backend/pom.xml backend/src
git commit -m "feat(backend): scaffold Spring Boot monolith with testcontainers base"
```

---

### Task 2: 本地基础设施 Compose

**Files:**
- Create: `docker-compose.yml`
- Modify: `README.md`

- [ ] **Step 1: 写 Compose 配置**

`docker-compose.yml`
```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: gal-mysql
    environment:
      MYSQL_DATABASE: gal
      MYSQL_USER: gal
      MYSQL_PASSWORD: galpass
      MYSQL_ROOT_PASSWORD: rootpass
      TZ: UTC
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-prootpass"]
      interval: 5s
      timeout: 3s
      retries: 20

  redis:
    image: redis:7-alpine
    container_name: gal-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20

  minio:
    image: minio/minio:latest
    container_name: gal-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data

  # Mailpit is a MailHog-compatible dev SMTP sink (same 1025/8025 ports).
  # It replaces MailHog because the MailHog image pull stalls on the available registry mirror.
  mailpit:
    image: axllent/mailpit:latest
    container_name: gal-mailpit
    ports:
      - "1025:1025"
      - "8025:8025"

volumes:
  mysql-data:
  minio-data:
```

- [ ] **Step 2: 校验 Compose 文件**

Run:
```bash
docker compose config --quiet
```
Expected: 无输出、退出码 0

- [ ] **Step 3: 启动并检查健康状态**

Run:
```bash
docker compose up -d
docker compose ps
```
Expected: mysql/redis/minio 显示 `healthy`（或运行中）；mailpit 运行中

- [ ] **Step 4: 写 README 开发说明**

`README.md`
```markdown
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

- API: http://localhost:8080/api/v1
- Swagger: http://localhost:8080/swagger-ui.html
- Mailpit（MailHog 兼容）: http://localhost:8025
- MinIO Console: http://localhost:9001（minioadmin/minioadmin）

## 测试

```bash
mvn -f backend/pom.xml verify
```

测试使用 Testcontainers，需要 Docker 正常运行。
```

- [ ] **Step 5: 提交**

```bash
git add docker-compose.yml README.md
git commit -m "chore: add local infra compose and dev readme"
```

---

### Task 3: 统一响应、错误码与链路 ID

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/shared/api/ApiResponse.java`
- Create: `backend/src/main/java/com/rance/galcommunity/shared/api/PageResponse.java`
- Create: `backend/src/main/java/com/rance/galcommunity/shared/api/ErrorCode.java`
- Create: `backend/src/main/java/com/rance/galcommunity/shared/api/BusinessException.java`
- Create: `backend/src/main/java/com/rance/galcommunity/shared/api/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/rance/galcommunity/shared/web/TraceIdFilter.java`
- Create: `backend/src/test/java/com/rance/galcommunity/support/ProbeController.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/SharedApiTest.java`

- [ ] **Step 1: 写失败的统一响应测试**

`backend/src/test/java/com/rance/galcommunity/support/ProbeController.java`
```java
package com.rance.galcommunity.support;

import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/probe")
public class ProbeController {

    public record ProbeRequest(@NotBlank String name) {
    }

    @PostMapping("/validation")
    public ApiResponse<Void> validation(@Valid @RequestBody ProbeRequest request) {
        return ApiResponse.ok();
    }

    @GetMapping("/business")
    public ApiResponse<Void> business() {
        throw new BusinessException(ErrorCode.BUSINESS_RULE, "业务规则不满足");
    }

    @GetMapping("/boom")
    public ApiResponse<Void> boom() {
        throw new IllegalStateException("boom");
    }
}
```

`backend/src/test/java/com/rance/galcommunity/identity/SharedApiTest.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.support.ProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

@Import(ProbeController.class)
@WithMockUser
class SharedApiTest extends IntegrationTestBase {

    @Test
    void validationErrorUsesEnvelopeAndTraceId() throws Exception {
        var result = mockMvc.perform(post("/test/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        String bodyTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isEqualTo(bodyTraceId);
    }

    @Test
    void businessErrorUses422() throws Exception {
        mockMvc.perform(get("/test/probe/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void unexpectedErrorIsMasked() throws Exception {
        mockMvc.perform(get("/test/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=SharedApiTest
```
Expected: BUILD FAILURE（`ApiResponse` 等类不存在）

- [ ] **Step 3: 写统一响应与错误码**

`backend/src/main/java/com/rance/galcommunity/shared/api/ApiResponse.java`
```java
package com.rance.galcommunity.shared.api;

import org.slf4j.MDC;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, MDC.get("traceId"));
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, MDC.get("traceId"));
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(errorCode.code(), message, data, MDC.get("traceId"));
    }
}
```

`backend/src/main/java/com/rance/galcommunity/shared/api/ErrorCode.java`
```java
package com.rance.galcommunity.shared.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(40001, HttpStatus.BAD_REQUEST),
    INVALID_CURSOR(40002, HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(40101, HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40102, HttpStatus.UNAUTHORIZED),
    TOKEN_REUSED(40103, HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED(40302, HttpStatus.FORBIDDEN),
    NOT_FOUND(40401, HttpStatus.NOT_FOUND),
    CONFLICT(40901, HttpStatus.CONFLICT),
    FILE_TOO_LARGE(41301, HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA(41501, HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    BUSINESS_RULE(42201, HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL(50001, HttpStatus.INTERNAL_SERVER_ERROR),
    UNAVAILABLE(50301, HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final HttpStatus status;

    ErrorCode(int code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
```

`backend/src/main/java/com/rance/galcommunity/shared/api/BusinessException.java`
```java
package com.rance.galcommunity.shared.api;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

`backend/src/main/java/com/rance/galcommunity/shared/api/PageResponse.java`
```java
package com.rance.galcommunity.shared.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }
}
```

- [ ] **Step 4: 写全局异常处理与链路 ID**

`backend/src/main/java/com/rance/galcommunity/shared/api/GlobalExceptionHandler.java`
```java
package com.rance.galcommunity.shared.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code, "参数校验失败", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, "请求体格式错误"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException ex) {
        ErrorCode code = ErrorCode.CONFLICT;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, "数据冲突，请稍后重试"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled error traceId={}", MDC.get("traceId"), ex);
        ErrorCode code = ErrorCode.INTERNAL;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, "服务器内部错误"));
    }
}
```

`backend/src/main/java/com/rance/galcommunity/shared/web/TraceIdFilter.java`
```java
package com.rance.galcommunity.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Pattern VALID_TRACE = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader("X-Request-Id");
        String traceId = incoming != null && VALID_TRACE.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=SharedApiTest
```
Expected: BUILD SUCCESS，3 个测试通过

- [ ] **Step 6: 提交**

```bash
git add backend/src
git commit -m "feat(shared): add API envelope, error codes and trace id filter"
```

---

### Task 4: 密码策略、哈希与限流

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/shared/security/PasswordPolicy.java`
- Create: `backend/src/main/java/com/rance/galcommunity/shared/util/Hashing.java`
- Create: `backend/src/main/java/com/rance/galcommunity/shared/ratelimit/RateLimitService.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/RateLimitIT.java`

- [ ] **Step 1: 写失败的测试**

`backend/src/test/java/com/rance/galcommunity/identity/RateLimitIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.ratelimit.RateLimitService;
import com.rance.galcommunity.shared.security.PasswordPolicy;
import com.rance.galcommunity.shared.util.Hashing;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RateLimitIT extends IntegrationTestBase {

    @Autowired
    RateLimitService rateLimitService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void allowsUpToLimitThenBlocks() {
        String key = "test:" + UUID.randomUUID();
        assertThat(rateLimitService.tryAcquire(key, 2, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimitService.tryAcquire(key, 2, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimitService.tryAcquire(key, 2, Duration.ofMinutes(1))).isFalse();
        assertThat(redisTemplate.getExpire(key)).isGreaterThan(0);
    }

    @Test
    void passwordPolicyRejectsWeakPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("onlyletters"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("密".repeat(30) + "1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void passwordPolicyAcceptsLettersAndDigits() {
        PasswordPolicy.validate("secret123");
        PasswordPolicy.validate("密码12345678");
    }

    @Test
    void hashingIsStableAndTokensAreUnique() {
        assertThat(Hashing.sha256("abc")).isEqualTo(Hashing.sha256("abc"));
        assertThat(Hashing.sha256("abc")).hasSize(64);
        assertThat(Hashing.randomToken()).isNotEqualTo(Hashing.randomToken());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=RateLimitIT
```
Expected: BUILD FAILURE（相关类不存在）

- [ ] **Step 3: 写实现**

`backend/src/main/java/com/rance/galcommunity/shared/security/PasswordPolicy.java`
```java
package com.rance.galcommunity.shared.security;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null) {
            throw invalid();
        }
        int byteLength = rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        // BCrypt only considers the first 72 bytes, so enforce the limit in bytes.
        if (rawPassword.length() < 8 || byteLength > 72 || !hasLetter || !hasDigit) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, "密码需 8-72 位且同时包含字母和数字");
    }
}
```

`backend/src/main/java/com/rance/galcommunity/shared/util/Hashing.java`
```java
package com.rance.galcommunity.shared.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

public final class Hashing {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Hashing() {
    }

    public static String sha256(String value) {
        Objects.requireNonNull(value, "value");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

`backend/src/main/java/com/rance/galcommunity/shared/ratelimit/RateLimitService.java`
```java
package com.rance.galcommunity.shared.ratelimit;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Fixed-window rate limiter. INCR + PEXPIRE are atomic via one Lua script.
 * Fail-closed: if Redis is unavailable, throws BusinessException(UNAVAILABLE)
 * so auth flows return 503 instead of silently disabling brute-force protection.
 */
@Service
public class RateLimitService {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        if (limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
        try {
            Long count = redis.execute(SCRIPT, List.of(key), String.valueOf(window.toMillis()));
            return count != null && count <= limit;
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.UNAVAILABLE, "限流服务暂不可用");
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=RateLimitIT
```
Expected: BUILD SUCCESS，4 个测试通过

- [ ] **Step 5: 提交**

```bash
git add backend/src
git commit -m "feat(shared): add password policy, hashing and redis rate limiting"
```

---

### Task 5: 身份数据模型与迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__identity.sql`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/UserAccount.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/Role.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/AccountStatus.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/RefreshToken.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/EmailVerification.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/PasswordReset.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/EmailOutboxEntry.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/EmailOutboxStatus.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/domain/EmailTemplate.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/repo/UserAccountRepository.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/repo/RefreshTokenRepository.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/repo/EmailVerificationRepository.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/repo/PasswordResetRepository.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/repo/EmailOutboxRepository.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/IdentitySchemaIT.java`

- [ ] **Step 1: 写失败的仓储测试**

`backend/src/test/java/com/rance/galcommunity/identity/IdentitySchemaIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class IdentitySchemaIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Test
    void savesAndFindsUserByEmail() {
        UserAccount user = new UserAccount();
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setNickname("Alice");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(false);
        users.saveAndFlush(user);

        assertThat(users.findByEmail("alice@example.com")).isPresent();
        assertThat(users.findById(user.getId())).isPresent();
        assertThat(user.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void emailMustBeUnique() {
        UserAccount first = new UserAccount();
        first.setEmail("dup@example.com");
        first.setPasswordHash("hash");
        first.setNickname("First");
        first.setRole(Role.USER);
        first.setStatus(AccountStatus.ACTIVE);
        first.setEmailVerified(false);
        users.saveAndFlush(first);

        UserAccount second = new UserAccount();
        second.setEmail("dup@example.com");
        second.setPasswordHash("hash");
        second.setNickname("Second");
        second.setRole(Role.USER);
        second.setStatus(AccountStatus.ACTIVE);
        second.setEmailVerified(false);

        assertThatThrownBy(() -> users.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=IdentitySchemaIT
```
Expected: BUILD FAILURE（实体/仓储不存在，或表不存在）

- [ ] **Step 3: 写迁移 SQL**

`backend/src/main/resources/db/migration/V1__identity.sql`
```sql
CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(40) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    bio VARCHAR(500) NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    email_verified BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id),
    KEY idx_refresh_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_email_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token (token_hash),
    KEY idx_email_verification_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_password_resets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_token (token_hash),
    KEY idx_password_reset_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_email_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    to_email VARCHAR(255) NOT NULL,
    template VARCHAR(40) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_email_outbox_due (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> `token_hash` 使用 `VARCHAR(64)` 以匹配实体 `@Column(length = 64)` 的 JDBC 类型；`CHAR(64)` 会被 Hibernate `ddl-auto=validate` 判为类型不匹配。

- [ ] **Step 4: 写实体、枚举与仓储**

`backend/src/main/java/com/rance/galcommunity/identity/domain/Role.java`
```java
package com.rance.galcommunity.identity.domain;

public enum Role {
    USER,
    ADMIN
}
```

`backend/src/main/java/com/rance/galcommunity/identity/domain/AccountStatus.java`
```java
package com.rance.galcommunity.identity.domain;

public enum AccountStatus {
    ACTIVE,
    BANNED,
    DELETED
}
```

`backend/src/main/java/com/rance/galcommunity/identity/domain/UserAccount.java`
```java
package com.rance.galcommunity.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_accounts")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(length = 500)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/domain/RefreshToken.java`
```java
package com.rance.galcommunity.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;
}
```

`backend/src/main/java/com/rance/galcommunity/identity/domain/EmailVerification.java`
```java
package com.rance.galcommunity.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_email_verifications")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
```

`backend/src/main/java/com/rance/galcommunity/identity/domain/PasswordReset.java`
```java
package com.rance.galcommunity.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_password_resets")
@Getter
@Setter
@NoArgsConstructor
public class PasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
```

`backend/src/main/java/com/rance/galcommunity/identity/domain/EmailOutboxStatus.java`
```java
package com.rance.galcommunity.identity.domain;

public enum EmailOutboxStatus {
    PENDING,
    SENT,
    FAILED
}
```

`backend/src/main/java/com/rance/galcommunity/identity/domain/EmailTemplate.java`
```java
package com.rance.galcommunity.identity.domain;

public enum EmailTemplate {
    VERIFY_EMAIL("验证你的邮箱"),
    RESET_PASSWORD("重置你的密码");

    private final String subject;

    EmailTemplate(String subject) {
        this.subject = subject;
    }

    public String subject() {
        return subject;
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/domain/EmailOutboxEntry.java`
```java
package com.rance.galcommunity.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_email_outbox")
@Getter
@Setter
@NoArgsConstructor
public class EmailOutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EmailTemplate template;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailOutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = EmailOutboxStatus.PENDING;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = Instant.now();
        }
    }

    public void markSent() {
        status = EmailOutboxStatus.SENT;
        payload = Map.of();
    }

    public void markFailed(Instant now) {
        attempts += 1;
        if (attempts >= 5) {
            status = EmailOutboxStatus.FAILED;
        } else {
            nextAttemptAt = now.plusSeconds((long) Math.pow(2, attempts) * 60);
        }
    }
}
```

Repositories:
`backend/src/main/java/com/rance/galcommunity/identity/repo/UserAccountRepository.java`
```java
package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

`backend/src/main/java/com/rance/galcommunity/identity/repo/RefreshTokenRepository.java`
```java
package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
```

`backend/src/main/java/com/rance/galcommunity/identity/repo/EmailVerificationRepository.java`
```java
package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.EmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByTokenHash(String tokenHash);
}
```

`backend/src/main/java/com/rance/galcommunity/identity/repo/PasswordResetRepository.java`
```java
package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.PasswordReset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {
    Optional<PasswordReset> findByTokenHash(String tokenHash);
}
```

`backend/src/main/java/com/rance/galcommunity/identity/repo/EmailOutboxRepository.java`
```java
package com.rance.galcommunity.identity.repo;

import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntry, Long> {
    List<EmailOutboxEntry> findTop20ByStatusAndNextAttemptAtBeforeOrderByIdAsc(
            EmailOutboxStatus status, Instant now);
}
```

> 若 Hibernate `ddl-auto=validate` 对 `payload JSON` 列报类型不匹配：把迁移中的该列改为 `TEXT`，并移除实体上的 `@JdbcTypeCode` 注解。

同时修改 `IntegrationTestBase`，加入每个测试前的状态清理，避免测试间数据与限流串扰：
```java
    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    protected org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.junit.jupiter.api.BeforeEach
    void resetState() {
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        for (String table : java.util.List.of(
                "user_email_outbox", "user_password_resets", "user_email_verifications",
                "user_refresh_tokens", "user_accounts", "event_publication")) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
            } catch (Exception ignored) {
                // 表在早期任务中可能尚不存在
            }
        }
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=IdentitySchemaIT
```
Expected: BUILD SUCCESS，2 个测试通过

- [ ] **Step 6: 提交**

```bash
git add backend/src
git commit -m "feat(identity): add identity schema, entities and repositories"
```

---

### Task 6: 模块边界与架构测试

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/shared/package-info.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/package-info.java`
- Create: `backend/src/test/java/com/rance/galcommunity/architecture/ModuleStructureTest.java`
- Create: `backend/src/test/java/com/rance/galcommunity/architecture/ArchitectureRulesTest.java`

- [ ] **Step 1: 写失败的架构测试**

`backend/src/test/java/com/rance/galcommunity/architecture/ModuleStructureTest.java`
```java
package com.rance.galcommunity.architecture;

import com.rance.galcommunity.GalCommunityApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleStructureTest {

    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(GalCommunityApplication.class).verify();
    }
}
```

`backend/src/test/java/com/rance/galcommunity/architecture/ArchitectureRulesTest.java`
```java
package com.rance.galcommunity.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.rance.galcommunity", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule controllersDoNotTouchRepositories =
            noClasses().that().resideInAPackage("..web..")
                    .should().dependOnClassesThat().resideInAPackage("..repo..");

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles =
            slices().matching("com.rance.galcommunity.(*)..").should().beFreeOfCycles();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=ModuleStructureTest,ArchitectureRulesTest
```
Expected: BUILD FAILURE（尚未声明模块，或规则失败）

- [ ] **Step 3: 声明模块**

`backend/src/main/java/com/rance/galcommunity/shared/package-info.java`
```java
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.rance.galcommunity.shared;

import org.springframework.modulith.ApplicationModule;
```

`backend/src/main/java/com/rance/galcommunity/identity/package-info.java`
```java
@ApplicationModule(allowedDependencies = {"shared"})
package com.rance.galcommunity.identity;

import org.springframework.modulith.ApplicationModule;
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=ModuleStructureTest,ArchitectureRulesTest
```
Expected: BUILD SUCCESS，2 个测试通过

- [ ] **Step 5: 提交**

```bash
git add backend/src
git commit -m "chore(architecture): enforce modulith boundaries and archunit rules"
```

---

### Task 7: JWT 密钥、令牌签发与安全配置

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/AppProperties.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/JwtKeyProvider.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/JwtConfig.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/service/TokenService.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/CurrentUser.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/RestAuthEntryPoint.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/RestAccessDeniedHandler.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/security/SecurityConfig.java`（替换 Task 1 的临时放行版）
- Create: `backend/src/main/java/com/rance/galcommunity/shared/web/ClientIp.java`
- Create: `backend/src/test/java/com/rance/galcommunity/support/ProbeSecureController.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/SecurityIT.java`

- [ ] **Step 1: 写失败的安全测试**

`backend/src/test/java/com/rance/galcommunity/support/ProbeSecureController.java`
```java
package com.rance.galcommunity.support;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProbeSecureController {

    @GetMapping("/test/secure/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("sub", jwt.getSubject(), "roles", jwt.getClaimAsStringList("roles"));
    }

    @GetMapping("/api/v1/admin/probe")
    public Map<String, Object> admin() {
        return Map.of("ok", true);
    }
}
```

`backend/src/test/java/com/rance/galcommunity/identity/SecurityIT.java`
```java
package com.rance.galcommunity.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.service.TokenService;
import com.rance.galcommunity.support.ProbeSecureController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(ProbeSecureController.class)
class SecurityIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    TokenService tokenService;

    private UserAccount createUser(String email, Role role) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setNickname("User");
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        return users.saveAndFlush(user);
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/test/secure/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void acceptsValidToken() throws Exception {
        UserAccount user = createUser("sec1@example.com", Role.USER);
        String token = tokenService.issue(user).value();

        mockMvc.perform(get("/test/secure/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(user.getId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void regularUserCannotAccessAdminEndpoint() throws Exception {
        UserAccount user = createUser("sec2@example.com", Role.USER);
        String token = tokenService.issue(user).value();

        mockMvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        UserAccount admin = createUser("sec3@example.com", Role.ADMIN);
        String token = tokenService.issue(admin).value();

        mockMvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=SecurityIT
```
Expected: BUILD FAILURE（`TokenService`/`AppProperties` 不存在）

- [ ] **Step 3: 写配置属性与 JWT 实现**

`backend/src/main/java/com/rance/galcommunity/identity/security/AppProperties.java`
```java
package com.rance.galcommunity.identity.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        List<String> allowedOrigins,
        Jwt jwt,
        Cookie cookie,
        Email email) {

    public record Jwt(String privateKey, String publicKey, String issuer,
                      Duration accessTtl, Duration refreshTtl) {
    }

    public record Cookie(boolean secure) {
    }

    public record Email(boolean dispatchEnabled, long pollIntervalMs) {
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/security/JwtKeyProvider.java`
```java
package com.rance.galcommunity.identity.security;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String kid;
    private final RSAKey rsaKey;

    public JwtKeyProvider(AppProperties.Jwt props) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            boolean hasPrivate = props.privateKey() != null && !props.privateKey().isBlank();
            boolean hasPublic = props.publicKey() != null && !props.publicKey().isBlank();

            if (hasPrivate) {
                privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(decodePem(props.privateKey())));
                if (hasPublic) {
                    publicKey = (RSAPublicKey) keyFactory.generatePublic(
                            new X509EncodedKeySpec(decodePem(props.publicKey())));
                } else if (privateKey instanceof RSAPrivateCrtKey crt) {
                    publicKey = (RSAPublicKey) keyFactory.generatePublic(
                            new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                } else {
                    throw new IllegalStateException("Public key is required when private key is not CRT format");
                }
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                privateKey = (RSAPrivateKey) pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
                log.warn("JWT keys are not configured; generated ephemeral RSA key. Do not use in production.");
            }

            kid = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()))
                    .substring(0, 16);
            rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT keys", e);
        }
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String kid() {
        return kid;
    }

    private static byte[] decodePem(String pem) {
        String body = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
```

> 生产密钥需使用 PKCS#8 私钥与 X.509 公钥 PEM（`BEGIN PRIVATE KEY` / `BEGIN PUBLIC KEY`）。

`backend/src/main/java/com/rance/galcommunity/identity/security/JwtConfig.java`
```java
package com.rance.galcommunity.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Bean
    JwtKeyProvider jwtKeyProvider(AppProperties props) {
        return new JwtKeyProvider(props.jwt());
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyProvider keyProvider) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keyProvider.rsaKey())));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyProvider keyProvider, AppProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyProvider.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwt().issuer()));
        return decoder;
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/service/TokenService.java`
```java
package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.security.AppProperties;
import com.rance.galcommunity.identity.security.JwtKeyProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtKeyProvider keyProvider;
    private final AppProperties props;

    public TokenService(JwtEncoder encoder, JwtKeyProvider keyProvider, AppProperties props) {
        this.encoder = encoder;
        this.keyProvider = keyProvider;
        this.props = props;
    }

    public IssuedToken issue(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.jwt().accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(user.getRole().name()))
                .claim("nickname", user.getNickname())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyProvider.kid()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, props.jwt().accessTtl().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
```

- [ ] **Step 4: 写安全配置与辅助类**

`backend/src/main/java/com/rance/galcommunity/identity/security/CurrentUser.java`
```java
package com.rance.galcommunity.identity.security;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;

public record CurrentUser(long id, boolean admin) {

    public static CurrentUser of(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new CurrentUser(Long.parseLong(jwt.getSubject()),
                roles != null && roles.contains("ADMIN"));
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/security/RestAuthEntryPoint.java`
```java
package com.rance.galcommunity.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = ErrorCode.UNAUTHENTICATED;
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, "请先登录"));
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/security/RestAccessDeniedHandler.java`
```java
package com.rance.galcommunity.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode code = ErrorCode.FORBIDDEN;
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, "没有权限"));
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/security/SecurityConfig.java`
```java
package com.rance.galcommunity.identity.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                            RestAuthEntryPoint authEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(authEntryPoint))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

`backend/src/main/java/com/rance/galcommunity/shared/web/ClientIp.java`
```java
package com.rance.galcommunity.shared.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=SecurityIT
```
Expected: BUILD SUCCESS，4 个测试通过

- [ ] **Step 6: 提交**

```bash
git add backend/src
git commit -m "feat(identity): add RS256 JWT, security rules and current-user plumbing"
```

---

### Task 8: 注册与验证邮件入队

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/identity/service/EmailOutboxService.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/service/AuthService.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/AuthController.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/UserProfileResponse.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/PublicUserProfile.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/RegistrationIT.java`

- [ ] **Step 1: 写失败的注册测试**

`backend/src/test/java/com/rance/galcommunity/identity/RegistrationIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.EmailVerificationRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.shared.util.Hashing;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RegistrationIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    EmailOutboxRepository outbox;

    @Autowired
    EmailVerificationRepository verifications;

    @Test
    void registersUserAndQueuesVerificationEmail() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "Alice@Example.com",
                                "nickname", "Alice",
                                "password", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("alice@example.com");
        assertThat(users.findByEmail("alice@example.com")).isPresent();

        EmailOutboxEntry email = outbox.findAll().get(0);
        assertThat(email.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        String rawToken = String.valueOf(email.getPayload().get("token"));
        assertThat(rawToken).isNotBlank();
        assertThat(verifications.findByTokenHash(Hashing.sha256(rawToken))).isPresent();
        assertThat(verifications.findAll().get(0).getTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    void duplicateEmailReturns40901() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "dup@example.com", "nickname", "Dup", "password", "secret123"));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void weakPasswordReturns40001() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "weak@example.com",
                                "nickname", "Weak",
                                "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=RegistrationIT
```
Expected: BUILD FAILURE（接口/服务不存在，404）

- [ ] **Step 3: 写 DTO 与邮件入队服务**

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/RegisterRequest.java`
```java
package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 2, max = 40) String nickname,
        @NotBlank String password) {
}
```

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/UserProfileResponse.java`
```java
package com.rance.galcommunity.identity.web.dto;

import com.rance.galcommunity.identity.domain.UserAccount;

public record UserProfileResponse(
        long id,
        String email,
        String nickname,
        String avatarUrl,
        String bio,
        boolean emailVerified,
        String role,
        java.time.Instant createdAt) {

    public static UserProfileResponse self(UserAccount user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getNickname(),
                user.getAvatarUrl(), user.getBio(), user.isEmailVerified(),
                user.getRole().name(), user.getCreatedAt());
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/PublicUserProfile.java`
```java
package com.rance.galcommunity.identity.web.dto;

import com.rance.galcommunity.identity.domain.UserAccount;

public record PublicUserProfile(
        long id,
        String nickname,
        String avatarUrl,
        String bio,
        java.time.Instant createdAt) {

    public static PublicUserProfile from(UserAccount user) {
        return new PublicUserProfile(user.getId(), user.getNickname(),
                user.getAvatarUrl(), user.getBio(), user.getCreatedAt());
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/service/EmailOutboxService.java`
```java
package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.domain.EmailTemplate;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOutboxService {

    private final EmailOutboxRepository outbox;

    public EmailOutboxService(EmailOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public void enqueue(String toEmail, EmailTemplate template, Map<String, Object> payload) {
        EmailOutboxEntry entry = new EmailOutboxEntry();
        entry.setToEmail(toEmail);
        entry.setTemplate(template);
        entry.setPayload(payload);
        entry.setStatus(EmailOutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setNextAttemptAt(Instant.now());
        outbox.save(entry);
    }
}
```

- [ ] **Step 4: 写注册服务与控制器**

`backend/src/main/java/com/rance/galcommunity/identity/service/AuthService.java`
```java
package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.EmailTemplate;
import com.rance.galcommunity.identity.domain.EmailVerification;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.EmailVerificationRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import com.rance.galcommunity.shared.ratelimit.RateLimitService;
import com.rance.galcommunity.shared.security.PasswordPolicy;
import com.rance.galcommunity.shared.util.Hashing;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final EmailVerificationRepository verifications;
    private final EmailOutboxService emailOutbox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimit;

    public AuthService(UserAccountRepository users,
                       EmailVerificationRepository verifications,
                       EmailOutboxService emailOutbox,
                       PasswordEncoder passwordEncoder,
                       RateLimitService rateLimit) {
        this.users = users;
        this.verifications = verifications;
        this.emailOutbox = emailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.rateLimit = rateLimit;
    }

    @Transactional
    public UserProfileResponse register(String email, String nickname, String password, String ip) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        PasswordPolicy.validate(password);
        if (!rateLimit.tryAcquire("rl:register:" + ip + ":" + normalized, 5, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "注册过于频繁，请稍后再试");
        }
        if (users.existsByEmail(normalized)) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已注册");
        }

        UserAccount user = new UserAccount();
        user.setEmail(normalized);
        user.setNickname(nickname.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(false);
        users.save(user);

        issueVerification(user);
        return UserProfileResponse.self(user);
    }

    private void issueVerification(UserAccount user) {
        String rawToken = Hashing.randomToken();
        EmailVerification verification = new EmailVerification();
        verification.setUserId(user.getId());
        verification.setTokenHash(Hashing.sha256(rawToken));
        verification.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        verifications.save(verification);
        emailOutbox.enqueue(user.getEmail(), EmailTemplate.VERIFY_EMAIL, Map.of("token", rawToken));
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/web/AuthController.java`
```java
package com.rance.galcommunity.identity.web;

import com.rance.galcommunity.identity.service.AuthService;
import com.rance.galcommunity.identity.web.dto.RegisterRequest;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.ApiResponse;
import com.rance.galcommunity.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.register(request.email(), request.nickname(),
                request.password(), ClientIp.of(httpRequest)));
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=RegistrationIT
```
Expected: BUILD SUCCESS，3 个测试通过

- [ ] **Step 6: 提交**

```bash
git add backend/src
git commit -m "feat(identity): implement registration with verification email outbox"
```

---

### Task 9: 邮件投递器与 Mailpit（MailHog 兼容）

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/identity/mail/EmailSender.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/mail/SmtpEmailSender.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/service/EmailOutboxProcessor.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/mail/EmailOutboxScheduler.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/security/AppProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`
- Modify: `backend/src/test/java/com/rance/galcommunity/IntegrationTestBase.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/EmailDispatchIT.java`

- [ ] **Step 1: 写失败的邮件投递测试**

`backend/src/test/java/com/rance/galcommunity/identity/EmailDispatchIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class EmailDispatchIT extends IntegrationTestBase {

    @Autowired
    EmailOutboxRepository outbox;

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "nickname", "Mail", "password", "secret123"))))
                .andExpect(status().isCreated());
    }

    @Test
    void sendsPendingEmailAndScrubsPayload() throws Exception {
        register("mail1@example.com");
        int processed = emailOutboxProcessor.processDue(Instant.now());
        assertThat(processed).isEqualTo(1);
        verify(emailSender).send(eq("mail1@example.com"), eq("验证你的邮箱"),
                contains("/verify-email?token="));

        EmailOutboxEntry entry = outbox.findAll().get(0);
        assertThat(entry.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(entry.getPayload()).isEmpty();
    }

    @Test
    void retriesWithBackoffAndFailsAfterFiveAttempts() throws Exception {
        doThrow(new RuntimeException("smtp down"))
                .when(emailSender).send(anyString(), anyString(), anyString());
        register("mail2@example.com");

        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            emailOutboxProcessor.processDue(now);
            now = now.plus(Duration.ofDays(1));
        }

        EmailOutboxEntry entry = outbox.findAll().get(0);
        assertThat(entry.getAttempts()).isEqualTo(5);
        assertThat(entry.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=EmailDispatchIT
```
Expected: BUILD FAILURE（`EmailSender`/`EmailOutboxProcessor` 不存在）

- [ ] **Step 3: 写发信接口与 SMTP 实现**

`backend/src/main/java/com/rance/galcommunity/identity/mail/EmailSender.java`
```java
package com.rance.galcommunity.identity.mail;

public interface EmailSender {
    void send(String to, String subject, String body);
}
```

`backend/src/main/java/com/rance/galcommunity/identity/mail/SmtpEmailSender.java`
```java
package com.rance.galcommunity.identity.mail;

import com.rance.galcommunity.identity.security.AppProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppProperties props;

    public SmtpEmailSender(JavaMailSender mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.email().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
```

修改 `AppProperties.Email` 增加 `from`：
```java
    public record Email(boolean dispatchEnabled, long pollIntervalMs, String from) {
    }
```

修改 `application.yml` 的 `app.email`：
```yaml
  email:
    dispatch-enabled: ${EMAIL_DISPATCH_ENABLED:true}
    poll-interval-ms: 5000
    from: ${MAIL_FROM:no-reply@gal.local}
```

修改 `application-test.yml`：
```yaml
  email:
    dispatch-enabled: false
    poll-interval-ms: 5000
    from: no-reply@gal.local
```

- [ ] **Step 4: 写投递器与调度器**

`backend/src/main/java/com/rance/galcommunity/identity/service/EmailOutboxProcessor.java`
```java
package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.EmailOutboxEntry;
import com.rance.galcommunity.identity.domain.EmailOutboxStatus;
import com.rance.galcommunity.identity.mail.EmailSender;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.security.AppProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxProcessor.class);

    private final EmailOutboxRepository outbox;
    private final EmailSender emailSender;
    private final AppProperties props;

    public EmailOutboxProcessor(EmailOutboxRepository outbox, EmailSender emailSender, AppProperties props) {
        this.outbox = outbox;
        this.emailSender = emailSender;
        this.props = props;
    }

    @Transactional
    public int processDue(Instant now) {
        List<EmailOutboxEntry> batch = outbox
                .findTop20ByStatusAndNextAttemptAtBeforeOrderByIdAsc(EmailOutboxStatus.PENDING, now);
        for (EmailOutboxEntry entry : batch) {
            try {
                Map<String, Object> payload = entry.getPayload();
                String token = String.valueOf(payload.get("token"));
                String link = switch (entry.getTemplate()) {
                    case VERIFY_EMAIL -> props.baseUrl() + "/verify-email?token=" + token;
                    case RESET_PASSWORD -> props.baseUrl() + "/reset-password?token=" + token;
                };
                String body = "请打开以下链接完成操作：\n" + link + "\n若非本人操作请忽略本邮件。";
                emailSender.send(entry.getToEmail(), entry.getTemplate().subject(), body);
                entry.markSent();
            } catch (Exception ex) {
                log.warn("Email delivery failed for outbox id={} attempt={}", entry.getId(), entry.getAttempts() + 1, ex);
                entry.markFailed(now);
            }
        }
        outbox.saveAll(batch);
        return batch.size();
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/mail/EmailOutboxScheduler.java`
```java
package com.rance.galcommunity.identity.mail;

import com.rance.galcommunity.identity.service.EmailOutboxProcessor;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class EmailOutboxScheduler {

    private final EmailOutboxProcessor processor;

    public EmailOutboxScheduler(EmailOutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${app.email.poll-interval-ms:5000}")
    public void run() {
        processor.processDue(Instant.now());
    }
}
```

- [ ] **Step 5: 在测试基座中加入邮件 mock 与注册助手**

修改 `backend/src/test/java/com/rance/galcommunity/IntegrationTestBase.java`，在类体内加入：
```java
    @org.springframework.boot.test.mock.mockito.MockBean
    protected com.rance.galcommunity.identity.mail.EmailSender emailSender;

    @Autowired
    protected com.rance.galcommunity.identity.service.EmailOutboxProcessor emailOutboxProcessor;

    protected String registerAndGetVerificationToken(String email, String nickname, String password)
            throws Exception {
        org.mockito.Mockito.clearInvocations(emailSender);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "nickname", nickname, "password", password))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated());

        emailOutboxProcessor.processDue(java.time.Instant.now());

        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.atLeastOnce())
                .send(org.mockito.ArgumentMatchers.eq(email),
                        org.mockito.ArgumentMatchers.anyString(), body.capture());
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        org.assertj.core.api.Assertions.assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
```

- [ ] **Step 6: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=EmailDispatchIT
```
Expected: BUILD SUCCESS，2 个测试通过

- [ ] **Step 7: 提交**

```bash
git add backend/src
git commit -m "feat(identity): add SMTP sender with outbox retry and scrub"
```

---

### Task 10: 登录与刷新令牌下发

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/RefreshCookie.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/LoginResponse.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/service/AuthService.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/web/AuthController.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/LoginIT.java`

- [ ] **Step 1: 写失败的登录测试**

`backend/src/test/java/com/rance/galcommunity/identity/LoginIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

class LoginIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    JwtDecoder jwtDecoder;

    private String body(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    @Test
    void loginSetsRefreshCookieAndReturnsAccessToken() throws Exception {
        registerAndGetVerificationToken("login1@example.com", "Login", "secret123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("login1@example.com", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("login1@example.com"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refresh_token=").contains("HttpOnly").contains("SameSite=Strict");

        String accessToken = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.accessToken");
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isNotBlank();
        assertThat(jwt.getClaimAsStringList("roles")).contains("USER");
        assertThat(users.findByEmail("login1@example.com")).isPresent();
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        registerAndGetVerificationToken("login2@example.com", "Login", "secret123");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("login2@example.com", "wrongpass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody@example.com", "secret123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void loginIsRateLimited() throws Exception {
        registerAndGetVerificationToken("login3@example.com", "Login", "secret123");
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(body("login3@example.com", "wrongpass1")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body("login3@example.com", "wrongpass1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=LoginIT
```
Expected: BUILD FAILURE（登录端点不存在）

- [ ] **Step 3: 写 Cookie 工厂与 DTO**

`backend/src/main/java/com/rance/galcommunity/identity/security/RefreshCookie.java`
```java
package com.rance.galcommunity.identity.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookie {

    public static final String NAME = "refresh_token";

    private final AppProperties props;

    public RefreshCookie(AppProperties props) {
        this.props = props;
    }

    public ResponseCookie create(String rawToken) {
        return ResponseCookie.from(NAME, rawToken)
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(props.jwt().refreshTtl())
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
```

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/LoginRequest.java`
```java
package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
}
```

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/LoginResponse.java`
```java
package com.rance.galcommunity.identity.web.dto;

public record LoginResponse(String accessToken, long expiresIn, UserProfileResponse user) {
}
```

- [ ] **Step 4: 实现登录**

在 `AuthService` 中增加字段，并将构造函数替换为完整的 8 参版本：
```java
    private final UserAccountRepository users;
    private final EmailVerificationRepository verifications;
    private final EmailOutboxService emailOutbox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimit;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokenService;
    private final AppProperties props;

    public AuthService(UserAccountRepository users,
                       EmailVerificationRepository verifications,
                       EmailOutboxService emailOutbox,
                       PasswordEncoder passwordEncoder,
                       RateLimitService rateLimit,
                       RefreshTokenRepository refreshTokens,
                       TokenService tokenService,
                       AppProperties props) {
        this.users = users;
        this.verifications = verifications;
        this.emailOutbox = emailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.rateLimit = rateLimit;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.props = props;
    }
```
补充 import：
```java
import com.rance.galcommunity.identity.repo.RefreshTokenRepository;
import com.rance.galcommunity.identity.security.AppProperties;
```

新增方法：
```java
    @Transactional
    public LoginResult login(String email, String password, String ip) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!rateLimit.tryAcquire("rl:login:" + ip + ":" + normalized, 10, Duration.ofMinutes(5))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "登录尝试过于频繁");
        }

        UserAccount user = users.findByEmail(normalized)
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "邮箱或密码错误"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "邮箱或密码错误");
        }

        TokenService.IssuedToken access = tokenService.issue(user);
        String refreshToken = createRefreshToken(user, java.util.UUID.randomUUID().toString());
        return new LoginResult(access, refreshToken, UserProfileResponse.self(user));
    }

    private String createRefreshToken(UserAccount user, String familyId) {
        String raw = Hashing.randomToken();
        com.rance.galcommunity.identity.domain.RefreshToken token =
                new com.rance.galcommunity.identity.domain.RefreshToken();
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(Hashing.sha256(raw));
        token.setExpiresAt(Instant.now().plus(props.jwt().refreshTtl()));
        refreshTokens.save(token);
        return raw;
    }

    public record LoginResult(TokenService.IssuedToken access, String refreshToken,
                              UserProfileResponse user) {
    }
```

在 `AuthController` 中增加依赖与登录端点：
```java
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest,
                                            jakarta.servlet.http.HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request.email(), request.password(),
                ClientIp.of(httpRequest));
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                refreshCookie.create(result.refreshToken()).toString());
        return ApiResponse.ok(new LoginResponse(result.access().value(),
                result.access().expiresInSeconds(), result.user()));
    }
```
补充 import：`com.rance.galcommunity.identity.security.RefreshCookie`、`com.rance.galcommunity.identity.web.dto.LoginRequest`、`com.rance.galcommunity.identity.web.dto.LoginResponse`。

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=LoginIT
```
Expected: BUILD SUCCESS，3 个测试通过

- [ ] **Step 6: 提交**

```bash
git add backend/src
git commit -m "feat(identity): add login with access token and refresh cookie"
```

---

### Task 11: 邮箱验证与重发

**Files:**
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/service/AuthService.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/VerifyEmailRequest.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/web/AuthController.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/VerifyEmailIT.java`

- [ ] **Step 1: 写失败的验证测试**

`backend/src/test/java/com/rance/galcommunity/identity/VerifyEmailIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class VerifyEmailIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    EmailOutboxRepository outbox;

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void verifiesEmailOnce() throws Exception {
        String token = registerAndGetVerificationToken("verify1@example.com", "Verify", "secret123");
        String body = objectMapper.writeValueAsString(Map.of("token", token));

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(users.findByEmail("verify1@example.com").orElseThrow().isEmailVerified()).isTrue();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void resendIsAuthenticatedAndRateLimited() throws Exception {
        registerAndGetVerificationToken("verify2@example.com", "Verify", "secret123");
        String access = loginToken("verify2@example.com", "secret123");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/resend-verification")
                            .header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));
    }

    @Test
    void resendRejectsAlreadyVerifiedEmail() throws Exception {
        String token = registerAndGetVerificationToken("verify3@example.com", "Verify", "secret123");
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk());
        String access = loginToken("verify3@example.com", "secret123");

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=VerifyEmailIT
```
Expected: BUILD FAILURE（端点不存在）

- [ ] **Step 3: 实现验证与重发**

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/VerifyEmailRequest.java`
```java
package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank String token) {
}
```

在 `AuthService` 中增加方法：
```java
    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerification verification = verifications.findByTokenHash(Hashing.sha256(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE, "验证链接无效"));
        if (verification.getUsedAt() != null || verification.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "验证链接已失效");
        }
        UserAccount user = users.findById(verification.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        user.setEmailVerified(true);
        verification.setUsedAt(Instant.now());
    }

    @Transactional
    public void resendVerification(long userId) {
        if (!rateLimit.tryAcquire("rl:resend:" + userId, 3, Duration.ofMinutes(10))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "重发过于频繁");
        }
        UserAccount user = users.findById(userId)
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "邮箱已验证");
        }
        issueVerification(user);
    }
```

在 `AuthController` 中增加：
```java
    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.ok();
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.oauth2.jwt.Jwt jwt) {
        authService.resendVerification(Long.parseLong(jwt.getSubject()));
        return ApiResponse.ok();
    }
```
补充 import：`com.rance.galcommunity.identity.web.dto.VerifyEmailRequest`。

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=VerifyEmailIT
```
Expected: BUILD SUCCESS，3 个测试通过

- [ ] **Step 5: 提交**

```bash
git add backend/src
git commit -m "feat(identity): add email verification and resend endpoints"
```

---

### Task 12: 刷新轮换、复用检测与登出

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/identity/security/OriginGuard.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/service/AuthService.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/web/AuthController.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/RefreshIT.java`

- [ ] **Step 1: 写失败的刷新测试**

`backend/src/test/java/com/rance/galcommunity/identity/RefreshIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RefreshIT extends IntegrationTestBase {

    private Cookie login(String email) throws Exception {
        registerAndGetVerificationToken(email, "Refresh", "secret123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    void refreshRotatesCookie() throws Exception {
        Cookie first = login("refresh1@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(first)
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = result.getResponse().getCookie("refresh_token");
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void reusingOldRefreshRevokesWholeFamily() throws Exception {
        Cookie first = login("refresh2@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(first).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk()).andReturn();
        Cookie second = result.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(first).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(second).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    void logoutInvalidatesRefresh() throws Exception {
        Cookie cookie = login("refresh3@example.com");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    void rejectsForeignOrigin() throws Exception {
        Cookie cookie = login("refresh4@example.com");
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie).header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=RefreshIT
```
Expected: BUILD FAILURE（刷新/登出端点不存在）

- [ ] **Step 3: 写 Origin 守卫**

`backend/src/main/java/com/rance/galcommunity/identity/security/OriginGuard.java`
```java
package com.rance.galcommunity.identity.security;

import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OriginGuard {

    private final Set<String> allowedOrigins;

    public OriginGuard(AppProperties props) {
        this.allowedOrigins = new HashSet<>(props.allowedOrigins());
    }

    public void check(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非法请求来源");
        }
    }
}
```

- [ ] **Step 4: 实现刷新与登出**

在 `AuthService` 中增加方法：
```java
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult refresh(String rawRefreshToken) {
        com.rance.galcommunity.identity.domain.RefreshToken token = refreshTokens
                .findByTokenHash(Hashing.sha256(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录"));

        if (token.getRevokedAt() != null || token.getReplacedBy() != null) {
            refreshTokens.revokeFamily(token.getFamilyId(), Instant.now());
            throw new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录");
        }

        UserAccount user = users.findById(token.getUserId())
                .filter(candidate -> candidate.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录"));

        String newRaw = createRefreshToken(user, token.getFamilyId());
        com.rance.galcommunity.identity.domain.RefreshToken newToken = refreshTokens
                .findByTokenHash(Hashing.sha256(newRaw)).orElseThrow();
        token.setRevokedAt(Instant.now());
        token.setReplacedBy(newToken.getId());

        TokenService.IssuedToken access = tokenService.issue(user);
        return new LoginResult(access, newRaw, UserProfileResponse.self(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Hashing.sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }
```

> 复用检测要先吊销令牌族再抛异常；`noRollbackFor` 保证吊销不会被异常回滚。

在 `AuthController` 中增加字段并替换为三参构造函数：
```java
    private final AuthService authService;
    private final RefreshCookie refreshCookie;
    private final OriginGuard originGuard;

    public AuthController(AuthService authService, RefreshCookie refreshCookie, OriginGuard originGuard) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
        this.originGuard = originGuard;
    }
```
补充 import：`com.rance.galcommunity.identity.security.OriginGuard`。

新增端点：
```java
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest httpRequest,
                                              jakarta.servlet.http.HttpServletResponse response) {
        originGuard.check(httpRequest);
        String raw = refreshCookie.read(httpRequest);
        if (raw == null) {
            throw new com.rance.galcommunity.shared.api.BusinessException(
                    com.rance.galcommunity.shared.api.ErrorCode.TOKEN_REUSED, "登录已失效，请重新登录");
        }
        AuthService.LoginResult result = authService.refresh(raw);
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                refreshCookie.create(result.refreshToken()).toString());
        return ApiResponse.ok(new LoginResponse(result.access().value(),
                result.access().expiresInSeconds(), result.user()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest,
                                    jakarta.servlet.http.HttpServletResponse response) {
        originGuard.check(httpRequest);
        authService.logout(refreshCookie.read(httpRequest));
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,
                refreshCookie.clear().toString());
        return ApiResponse.ok();
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=RefreshIT
```
Expected: BUILD SUCCESS，4 个测试通过

- [ ] **Step 6: 提交**

```bash
git add backend/src
git commit -m "feat(identity): add refresh rotation, reuse detection and logout"
```

---

### Task 13: 找回与重置密码

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/ForgotPasswordRequest.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/ResetPasswordRequest.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/service/AuthService.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/web/AuthController.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/PasswordResetIT.java`

- [ ] **Step 1: 写失败的找回测试**

`backend/src/test/java/com/rance/galcommunity/identity/PasswordResetIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.EmailTemplate;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.PasswordResetRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class PasswordResetIT extends IntegrationTestBase {

    @Autowired
    EmailOutboxRepository outbox;

    @Autowired
    PasswordResetRepository resets;

    private String registerRaw(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "nickname", "Reset", "password", "secret123"))))
                .andExpect(status().isCreated());
        return email;
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("refresh_token");
    }

    @Test
    void forgotAndResetPasswordRevokesSessions() throws Exception {
        String email = "reset1@example.com";
        registerRaw(email);
        Cookie oldCookie = loginCookie(email, "secret123");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        String rawToken = outbox.findAll().stream()
                .filter(entry -> entry.getTemplate() == EmailTemplate.RESET_PASSWORD)
                .findFirst().orElseThrow()
                .getPayload().get("token").toString();
        assertThat(rawToken).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", "newsecret1"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "secret123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "newsecret1"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(oldCookie).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", rawToken, "newPassword", "another123"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody@example.com"))))
                .andExpect(status().isOk());
        assertThat(resets.count()).isZero();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=PasswordResetIT
```
Expected: BUILD FAILURE（端点不存在）

- [ ] **Step 3: 写 DTO**

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/ForgotPasswordRequest.java`
```java
package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@Email @NotBlank String email) {
}
```

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/ResetPasswordRequest.java`
```java
package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
}
```

- [ ] **Step 4: 实现找回与重置**

在 `AuthService` 中增加依赖与完整构造函数（替换旧构造函数）：
```java
    private final UserAccountRepository users;
    private final EmailVerificationRepository verifications;
    private final PasswordResetRepository resets;
    private final EmailOutboxService emailOutbox;
    private final PasswordEncoder passwordEncoder;
    private final RateLimitService rateLimit;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokenService;
    private final AppProperties props;

    public AuthService(UserAccountRepository users,
                       EmailVerificationRepository verifications,
                       PasswordResetRepository resets,
                       EmailOutboxService emailOutbox,
                       PasswordEncoder passwordEncoder,
                       RateLimitService rateLimit,
                       RefreshTokenRepository refreshTokens,
                       TokenService tokenService,
                       AppProperties props) {
        this.users = users;
        this.verifications = verifications;
        this.resets = resets;
        this.emailOutbox = emailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.rateLimit = rateLimit;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.props = props;
    }
```
补充 import：
```java
import com.rance.galcommunity.identity.domain.PasswordReset;
import com.rance.galcommunity.identity.repo.PasswordResetRepository;
import com.rance.galcommunity.identity.security.AppProperties;
```

新增方法：
```java
    @Transactional
    public void forgotPassword(String email, String ip) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!rateLimit.tryAcquire("rl:forgot:" + ip + ":" + normalized, 5, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "请求过于频繁");
        }
        users.findByEmail(normalized)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .ifPresent(user -> {
                    String rawToken = Hashing.randomToken();
                    PasswordReset reset = new PasswordReset();
                    reset.setUserId(user.getId());
                    reset.setTokenHash(Hashing.sha256(rawToken));
                    reset.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
                    resets.save(reset);
                    emailOutbox.enqueue(user.getEmail(), EmailTemplate.RESET_PASSWORD,
                            Map.of("token", rawToken));
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword);
        PasswordReset reset = resets.findByTokenHash(Hashing.sha256(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE, "重置链接无效"));
        if (reset.getUsedAt() != null || reset.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "重置链接已失效");
        }
        UserAccount user = users.findById(reset.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        reset.setUsedAt(Instant.now());
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());
    }
```

在 `AuthController` 增加：
```java
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                            HttpServletRequest httpRequest) {
        authService.forgotPassword(request.email(), ClientIp.of(httpRequest));
        return ApiResponse.ok();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok();
    }
```
补充 import：`ForgotPasswordRequest`、`ResetPasswordRequest`。

- [ ] **Step 5: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=PasswordResetIT
```
Expected: BUILD SUCCESS，2 个测试通过

- [ ] **Step 6: 提交**

```bash
git add backend/src
git commit -m "feat(identity): add forgot and reset password flow"
```

---

### Task 14: 资料读取、更新与账号注销

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/dto/UpdateProfileRequest.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/service/UserService.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/web/UserController.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/ProfileIT.java`

- [ ] **Step 1: 写失败的资料测试**

`backend/src/test/java/com/rance/galcommunity/identity/ProfileIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.repo.EmailOutboxRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ProfileIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    EmailOutboxRepository outbox;

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Cookie loginCookie(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("refresh_token");
    }

    @Test
    void publicProfileHidesEmailAndSelfProfileShowsIt() throws Exception {
        registerAndGetVerificationToken("profile1@example.com", "Profile", "secret123");
        long id = users.findByEmail("profile1@example.com").orElseThrow().getId();
        String access = loginToken("profile1@example.com", "secret123");

        mockMvc.perform(get("/api/v1/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Profile"))
                .andExpect(jsonPath("$.data.email").doesNotExist());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("profile1@example.com"));
    }

    @Test
    void updateRequiresVerifiedEmailAndChangesFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "profile2@example.com",
                                "nickname", "Before",
                                "password", "secret123"))))
                .andExpect(status().isCreated());
        String access = loginToken("profile2@example.com", "secret123");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "After"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));

        String rawToken = outbox.findAll().stream()
                .filter(entry -> entry.getToEmail().equals("profile2@example.com"))
                .findFirst().orElseThrow()
                .getPayload().get("token").toString();

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nickname", "After", "bio", "hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("After"))
                .andExpect(jsonPath("$.data.bio").value("hello"));
    }

    @Test
    void deleteAccountAnonymizesAndRevokesSessions() throws Exception {
        registerAndGetVerificationToken("profile3@example.com", "Delete", "secret123");
        Cookie refresh = loginCookie("profile3@example.com", "secret123");
        String access = loginToken("profile3@example.com", "secret123");

        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        var user = users.findByEmail("profile3@example.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(user.getNickname()).isEqualTo("已注销用户");
        assertThat(user.getEmail()).startsWith("deleted-");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "profile3@example.com", "password", "secret123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh).header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=ProfileIT
```
Expected: BUILD FAILURE（`UserService`/`UserController` 不存在）

- [ ] **Step 3: 写 UserService 与 UserController**

`backend/src/main/java/com/rance/galcommunity/identity/web/dto/UpdateProfileRequest.java`
```java
package com.rance.galcommunity.identity.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 40) String nickname,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "头像需为 http/https 链接") String avatarUrl,
        @Size(max = 500) String bio) {
}
```

`backend/src/main/java/com/rance/galcommunity/identity/service/UserService.java`
```java
package com.rance.galcommunity.identity.service;

import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.RefreshTokenRepository;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import com.rance.galcommunity.identity.web.dto.PublicUserProfile;
import com.rance.galcommunity.identity.web.dto.UpdateProfileRequest;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.BusinessException;
import com.rance.galcommunity.shared.api.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PublicUserProfile publicProfile(long id) {
        return PublicUserProfile.from(requireActive(id));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(long id) {
        return UserProfileResponse.self(requireActive(id));
    }

    @Transactional
    public UserProfileResponse update(long id, UpdateProfileRequest request) {
        UserAccount user = requireActive(id);
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "请先验证邮箱");
        }
        if (request.nickname() != null && !request.nickname().isBlank()) {
            user.setNickname(request.nickname().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().isBlank() ? null : request.bio().trim());
        }
        return UserProfileResponse.self(user);
    }

    @Transactional
    public void deleteAccount(long id) {
        UserAccount user = requireActive(id);
        user.setStatus(AccountStatus.DELETED);
        user.setEmail("deleted-" + user.getId() + "@invalid.local");
        user.setNickname("已注销用户");
        user.setAvatarUrl(null);
        user.setBio(null);
        user.setEmailVerified(false);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());
    }

    private UserAccount requireActive(long id) {
        return users.findById(id)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }
}
```

> 注销后 refresh 全部吊销；已签发的 access token 在 30 分钟有效期内仍可用，这是无状态 JWT 的已知取舍。

`backend/src/main/java/com/rance/galcommunity/identity/web/UserController.java`
```java
package com.rance.galcommunity.identity.web;

import com.rance.galcommunity.identity.security.RefreshCookie;
import com.rance.galcommunity.identity.service.UserService;
import com.rance.galcommunity.identity.web.dto.PublicUserProfile;
import com.rance.galcommunity.identity.web.dto.UpdateProfileRequest;
import com.rance.galcommunity.identity.web.dto.UserProfileResponse;
import com.rance.galcommunity.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final RefreshCookie refreshCookie;

    public UserController(UserService userService, RefreshCookie refreshCookie) {
        this.userService = userService;
        this.refreshCookie = refreshCookie;
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicUserProfile> publicProfile(@PathVariable long id) {
        return ApiResponse.ok(userService.publicProfile(id));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.me(Long.parseLong(jwt.getSubject())));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.update(Long.parseLong(jwt.getSubject()), request));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        userService.deleteAccount(Long.parseLong(jwt.getSubject()));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString());
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=ProfileIT
```
Expected: BUILD SUCCESS，3 个测试通过

- [ ] **Step 5: 提交**

```bash
git add backend/src
git commit -m "feat(identity): add profile update and account deletion"
```

---

### Task 15: 供其他模块使用的用户摘要 API

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/identity/api/IdentityApi.java`
- Create: `backend/src/main/java/com/rance/galcommunity/identity/api/UserSummary.java`
- Modify: `backend/src/main/java/com/rance/galcommunity/identity/service/UserService.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/IdentityApiIT.java`

- [ ] **Step 1: 写失败的模块 API 测试**

`backend/src/test/java/com/rance/galcommunity/identity/IdentityApiIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.identity.api.IdentityApi;
import com.rance.galcommunity.identity.domain.AccountStatus;
import com.rance.galcommunity.identity.domain.Role;
import com.rance.galcommunity.identity.domain.UserAccount;
import com.rance.galcommunity.identity.repo.UserAccountRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdentityApiIT extends IntegrationTestBase {

    @Autowired
    UserAccountRepository users;

    @Autowired
    IdentityApi identityApi;

    private UserAccount create(String email, AccountStatus status) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setNickname("N-" + email);
        user.setRole(Role.USER);
        user.setStatus(status);
        user.setEmailVerified(true);
        return users.saveAndFlush(user);
    }

    @Test
    void returnsOnlyActiveUsersInBatch() {
        UserAccount first = create("api1@example.com", AccountStatus.ACTIVE);
        UserAccount second = create("api2@example.com", AccountStatus.ACTIVE);
        UserAccount banned = create("api3@example.com", AccountStatus.BANNED);

        Map<Long, com.rance.galcommunity.identity.api.UserSummary> result =
                identityApi.findUsers(List.of(first.getId(), second.getId(), banned.getId()));

        assertThat(result).hasSize(2);
        assertThat(result.get(first.getId()).nickname()).isEqualTo("N-api1@example.com");
        assertThat(identityApi.findUser(banned.getId())).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=IdentityApiIT
```
Expected: BUILD FAILURE（`IdentityApi` 不存在）

- [ ] **Step 3: 写 API 与实现**

`backend/src/main/java/com/rance/galcommunity/identity/api/UserSummary.java`
```java
package com.rance.galcommunity.identity.api;

public record UserSummary(long id, String nickname, String avatarUrl) {
}
```

`backend/src/main/java/com/rance/galcommunity/identity/api/IdentityApi.java`
```java
package com.rance.galcommunity.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface IdentityApi {
    Optional<UserSummary> findUser(long id);
    Map<Long, UserSummary> findUsers(Collection<Long> ids);
}
```

让 `UserService` 实现该接口：
```java
public class UserService implements com.rance.galcommunity.identity.api.IdentityApi {
```
并新增方法：
```java
    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<com.rance.galcommunity.identity.api.UserSummary> findUser(long id) {
        return users.findById(id)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .map(user -> new com.rance.galcommunity.identity.api.UserSummary(
                        user.getId(), user.getNickname(), user.getAvatarUrl()));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, com.rance.galcommunity.identity.api.UserSummary> findUsers(
            java.util.Collection<Long> ids) {
        return users.findAllById(ids).stream()
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .collect(java.util.stream.Collectors.toMap(
                        UserAccount::getId,
                        user -> new com.rance.galcommunity.identity.api.UserSummary(
                                user.getId(), user.getNickname(), user.getAvatarUrl())));
    }
```

> 说明：批量查询就绪；Redis 缓存等消费方（P4 社区动态流）接入时再加，避免过早优化。

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=IdentityApiIT
```
Expected: BUILD SUCCESS，1 个测试通过

- [ ] **Step 5: 提交**

```bash
git add backend/src
git commit -m "feat(identity): expose batch user summary module api"
```

---

### Task 16: OpenAPI、Actuator 与日志验证

**Files:**
- Create: `backend/src/main/java/com/rance/galcommunity/shared/config/OpenApiConfig.java`
- Create: `backend/src/test/java/com/rance/galcommunity/identity/ApiDocsIT.java`

- [ ] **Step 1: 写失败的文档与运维端点测试**

`backend/src/test/java/com/rance/galcommunity/identity/ApiDocsIT.java`
```java
package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class ApiDocsIT extends IntegrationTestBase {

    @Test
    void openApiDocumentIsAvailable() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Gal Community API");
        assertThat(body).contains("bearerAuth");
    }

    @Test
    void healthAndPrometheusArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=ApiDocsIT
```
Expected: 第一个测试失败（文档标题/安全方案缺失）

- [ ] **Step 3: 写 OpenAPI 配置**

`backend/src/main/java/com/rance/galcommunity/shared/config/OpenApiConfig.java`
```java
package com.rance.galcommunity.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI galCommunityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gal Community API")
                        .version("v1")
                        .description("Galgame 垂直社区 API"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:
```bash
mvn -f backend/pom.xml test -Dtest=ApiDocsIT
```
Expected: BUILD SUCCESS，2 个测试通过

- [ ] **Step 5: 提交**

```bash
git add backend/src
git commit -m "feat(shared): document api with openapi and expose metrics"
```

---

### Task 17: CI、README 与全量验证

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `README.md`

- [ ] **Step 1: 写 CI 工作流**

`.github/workflows/ci.yml`
```yaml
name: ci

on:
  push:
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Verify backend
        run: mvn -f backend/pom.xml -B verify
```

- [ ] **Step 2: 补充 README 的认证示例**

在 `README.md` 的“测试”之前加入：
````markdown
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

## 测试
````

- [ ] **Step 3: 运行后端全量测试**

Run:
```bash
mvn -f backend/pom.xml verify
```
Expected: BUILD SUCCESS；所有 `*IT` 与单元测试通过

- [ ] **Step 4: 确认工作区只含预期变更**

Run:
```bash
git status --short
```
Expected: 仅显示 `.github/workflows/ci.yml` 与 `README.md`（未提交的新/改动文件）

- [ ] **Step 5: 提交**

```bash
git add .github/workflows/ci.yml README.md
git commit -m "ci: run backend verify and document auth flow"
```

---

## Self-Review

**1. Spec coverage（P1 范围）**

| 设计要求 | 覆盖任务 |
|---|---|
| Compose 基础设施 | Task 2 |
| 单体骨架 + 统一响应/错误/链路 ID | Task 1、3 |
| Modulith 模块边界 + ArchUnit | Task 6 |
| 注册、邮箱验证、重发 | Task 8、9、11 |
| 登录、access JWT、refresh Cookie | Task 7、10 |
| refresh 轮换 + 复用检测 + 登出 | Task 12 |
| 找回/重置密码 | Task 13 |
| 资料读取/更新、注销、匿名化 | Task 14 |
| 权限与邮箱未验证写限制 | Task 7、14 |
| 限流（注册/登录/重发/找回） | Task 4、8、10、11、13 |
| 邮件 outbox、重试、payload 清理 | Task 9 |
| OpenAPI、Actuator、Prometheus | Task 16 |
| 模块间用户摘要 API | Task 15 |
| CI 与文档 | Task 17 |

已知延后项（不在 P1-A）：前端 Nuxt 认证页面（P1-B）、用户摘要 Redis 缓存（P4）、SMTP 域名 SPF/DKIM（上线运维配置，见设计文档 8.7）。

**2. Placeholder scan**

已逐任务检查：无 TBD/TODO；所有代码步骤含完整实现；ProfileIT 直接通过 `EmailOutboxRepository` 获取验证令牌，无占位实现。

**3. Type consistency**

- `TokenService.IssuedToken(value, expiresInSeconds)` 在登录/刷新中一致使用。
- `RefreshCookie.NAME` 与控制器读取一致。
- `AuthService.LoginResult(access, refreshToken, user)` 在登录/刷新中一致。
- `EmailTemplate` 的 `VERIFY_EMAIL` / `RESET_PASSWORD` 与投递器 switch 一致。
- `UserService` 在 Task 14 创建，Task 15 追加 `IdentityApi` 实现，方法名与接口一致。

**4. 执行注意**

- 每次测试前由 `IntegrationTestBase` 清空身份模块表与 Redis（见 Task 5 的修改步骤），避免测试间数据/限流串扰。
- Testcontainers 首次启动较慢；若本机未启动 Docker，所有 IT 会失败。
- JWT 密钥开发环境自动生成临时 RSA；生产必须注入 `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-11-16-p1a-backend-base-auth.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**


