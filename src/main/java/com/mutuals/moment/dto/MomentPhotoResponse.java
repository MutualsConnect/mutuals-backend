package com.mutuals.moment.dto;

import com.mutuals.user.dto.UserSummaryResponse;

import java.time.Instant;

/**
 * La foto de una de las dos personas. La del amigo se revela solo cuando el momento se completa;
 * la propia siempre es visible.
 */
public record MomentPhotoResponse(
        UserSummaryResponse user,
        boolean revealed,
        String photoUrl,
        String caption,
        Instant submittedAt
) {
}
