package com.mutuals.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** ID token (JWT) que Google Identity Services entrega al frontend. */
public record GoogleLoginRequest(
        @NotBlank String idToken
) {
}
