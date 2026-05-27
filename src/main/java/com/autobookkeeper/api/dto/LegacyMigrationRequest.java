package com.autobookkeeper.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LegacyMigrationRequest(@NotBlank String legacyToken) {
}
