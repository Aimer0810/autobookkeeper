# AutoBookkeeper 云端可用架构设计

## 1. 背景与目标

AutoBookkeeper 是一个面向 iPhone 日常使用的智能截图记账助手。用户通过 iOS 快捷指令分享支付截图，系统完成图像识别、账单结构化、自动分类、持久化存储和 Web 查询。

由于用户的电脑不能长期运行项目，默认部署方式从“局域网电脑后端”调整为“云端轻量 Java 后端”。Vercel 不作为 Java 主后端，仅作为后续可选的 Web 前端托管平台。

第一版目标是交付一个可运行、可部署、隐私边界清晰的 Java Spring Boot 项目骨架，支持本地开发、云端部署和家庭服务器部署三种 profile。

## 2. 推荐部署架构

默认采用云端轻量 Java 后端：

```text
iPhone 快捷指令
  -> HTTPS POST /api/process
  -> Spring Boot Java 后端
  -> AI/OCR 识别管线
  -> AccountingEngine 分类与记账
  -> PostgreSQL 或本地开发数据库
  -> Web UI / REST API 查询
```

部署目标优先级：

1. Render / Railway / Fly.io：适合个人项目和 Spring Boot 长驻服务。
2. 云服务器 / VPS：适合长期稳定运行和完整控制。
3. NAS / 树莓派 / 家庭服务器：适合隐私优先场景，配合 Tailscale 或 ZeroTier。
4. Vercel：仅作为可选前端托管，不承载 Java OCR 和记账核心逻辑。

## 3. Spring Profiles

项目内置三个运行模式：

- `local`：本地开发，使用 H2 文件数据库，允许较详细日志。
- `cloud`：云端生产，使用 PostgreSQL，启用 API Token 鉴权和脱敏日志。
- `home`：家庭服务器，默认局域网或 Tailscale 访问，可使用 SQLite/H2 或 PostgreSQL。

## 4. 核心模块

### 4.1 API 层

提供 REST API：

- `POST /api/process`：接收 Base64 图片，返回识别和记账结果。
- `GET /api/transactions`：分页查询账目。
- `GET /api/transactions/{id}`：查询单笔账目详情。
- `GET /api/health`：健康检查。

所有写入接口在 `cloud` 和 `home` 模式下要求请求头：

```text
X-API-Token: <user-configured-token>
```

### 4.2 AI/OCR 层

定义统一接口：

```text
AIService.extractBillFromImage(byte[] imageData)
```

实现类：

- `CloudVisionServiceImpl`：调用外部视觉大模型 API，密钥占位符统一为 `{{API_KEY}}`。
- `TesseractOCRServiceImpl`：离线 OCR 替补方案。
- `LocalOCRServiceImpl`：为后续本地模型或私有模型预留扩展点。

识别策略：

1. 如果启用云端 AI，优先调用 `CloudVisionServiceImpl`。
2. 如果云端 AI 超时、失败或返回低置信度，降级到 OCR。
3. 如果识别仍不足，返回需要人工复核的结果，不崩溃。

### 4.3 记账与分类层

`AccountingEngine` 接收 `Bill`，输出 `Transaction`。

分类规则来自：

```text
src/main/resources/category_rules.properties
```

规则示例：

```properties
餐饮=麦当劳,肯德基,星巴克,瑞幸,美团,饿了么
交通=滴滴,高德打车,地铁,公交
购物=淘宝,京东,拼多多,抖音商城
```

分类顺序：

1. 优先使用用户历史修正规则。
2. 其次匹配配置文件关键词。
3. 再使用 AI 返回分类。
4. 最后归类为“未分类”。

### 4.4 数据层

实体包括：

- `Bill`：AI/OCR 提取出的账单草稿。
- `Transaction`：最终入库账目。
- `ProcessingRecord`：处理审计记录。

生产环境默认 PostgreSQL。本地开发使用 H2。数据库中保留：

- 交易日期
- 金额
- 商家
- 分类
- 置信度
- 原始 OCR 文本
- 结构化识别 JSON
- 处理状态
- 创建时间

截图原图默认不长期保存，避免隐私泄露。后续可通过配置启用加密保存。

## 5. 安全设计

云端部署必须默认包含以下约束：

- API Token 鉴权。
- HTTPS 部署说明。
- 请求体大小限制。
- 日志脱敏，不打印完整金额、商家和 OCR 原文。
- API Key 不写入代码，只通过环境变量或配置注入。
- 外部 AI 服务调用可关闭。
- 原始截图默认不持久化。

配置项示例：

```yaml
autobookkeeper:
  api-token: ${AUTOBOOKKEEPER_API_TOKEN}
  ai:
    provider: cloud
    api-key: ${VISION_API_KEY:{{API_KEY}}}
    timeout-ms: 2500
  privacy:
    persist-original-image: false
    redact-logs: true
```

## 6. iPhone 集成方案

使用 iOS 快捷指令完成最小客户端：

1. 接收共享表单中的图片。
2. 将图片转换为 Base64。
3. 构造 JSON：

```json
{
  "imageBase64": "...",
  "source": "ios-shortcuts"
}
```

4. 发送 POST 请求到：

```text
https://<your-backend-domain>/api/process
```

5. 添加请求头：

```text
Content-Type: application/json
X-API-Token: <your-token>
```

6. 显示后端返回的商家、金额、分类和状态。

## 7. Web UI 策略

第一版由 Spring Boot 提供静态 Web UI，用于降低部署复杂度：

- 首页显示账目列表。
- 支持按月份和分类筛选。
- 支持查看单笔账目的识别文本和结构化数据。

Vercel 后续可作为独立前端托管方案，但第一版不强依赖它。

## 8. 错误处理与降级

- AI 服务超时：降级 OCR。
- OCR 低置信度：返回人工复核状态。
- 图片过大：返回清晰错误提示。
- API Token 缺失：返回 401。
- 数据库写入失败：返回 500，同时记录脱敏错误日志。

## 9. 测试策略

第一版包含：

- `AccountingEngine` 分类规则单元测试。
- `AIService` JSON 解析单元测试。
- `TransactionRepository` 数据持久化测试。
- `ProcessController` API 鉴权和请求校验测试。

## 10. 第一版交付范围

生成完整 Maven 项目骨架：

- `pom.xml`
- Spring Boot 启动类
- Controller / Service / Repository / Entity / DTO
- AIService 及三种实现类
- AccountingEngine
- 配置文件和分类规则
- 静态 Web UI
- README
- iPhone 快捷指令集成手册
- Render/Railway/Fly.io 部署说明
- Vercel 前端可选说明

第一版不包含完整 SwiftUI App，不包含真实 AI Key，不包含生产级多用户系统。
