package com.autobookkeeper.ai;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import com.autobookkeeper.domain.Bill;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CompositeAIService implements AIService {

    private final AutoBookkeeperProperties properties;
    private final CloudVisionServiceImpl cloudVisionService;
    private final TesseractOCRServiceImpl tesseractOCRService;
    private final LocalOCRServiceImpl localOCRService;

    public CompositeAIService(AutoBookkeeperProperties properties, CloudVisionServiceImpl cloudVisionService, TesseractOCRServiceImpl tesseractOCRService, LocalOCRServiceImpl localOCRService) {
        this.properties = properties;
        this.cloudVisionService = cloudVisionService;
        this.tesseractOCRService = tesseractOCRService;
        this.localOCRService = localOCRService;
    }

    @Override
    public Bill extractBillFromImage(byte[] imageData) {
        String provider = properties.ai() == null ? "cloud" : properties.ai().provider();
        try {
            if ("local".equalsIgnoreCase(provider)) {
                return localOCRService.extractBillFromImage(imageData);
            }
            if ("tesseract".equalsIgnoreCase(provider)) {
                return tesseractOCRService.extractBillFromImage(imageData);
            }
            Bill cloudBill = cloudVisionService.extractBillFromImage(imageData);
            if (cloudBill.confidence() >= 0.75 && !cloudBill.needsReview()) {
                return cloudBill;
            }
            Bill fallbackBill = tesseractOCRService.extractBillFromImage(imageData);
            return new Bill(
                    fallbackBill.date(),
                    fallbackBill.amount(),
                    fallbackBill.merchant(),
                    fallbackBill.category(),
                    cloudBill.rawText() + "\n" + fallbackBill.rawText(),
                    fallbackBill.structuredJson(),
                    Math.max(cloudBill.confidence(), fallbackBill.confidence()),
                    true
            );
        } catch (RuntimeException exception) {
            return tesseractOCRService.extractBillFromImage(imageData);
        }
    }
}
