package com.autobookkeeper.ai;

import com.autobookkeeper.domain.Bill;

public interface AIService {

    Bill extractBillFromImage(byte[] imageData);
}
