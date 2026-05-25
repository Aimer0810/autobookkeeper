# iPhone 快捷指令集成手册

## 目标

通过 iOS 共享表单把支付截图发送到 AutoBookkeeper 后端，实现截图即记账。

## 快捷指令步骤

1. 创建新快捷指令，启用“在共享表单中显示”。
2. 接收类型选择“图像”。
3. 添加“获取快捷指令输入”。
4. 添加“编码媒体”，格式选择 Base64。
5. 添加“文本”，内容为：

```json
{
  "imageBase64": "编码媒体的结果",
  "source": "ios-shortcuts"
}
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

10. 请求正文选择 JSON，把 `imageBase64` 设置为 Base64 编码结果，把 `source` 设置为 `ios-shortcuts`。
11. 添加“显示结果”，显示商家、金额、分类和是否需要复核。

## 注意事项

- 云端部署必须使用 HTTPS。
- Token 应使用长随机字符串。
- 如果 `VISION_API_KEY` 仍是 `{{API_KEY}}`，后端不会把截图发送给外部 AI 服务，会返回需要复核的降级结果。
