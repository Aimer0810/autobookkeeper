# AutoBookkeeper

AutoBookkeeper 是一个面向 iPhone 日常使用的智能截图记账助手。iPhone 通过快捷指令分享支付截图，Java Spring Boot 后端完成 AI/OCR 识别、自动分类、入库和 Web 查询。

## 推荐架构

- Java 后端部署到 Render、Railway、Fly.io 或云服务器。
- iPhone 快捷指令通过 HTTPS 调用 `/api/process`。
- 本地开发使用 H2，云端生产建议使用 PostgreSQL。
- Vercel 仅作为后续可选前端托管，不作为 Java 主后端。

## 快速启动

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

打开：

```text
http://localhost:8080
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

响应会包含运行状态、版本和当前 profile：

```json
{
  "status": "UP",
  "version": "0.1.0",
  "profiles": ["local"]
}
```

## 本地 iPhone 联调启动

先打包应用：

```powershell
mvn package "-DskipTests"
```

设置本地访问 Token 和 AI Key：

```powershell
$env:AUTOBOOKKEEPER_API_TOKEN="your-long-random-token"
$env:VISION_API_KEY="your-dashscope-api-key"
```

启动本地后端：

```powershell
.\scripts\start-local.ps1
```

脚本默认使用阿里云百炼 OpenAI 兼容接口：

```text
VISION_API_ENDPOINT=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
VISION_MODEL=qwen3.6-flash
AUTOBOOKKEEPER_AI_TIMEOUT_MS=30000
```

如需覆盖默认值，可在启动脚本前设置同名环境变量。

检查 AI 配置是否生效：

```powershell
Invoke-RestMethod http://localhost:8080/api/health | ConvertTo-Json -Depth 5
```

确认响应中：

```json
{
  "ai": {
    "apiKeyConfigured": true,
    "endpoint": "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
    "model": "qwen3.6-flash",
    "timeoutMs": 30000
  }
}
```

## 环境变量

```text
AUTOBOOKKEEPER_API_TOKEN=your-long-random-token
VISION_API_KEY={{API_KEY}}
VISION_API_ENDPOINT=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
VISION_MODEL=qwen3.6-flash
AUTOBOOKKEEPER_AI_TIMEOUT_MS=30000
SPRING_PROFILES_ACTIVE=cloud
DATABASE_URL=jdbc:postgresql://host:5432/autobookkeeper
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your-password
```

可以复制 `.env.example` 作为部署平台的环境变量清单。不要把真实 `.env` 文件提交到 Git。

使用阿里云百炼 OpenAI 兼容接口时，可以设置：

```text
VISION_API_ENDPOINT=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
VISION_MODEL=qwen3.6-flash
```

## Docker 部署

项目包含 `Dockerfile`，可用于 Render、Railway、Fly.io 或普通 VPS：

```powershell
docker build -t autobookkeeper .
docker run -p 8080:8080 --env-file .env autobookkeeper
```

如果你的本地 Docker 网络无法稳定访问 Maven Central，可以先在宿主机打包，再用 `Dockerfile.runtime` 验证运行镜像：

```powershell
mvn package
docker build -f Dockerfile.runtime -t autobookkeeper:runtime-local .
docker run -p 18080:8080 -e SPRING_PROFILES_ACTIVE=local autobookkeeper:runtime-local
Invoke-RestMethod http://localhost:18080/api/health
```

## Render Blueprint

项目包含 `render.yaml`，可在 Render 中创建 Web Service 和 PostgreSQL 数据库。创建后请在 Render 控制台手动设置：

```text
AUTOBOOKKEEPER_API_TOKEN
VISION_API_KEY
VISION_API_ENDPOINT
```

Render 的 PostgreSQL `connectionString` 默认是 `postgresql://...`。项目的 Docker 入口脚本会自动转换为 Spring Boot 需要的 `jdbc:postgresql://...`。

Render 会为 Web Service 注入 `PORT` 环境变量，应用会通过 `server.port=${PORT:8080}` 自动监听 Render 指定端口；本地开发仍默认使用 `8080`。`render.yaml` 已配置 `healthCheckPath: /api/health`。

## API 示例

iPhone 快捷指令调用 `/api/process` 时，请使用：

```text
Method: POST
Content-Type: application/json
Header: X-API-Token: your-long-random-token
Body:
{
  "imageBase64": "Base64 encoded screenshot",
  "source": "ios-shortcuts"
}
```

后端会兼容 iPhone 快捷指令可能产生的 `data:image/...;base64,` 前缀和换行符。

```powershell
$body = @{ imageBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('fake-image')); source = 'ios-shortcuts' } | ConvertTo-Json
Invoke-RestMethod https://your-domain.example/api/process -Method Post -ContentType 'application/json' -Headers @{ 'X-API-Token' = 'your-token' } -Body $body
```

账目查询接口同样需要 Token：

```powershell
Invoke-RestMethod 'https://your-domain.example/api/transactions?page=0&size=20' -Headers @{ 'X-API-Token' = 'your-token' }
```

查询月度汇总：

```powershell
Invoke-RestMethod 'https://your-domain.example/api/transactions/summary?month=2026-05' -Headers @{ 'X-API-Token' = 'your-token' }
```

查询单条识别详情：

```powershell
Invoke-RestMethod 'http://localhost:8080/api/transactions/1' -Headers @{ 'X-API-Token' = 'your-token' } | ConvertTo-Json -Depth 5
```

人工复核或修正 AI 识别结果：

```powershell
$review = @{ transactionDate = '2026-05-25'; amount = 18.50; merchant = '星巴克'; category = '餐饮'; status = 'PROCESSED' } | ConvertTo-Json
Invoke-RestMethod 'https://your-domain.example/api/transactions/1' -Method Patch -ContentType 'application/json' -Headers @{ 'X-API-Token' = 'your-token' } -Body $review
```

删除误识别账目：

```powershell
Invoke-RestMethod 'https://your-domain.example/api/transactions/1' -Method Delete -Headers @{ 'X-API-Token' = 'your-token' }
```

## 隐私默认值

- 不硬编码真实 AI Key。
- 外部 AI Key 使用 `{{API_KEY}}` 占位符或环境变量。
- 原始截图默认不持久化。
- 云端部署必须启用 HTTPS 和 API Token。
