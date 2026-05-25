# 游戏策略 Agent — API 设计文档

> 对应实施计划：`game-agent-mvp-plan.md`
> 版本：v1.0 | 最后更新：2026-05-25

---

## 一、通用约定

### 1.1 基础 URL

| 环境 | 基础 URL |
|------|----------|
| 本地开发 | `http://localhost:8080` |
| Staging | `https://staging-api.game-agent.example.com` |
| Production | `https://api.game-agent.example.com` |

所有 API 以 `/api/v1/` 为前缀（Actuator 端点除外）。

### 1.2 认证方式

**Bearer JWT**：

```
Authorization: Bearer <jwt-token>
```

- Token 通过 OAuth2 授权流程获取（Spring Security Resource Server）
- Token 中携带 `user_id`、`role`、`exp`（过期时间）
- 所有 API（除 `/actuator/health` 外）均需鉴权

### 1.3 通用响应格式

**成功响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "trace_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**错误响应**：

```json
{
  "code": 10001,
  "message": "知识库检索失败",
  "detail": "ES 连接超时",
  "trace_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**分页请求参数**：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 0 | 页码（从 0 开始） |
| `size` | int | 20 | 每页条数（max 100） |
| `sort` | string | `created_at,desc` | 排序字段和方向 |

**分页响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "total_elements": 100,
    "total_pages": 5
  },
  "trace_id": "..."
}
```

### 1.4 错误码速查

| 错误码 | HTTP 状态码 | 说明 |
|--------|-------------|------|
| `0` | 200 | 成功 |
| `A0001` | 500 | Agent 意图识别失败 |
| `A0002` | 500 | LLM 响应超时 |
| `A1000` | 400 | 工具调用参数校验失败 |
| `A1001` | 500 | 工具执行超时 |
| `K0001` | 500 | ES 连接失败 |
| `K0002` | 500 | 索引不存在或维度不匹配 |
| `K1000` | 404 | 检索无结果 |
| `K1001` | 500 | Rerank 排序失败 |
| `I0001` | 400 | URL 不可达或格式不正确 |
| `I0002` | 400 | 文档格式不支持 |
| `I1000` | 500 | Embedding 失败 |
| `I1001` | 500 | ES 写入失败 |
| `G0001` | 401 | Token 过期或无效 |
| `G0002` | 403 | 权限不足 |
| `G0003` | 429 | 请求频率超限 |
| `G1000` | 400 | 请求参数校验失败 |

### 1.5 SSE 事件类型枚举

| 事件名 | 说明 | data 字段 |
|--------|------|-----------|
| `message` | 流式文本片段 | `{"text": "..."}` |
| `citations` | 引用来源 | `{"citations": [...]}` |
| `lineup_card` | 阵容图 | `{"image_url": "...", "board": {...}}` |
| `voice_text` | TTS 精简文本 | `{"text": "..."}` |
| `done` | 流结束 | `{"message_id": "..."}` |
| `error` | 错误 | `{"code": "...", "message": "...", "trace_id": "..."}` |

---

## 二、采集与知识库 API

### 2.1 上传文档

上传本地文档（支持 Markdown、TXT、PDF）进行知识库导入。

```
POST /api/v1/ingest/document
```

**请求**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | 文档文件，支持 `.md`、`.txt`、`.pdf`，最大 10MB |
| `source_type` | String | 否 | 手动指定来源类型：`official` / `social` / `skill`。默认 auto |
| `tags` | String | 否 | 逗号分隔的标签，如 `s14,阵容,法师` |

**响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "task_id": "ingest_task_uuid",
    "status": "PENDING",
    "document_name": "s14-patch-notes.md",
    "estimated_duration_ms": 5000
  },
  "trace_id": "..."
}
```

### 2.2 链接导入

```
POST /api/v1/ingest/url
```

**请求体**：

```json
{
  "url": "https://example.com/s14-patch-notes",
  "source_type": "official",
  "tags": "s14,版本公告"
}
```

**响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "task_id": "ingest_task_uuid",
    "status": "PENDING",
    "url": "https://example.com/s14-patch-notes"
  },
  "trace_id": "..."
}
```

### 2.3 查询采集任务状态

```
GET /api/v1/ingest/tasks/{taskId}
```

**响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "task_id": "ingest_task_uuid",
    "status": "RUNNING",
    "progress": {
      "fetched": 1,
      "chunked": 5,
      "embedded": 3,
      "indexed": 0,
      "total": 10
    },
    "created_at": "2026-05-25T10:00:00Z",
    "updated_at": "2026-05-25T10:00:05Z",
    "error_message": null
  },
  "trace_id": "..."
}
```

可能的状态值：`PENDING` → `RUNNING` → `SUCCESS` / `FAILED`

### 2.4 手动触发定时采集

```
POST /api/v1/ingest/trigger
```

**请求体**：

```json
{
  "source": "official",
  "priority": "HIGH"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source` | String | 否 | 指定采集源：`official` / `social` / `all`。默认 `all` |
| `priority` | String | 否 | `HIGH` / `NORMAL`。默认 `NORMAL` |

**响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "trigger_id": "trigger_uuid",
    "task_ids": ["task_1", "task_2"],
    "status": "PENDING"
  },
  "trace_id": "..."
}
```

### 2.5 查询采集任务列表

```
GET /api/v1/ingest/tasks?status=RUNNING&page=0&size=20
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `status` | String | 否 | 按状态过滤：`PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| `source_type` | String | 否 | 按来源类型过滤：`official` / `social` / `skill` |

---

## 三、对话 API

### 3.1 同步问答

```
POST /api/v1/chat
```

**请求体**：

```json
{
  "session_id": "session_uuid",
  "message": "S14 赛季什么阵容最强？",
  "season_tag": "s14",
  "voice_mode": false
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | String | 否 | 会话 ID。不传则创建新会话 |
| `message` | String | 是 | 用户输入文本，最大 2000 字符 |
| `season_tag` | String | 否 | 指定赛季版本。默认使用当前活跃赛季 |
| `voice_mode` | Boolean | 否 | 是否启用语音模式（返回 `voice_text`） |

**响应**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "message_id": "msg_uuid",
    "session_id": "session_uuid",
    "text_advice": "当前版本 T0 阵容是命运法师...",
    "lineup_card": {
      "image_url": "https://cdn.game-agent.example.com/lineups/msg_uuid.png",
      "board": {
        "champions": [
          {"name": "安妮", "position": [1, 2], "items": ["青龙刀", "法爆"], "star": 3},
          {"name": "维迦", "position": [2, 2], "items": ["蓝buff", "科技枪"], "star": 2}
        ],
        "synergies": ["7 法师", "2 神谕", "2 命运"],
        "level": 8
      }
    },
    "citations": [
      {
        "source": "金铲铲之战官网",
        "source_type": "official",
        "title": "S14 版本更新公告",
        "url": "https://...",
        "snippet": "新增命运法师羁绊..."
      },
      {
        "source": "红莲",
        "source_type": "social",
        "title": "红莲 S14 法师阵容教学",
        "url": "https://...",
        "snippet": "法师是目前版本最稳定的上分选择"
      }
    ],
    "voice_text": "当前版本 T0 是命运法师，核心是安妮和维迦。",
    "tokens_used": 1250,
    "model_used": "qwen-plus"
  },
  "trace_id": "..."
}
```

### 3.2 SSE 流式问答

```
GET /api/v1/chat/stream?session_id=xxx&message=xxx&season_tag=s14
```

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | String | 否 | 会话 ID |
| `message` | String | 是 | 用户输入（URL 编码） |
| `season_tag` | String | 否 | 赛季版本 |

**响应**：`text/event-stream`

```
event: message
data: {"text": "当前版本"}

event: message
data: {"text": " T0 阵容是命运法师。"}

event: citations
data: {"citations": [{"source": "金铲铲之战官网", "url": "..."}]}

event: lineup_card
data: {"image_url": "https://...", "board": {...}}

event: voice_text
data: {"text": "当前版本 T0 是命运法师。"}

event: done
data: {"message_id": "msg_uuid", "session_id": "session_uuid", "tokens_used": 1250}
```

客户端应持续读取直到收到 `event: done`。

### 3.3 语音输入（STT）

```
POST /api/v1/chat/voice
```

**请求**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `audio` | File | 是 | 音频文件，支持 WAV/MP3/M4A，最大 5MB，最长 30s |
| `session_id` | String | 否 | 会话 ID |
| `season_tag` | String | 否 | 赛季版本 |

**响应**：同 `POST /api/v1/chat`（同步问答），额外包含 `transcribed_text`

```json
{
  "code": 0,
  "data": {
    "transcribed_text": "S14 什么阵容最强",
    "text_advice": "...",
    "voice_text": "..."
  }
}
```

### 3.4 TTS 播报文本获取

```
GET /api/v1/chat/voice/{messageId}
```

**响应**：直接返回音频流（`audio/wav`），内容为 `voice_text` 的语音合成结果。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `speed` | Float | 否 | 语速倍率，0.5-2.0。默认根据用户偏好 |

### 3.5 会话列表

```
GET /api/v1/chat/sessions?page=0&size=20
```

**响应**：

```json
{
  "code": 0,
  "data": {
    "content": [
      {
        "session_id": "session_uuid",
        "title": "S14 法师阵容讨论",
        "message_count": 12,
        "last_message_at": "2026-05-25T10:30:00Z",
        "season_tag": "s14",
        "created_at": "2026-05-25T09:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total_elements": 5,
    "total_pages": 1
  }
}
```

### 3.6 会话历史

```
GET /api/v1/chat/sessions/{sessionId}/messages?page=0&size=50
```

**响应**：

```json
{
  "code": 0,
  "data": {
    "content": [
      {
        "role": "user",
        "text": "S14 什么阵容最强？",
        "created_at": "2026-05-25T10:00:00Z"
      },
      {
        "role": "assistant",
        "text": "当前版本 T0 阵容是命运法师...",
        "citations": [...],
        "created_at": "2026-05-25T10:00:02Z"
      }
    ]
  }
}
```

---

## 四、反馈 API

### 4.1 提交反馈

```
POST /api/v1/feedback
```

**请求体**：

```json
{
  "message_id": "msg_uuid",
  "rating": 4,
  "feedback_type": "赞",
  "comment": "回答很有帮助，但阵容站位可以更详细",
  "correct_answer": "建议补充：安妮放第二排中间，维迦放角落"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message_id` | String | 是 | 关联的消息 ID |
| `rating` | Int | 是 | 1-5 星评分 |
| `feedback_type` | String | 否 | `赞` / `踩` / `纠错` |
| `comment` | String | 否 | 用户补充说明，最大 500 字符 |
| `correct_answer` | String | 否 | 用户认为的正确回答（用于纠错场景） |

### 4.2 导出评估数据集

```
GET /api/v1/feedback/dataset?min_rating=3&from=2026-05-01&to=2026-05-25&page=0&size=50
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `min_rating` | Int | 否 | 最低评分过滤（常用于导出低分数据做分析） |
| `from` | String | 否 | 开始日期（ISO 8601） |
| `to` | String | 否 | 结束日期（ISO 8601） |

**响应**：返回可用于 Agent 评估的标准化问答对列表

```json
{
  "code": 0,
  "data": {
    "content": [
      {
        "question": "S14 什么阵容最强？",
        "answer": "当前版本 T0 阵容是命运法师...",
        "rating": 2,
        "feedback_type": "踩",
        "correct_answer": "建议补充安妮和维迦的出装顺序",
        "created_at": "2026-05-25T10:00:00Z"
      }
    ]
  }
}
```

---

## 五、管理 API

### 5.1 重建知识库索引

```
POST /api/v1/admin/knowledge/reindex
```

**请求体**：

```json
{
  "source_type": "all",
  "season_tag": "s14",
  "confirm": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source_type` | String | 否 | 重建范围：`all` / `official` / `social` / `skill` |
| `season_tag` | String | 否 | 指定赛季。默认全部赛季 |
| `confirm` | Boolean | 是 | 二次确认，必须为 `true` |

### 5.2 更新赛季配置

```
PUT /api/v1/admin/season
```

**请求体**：

```json
{
  "season": "S14",
  "version_tag": "14.5",
  "start_date": "2026-05-15",
  "official_sources": [
    {"url": "https://game.gtimg.cn/..."}
  ],
  "social_sources": [
    {"platform": "bilibili", "creators": ["红莲", "神超"]}
  ],
  "skill_files": ["synergies.md", "economy.md"]
}
```

### 5.3 健康检查

```
GET /api/v1/admin/health
```

**响应**：

```json
{
  "status": "UP",
  "components": {
    "dashscope": { "status": "UP", "latency_ms": 320 },
    "elasticsearch": { "status": "UP", "cluster_status": "green", "latency_ms": 12 },
    "mongodb": { "status": "UP", "latency_ms": 5 },
    "ingest_queue": { "status": "UP", "queue_depth": 3, "max_capacity": 1000 }
  },
  "uptime_seconds": 86400
}
```

### 5.4 Actuator 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `GET /actuator/health` | 公开 | 聚合健康状态 |
| `GET /actuator/health/liveness` | 公开 | K8s 存活探针 |
| `GET /actuator/health/readiness` | 公开 | K8s 就绪探针 |
| `GET /actuator/metrics` | 认证 | Prometheus 指标 |
| `GET /actuator/metrics/{name}` | 认证 | 单一指标详情 |
| `GET /actuator/prometheus` | 认证 | Prometheus 抓取端点 |
| `GET /actuator/info` | 可选 | 应用信息 |

---

## 六、MCP 工具协议

MCP 工具运行在 Agent 内部，由 ReactAgent 根据用户意图自动调用，不对外暴露 REST 接口。

### 6.1 工具元数据

| 工具名称 | 描述 | 调用条件 |
|----------|------|----------|
| `searchOfficialMeta` | 搜索官方元数据（版本公告、平衡改动） | 用户询问版本更新、平衡性调整 |
| `searchSocialStrategy` | 搜索社媒策略内容（博主阵容、站位） | 用户询问阵容推荐、运营思路 |
| `loadSkillDoc` | 读取本地策略技能规则文档 | 用户询问规则类问题（装备合成、羁绊） |

### 6.2 searchOfficialMeta

```json
{
  "name": "searchOfficialMeta",
  "description": "搜索官方元数据（版本公告/平衡改动）",
  "input_schema": {
    "type": "object",
    "properties": {
      "versionTag": {
        "type": "string",
        "description": "赛季版本号，如 's14'"
      },
      "query": {
        "type": "string",
        "description": "用户问题关键词"
      }
    },
    "required": ["versionTag", "query"]
  },
  "output_schema": {
    "type": "array",
    "items": {
      "type": "object",
      "properties": {
        "text": { "type": "string" },
        "metadata": {
          "type": "object",
          "properties": {
            "source": { "type": "string" },
            "source_type": { "type": "string", "enum": ["official"] },
            "published_at": { "type": "string", "format": "date-time" },
            "citation_url": { "type": "string", "format": "uri" },
            "confidence_score": { "type": "number" }
          }
        }
      }
    }
  }
}
```

### 6.3 searchSocialStrategy

```json
{
  "name": "searchSocialStrategy",
  "description": "搜索社媒策略内容（博主阵容/站位/节奏）",
  "input_schema": {
    "type": "object",
    "properties": {
      "query": {
        "type": "string",
        "description": "检索关键词"
      },
      "minConfidence": {
        "type": "number",
        "description": "最低可信度阈值 0.0-1.0"
      }
    },
    "required": ["query"]
  },
  "output_schema": {
    "type": "array",
    "items": {
      "type": "object",
      "properties": {
        "text": { "type": "string" },
        "metadata": {
          "type": "object",
          "properties": {
            "source": { "type": "string" },
            "source_type": { "type": "string", "enum": ["social"] },
            "author": { "type": "string" },
            "confidence_score": { "type": "number" },
            "published_at": { "type": "string", "format": "date-time" }
          }
        }
      }
    }
  }
}
```

### 6.4 loadSkillDoc

```json
{
  "name": "loadSkillDoc",
  "description": "读取本地策略技能规则文档（最高优先级）",
  "input_schema": {
    "type": "object",
    "properties": {
      "skillName": {
        "type": "string",
        "description": "文档名，如 'synergies', 'economy', 'positioning'",
        "enum": ["synergies", "economy", "positioning", "items"]
      }
    },
    "required": ["skillName"]
  },
  "output_schema": {
    "type": "object",
    "properties": {
      "content": {
        "type": "string",
        "description": "Markdown 格式的完整规则文档内容"
      }
    }
  }
}
```

---

## 七、阵容图 JSON Schema

`lineup_card` 的结构化输出协议：

```json
{
  "image_url": "https://cdn.game-agent.example.com/lineups/msg_uuid.png",
  "board": {
    "level": 8,
    "experience": "6/10",
    "gold": 42,
    "champions": [
      {
        "name": "安妮",
        "cost": 2,
        "star": 3,
        "position": [1, 2],
        "items": ["青龙刀", "法爆", "帽子"],
        "synergies": ["法师", "神谕"]
      }
    ],
    "bench": [
      {
        "name": "阿狸",
        "cost": 4,
        "star": 1,
        "items": []
      }
    ],
    "active_synergies": [
      {"name": "7 法师", "description": "法师获得 120% 法术强度"},
      {"name": "2 神谕", "description": "每 2 秒回复 15 法力值"}
    ],
    "item_pool": {
      "components": ["大棒", "眼泪", "大剑"],
      "completed": ["青龙刀", "法爆"]
    },
    "position_map": {
      "grid": [
        [null, null, null, null],
        [null, null, null, null],
        [{"name": "安妮", "star": 3}, null, null, null],
        [null, {"name": "维迦", "star": 2}, null, null]
      ]
    }
  }
}
```

**一致性约束**：阵容图中的所有信息必须来自 Agent 输出的结构化 JSON，禁止从自然语言自由「画」阵容。前端渲染引擎根据此 JSON 生成统一的 Canvas/SVG 布局。

---

## 八、语音交互协议

### 8.1 交互流程

```
用户(按住录音) → 语音输入 → STT(百炼语音API) → 转录文本 → Agent处理
Agent返回 voice_text → TTS(百炼语音API) → 音频播报(可打断)
```

### 8.2 协议约定

| 阶段 | 说明 | 实现方式 |
|------|------|----------|
| 输入 | 按住录音 → STT 转录 | `POST /api/v1/chat/voice` 上传音频 |
| 输出 | Agent 返回精简 `voice_text` | SSE `event: voice_text` 事件 |
| 打断 | 新语音输入自动停止当前播报 | 前端 cancel 上一个 AudioContext |
| 战斗回合 | 优先输出「10 秒可执行动作」 | Agent system prompt 阶段感知 |
| 长回答 | 用户说「继续说细节」继续展开 | 会话 ID 上下文延续 |

### 8.3 voice_text 输出规范

- 长度：中文 30-80 字（约 10-20 秒播报时长）
- 内容：仅包含「行动指令 + 关键依据」，去掉数字细节和引用信息
- 示例：
  > **好**：当前推荐走法师阵容，核心拿安妮和维迦，优先抢大棒和眼泪。
  > **不好**：根据官方 S14 版本公告，新增了命运法师羁绊（来源: 金铲铲官网），建议拿安妮（3费卡）、维迦（4费卡）...

---

## 文档维护约定

1. **接口变更时**：同时更新 `docs/api-design.md` 和 `game-agent-mvp-plan.md` 中对应的 API 概览章节
2. **新增接口**：按照本文件编号规则追加，不破坏现有编号
3. **重大变更**（路径变更、破坏性修改）：更新文档版本号并记录变更日志
