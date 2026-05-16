package com.ebank.admin;

import com.ebank.common.dto.ApiResponse;
import com.ebank.common.security.JwtKeyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only operations. Every method here requires ROLE_ADMIN;
 * authenticated users without that role receive 403 Forbidden.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final JwtKeyStore keyStore;

    /**
     * Rotate JWT signing keys without downtime.
     * The previous public key remains in the JWKS response so tokens issued
     * before the rotation stay valid until their expiry timestamp.
     */
    @PostMapping("/rotate-keys")
    public ResponseEntity<ApiResponse<String>> rotateKeys() {
        keyStore.rotate();
        return ResponseEntity.ok(ApiResponse.success(
                keyStore.currentKid(),
                "Keys rotated. Previous key stays in JWKS until current tokens expire."));
    }
}
