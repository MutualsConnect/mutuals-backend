package com.mutuals.auth.service;

import com.mutuals.auth.dto.AuthResponse;
import com.mutuals.auth.dto.GoogleLoginRequest;
import com.mutuals.auth.dto.LoginRequest;
import com.mutuals.auth.dto.RegisterRequest;
import com.mutuals.auth.entity.RefreshToken;
import com.mutuals.common.exception.AccountSuspendedException;
import com.mutuals.common.exception.DuplicateResourceException;
import com.mutuals.common.exception.InvalidCredentialsException;
import com.mutuals.common.exception.InvalidTokenException;
import com.mutuals.event.UserRegisteredEvent;
import com.mutuals.security.JwtService;
import com.mutuals.security.UserPrincipal;
import com.mutuals.user.entity.ProfileCustomization;
import com.mutuals.user.entity.User;
import com.mutuals.user.entity.UserPreferences;
import com.mutuals.user.mapper.UserMapper;
import com.mutuals.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username is already taken");
        }
        User user = new User();
        user.setEmail(email);
        user.setUsername(request.username());
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setLastActiveAt(clock.instant());
        user.attachPreferences(new UserPreferences());
        user.attachCustomization(new ProfileCustomization());
        User saved = userRepository.save(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId()));
        return buildResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(Locale.ROOT), request.password()));
        } catch (DisabledException ex) {
            throw new AccountSuspendedException();
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException();
        }
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow(InvalidCredentialsException::new);
        user.setLastActiveAt(clock.instant());
        return buildResponse(user);
    }

    /**
     * Inicia sesión con Google. Si el correo (verificado por Google) ya existe, entra a esa cuenta;
     * si no, crea una cuenta nueva con un username generado y una contraseña aleatoria
     * (puede definir una propia con "olvidé mi contraseña").
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleTokenVerifier.GoogleUser google = googleTokenVerifier.verify(request.idToken());
        String email = google.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return buildResponse(registerFromGoogle(email, google));
        }
        if (!user.isActive()) {
            throw new AccountSuspendedException();
        }
        if (user.getAvatarUrl() == null && google.picture() != null && google.picture().length() <= 500) {
            user.setAvatarUrl(google.picture());
        }
        user.setLastActiveAt(clock.instant());
        return buildResponse(user);
    }

    private User registerFromGoogle(String email, GoogleTokenVerifier.GoogleUser google) {
        String username = uniqueUsername(email.substring(0, email.indexOf('@')));
        String name = google.name() == null || google.name().isBlank() ? username : google.name().trim();
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setDisplayName(name.length() > 50 ? name.substring(0, 50) : name);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        if (google.picture() != null && google.picture().length() <= 500) {
            user.setAvatarUrl(google.picture());
        }
        user.setLastActiveAt(clock.instant());
        user.attachPreferences(new UserPreferences());
        user.attachCustomization(new ProfileCustomization());
        User saved = userRepository.save(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId()));
        return saved;
    }

    /** Convierte la parte local del correo en un username válido (^[a-z0-9._]{3,30}$) y libre. */
    private String uniqueUsername(String localPart) {
        String base = localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._]", "");
        if (base.length() < 3) {
            base = base + "user";
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken consumed = refreshTokenService.consume(rawRefreshToken);
        User user = consumed.getUser();
        if (!user.isActive()) {
            throw new AccountSuspendedException();
        }
        return buildResponse(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private AuthResponse buildResponse(User user) {
        String accessToken = jwtService.generateAccessToken(UserPrincipal.from(user));
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken, TOKEN_TYPE, jwtService.getAccessTokenTtlSeconds(),
                userMapper.toMe(user));
    }
}
