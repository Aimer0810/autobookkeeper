package com.autobookkeeper.ai;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.domain.Bill;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CloudVisionServiceImplTest {

    @Test
    void parsesBillJsonFromVisionResponse() {
        CloudVisionServiceImpl service = new CloudVisionServiceImpl(new AutoBookkeeperProperties(
                "",
                new AutoBookkeeperProperties.Ai("cloud", "{{API_KEY}}", 2500),
                new AutoBookkeeperProperties.Privacy(false, true)
        ));

        Bill bill = service.parseBillJson("{\"date\":\"2026-05-25\",\"amount\":\"42.50\",\"merchant\":\"瑞幸咖啡\",\"category\":\"餐饮\",\"confidence\":0.92,\"rawText\":\"支付给瑞幸咖啡 42.50\"}");

        assertThat(bill.date()).isEqualTo(LocalDate.of(2026, 5, 25));
        assertThat(bill.amount()).isEqualByComparingTo(new BigDecimal("42.50"));
        assertThat(bill.merchant()).isEqualTo("瑞幸咖啡");
        assertThat(bill.category()).isEqualTo("餐饮");
        assertThat(bill.confidence()).isEqualTo(0.92);
        assertThat(bill.needsReview()).isFalse();
    }

    @Test
    void sendsImageToVisionApiAndParsesReturnedBillJson() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"choices":[{"message":{"content":"{\\"date\\":\\"2026-05-25\\",\\"amount\\":\\"12.80\\",\\"merchant\\":\\"麦当劳\\",\\"category\\":\\"餐饮\\",\\"confidence\\":0.91,\\"rawText\\":\\"麦当劳 12.80\\"}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/chat/completions");
            CloudVisionServiceImpl service = new CloudVisionServiceImpl(new AutoBookkeeperProperties(
                    "",
                    new AutoBookkeeperProperties.Ai("cloud", "real-test-key", 2500),
                    new AutoBookkeeperProperties.Privacy(false, true)
            ), endpoint);

            Bill bill = service.extractBillFromImage("image-bytes".getBytes());

            assertThat(requestBody.get()).contains("data:image/png;base64");
            assertThat(requestBody.get()).contains("提取支付截图账单");
            assertThat(bill.amount()).isEqualByComparingTo(new BigDecimal("12.80"));
            assertThat(bill.merchant()).isEqualTo("麦当劳");
            assertThat(bill.category()).isEqualTo("餐饮");
            assertThat(bill.needsReview()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesConfiguredVisionModelInRequestBody() {
        CloudVisionServiceImpl service = new CloudVisionServiceImpl(new AutoBookkeeperProperties(
                "",
                new AutoBookkeeperProperties.Ai("cloud", "real-test-key", 2500, "https://example.com/v1/chat/completions", "qwen3.6-flash"),
                new AutoBookkeeperProperties.Privacy(false, true)
        ));

        String requestBody = service.buildVisionRequest("image-bytes".getBytes());

        assertThat(requestBody).contains("\"model\":\"qwen3.6-flash\"");
        assertThat(requestBody).contains("\"max_tokens\":220");
    }

    @Test
    void includesAccuracyGuidanceForChineseMerchantAndReviewConfidence() {
        CloudVisionServiceImpl service = new CloudVisionServiceImpl(new AutoBookkeeperProperties(
                "",
                new AutoBookkeeperProperties.Ai("cloud", "real-test-key", 2500, "https://example.com/v1/chat/completions", "qwen3.6-flash"),
                new AutoBookkeeperProperties.Privacy(false, true)
        ));

        String requestBody = service.buildVisionRequest("image-bytes".getBytes());

        assertThat(requestBody).contains("拼音、英文、昵称原样返回");
        assertThat(requestBody).contains("如 ru zi ni sa，原样填入 merchant");
        assertThat(requestBody).contains("未知商家");
        assertThat(requestBody).contains("confidence<=0.74");
        assertThat(requestBody).contains("餐饮、交通、购物、住房、医疗、娱乐、生活缴费、转账、收入、其他、未分类");
    }

    @Test
    void usesEnvironmentAiConfigurationWhenPropertiesRecordContainsDefaults() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>("");
        AtomicReference<String> requestBody = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"choices":[{"message":{"content":"{\\"date\\":\\"2026-05-25\\",\\"amount\\":\\"18.60\\",\\"merchant\\":\\"便利店\\",\\"category\\":\\"购物\\",\\"confidence\\":0.88,\\"rawText\\":\\"便利店 18.60\\"}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("autobookkeeper.ai.api-key", "environment-test-key")
                    .withProperty("autobookkeeper.ai.endpoint", "http://localhost:" + server.getAddress().getPort() + "/v1/chat/completions")
                    .withProperty("autobookkeeper.ai.model", "qwen3.6-flash")
                    .withProperty("autobookkeeper.ai.timeout-ms", "30000");
            CloudVisionServiceImpl service = new CloudVisionServiceImpl(new AutoBookkeeperProperties(
                    "",
                    new AutoBookkeeperProperties.Ai("cloud", "{{API_KEY}}", 2500),
                    new AutoBookkeeperProperties.Privacy(false, true)
            ), environment);

            Bill bill = service.extractBillFromImage("image-bytes".getBytes());

            assertThat(authorization.get()).isEqualTo("Bearer environment-test-key");
            assertThat(requestBody.get()).contains("\"model\":\"qwen3.6-flash\"");
            assertThat(bill.amount()).isEqualByComparingTo(new BigDecimal("18.60"));
            assertThat(bill.needsReview()).isFalse();
        } finally {
            server.stop(0);
        }
    }
}
