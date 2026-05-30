# 金铲铲之战 AI 策略 Agent 后端

## 项目概述

基于 Spring Boot 3.5 + Spring AI Alibaba 的企业级游戏策略 AI Agent 后端，为《金铲铲之战》（Teamfight Tactics Mobile）提供实时策略建议、阵容推荐和内容采集服务。采用双源 RAG（官方 + 社媒）、事件驱动多 Agent 协作架构。

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言/框架 | Java 21, Spring Boot 3.5.4, Spring AI 1.1.2 |
| AI | DashScope Qwen-Plus (Chat), text-embedding-v4 (Embedding), Rerank |
| 存储 | Elasticsearch 8.x (向量 + BM25), MongoDB 7.x (会话) |
| 安全 | Spring Security + JWT, 角色权限管理 (ADMIN/ANALYST/BASIC) |
| 构建 | Maven 多模块 (8 个子模块) |
| 部署 | Docker Compose (ES + MongoDB) |

## 模块架构

```
game-strategy-agent
├── game-agent-common       — 通用基础设施（统一响应/错误码/异常体系/链路追踪）
├── game-agent-ingestion    — 双源采集流水线（官方网页/B站社媒/本地文件 → 分块 → Embedding → ES）
├── game-agent-knowledge    — 混合检索（向量召回 + DashScope Rerank 精排 + Citations 协议）
├── game-agent-core         — 多 Agent 编排中心（SupervisorAgent + 事件驱动子 Agent）
├── game-agent-memory       — 会话管理（MongoDB CRUD + LLM 自动蒸馏）
├── game-agent-api          — REST API 层（Chat/Ingestion/Auth 控制器 + Spring Security）
├── game-agent-render       — 阵容图渲染（结构化数据模型 → SVG 棋盘生成器）
└── game-agent-bootstrap    — Spring Boot 启动入口 + 配置聚合
```

## 核心亮点

### 1. 双源 RAG 知识库
- **官方源**：定时采集金铲铲之战官网公告，Jsoup 清洗后经分块/Embedding 入库到 ES 向量库
- **社媒源**：B站 UP 主持定采集（红莲/神超等），空间页视频列表解析 + URL 去重
- **三种入库方式**：HTTP 文件上传、URL 导入、本地文件路径导入
- **混合检索**：ES 向量召回(topK×3) → DashScope Rerank 精排 → 带 Citations 返回

### 2. 数据来源权限管理
- Spring Security + JWT 令牌认证
- 基于角色的数据源访问控制：
  - **ADMIN**：official + social + skill 全源访问
  - **ANALYST**：official + social（策略分析）
  - **BASIC**：仅 official（基础查询）
- 权限颗粒度到每个 source_type，拦截器校验 + 注解式权限检查
- 预置用户：admin/admin123, analyst/analyst123, basic/basic123

### 3. 事件驱动多 Agent 协作
- **SupervisorAgent**：意图识别器，接收用户消息后通过事件总线分发给子 Agent
- **SearchAgent**：知识库检索 Agent，异步监听 SearchEvent，调用混合检索服务
- **StrategyAgent**：策略生成 Agent，基于检索结果调用 Qwen-Plus 生成策略建议
- **LineupAgent**：阵容图渲染 Agent，将结构化阵容数据渲染为 SVG 棋盘图
- 基于 Spring ApplicationEventPublisher + @Async 实现异步并行处理

### 4. 对话管理与会话蒸馏
- MongoDB 持久化会话和消息
- 每 10 条消息触发 LLM 自动蒸馏摘要，压缩历史上下文
- getHistory() 将已蒸馏消息转为 SystemMessage，保持 ChatClient 上下文窗口可控

### 5. 阵容 SVG 渲染
- 4×7 棋盘网格布局，深色主题
- 英雄按费用着色（白/绿/蓝/紫/金），星级用金色 ★ 标示
- 装备用小色块 + 缩写显示，右侧面板展示羁绊和装备池
- 每个英雄显示位置、星级、装备信息

### 6. API 端点

| 端点 | 说明 |
|------|------|
| POST /api/v1/auth/login | 登录获取 JWT |
| POST /api/v1/chat | 同步问答（带历史上下文） |
| GET /api/v1/chat/stream | SSE 流式问答 |
| POST /api/v1/ingest/url | URL 导入知识库 |
| POST /api/v1/ingest/document | 文件上传入库 |
| POST /api/v1/ingest/local-file | 本地文件路径入库 |
| POST /api/v1/ingest/social/fetch-all | 批量采集 B站 UP 主 |
| POST /api/v1/ingest/social/url | 单个 B站视频入库 |
