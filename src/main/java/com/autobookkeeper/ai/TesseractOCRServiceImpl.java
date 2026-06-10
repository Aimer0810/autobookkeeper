package com.autobookkeeper.ai;

import com.autobookkeeper.domain.Bill;
import org.springframework.stereotype.Component;

@Component
public class TesseractOCRServiceImpl implements AIService {

    @Override
    public Bill extractBillFromImage(byte[] imageData) {
        return AIService.placeholderBill(
                "离线 OCR 尚未配置真实 Tesseract 模型，请人工复核。",
                "{\"provider\":\"tesseract-fallback\",\"needsReview\":true}"
        );
    }
}
