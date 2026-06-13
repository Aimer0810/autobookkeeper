package com.autobookkeeper.api.dto;

import java.time.Instant;

public record ProfileResponse(
        String username,
        String ownerKey,
        String apiToken,
        String avatarData,
        Instant createdAt
) {
}
