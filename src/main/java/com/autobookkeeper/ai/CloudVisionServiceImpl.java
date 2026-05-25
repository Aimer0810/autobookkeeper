package com.autobookkeeper.ai;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.domain.Bill;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class CloudVisionServiceImpl implements AIService {

    private final AutoBookkeeperProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CloudVisionServiceImpl(AutoBookkeeperProperties properties) {
        this.properties = properties;
    }

    @Override
    public Bill extractBillFromImage(byte[] imageData) {
        String apiKey = properties.ai() == null ? "{{API_KEY}}" : properties.ai().apiKey();
        if (apiKey == null || apiKey.isBlank() || "{{API_KEY}}".equals(apiKey)) {
            return new Bill(
                    LocalDate.now(),
                    BigDecimal.ZERO,
                    "待复核商家",
                    "未分类",
                    "云端视觉 API Key 未配置，未向外部服务发送截图。",
                    "{\"provider\":\"cloud\",\"skipped\":true,\"reason\":\"api-key-not-configured\"}",
                    0.1,
                    true
            );
        }
        return new Bill(
                LocalDate.now(),
                BigDecimal.ZERO,
                "待复核商家",
                "未分类",
                "云端视觉 API 调用接口已预留，请接入具体服务后返回结构化 JSON。",
                "{\"provider\":\"cloud\",\"needsIntegration\":true}",
                0.3,
                true
        );
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
            return new Bill(
                    LocalDate.now(),
                    BigDecimal.ZERO,
                    "待复核商家",
                    "未分类",
                    "视觉模型返回 JSON 解析失败。",
                    json,
                    0.0,
                    true
            );
        }
    }

    private String text(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.asText().isBlank() ? defaultValue : node.asText();
    }
}
