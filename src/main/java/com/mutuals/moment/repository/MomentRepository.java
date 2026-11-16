package com.mutuals.moment.repository;

import com.mutuals.moment.entity.Moment;
import com.mutuals.moment.entity.MomentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MomentRepository extends JpaRepository<Moment, Long> {

    Optional<Moment> findFirstByStreakIdAndStatusAndExpiresAtAfter(Long streakId, MomentStatus status, Instant now);

    List<Moment> findByStreakIdAndStatusAndExpiresAtLessThanEqual(Long streakId, MomentStatus status, Instant now);

    @Query("""
            select m from Moment m
            join fetch m.streak s
            join fetch s.mutual mu
            where m.status = :status and m.expiresAt > :now
              and (mu.userA.id = :userId or mu.userB.id = :userId)
            order by m.startedAt desc
            """)
    List<Moment> findOpenFor(@Param("userId") Long userId, @Param("status") MomentStatus status,
                             @Param("now") Instant now);

    @Query(value = """
            select m from Moment m
            join m.streak s
            join s.mutual mu
            where m.status = :status and (mu.userA.id = :userId or mu.userB.id = :userId)
            order by m.completedAt desc
            """,
            countQuery = """
                    select count(m) from Moment m
                    join m.streak s
                    join s.mutual mu
                    where m.status = :status and (mu.userA.id = :userId or mu.userB.id = :userId)
                    """)
    Page<Moment> findByStatusFor(@Param("userId") Long userId, @Param("status") MomentStatus status,
                                 Pageable pageable);
}
