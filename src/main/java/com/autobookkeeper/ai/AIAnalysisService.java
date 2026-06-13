package com.autobookkeeper.ai;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.domain.Transaction;
import com.autobookkeeper.domain.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AIAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(AIAnalysisService.class);

    private final AutoBookkeeperProperties properties;
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIAnalysisService(AutoBookkeeperProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public String analyze(String month, List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return "本月暂无交易记录，无法生成分析。";
        }

        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank() || "{{API_KEY}}".equals(apiKey)) {
            return generateLocalAnalysis(month, transactions);
        }

        try {
            String prompt = buildPrompt(month, transactions);
            String response = callAI(apiKey, prompt);
            return response != null ? response : generateLocalAnalysis(month, transactions);
        } catch (Exception e) {
            logger.warn("AI analysis failed, falling back to local analysis", e);
            return generateLocalAnalysis(month, transactions);
        }
    }

    private String buildPrompt(String month, List<Transaction> transactions) {
        BigDecimal totalIncome = transactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> expenseByCategory = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

        Map<String, Long> expenseByMerchant = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(Transaction::getMerchant, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("你是一个个人财务分析师。请根据以下").append(month).append("的记账数据，给出简洁的中文分析报告。\n\n");
        sb.append("## 数据概览\n");
        sb.append("- 总收入：¥").append(totalIncome).append("\n");
        sb.append("- 总支出：¥").append(totalExpense).append("\n");
        sb.append("- 结余：¥").append(totalIncome.subtract(totalExpense)).append("\n");
        sb.append("- 交易笔数：").append(transactions.size()).append("\n\n");

        sb.append("## 支出分类\n");
        expenseByCategory.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> sb.append("- ").append(e.getKey()).append("：¥").append(e.getValue()).append("\n"));

        sb.append("\n## 高频商户 TOP5\n");
        expenseByMerchant.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .forEach(e -> sb.append("- ").append(e.getKey()).append("：").append(e.getValue()).append("笔\n"));

        sb.append("\n请从以下几个方面分析：\n");
        sb.append("1. 📊 消费结构（各分类占比是否合理）\n");
        sb.append("2. 💡 消费建议（哪些方面可以优化）\n");
        sb.append("3. 🎯 储蓄评估（结余率是否健康）\n");
        sb.append("4. ⚠️ 异常提醒（如有大额或高频消费）\n");
        sb.append("\n请简洁回答，每点 1-2 句话，使用 emoji 分点。");
        return sb.toString();
    }

    private String callAI(String apiKey, String prompt) throws Exception {
        String endpoint = environment != null
                ? environment.getProperty("autobookkeeper.ai.endpoint", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                : "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        String model = environment != null
                ? environment.getProperty("autobookkeeper.ai.model", "qwen3-vl-flash")
                : "qwen3-vl-flash";
        int timeout = environment != null
                ? environment.getProperty("autobookkeeper.ai.timeout-ms", Integer.class, 30000)
                : 30000;

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", 800);
        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeout))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode json = objectMapper.readTree(response.body());
            return json.path("choices").path(0).path("message").path("content").asText(null);
        }
        logger.warn("AI analysis API returned status {}", response.statusCode());
        return null;
    }

    private String generateLocalAnalysis(String month, List<Transaction> transactions) {
        BigDecimal totalIncome = transactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalIncome.subtract(totalExpense);
        int count = transactions.size();

        Map<String, BigDecimal> expenseByCategory = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

        StringBuilder analysis = new StringBuilder();
        analysis.append("📊 **").append(month).append(" 财务分析报告**\n\n");

        // 储蓄评估
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            double savingRate = balance.doubleValue() / totalIncome.doubleValue() * 100;
            analysis.append("🎯 **储蓄评估**\n");
            analysis.append("本月收入 ¥").append(totalIncome).append("，支出 ¥").append(totalExpense);
            analysis.append("，结余 ¥").append(balance);
            analysis.append("，储蓄率 ").append(String.format("%.1f", savingRate)).append("%。");
            if (savingRate >= 30) analysis.append(" 储蓄率优秀，继续保持！\n\n");
            else if (savingRate >= 10) analysis.append(" 储蓄率尚可，建议适当控制支出。\n\n");
            else analysis.append(" 储蓄率偏低，建议关注非必要支出。\n\n");
        } else {
            analysis.append("🎯 **储蓄评估**\n本月无收入记录，支出 ¥").append(totalExpense).append("。\n\n");
        }

        // 消费结构
        analysis.append("📊 **消费结构**\n");
        expenseByCategory.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .forEach(e -> {
                    double pct = totalExpense.compareTo(BigDecimal.ZERO) > 0
                            ? e.getValue().doubleValue() / totalExpense.doubleValue() * 100 : 0;
                    analysis.append("- ").append(e.getKey()).append("：¥").append(e.getValue())
                            .append("（").append(String.format("%.1f", pct)).append("%）\n");
                });
        analysis.append("\n");

        // 消费建议
        analysis.append("💡 **消费建议**\n");
        if (count > 50) analysis.append("- 本月交易 ").append(count).append(" 笔，消费频次较高，建议合并小额消费。\n");
        BigDecimal topCategory = expenseByCategory.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (totalExpense.compareTo(BigDecimal.ZERO) > 0 && topCategory.doubleValue() / totalExpense.doubleValue() > 0.5) {
            String topName = expenseByCategory.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
            analysis.append("- ").append(topName).append("占比超过 50%，建议关注该类消费是否必要。\n");
        }
        analysis.append("- 建议设置月度预算，合理规划每类支出。\n");

        return analysis.toString();
    }

    private String resolveApiKey() {
        if (environment != null) {
            return environment.getProperty("autobookkeeper.ai.api-key",
                    properties.ai() == null ? "{{API_KEY}}" : properties.ai().apiKey());
        }
        return properties.ai() == null ? "{{API_KEY}}" : properties.ai().apiKey();
    }
}
