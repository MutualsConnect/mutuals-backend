package com.mutuals.event;

import java.time.Instant;

public record MomentStartedEvent(Long momentId, Long initiatorId, Long recipientId, Instant expiresAt) {
}
