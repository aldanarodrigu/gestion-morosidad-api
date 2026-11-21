package com.intendencia.gestion_morosidad_api.modules.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PasswordController {
    private final PasswordService passwords;

    @PutMapping("/api/auth/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cambiar(@Valid @RequestBody CambioPassword datos, Principal principal) {
        passwords.cambiar(principal.getName(), datos.passwordActual(), datos.passwordNueva());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/usuarios/{id}/password/reset")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<PasswordService.PasswordResetResponse> restablecer(@PathVariable long id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(passwords.restablecer(principal.getName(), id));
    }

    public record CambioPassword(
            @NotBlank @Size(max = 72) String passwordActual,
            @NotBlank @Size(max = 72) String passwordNueva) {}
}
