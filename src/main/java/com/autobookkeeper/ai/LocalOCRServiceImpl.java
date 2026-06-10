package com.autobookkeeper.ai;

import com.autobookkeeper.domain.Bill;
import org.springframework.stereotype.Component;

@Component
public class LocalOCRServiceImpl implements AIService {

    @Override
    public Bill extractBillFromImage(byte[] imageData) {
        return AIService.placeholderBill(
                "本地视觉模型尚未配置，请人工复核。",
                "{\"provider\":\"local-ocr-placeholder\",\"needsReview\":true}"
        );
    }
}
