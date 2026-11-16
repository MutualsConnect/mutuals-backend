package com.mutuals.moment.mapper;

import com.mutuals.moment.dto.MomentPhotoResponse;
import com.mutuals.moment.dto.MomentResponse;
import com.mutuals.moment.entity.Moment;
import com.mutuals.moment.entity.MomentPhoto;
import com.mutuals.moment.entity.MomentStatus;
import com.mutuals.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MomentMapper {

    private final UserMapper userMapper;

    public MomentResponse toMoment(Moment moment, Long viewerId, Instant now) {
        // Un momento abierto cuyo tiempo ya pasó se muestra como vencido, aunque aún no se haya marcado.
        MomentStatus status = moment.getStatus() == MomentStatus.OPEN && !moment.getExpiresAt().isAfter(now)
                ? MomentStatus.EXPIRED
                : moment.getStatus();
        boolean completed = status == MomentStatus.COMPLETED;
        long secondsLeft = status == MomentStatus.OPEN
                ? Math.max(0, Duration.between(now, moment.getExpiresAt()).toSeconds())
                : 0;
        return new MomentResponse(
                moment.getId(),
                moment.getStreak().getId(),
                userMapper.toSummary(moment.getStreak().getMutual().other(viewerId)),
                status,
                moment.getInitiator().getId().equals(viewerId),
                moment.getStartedAt(),
                moment.getExpiresAt(),
                secondsLeft,
                moment.hasPhotoFrom(viewerId),
                moment.getPhotos().stream().anyMatch(photo -> !photo.getUser().getId().equals(viewerId)),
                moment.getPhotos().stream().map(photo -> toPhoto(photo, viewerId, completed)).toList());
    }

    private MomentPhotoResponse toPhoto(MomentPhoto photo, Long viewerId, boolean completed) {
        boolean revealed = completed || photo.getUser().getId().equals(viewerId);
        return new MomentPhotoResponse(
                userMapper.toSummary(photo.getUser()),
                revealed,
                revealed ? photo.getPhotoUrl() : null,
                revealed ? photo.getCaption() : null,
                photo.getSubmittedAt());
    }
}
