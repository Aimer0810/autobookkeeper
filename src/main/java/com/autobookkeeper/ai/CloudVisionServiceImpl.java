package com.autobookkeeper.ai;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.domain.Bill;
import com.autobookkeeper.domain.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(CloudVisionServiceImpl.class);
    private static final String DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_MODEL = "qwen3-vl-flash";
    private static final int DEFAULT_TIMEOUT_MS = 30000;
    private static final String UNCONFIGURED_KEY_PLACEHOLDER = "{{API_KEY}}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;

    @Autowired
    public CloudVisionServiceImpl(AutoBookkeeperProperties properties, Environment environment) {
        this(
                URI.create(environment.getProperty("autobookkeeper.ai.endpoint", resolveEndpoint(properties))),
                environment.getProperty("autobookkeeper.ai.api-key", resolveApiKey(properties)),
                environment.getProperty("autobookkeeper.ai.model", resolveModel(properties)),
                environment.getProperty("autobookkeeper.ai.timeout-ms", Integer.class, resolveTimeoutMs(properties))
        );
    }

    public CloudVisionServiceImpl(AutoBookkeeperProperties properties, URI endpoint) {
        this(endpoint, resolveApiKey(properties), resolveModel(properties), resolveTimeoutMs(properties));
    }

    CloudVisionServiceImpl(URI endpoint, String apiKey, String model, int timeoutMs) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public Bill extractBillFromImage(byte[] imageData) {
        if (apiKey == null || apiKey.isBlank() || UNCONFIGURED_KEY_PLACEHOLDER.equals(apiKey)) {
            return reviewBill(
                    "云端视觉 API Key 未配置，未向外部服务发送截图。",
                    "{\"provider\":\"cloud\",\"skipped\":true,\"reason\":\"api-key-not-configured\"}"
            );
        }
        try {
            Bill bill = requestBill(httpClient, apiKey, imageData, false);
            if (shouldRetryWithStrictPrompt(bill)) {
                Bill strictBill = requestBill(httpClient, apiKey, imageData, true);
                if (!shouldRetryWithStrictPrompt(strictBill) || strictBill.confidence() >= bill.confidence()) {
                    return strictBill;
                }
            }
            return bill;
        } catch (Exception exception) {
            logger.warn("Cloud vision API call failed for endpoint {} model {}", endpoint, model, exception);
            return reviewBill("云端视觉 API 调用失败：" + exception.getClass().getSimpleName() + " " + abbreviate(exception.getMessage()), "{\"provider\":\"cloud\",\"error\":\"vision-api-call-failed\"}");
        }
    }

    String buildVisionRequest(byte[] imageData) {
        return buildVisionRequest(imageData, false);
    }

    String buildVisionRequest(byte[] imageData, boolean strictReview) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", strictReview ? 360 : 300);
        ObjectNode responseFormat = root.putObject("response_format");
        responseFormat.put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt(strictReview));

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", userPrompt(strictReview));
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
            TransactionType type = TransactionType.fromLabel(text(root, "type", "支出"));
            String category = text(root, "category", "未分类");
            String rawText = text(root, "rawText", "");
            double confidence = root.path("confidence").asDouble(0.0);
            return new Bill(date, amount, merchant, type, category, rawText, json, confidence, confidence < 0.75);
        } catch (Exception exception) {
            logger.warn("Cloud vision JSON parsing failed. contentSnippet={}", abbreviate(json), exception);
            return reviewBill("视觉模型返回 JSON 解析失败。", json);
        }
    }

    private Bill requestBill(HttpClient client, String apiKey, byte[] imageData, boolean strictReview) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildVisionRequest(imageData, strictReview)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.warn("Cloud vision API returned status {} for endpoint {} model {} bodySnippet {}", response.statusCode(), endpoint, model, abbreviate(response.body()));
            return reviewBill("云端视觉 API 返回非成功状态：" + response.statusCode(), "{\"provider\":\"cloud\",\"status\":" + response.statusCode() + "}");
        }
        return parseBillJson(extractContent(response.body()));
    }

    private boolean shouldRetryWithStrictPrompt(Bill bill) {
        return bill.amount().compareTo(BigDecimal.ZERO) <= 0
                || bill.confidence() < 0.5
                || "未知商家".equals(bill.merchant())
                || "待复核商家".equals(bill.merchant());
    }

    private String systemPrompt(boolean strictReview) {
        String prefix = strictReview ? "严格复核模式。" : "";
        return prefix + "只返回 JSON，不要 Markdown。字段：date, amount, merchant, type, category, confidence, rawText。"
                + "处理复杂截图时，先在整张图中定位真正的交易/订单/账单区域，再提取字段。"
                + "多金额优先级：实付金额、支付金额、付款金额、订单金额、消费金额、本次支出、退款金额；忽略红包、奖励、优惠券、余额、额度、广告、按钮。"
                + "merchant 优先取收款方、商户、店铺、对方账户、商品说明、备注；拼音、英文、昵称原样返回，确实看不清才用未知商家。"
                + "date 优先取交易时间、支付时间、下单时间、账单时间；日期 YYYY-MM-DD。"
                + "type 只能是收入或支出，付款/消费/转给别人是支出，工资/奖金/报销/退款/收到转账是收入，收入或支出不确定时用支出。"
                + "月账单/周账单/分期账单如果没有单笔商户，就把 merchant 填账单产品名，如美团月付/花呗/白条，并把 rawText 标明账单汇总。"
                + "category 限：餐饮、交通、购物、住房、医疗、娱乐、生活缴费、转账、收入、其他、未分类。金额/商户/日期不确定时 confidence<=0.74。";
    }

    private String userPrompt(boolean strictReview) {
        String prefix = strictReview ? "严格复核模式：上一次识别低置信度或金额为 0，请重新检查复杂截图中的真实交易区域。" : "";
        return prefix + "提取支付截图账单。日期 YYYY-MM-DD；金额只返回数字。若收款方是拼音/英文/昵称，如 ru zi ni sa，原样填入 merchant。"
                + "如果截图包含奖励、红包、广告、余额、额度、按钮，不要把它们当成交易金额或商户。";
    }

    private String extractContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").path(0).path("message").path("content").asText("{}");
    }

    private static String resolveEndpoint(AutoBookkeeperProperties properties) {
        return properties.ai() == null || properties.ai().endpoint() == null || properties.ai().endpoint().isBlank()
                ? DEFAULT_ENDPOINT
                : properties.ai().endpoint();
    }

    private static int resolveTimeoutMs(AutoBookkeeperProperties properties) {
        return properties.ai() == null || properties.ai().timeoutMs() <= 0 ? DEFAULT_TIMEOUT_MS : properties.ai().timeoutMs();
    }

    private static String resolveModel(AutoBookkeeperProperties properties) {
        return properties.ai() == null || properties.ai().model() == null || properties.ai().model().isBlank()
                ? DEFAULT_MODEL
                : properties.ai().model();
    }

    private static String resolveApiKey(AutoBookkeeperProperties properties) {
        return properties.ai() == null ? UNCONFIGURED_KEY_PLACEHOLDER : properties.ai().apiKey();
    }

    private Bill reviewBill(String rawText, String structuredJson) {
        return new Bill(
                LocalDate.now(),
                BigDecimal.ZERO,
                "待复核商家",
                TransactionType.EXPENSE,
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

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
