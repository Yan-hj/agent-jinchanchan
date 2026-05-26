# 游戏策略 Agent — 项目实施参考文档

> 对应实施计划：`game-agent-mvp-plan.md`
> API 设计：`docs/api-design.md`
> 版本：v1.0 | 最后更新：2026-05-26

---

## 目录

1. [项目全景与版本选型](#1-项目全景与版本选型)
2. [Maven 多模块结构](#2-maven-多模块结构)
3. [`game-agent-common` 模块](#3-game-agent-common-模块)
4. [`game-agent-api` 模块](#4-game-agent-api-模块)
5. [`game-agent-bootstrap` 模块](#5-game-agent-bootstrap-模块)
6. [工程配置文档](#6-工程配置文档)
7. [占位模块说明](#7-占位模块说明)
8. [附录：开发环境启动](#8-附录开发环境启动)

---

## 1. 项目全景与版本选型

### 1.1 项目目标

为《金铲铲之战》（Teamfight Tactics 手游版）构建一个双源驱动的实时游戏策略 Agent，提供阵容推荐、装备合成、经济运营、站位调整等策略建议，核心差异化在于 **知识新鲜度**（版本更新后 2 小时内可用）和 **来源可追溯**（每条建议附带官方/社媒引用）。

### 1.2 版本选型

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | **21.0.11** | 虚拟线程支持，Spring Boot 3.5 基线要求 |
| Spring Boot | **3.5.4** | 当前最新 3.5.x 稳定版 |
| Spring AI Alibaba (SAA) | **1.1.2.0** | Agent 框架，统一阿里云 DashScope 集成 |
| Spring AI | **1.1.2** | 与 SAA 1.1.2.0 配套 |
| Spring AI Extensions | **1.1.2.1** | 扩展 BOM |
| Spring Cloud | **2025.0.1 (Northfields)** | 管理 Resilience4j Circuit Breaker 等 |
| Elasticsearch | 8.x (由 docker-compose 提供) | 向量库 + BM25 全文检索 |
| MongoDB | 7.x (由 docker-compose 提供) | 会话存储与蒸馏摘要 |
| Maven | 3.9.9 (Wrapper 管理) | 构建工具 |

### 1.3 架构全景

```
                    ┌──────────────────┐
                    │    Frontend      │  (后续实现 - 本次跳过)
                    │  (Vue/React/SSE) │
                    └────────┬─────────┘
                             │ REST + SSE
                    ┌────────▼─────────┐
                    │  game-agent-api  │  Controller 层
                    │  (REST + SSE)    │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
    ┌─────────▼────┐ ┌──────▼──────┐ ┌─────▼────────┐
    │ game-agent-  │ │ game-agent- │ │ game-agent-  │
    │ core         │ │ knowledge   │ │ memory       │
    │ (Agent编排)  │ │ (检索/RAG)  │ │ (会话/蒸馏)  │
    └─────────┬────┘ └──────┬──────┘ └──────┬────────┘
              │             │               │
    ┌─────────▼─────────────▼───────────────▼────────┐
    │              game-agent-ingestion               │
    │          (采集、清洗、Document 构建)              │
    └─────────────────────┬───────────────────────────┘
                          │
              ┌───────────▼───────────┐
              │  Docker Services      │
              │  ES (向量) + Mongo    │
              │  (会话/反馈/偏好)     │
              └───────────────────────┘
```

---

## 2. Maven 多模块结构

### 2.1 父子模块关系

父模块 `pom.xml` (`game-strategy-agent`) 管理全部 8 个子模块的依赖版本与公共配置：

```
game-strategy-agent (pom)           ← BOM 管理器
├── game-agent-common               ← 基础库 (DTO/异常/常量)
├── game-agent-ingestion            ← 采集流水线 (占位)
├── game-agent-knowledge            ← 知识库与检索 (占位)
├── game-agent-core                 ← Agent 编排 (占位)
├── game-agent-memory               ← 会话与蒸馏 (占位)
├── game-agent-api                  ← REST API 层
├── game-agent-render               ← 阵容渲染 (占位)
└── game-agent-bootstrap            ← Spring Boot 启动入口
```

### 2.2 父 POM 关键配置

文件：`pom.xml`

**dependencyManagement BOM 导入顺序（优先级从上到下递减）**：

| BOM | 管理范围 |
|-----|----------|
| `spring-boot-starter-parent` (parent) | Spring Boot 全局版本锁定 |
| `spring-ai-alibaba-bom` | DashScope、Agent Framework |
| `spring-ai-bom` | VectorStore、Retriever、ChatClient |
| `spring-ai-alibaba-extensions-bom` | SAA 扩展组件 |
| `spring-cloud-dependencies` | Resilience4j、Circuit Breaker |

**properties**：

| 属性名 | 值 | 说明 |
|--------|-----|------|
| `java.version` | `21` | 编译与 source/target 级别 |
| `spring-ai-alibaba.version` | `1.1.2.0` | SAA BOM 版本 |
| `spring-ai.version` | `1.1.2` | Spring AI BOM 版本 |
| `spring-ai-extensions.version` | `1.1.2.1` | SAA Extensions BOM 版本 |
| `springdoc.version` | `2.8.5` | SpringDoc OpenAPI 版本 |

**公共全模块依赖**：

- `lombok` — 可选编译依赖，所有模块可用
- `spring-boot-starter-test` — test scope

### 2.3 模块间依赖图

```
bootstrap
  └─ api
       └─ common
  + ingestion ─┐
  + knowledge  ├─ core
  + memory ────┘
  + render

core → knowledge + memory
knowledge → common
memory → common + spring-boot-starter-data-mongodb
ingestion → common
render → common
```

---

## 3. `game-agent-common` 模块

**职责**：提供全项目共用的基础设施：统一响应格式、错误码枚举、异常体系、链路追踪 ID、元数据常量。

**包路径**：`com.game.agent.common`

### 3.1 `ApiResponse<T>` — 统一响应包装

**文件**：`game-agent-common/src/main/java/com/game/agent/common/ApiResponse.java`

使用 Java `record` 定义，不可变、自动生成 equals/hashCode/toString。

**字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `String` | 业务码，`"0"` 表示成功，非零为错误码（如 `"A0001"`） |
| `message` | `String` | 提示信息，成功时为 `"success"`，失败时为错误描述 |
| `data` | `T` | 响应数据主体 |
| `traceId` | `String` | 请求追踪 ID，自动从 `TraceIdContext` 获取 |

**静态工厂方法**：

| 方法 | 签名 | 说明 |
|------|------|------|
| `success(data)` | `static <T> ApiResponse<T> success(T data)` | 创建成功响应，code=0，携带数据 |
| `success()` | `static <T> ApiResponse<T> success()` | 创建成功响应，data 为 null |
| `error(errorCode)` | `static <T> ApiResponse<T> error(ErrorCode)` | 从 ErrorCode 枚举创建错误响应 |
| `error(errorCode, detail)` | `static <T> ApiResponse<T> error(ErrorCode, String)` | 错误响应，覆盖 message 为 detail |
| `error(code, message)` | `static <T> ApiResponse<T> error(String, String)` | 直接指定 code/message 创建错误响应 |

### 3.2 `ErrorCode` — 错误码枚举

**文件**：`game-agent-common/src/main/java/com/game/agent/common/ErrorCode.java`

全量错误码定义，按模块分段（符合 `game-agent-mvp-plan.md` 第 11 节规范）。

**枚举常量**：

| 常量名 | 错误码 | HTTP 状态 | 说明 |
|--------|--------|-----------|------|
| **Agent 运行时** ||||
| `AGENT_INTENT_FAILED` | A0001 | 500 | 意图识别失败 |
| `AGENT_LLM_TIMEOUT` | A0002 | 500 | LLM 响应超时 |
| **Agent 工具** ||||
| `TOOL_PARAM_INVALID` | A1000 | 400 | 工具参数校验失败 |
| `TOOL_EXECUTION_TIMEOUT` | A1001 | 500 | 工具执行超时 |
| **知识库** ||||
| `ES_CONNECTION_FAILED` | K0001 | 500 | ES 连接失败 |
| `ES_INDEX_MISMATCH` | K0002 | 500 | 索引不存在或维度不匹配 |
| **检索** ||||
| `RETRIEVAL_NO_RESULTS` | K1000 | 404 | 检索无结果 |
| `RERANK_FAILED` | K1001 | 500 | Rerank 排序失败 |
| **采集** ||||
| `INGEST_URL_UNREACHABLE` | I0001 | 400 | URL 不可达 |
| `INGEST_FORMAT_UNSUPPORTED` | I0002 | 400 | 文档格式不支持 |
| **采集流水线** ||||
| `INGEST_EMBEDDING_FAILED` | I1000 | 500 | Embedding 失败 |
| `INGEST_ES_WRITE_FAILED` | I1001 | 500 | ES 写入失败 |
| **鉴权** ||||
| `AUTH_TOKEN_INVALID` | G0001 | 401 | Token 过期或无效 |
| `AUTH_PERMISSION_DENIED` | G0002 | 403 | 权限不足 |
| `AUTH_RATE_LIMITED` | G0003 | 429 | 请求频率超限 |
| **通用** ||||
| `PARAM_INVALID` | G1000 | 400 | 参数校验失败 |
| `RESOURCE_NOT_FOUND` | G1001 | 404 | 资源不存在 |
| `RATE_LIMIT_EXCEEDED` | G1002 | 429 | 频率超限 |
| **系统内部** ||||
| `INTERNAL_ERROR` | Z0001 | 500 | 服务器内部错误 |

**实例方法**：

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `code()` | `String` | 获取错误码字符串（如 `"A0001"`） |
| `message()` | `String` | 获取错误描述 |
| `httpStatus()` | `int` | 获取对应的 HTTP 状态码 |

### 3.3 `PagedResponse<T>` — 分页响应

**文件**：`game-agent-common/src/main/java/com/game/agent/common/PagedResponse.java`

继承自 `ApiResponse` 风格的分页响应，用于列表查询接口。

**字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `String` | 业务码，`"0"` 表示成功 |
| `message` | `String` | 提示信息 |
| `traceId` | `String` | 链路追踪 ID |
| `content` | `List<T>` | 当前页数据列表 |
| `page` | `int` | 当前页码（从 0 开始） |
| `size` | `int` | 每页大小 |
| `totalElements` | `long` | 总记录数 |
| `totalPages` | `int` | 总页数 |

**静态工厂方法**：

| 方法 | 说明 |
|------|------|
| `of(content, page, size, totalElements)` | 根据当前页内容、页码、大小和总数创建分页响应，自动计算 totalPages |

### 3.4 异常体系

**包路径**：`com.game.agent.common.exception`

```
BusinessException (abstract)
├── AgentException        ← Agent 运行时/工具异常
├── KnowledgeException    ← 知识库/检索异常
├── IngestionException    ← 采集/流水线异常
└── RateLimitException    ← 限流异常
```

**`BusinessException`** — 抽象基类

| 方法 | 说明 |
|------|------|
| `getErrorCode()` | 返回关联的 `ErrorCode` 枚举 |

**子类构造函数**：

| 子类 | 构造函数参数 | 用法示例 |
|------|-------------|----------|
| `AgentException` | `(ErrorCode)`, `(ErrorCode, String)`, `(ErrorCode, Throwable)` | `new AgentException(ErrorCode.AGENT_INTENT_FAILED)` |
| `KnowledgeException` | 同上 | `new KnowledgeException(ErrorCode.ES_CONNECTION_FAILED, "连接超时")` |
| `IngestionException` | 同上 | `new IngestionException(ErrorCode.INGEST_URL_UNREACHABLE)` |
| `RateLimitException` | `()`, `(String)` | `new RateLimitException()` 或 `new RateLimitException("用户日配额耗尽")` |

### 3.5 `TraceIdContext` — 链路追踪上下文

**文件**：`game-agent-common/src/main/java/com/game/agent/common/context/TraceIdContext.java`

通过 `ThreadLocal` 实现每请求一个 traceId，无需手动传递参数。

**核心机制**：
- 初始化：第一次调用 `current()` 时自动生成一个 32 位无横线 UUID
- 生命周期：从 `TraceIdFilter` 进入 → 请求处理完成 → `clear()` 清理
- 隔离性：每个 HTTP 请求独立线程，虚拟线程也不影响 ThreadLocal

**静态方法**：

| 方法 | 说明 |
|------|------|
| `set(traceId)` | 手动设置 traceId |
| `current()` | 获取当前线程的 traceId（不存在则自动生成） |
| `clear()` | 清理当前线程的 traceId（防止内存泄漏） |

### 3.6 `TraceIdFilter` — 链路追踪过滤器

**文件**：`game-agent-common/src/main/java/com/game/agent/common/context/TraceIdFilter.java`

实现 `jakarta.servlet.Filter`，自动为每个 HTTP 请求注入 traceId。

**执行流程**：
1. 从请求头 `X-Trace-Id` 读取（若存在则复用，方便前端传递）
2. 没有则自动生成 UUID
3. 设置到 `TraceIdContext`（供 Java 代码访问）
4. 设置到 SLF4J `MDC`（供日志输出 `[%X{traceId}]`）
5. 设置到响应头 `X-Trace-Id`（返回给客户端）
6. 调用 `chain.doFilter()` 执行后续处理
7. `finally` 中清理 `TraceIdContext` 和 `MDC`

**关键常量**：

| 常量 | 值 | 说明 |
|------|-----|------|
| `TRACE_ID_HEADER` | `"X-Trace-Id"` | 请求/响应头字段名 |
| `MDC_KEY` | `"traceId"` | MDC 中的 key |

### 3.7 `SourceType` — 来源类型枚举

**文件**：`game-agent-common/src/main/java/com/game/agent/common/metadata/SourceType.java`

**枚举值**：

| 常量 | value | 说明 |
|------|-------|------|
| `OFFICIAL` | `"official"` | 官方来源（官网公告、Tencent API） |
| `SOCIAL` | `"social"` | 社媒来源（B站、抖音博主） |
| `SKILL` | `"skill"` | 规则文档（`skills/` 目录下的 Markdown） |

**方法**：

| 方法 | 说明 |
|------|------|
| `value()` | 获取字符串值 |
| `fromValue(String)` | 从字符串反查枚举，不匹配时抛 `IllegalArgumentException` |

### 3.8 `DocumentMetadata` — 文档元数据常量

**文件**：`game-agent-common/src/main/java/com/game/agent/common/metadata/DocumentMetadata.java`

定义 `Document` 的 `metadata` Map 中使用的 key 常量，所有模块统一引用。

**常量**：

| 常量名 | 值 | 说明 |
|--------|-----|------|
| `SOURCE` | `"source"` | 来源名称 |
| `SOURCE_TYPE` | `"source_type"` | 来源类型（official/social/skill） |
| `AUTHOR` | `"author"` | 作者名称（社媒特有） |
| `PUBLISHED_AT` | `"published_at"` | 发布时间 |
| `VERSION_TAG` | `"version_tag"` | 赛季版本号 |
| `CONFIDENCE_SCORE` | `"confidence_score"` | 可信度评分 |
| `CITATION_URL` | `"citation_url"` | 原文引用链接 |

---

## 4. `game-agent-api` 模块

**职责**：REST API 层，处理入站 HTTP 请求，包括跨域配置、API 文档、健康检查和全局异常处理。

**包路径**：`com.game.agent.api`

**依赖**：`game-agent-common`

### 4.1 `WebConfig` — Web 配置

**文件**：`game-agent-api/src/main/java/com/game/agent/api/config/WebConfig.java`

实现 `WebMvcConfigurer`，配置：
- **CORS**：允许 `/api/**` 路径跨域访问（本地开发时前端 不同端口访问）
- **TraceIdFilter 注册**：将 `TraceIdFilter` 注册为 `FilterRegistrationBean`，覆盖 `/*`，优先级 `Integer.MIN_VALUE`（最先执行）

**CORS 配置详情**：

| 属性 | 值 |
|------|-----|
| 路径 | `/api/**` |
| Origin 模式 | `*` |
| 允许方法 | GET, POST, PUT, DELETE, OPTIONS |
| 允许 Headers | `*` |
| 允许凭证 | true |
| 预检缓存 | 3600s |

**Bean 定义**：

| Bean | 类型 | 说明 |
|------|------|------|
| `traceIdFilter()` | `FilterRegistrationBean<TraceIdFilter>` | 注册 TraceIdFilter 到所有 URL |

### 4.2 `OpenApiConfig` — OpenAPI 文档配置

**文件**：`game-agent-api/src/main/java/com/game/agent/api/config/OpenApiConfig.java`

配置 SpringDoc OpenAPI 3 基本信息，Swagger UI 通过 `/swagger-ui.html` 访问。

**Bean 定义**：

| Bean | 说明 |
|------|------|
| `gameAgentOpenAPI()` | 配置 API 标题、描述、版本、联系人信息 |

### 4.3 `HealthController` — 健康检查

**文件**：`game-agent-api/src/main/java/com/game/agent/api/controller/HealthController.java`

**端点**：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/health` | 返回应用健康状态 |

**响应体示例**：
```json
{
  "code": "0",
  "message": "success",
  "data": {
    "status": "UP",
    "liveness": true,
    "readiness": true,
    "uptime_seconds": 3600
  },
  "traceId": "a1b2c3d4e5f67890abcdef1234567890"
}
```

**内部逻辑**：
- 使用 `ApplicationAvailability` 读取 Spring Boot 的 Liveness 和 Readiness 状态
- 使用 `ManagementFactory.getRuntimeMXBean()` 获取 JVM 运行时长

### 4.4 `GlobalExceptionHandler` — 全局异常处理

**文件**：`game-agent-api/src/main/java/com/game/agent/api/handler/GlobalExceptionHandler.java`

扩展 `ResponseEntityExceptionHandler`，统一捕获和处理三类异常。

**异常处理器**：

| 方法 | 捕获异常 | HTTP 状态 | 说明 |
|------|----------|-----------|------|
| `handleBusinessException` | `BusinessException` | 由 ErrorCode 决定 | 提取 ErrorCode 中的 httpStatus 和 message |
| `handleUnhandledException` | `Exception` | 500 | 兜底处理未预期的异常，打印完整堆栈 |
| `handleMethodArgumentNotValid` | `MethodArgumentNotValidException` | 400 | 解析 @Valid 校验失败的字段错误，拼接为可读字符串 |

---

## 5. `game-agent-bootstrap` 模块

**职责**：Spring Boot 启动入口，聚合所有模块的配置和依赖。

**包路径**：`com.game.agent`

### 5.1 `GameAgentApplication` — 启动类

**文件**：`game-agent-bootstrap/src/main/java/com/game/agent/GameAgentApplication.java`

```java
@SpringBootApplication
@EnableScheduling
public class GameAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameAgentApplication.class, args);
    }
}
```

**注解说明**：

| 注解 | 说明 |
|------|------|
| `@SpringBootApplication` | 组合注解 = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`，自动扫描 `com.game.agent` 下所有组件 |
| `@EnableScheduling` | 开启 `@Scheduled` 定时任务支持（为后续官方源定时巡检做准备） |

**Maven 插件配置**（在 `game-agent-bootstrap/pom.xml` 中）：
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <mainClass>com.game.agent.GameAgentApplication</mainClass>
    </configuration>
</plugin>
```

**`game-agent-bootstrap/pom.xml` 依赖汇总**：

| 依赖 | 说明 |
|------|------|
| `game-agent-api` | 引入 controller/handler/config |
| `spring-boot-starter-web` | Tomcat + REST 支持 |
| `spring-boot-starter-actuator` | 健康检查、指标、探针 |
| `spring-ai-alibaba-starter-dashscope` | DashScope 自动配置（ChatModel + EmbeddingModel） |
| `spring-ai-starter-vector-store-elasticsearch` | ES VectorStore 自动配置 |
| `spring-boot-starter-data-mongodb` | MongoDB 自动配置 |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI |
| `spring-cloud-starter-circuitbreaker-resilience4j` | 熔断降级 |
| `logstash-logback-encoder` | 结构化 JSON 日志 |

### 5.2 `application.yml` — 环境配置

**文件**：`game-agent-bootstrap/src/main/resources/application.yml`

**配置结构**：

```yaml
# ─── 服务器 ───
server.port: 8080

# ─── Spring 基础 ───
spring.application.name: game-strategy-agent
spring.profiles.active: dev                  # 默认激活 dev profile

# ─── AI / DashScope ───
spring.ai.dashscope.api-key: ${DASHSCOPE_API_KEY}
spring.ai.dashscope.chat.options.model: qwen-plus
spring.ai.dashscope.embedding.options.model: text-embedding-v4

# ─── ES 向量库 ───
spring.ai.vectorstore.elasticsearch.initialize-schema: true
spring.ai.vectorstore.elasticsearch.index-name: game-strategy-index
spring.ai.vectorstore.elasticsearch.dimensions: 1024   # text-embedding-v4 维度

# ─── 数据源 ───
spring.data.mongodb.uri: ${MONGODB_URI:mongodb://localhost:27017/game-agent}
spring.data.elasticsearch.uris: ${ES_URIS:http://localhost:9200}

# ─── Jackson 序列化 ───
spring.jackson.default-property-inclusion: non_null    # null 字段不输出
spring.jackson.date-format: yyyy-MM-dd'T'HH:mm:ss'Z'
spring.jackson.time-zone: UTC

# ─── API 文档 ───
springdoc.api-docs.path: /api-docs
springdoc.swagger-ui.path: /swagger-ui.html

# ─── Actuator ───
management.endpoints.web.exposure.include: health,info,metrics,prometheus
management.endpoint.health.show-details: always
management.endpoint.health.probes.enabled: true

# ─── 日志 ───
logging.level.com.game.agent: INFO
```

**内置 `dev` profile 覆盖**：
```yaml
spring:
  config:
    activate:
      on-profile: dev
spring.ai.dashscope.api-key: ${DASHSCOPE_API_KEY:sk-placeholder}
logging.level.com.game.agent: DEBUG
logging.pattern.console: "%d ... [%X{traceId}] ..."    # 带 traceId 的控制台格式
```

**环境变量速查**：

| 环境变量 | 用途 | 默认值 |
|----------|------|--------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key | `sk-placeholder` (dev) |
| `MONGODB_URI` | MongoDB 连接地址 | `mongodb://localhost:27017/game-agent` |
| `ES_URIS` | Elasticsearch 连接地址 | `http://localhost:9200` |

### 5.3 `logback-spring.xml` — 日志配置

**文件**：`game-agent-bootstrap/src/main/resources/logback-spring.xml`

**两个 Appender**：

| Appender | 编码器 | 适用环境 |
|----------|--------|----------|
| `CONSOLE` | `LogstashEncoder` (JSON) | staging, prod |
| `CONSOLE_PLAIN` | `%d [%X{traceId}] %-5level %logger - %msg` | dev |

`LogstashEncoder` 输出示例：
```json
{"@timestamp":"2026-05-26T10:00:00.000Z","level":"INFO","logger":"c.g.a.api.handler.GlobalExceptionHandler","traceId":"a1b2c3d...","message":"...","thread":"http-nio-8080-exec-1"}
```

---

## 6. 工程配置文档

### 6.1 `docker-compose.yml`

**文件**：`docker-compose.yml`

提供本地开发所需的第三方服务：

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| `elasticsearch` | docker.elastic.co/elasticsearch/elasticsearch:8.17.4 | 9200/9300 | 向量库 + BM25 |
| `mongodb` | mongo:7.0 | 27017 | 会话存储 |

启动：`docker-compose up -d`

### 6.2 Maven Wrapper

**文件**：
- `mvnw.cmd` — Windows 批处理
- `mvnw` — Unix Shell
- `.mvn/wrapper/maven-wrapper.properties` — 版本配置（Maven 3.9.9）

使用方式：`./mvnw.cmd clean compile`（Windows）或 `./mvnw clean compile`（Unix）

### 6.3 `.gitignore`

**当前忽略**：`target/`、`*.class`、`.idea/`、`.env`、`.claude/`、`*.bat`、`node_modules/`

---

## 7. 占位模块说明

以下模块已创建 `pom.xml` 但尚未编写 Java 代码，规划中的作用如下：

| 模块 | 预期职责 | 依赖 | 计划任务 |
|------|----------|------|----------|
| **`game-agent-ingestion`** | 官方公告定时采集与社媒策略采集、HTML 清洗、Document 构建 | common | `official-ingestion` + `social-ingestion` |
| **`game-agent-knowledge`** | ES VectorStore 封装、混合检索（向量 + BM25 + Rerank） | common | `hybrid-retrieval` |
| **`game-agent-core`** | Agent 编排层：ReactAgent/Graph、`@Tool` 方法、多模型路由 | knowledge + memory | `agent-mvp` |
| **`game-agent-memory`** | MongoDB 会话 CRUD、ChatMemory 管理、超 10 条消息触发蒸馏归档 | common + spring-boot-starter-data-mongodb | `memory-distill` |
| **`game-agent-render`** | 阵容图结构化渲染（JSON → Canvas/SVG），不涉及前端代码 | common | `minimal-frontend` 中涉及 |

---

## 8. 附录：开发环境启动

### 8.1 前置条件

- JDK 21（`JAVA_HOME=E:\downloads\Java\jdk-21.0.11`）
- Docker Desktop（可选，用于本地 ES + MongoDB）
- Dashboard API Key（可选，dev 模式可使用占位符）

### 8.2 启动步骤

```powershell
# 1. 启动依赖服务（ES + MongoDB）
docker-compose up -d

# 2. 编译（需要先设置 JDK 21）
$env:JAVA_HOME = "E:\downloads\Java\jdk-21.0.11"
$env:DASHSCOPE_API_KEY = "sk-placeholder"
$env:MONGODB_URI = "mongodb://localhost:27017/game-agent"
$env:ES_URIS = "http://localhost:9200"
./mvnw.cmd clean compile

# 3. 运行
./mvnw.cmd spring-boot:run -pl game-agent-bootstrap

# 4. 验证
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
```

### 8.3 验证清单

| 检查项 | 预期结果 |
|--------|----------|
| `GET /api/v1/health` | `{"code":"0","data":{"status":"UP",...},"traceId":"..."}` |
| `GET /actuator/health` | Spring Boot Actuator 完整健康详情 |
| `GET /unexisting-path` | 500 错误，带 `{"code":"Z0001","traceId":"..."}` |
| 响应头 | `X-Trace-Id` 存在且不为空 |
| Swagger UI | `http://localhost:8080/swagger-ui.html` 可访问 |
