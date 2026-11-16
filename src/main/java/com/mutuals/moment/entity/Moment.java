package com.mutuals.moment.entity;

import com.mutuals.common.entity.BaseEntity;
import com.mutuals.streak.entity.Streak;
import com.mutuals.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Un momento de foto: una persona lo lanza y ambas tienen una ventana corta (2 minutos por defecto)
 * para tomar una foto de lo que están haciendo. Si las dos fotos llegan a tiempo, se confirma la
 * racha del día con el método {@code PHOTO}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "moments",
        indexes = @Index(name = "idx_moments_streak_status", columnList = "streak_id, status"))
public class Moment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "streak_id", nullable = false)
    private Streak streak;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiator_id", nullable = false)
    private User initiator;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MomentStatus status = MomentStatus.OPEN;

    private Instant completedAt;

    @OneToMany(mappedBy = "moment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MomentPhoto> photos = new ArrayList<>();

    public void addPhoto(MomentPhoto photo) {
        photo.setMoment(this);
        photos.add(photo);
    }

    public boolean hasPhotoFrom(Long userId) {
        return photos.stream().anyMatch(photo -> photo.getUser().getId().equals(userId));
    }

    /** Sigue abierto y todavía hay tiempo. */
    public boolean isOpenAt(Instant now) {
        return status == MomentStatus.OPEN && expiresAt.isAfter(now);
    }
}
