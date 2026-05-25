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

## 环境变量

```text
AUTOBOOKKEEPER_API_TOKEN=your-long-random-token
VISION_API_KEY={{API_KEY}}
SPRING_PROFILES_ACTIVE=cloud
DATABASE_URL=jdbc:postgresql://host:5432/autobookkeeper
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your-password
```

可以复制 `.env.example` 作为部署平台的环境变量清单。不要把真实 `.env` 文件提交到 Git。

## Docker 部署

项目包含 `Dockerfile`，可用于 Render、Railway、Fly.io 或普通 VPS：

```powershell
docker build -t autobookkeeper .
docker run -p 8080:8080 --env-file .env autobookkeeper
```

## Render Blueprint

项目包含 `render.yaml`，可在 Render 中创建 Web Service 和 PostgreSQL 数据库。创建后请在 Render 控制台手动设置：

```text
AUTOBOOKKEEPER_API_TOKEN
VISION_API_KEY
```

Render 的 PostgreSQL `connectionString` 默认是 `postgresql://...`。项目的 Docker 入口脚本会自动转换为 Spring Boot 需要的 `jdbc:postgresql://...`。

Render 会为 Web Service 注入 `PORT` 环境变量，应用会通过 `server.port=${PORT:8080}` 自动监听 Render 指定端口；本地开发仍默认使用 `8080`。`render.yaml` 已配置 `healthCheckPath: /api/health`。

## API 示例

```powershell
$body = @{ imageBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('fake-image')); source = 'ios-shortcuts' } | ConvertTo-Json
Invoke-RestMethod https://your-domain.example/api/process -Method Post -ContentType 'application/json' -Headers @{ 'X-API-Token' = 'your-token' } -Body $body
```

账目查询接口同样需要 Token：

```powershell
Invoke-RestMethod 'https://your-domain.example/api/transactions?page=0&size=20' -Headers @{ 'X-API-Token' = 'your-token' }
```

## 隐私默认值

- 不硬编码真实 AI Key。
- 外部 AI Key 使用 `{{API_KEY}}` 占位符或环境变量。
- 原始截图默认不持久化。
- 云端部署必须启用 HTTPS 和 API Token。
