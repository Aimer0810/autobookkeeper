# iPhone 快捷指令集成手册

## 目标

通过 iOS 共享表单把支付截图发送到 AutoBookkeeper 后端，实现截图即记账。

## 快捷指令步骤

1. 创建新快捷指令，启用“在共享表单中显示”。
2. 接收类型选择“图像”。
3. 添加“获取快捷指令输入”。
4. 添加“编码媒体”，输入选择“快捷指令输入”，格式选择 Base64。
5. 添加“词典”，加入两个键：

```text
imageBase64 = 编码媒体的结果
source = ios-shortcuts
```

6. 添加“获取 URL 内容”。
7. URL 填写：

```text
https://<your-backend-domain>/api/process
```

8. 方法选择 `POST`。
9. 请求头添加：

```text
Content-Type: application/json
X-API-Token: <your-token>
```

10. 请求正文选择 JSON，请求体使用上一步创建的词典。
11. 添加“获取词典值”，从请求结果中分别读取：

```text
merchant
amount
category
status
needsReview
transactionId
```

12. 添加“显示结果”，内容示例：

```text
已记账：merchant
金额：amount
分类：category
状态：status
需要复核：needsReview
交易 ID：transactionId
```

如果你的 iOS 版本不方便用“词典”构造 JSON，也可以使用“文本”动作手写 JSON，并将 `编码媒体的结果` 插入到 `imageBase64` 字段：

```json
{
  "imageBase64": "编码媒体的结果",
  "source": "ios-shortcuts"
}
```

## 后端请求格式

URL：

```text
https://<your-backend-domain>/api/process
```

Method：

```text
POST
```

Headers：

```text
Content-Type: application/json
X-API-Token: <your-token>
```

Body：

```json
{
  "imageBase64": "<base64-encoded-screenshot>",
  "source": "ios-shortcuts"
}
```

响应示例：

```json
{
  "transactionId": 1,
  "status": "NEEDS_REVIEW",
  "merchant": "待复核",
  "amount": 0,
  "category": "待分类",
  "confidence": 0.2,
  "needsReview": true
}
```

## 注意事项

- 云端部署必须使用 HTTPS。
- Token 应使用长随机字符串。
- 如果 `VISION_API_KEY` 仍是 `{{API_KEY}}`，后端不会把截图发送给外部 AI 服务，会返回需要复核的降级结果。
- 使用阿里云百炼 OpenAI 兼容接口时，本地可设置 `VISION_API_ENDPOINT=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`，并把 `VISION_MODEL` 设置为你的百炼视觉模型名，例如 `qwen3.6-flash`。
