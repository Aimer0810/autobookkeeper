package com.autobookkeeper.ai;

import com.autobookkeeper.domain.Bill;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class TesseractOCRServiceImpl implements AIService {

    @Override
    public Bill extractBillFromImage(byte[] imageData) {
        return new Bill(
                LocalDate.now(),
                BigDecimal.ZERO,
                "待复核商家",
                "未分类",
                "离线 OCR 尚未配置真实 Tesseract 模型，请人工复核。",
                "{\"provider\":\"tesseract-fallback\",\"needsReview\":true}",
                0.2,
                true
        );
    }
}
