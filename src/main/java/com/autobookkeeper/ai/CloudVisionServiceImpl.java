package com.autobookkeeper.ai;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.domain.Bill;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;

@Component
public class CloudVisionServiceImpl implements AIService {

    private final AutoBookkeeperProperties properties;
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final URI endpoint;

    @Autowired
    public CloudVisionServiceImpl(AutoBookkeeperProperties properties, Environment environment) {
        this(properties, environment, URI.create(environment.getProperty("autobookkeeper.ai.endpoint", configuredEndpoint(properties))));
    }

    public CloudVisionServiceImpl(AutoBookkeeperProperties properties, URI endpoint) {
        this.properties = properties;
        this.environment = null;
        this.endpoint = endpoint;
    }

    public CloudVisionServiceImpl(AutoBookkeeperProperties properties) {
        this(properties, URI.create(configuredEndpoint(properties)));
    }

    CloudVisionServiceImpl(AutoBookkeeperProperties properties, Environment environment, URI endpoint) {
        this.properties = properties;
        this.environment = environment;
        this.endpoint = endpoint;
    }

    @Override
    public Bill extractBillFromImage(byte[] imageData) {
        String apiKey = apiKey();
        if (apiKey == null || apiKey.isBlank() || "{{API_KEY}}".equals(apiKey)) {
            return reviewBill(
                    "云端视觉 API Key 未配置，未向外部服务发送截图。",
                    "{\"provider\":\"cloud\",\"skipped\":true,\"reason\":\"api-key-not-configured\"}"
            );
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeoutMs()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildVisionRequest(imageData)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return reviewBill("云端视觉 API 返回非成功状态：" + response.statusCode(), "{\"provider\":\"cloud\",\"status\":" + response.statusCode() + "}");
            }
            return parseBillJson(extractContent(response.body()));
        } catch (Exception exception) {
            return reviewBill("云端视觉 API 调用失败，请人工复核。", "{\"provider\":\"cloud\",\"error\":\"vision-api-call-failed\"}");
        }
    }

    String buildVisionRequest(byte[] imageData) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model());
        root.put("max_tokens", 500);
        ObjectNode responseFormat = root.putObject("response_format");
        responseFormat.put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", "你是严谨的个人记账票据识别助手，只返回 JSON，不要返回 Markdown。字段必须包含 date, amount, merchant, category, confidence, rawText。");

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", "请从这张支付截图中提取账单信息，日期使用 YYYY-MM-DD，金额只返回数字字符串，分类使用中文。");
        ObjectNode image = content.addObject();
        image.put("type", "image_url");
        ObjectNode imageUrl = image.putObject("image_url");
        imageUrl.put("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build vision request", exception);
        }
    }

    public Bill parseBillJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            LocalDate date = LocalDate.parse(text(root, "date", LocalDate.now().toString()));
            BigDecimal amount = new BigDecimal(text(root, "amount", "0"));
            String merchant = text(root, "merchant", "未知商家");
            String category = text(root, "category", "未分类");
            String rawText = text(root, "rawText", "");
            double confidence = root.path("confidence").asDouble(0.0);
            return new Bill(date, amount, merchant, category, rawText, json, confidence, confidence < 0.75);
        } catch (Exception exception) {
            return reviewBill("视觉模型返回 JSON 解析失败。", json);
        }
    }

    private String extractContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").path(0).path("message").path("content").asText("{}");
    }

    private int timeoutMs() {
        if (environment != null) {
            return environment.getProperty("autobookkeeper.ai.timeout-ms", Integer.class, configuredTimeoutMs(properties));
        }
        return configuredTimeoutMs(properties);
    }

    private String model() {
        if (environment != null) {
            return environment.getProperty("autobookkeeper.ai.model", configuredModel(properties));
        }
        return configuredModel(properties);
    }

    private String apiKey() {
        if (environment != null) {
            return environment.getProperty("autobookkeeper.ai.api-key", configuredApiKey(properties));
        }
        return configuredApiKey(properties);
    }

    private static String configuredEndpoint(AutoBookkeeperProperties properties) {
        return properties.ai() == null || properties.ai().endpoint() == null || properties.ai().endpoint().isBlank()
                ? "https://api.openai.com/v1/chat/completions"
                : properties.ai().endpoint();
    }

    private static int configuredTimeoutMs(AutoBookkeeperProperties properties) {
        return properties.ai() == null || properties.ai().timeoutMs() <= 0 ? 2500 : properties.ai().timeoutMs();
    }

    private static String configuredModel(AutoBookkeeperProperties properties) {
        return properties.ai() == null || properties.ai().model() == null || properties.ai().model().isBlank()
                ? "gpt-4o-mini"
                : properties.ai().model();
    }

    private static String configuredApiKey(AutoBookkeeperProperties properties) {
        return properties.ai() == null ? "{{API_KEY}}" : properties.ai().apiKey();
    }

    private Bill reviewBill(String rawText, String structuredJson) {
        return new Bill(
                LocalDate.now(),
                BigDecimal.ZERO,
                "待复核商家",
                "未分类",
                rawText,
                structuredJson,
                0.1,
                true
        );
    }

    private String text(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.asText().isBlank() ? defaultValue : node.asText();
    }
}
