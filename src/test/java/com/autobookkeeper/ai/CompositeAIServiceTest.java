package com.autobookkeeper.ai;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.domain.Bill;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeAIServiceTest {

    @Test
    void preservesLowConfidenceCloudResultInsteadOfReplacingWithPlaceholderFallback() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = """
                    {"choices":[{"message":{"content":"{\\"date\\":\\"2026-05-26\\",\\"amount\\":\\"5.70\\",\\"merchant\\":\\"珍果鲜吉满博理工店\\",\\"category\\":\\"餐饮\\",\\"confidence\\":0.60,\\"rawText\\":\\"珍果鲜吉满博理工店 5.70\\"}"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AutoBookkeeperProperties properties = new AutoBookkeeperProperties(
                    "", "", "", "",
                    new AutoBookkeeperProperties.Ai("cloud", "real-test-key", 2500, "https://example.com/v1/chat/completions", "qwen3-vl-flash"),
                    new AutoBookkeeperProperties.Privacy(false, true)
            );
            URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/v1/chat/completions");
            CompositeAIService service = new CompositeAIService(
                    properties,
                    new CloudVisionServiceImpl(properties, endpoint),
                    new TesseractOCRServiceImpl(),
                    new LocalOCRServiceImpl()
            );

            Bill bill = service.extractBillFromImage("image-bytes".getBytes());

            assertThat(bill.amount()).isEqualByComparingTo(new BigDecimal("5.70"));
            assertThat(bill.merchant()).isEqualTo("珍果鲜吉满博理工店");
            assertThat(bill.category()).isEqualTo("餐饮");
            assertThat(bill.confidence()).isEqualTo(0.60);
            assertThat(bill.needsReview()).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
