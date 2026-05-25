package com.autobookkeeper.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcessImageRequest(
        @NotBlank String imageBase64,
        String source
) {
}
