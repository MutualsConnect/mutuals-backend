package com.mutuals.moment.dto;

import com.mutuals.moment.entity.MomentStatus;
import com.mutuals.user.dto.UserSummaryResponse;

import java.time.Instant;
import java.util.List;

public record MomentResponse(
        Long id,
        Long streakId,
        UserSummaryResponse friend,
        MomentStatus status,
        boolean startedByMe,
        Instant startedAt,
        Instant expiresAt,
        long secondsLeft,
        boolean submittedByMe,
        boolean friendSubmitted,
        List<MomentPhotoResponse> photos
) {
}
