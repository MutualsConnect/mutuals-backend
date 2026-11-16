package com.mutuals.moment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMomentRequest(@NotNull @Positive Long friendId) {
}
