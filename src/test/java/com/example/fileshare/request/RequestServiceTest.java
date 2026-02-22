package com.example.fileshare.request;

import com.example.fileshare.repository.UserRepository;
import com.example.fileshare.user.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestServiceTest {

    @Test
    void register_createsUnverifiedUserAndSendsEmail() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
        VerificationMailService verificationMailService = mock(VerificationMailService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtHelper jwt = mock(JwtHelper.class);

        when(users.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(encoder.encode("secret123")).thenReturn("hashed");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(verificationTokens.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationMailService.canSend()).thenReturn(true);

        RequestService service = new RequestService(
                users,
                verificationTokens,
                passwordResetTokens,
                verificationMailService,
                encoder,
                jwt,
                30,
                "http://localhost:5173",
                30,
                "http://localhost:5173"
        );

        boolean sent = service.register("User@Example.com", "secret123");

        Assertions.assertTrue(sent);
        verify(users).findByEmail("user@example.com");
        verify(users).saveAndFlush(any(User.class));
        verify(verificationTokens).deleteAllByUserIdAndConsumedAtIsNull(10L);
        verify(verificationMailService).sendVerificationMailAsync(eq("user@example.com"), any(String.class));
    }

    @Test
    void register_existingUnverified_updatesAndResends() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
        VerificationMailService verificationMailService = mock(VerificationMailService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtHelper jwt = mock(JwtHelper.class);

        User existing = new User();
        existing.setId(20L);
        existing.setEmail("user@example.com");
        existing.setPasswordHashed("old");
        existing.setEmailVerified(false);

        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(encoder.encode("secret123")).thenReturn("hashed");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationTokens.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationMailService.canSend()).thenReturn(true);

        RequestService service = new RequestService(
                users,
                verificationTokens,
                passwordResetTokens,
                verificationMailService,
                encoder,
                jwt,
                30,
                "http://localhost:5173",
                30,
                "http://localhost:5173"
        );

        boolean sent = service.register("user@example.com", "secret123");

        Assertions.assertTrue(sent);
        verify(users).saveAndFlush(existing);
        verify(verificationTokens).deleteAllByUserIdAndConsumedAtIsNull(20L);
        verify(verificationMailService).sendVerificationMailAsync(eq("user@example.com"), any(String.class));
    }

    @Test
    void register_existingVerified_returnsConflict() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
        VerificationMailService verificationMailService = mock(VerificationMailService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtHelper jwt = mock(JwtHelper.class);

        User existing = new User();
        existing.setId(30L);
        existing.setEmail("user@example.com");
        existing.setEmailVerified(true);

        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        RequestService service = new RequestService(
                users,
                verificationTokens,
                passwordResetTokens,
                verificationMailService,
                encoder,
                jwt,
                30,
                "http://localhost:5173",
                30,
                "http://localhost:5173"
        );

        ResponseStatusException ex = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> service.register("user@example.com", "secret123")
        );

        Assertions.assertEquals(409, ex.getStatusCode().value());
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void login_rejectsWhenEmailNotVerified() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
        VerificationMailService verificationMailService = mock(VerificationMailService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtHelper jwt = mock(JwtHelper.class);

        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHashed("hashed");
        user.setEmailVerified(false);

        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("secret123", "hashed")).thenReturn(true);

        RequestService service = new RequestService(
                users,
                verificationTokens,
                passwordResetTokens,
                verificationMailService,
                encoder,
                jwt,
                30,
                "http://localhost:5173",
                30,
                "http://localhost:5173"
        );

        ResponseStatusException ex = Assertions.assertThrows(
                ResponseStatusException.class,
                () -> service.login("user@example.com", "secret123")
        );

        Assertions.assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void verifyEmail_marksUserVerifiedAndConsumesToken() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
        VerificationMailService verificationMailService = mock(VerificationMailService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtHelper jwt = mock(JwtHelper.class);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken("verification-token");
        token.setUserId(99L);
        token.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        User user = new User();
        user.setId(99L);
        user.setEmail("user@example.com");
        user.setEmailVerified(false);

        when(verificationTokens.findByToken("verification-token")).thenReturn(Optional.of(token));
        when(users.findById(99L)).thenReturn(Optional.of(user));

        RequestService service = new RequestService(
                users,
                verificationTokens,
                passwordResetTokens,
                verificationMailService,
                encoder,
                jwt,
                30,
                "http://localhost:5173",
                30,
                "http://localhost:5173"
        );

        service.verifyEmail("verification-token");

        Assertions.assertTrue(user.isEmailVerified());
        Assertions.assertNotNull(token.getConsumedAt());
        verify(users).save(user);
        verify(verificationTokens).save(token);
    }

    @Test
    void forgotPassword_existingUser_createsResetTokenAndSendsMail() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
        VerificationMailService verificationMailService = mock(VerificationMailService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtHelper jwt = mock(JwtHelper.class);

        User user = new User();
        user.setId(42L);
        user.setEmail("user@example.com");

        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(verificationMailService.canSend()).thenReturn(true);
        when(passwordResetTokens.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequestService service = new RequestService(
                users,
                verificationTokens,
                passwordResetTokens,
                verificationMailService,
                encoder,
                jwt,
                30,
                "http://localhost:5173",
                30,
                "http://localhost:5173"
        );

        boolean sent = service.forgotPassword("user@example.com");

        Assertions.assertTrue(sent);
        verify(passwordResetTokens).deleteAllByUserIdAndConsumedAtIsNull(42L);
        verify(verificationMailService).sendPasswordResetMailAsync(eq("user@example.com"), any(String.class));
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndConsumesToken() {
        UserRepository users = mock(UserRepository.class);
        EmailVerificationTokenRepository verificationTokens = mock(EmailVerificationTokenRepository.class);
        PasswordResetTokenRepository passwordResetTokens = mock(PasswordResetTokenRepository.class);
        VerificationMailService verificationMailService = mock(VerificationMailService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtHelper jwt = mock(JwtHelper.class);

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("reset-token");
        token.setUserId(7L);
        token.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");
        user.setPasswordHashed("old");

        when(passwordResetTokens.findByToken("reset-token")).thenReturn(Optional.of(token));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.encode("newSecret123")).thenReturn("newHash");

        RequestService service = new RequestService(
                users,
                verificationTokens,
                passwordResetTokens,
                verificationMailService,
                encoder,
                jwt,
                30,
                "http://localhost:5173",
                30,
                "http://localhost:5173"
        );

        service.resetPassword("reset-token", "newSecret123");

        Assertions.assertEquals("newHash", user.getPasswordHashed());
        Assertions.assertNotNull(token.getConsumedAt());
        verify(users).save(user);
        verify(passwordResetTokens).save(token);
    }
}
