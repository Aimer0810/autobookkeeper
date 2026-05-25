# Render / Railway / Fly.io 部署说明

## 构建命令

```bash
mvn clean package -DskipTests
```

## 启动命令

```bash
java -jar target/autobookkeeper-0.1.0.jar --spring.profiles.active=cloud
```

## 必需环境变量

```text
SPRING_PROFILES_ACTIVE=cloud
AUTOBOOKKEEPER_API_TOKEN=<long-random-token>
VISION_API_KEY={{API_KEY}}
DATABASE_URL=jdbc:postgresql://<host>:<port>/<database>
DATABASE_USERNAME=<username>
DATABASE_PASSWORD=<password>
```

## 平台建议

- Render：选择 Web Service，运行 Java 17 LTS，绑定 PostgreSQL。
- Railway：创建 Java 服务和 PostgreSQL 插件，配置环境变量。
- Fly.io：适合进阶用户，可用 Docker 或 Java 直接部署。

## 安全要求

- 生产域名必须启用 HTTPS。
- 不要把真实 API Key 写入 Git。
- 不要在日志里打印完整 OCR 文本和截图内容。
- 建议定期导出数据库备份。
