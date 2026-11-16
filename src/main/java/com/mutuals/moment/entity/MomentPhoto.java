package com.mutuals.moment.entity;

import com.mutuals.common.entity.BaseEntity;
import com.mutuals.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "moment_photos",
        uniqueConstraints = @UniqueConstraint(name = "uk_moment_photos_user", columnNames = {"moment_id", "user_id"}))
public class MomentPhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moment_id", nullable = false)
    private Moment moment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String photoUrl;

    @Size(max = 140)
    @Column(length = 140)
    private String caption;

    @Column(nullable = false)
    private Instant submittedAt;
}
