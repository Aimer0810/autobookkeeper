package com.autobookkeeper.ai;

import com.autobookkeeper.domain.Bill;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class LocalOCRServiceImpl implements AIService {

    @Override
    public Bill extractBillFromImage(byte[] imageData) {
        return new Bill(
                LocalDate.now(),
                BigDecimal.ZERO,
                "待复核商家",
                "未分类",
                "本地视觉模型尚未配置，请人工复核。",
                "{\"provider\":\"local-ocr-placeholder\",\"needsReview\":true}",
                0.2,
                true
        );
    }
}
