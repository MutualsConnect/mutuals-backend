package com.mutuals.moment.service;

import com.mutuals.common.dto.PageResponse;
import com.mutuals.common.exception.ConflictException;
import com.mutuals.common.exception.DuplicateResourceException;
import com.mutuals.common.exception.ForbiddenOperationException;
import com.mutuals.common.exception.InvalidOperationException;
import com.mutuals.common.exception.ResourceNotFoundException;
import com.mutuals.config.AppProperties;
import com.mutuals.event.MomentStartedEvent;
import com.mutuals.moment.dto.MomentResponse;
import com.mutuals.moment.entity.Moment;
import com.mutuals.moment.entity.MomentPhoto;
import com.mutuals.moment.entity.MomentStatus;
import com.mutuals.moment.mapper.MomentMapper;
import com.mutuals.moment.repository.MomentRepository;
import com.mutuals.security.CurrentUserService;
import com.mutuals.storage.StorageService;
import com.mutuals.streak.entity.InteractionMethod;
import com.mutuals.streak.entity.Streak;
import com.mutuals.streak.service.InteractionService;
import com.mutuals.streak.service.StreakService;
import com.mutuals.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Momentos de foto. Una persona lanza el momento y las dos tienen una ventana corta para subir una
 * foto de lo que están haciendo; al llegar la segunda foto a tiempo, la racha del día se confirma sola.
 */
@Service
@RequiredArgsConstructor
public class MomentService {

    private static final String PHOTO_FOLDER = "moments";

    private final MomentRepository momentRepository;
    private final StreakService streakService;
    private final InteractionService interactionService;
    private final CurrentUserService currentUserService;
    private final MomentMapper momentMapper;
    private final StorageService storageService;
    private final AppProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public MomentResponse start(Long friendId) {
        User me = currentUserService.getCurrentUser();
        Streak streak = streakService.requireActiveStreakWith(me.getId(), friendId);
        Instant now = clock.instant();
        if (streak.isCountedOn(LocalDate.now(clock))) {
            throw new ConflictException("Today's interaction with this friend is already confirmed");
        }
        expireStale(streak.getId(), now);
        if (momentRepository.findFirstByStreakIdAndStatusAndExpiresAtAfter(streak.getId(), MomentStatus.OPEN, now)
                .isPresent()) {
            throw new DuplicateResourceException("There is already an open moment with this friend");
        }
        Moment moment = new Moment();
        moment.setStreak(streak);
        moment.setInitiator(me);
        moment.setStartedAt(now);
        moment.setExpiresAt(now.plusSeconds(properties.moment().windowSeconds()));
        Moment saved = momentRepository.save(moment);
        eventPublisher.publishEvent(new MomentStartedEvent(saved.getId(), me.getId(), friendId, saved.getExpiresAt()));
        return momentMapper.toMoment(saved, me.getId(), now);
    }

    /** Sin revertir al vencer: así el estado EXPIRED queda guardado aunque la petición falle. */
    @Transactional(noRollbackFor = InvalidOperationException.class)
    public MomentResponse submitPhoto(Long momentId, MultipartFile photo, String caption) {
        User me = currentUserService.getCurrentUser();
        Moment moment = requireMember(momentId, me.getId());
        Instant now = clock.instant();
        if (moment.getStatus() == MomentStatus.COMPLETED) {
            throw new ConflictException("This moment is already complete");
        }
        if (!moment.isOpenAt(now)) {
            moment.setStatus(MomentStatus.EXPIRED);
            throw new InvalidOperationException("The time for this moment is over");
        }
        if (photo == null || photo.isEmpty()) {
            throw new InvalidOperationException("A photo is required to join the moment");
        }
        if (moment.hasPhotoFrom(me.getId())) {
            throw new DuplicateResourceException("You already shared your photo for this moment");
        }
        MomentPhoto momentPhoto = new MomentPhoto();
        momentPhoto.setUser(me);
        momentPhoto.setPhotoUrl(storageService.store(photo, PHOTO_FOLDER));
        momentPhoto.setCaption(caption == null || caption.isBlank() ? null : caption.trim());
        momentPhoto.setSubmittedAt(now);
        moment.addPhoto(momentPhoto);
        if (moment.getPhotos().size() == 2) {
            complete(moment, now);
        }
        return momentMapper.toMoment(momentRepository.save(moment), me.getId(), now);
    }

    @Transactional(readOnly = true)
    public List<MomentResponse> active() {
        Long me = currentUserService.getCurrentUserId();
        Instant now = clock.instant();
        return momentRepository.findOpenFor(me, MomentStatus.OPEN, now).stream()
                .map(moment -> momentMapper.toMoment(moment, me, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public MomentResponse get(Long momentId) {
        Long me = currentUserService.getCurrentUserId();
        return momentMapper.toMoment(requireMember(momentId, me), me, clock.instant());
    }

    /** Recuerdos: los momentos completados, con las dos fotos reveladas. */
    @Transactional(readOnly = true)
    public PageResponse<MomentResponse> history(Pageable pageable) {
        Long me = currentUserService.getCurrentUserId();
        Instant now = clock.instant();
        return PageResponse.from(momentRepository.findByStatusFor(me, MomentStatus.COMPLETED, pageable),
                moment -> momentMapper.toMoment(moment, me, now));
    }

    private void complete(Moment moment, Instant now) {
        moment.setStatus(MomentStatus.COMPLETED);
        moment.setCompletedAt(now);
        Streak streak = moment.getStreak();
        User initiator = moment.getInitiator();
        User other = streak.getMutual().other(initiator.getId());
        interactionService.recordVerified(streak, initiator, other, InteractionMethod.PHOTO);
    }

    private void expireStale(Long streakId, Instant now) {
        momentRepository.findByStreakIdAndStatusAndExpiresAtLessThanEqual(streakId, MomentStatus.OPEN, now)
                .forEach(stale -> stale.setStatus(MomentStatus.EXPIRED));
    }

    private Moment requireMember(Long momentId, Long userId) {
        Moment moment = momentRepository.findById(momentId)
                .orElseThrow(() -> new ResourceNotFoundException("Moment", momentId));
        if (!moment.getStreak().getMutual().involves(userId)) {
            throw new ForbiddenOperationException("You are not part of this moment");
        }
        return moment;
    }
}
