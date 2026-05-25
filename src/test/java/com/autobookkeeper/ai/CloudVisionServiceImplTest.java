package com.autobookkeeper.ai;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.domain.Bill;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
