package com.incidentiq.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** Sign-in and "who am I". Sign-out is handled by Spring Security's logout filter. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignInGuard guard;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            SignInGuard guard) {

        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.guard = guard;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String address = httpRequest.getRemoteAddr();
        // Locked accounts (and busy addresses) are refused before the password is checked.
        guard.checkAllowed(request.email(), address);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email().trim(), request.password()));
        } catch (AuthenticationException exception) {
            guard.recordFailure(request.email(), address);
            // If that failure locked it, say so now rather than on the next try.
            guard.checkAllowed(request.email(), address);
            // Same answer for unknown email and wrong password.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthError("Incorrect email or password."));
        }
        guard.recordSuccess(request.email());

        // New session id on sign-in to prevent session fixation.
        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(currentUser(principal));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthError("Not signed in."));
        }

        UserResponse user = currentUser(principal);
        return user == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthError("Your account no longer exists."))
                : ResponseEntity.ok(user);
    }

    /**
     * Change your own password. A wrong current password is a 400, not a 401,
     * so the frontend doesn't treat it as being signed out. This session stays
     * signed in; other sessions of this account are signed out on their next
     * request (see {@link CurrentUserRefreshFilter}).
     */
    @PutMapping("/password")
    @Transactional
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        UserAccount user = userRepository.findById(principal.id()).orElse(null);
        if (user != null) {
            guard.checkNotLocked(user);
        }
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            if (user != null) {
                guard.recordFailure(user);
            }
            return ResponseEntity.badRequest()
                    .body(new AuthError("Your current password is incorrect."));
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest()
                    .body(new AuthError("Choose a password that's different from your current one."));
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);

        // Keep this session signed in with the new hash.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(CurrentUserRefreshFilter.authenticated(user));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        log.info("User {} changed their password", user.getId());
        return ResponseEntity.noContent().build();
    }

    /** Fresh from the database so name or role changes show up immediately. */
    private UserResponse currentUser(AppUserPrincipal principal) {
        return userRepository.findById(principal.id())
                .map(UserResponse::from)
                .orElse(null);
    }

    public record AuthError(String message) {
    }
}
