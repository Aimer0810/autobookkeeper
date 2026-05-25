# Vercel 前端可选说明

Vercel 不建议作为 AutoBookkeeper 的 Java 主后端，因为本项目核心依赖 Spring Boot 长驻服务、JPA 数据库连接和潜在 OCR/AI 处理。

适合 Vercel 的部分：

- 独立 Web 看板前端。
- 登录页或说明页。
- 轻量 API 代理。

第一版已经由 Spring Boot 提供 `src/main/resources/static/index.html`，因此无需 Vercel 也可以使用 Web UI。

如果后续拆分前端，建议架构为：

```text
Vercel Frontend -> HTTPS -> Java Spring Boot Backend -> PostgreSQL
```

不要在 Vercel 前端代码中暴露 `AUTOBOOKKEEPER_API_TOKEN` 或真实 AI API Key。
