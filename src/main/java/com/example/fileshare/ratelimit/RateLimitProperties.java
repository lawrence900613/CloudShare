package com.example.fileshare.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private boolean failOpen = false;
    private Limit authLogin = new Limit(10, Duration.ofMinutes(1));
    private Limit authRegister = new Limit(5, Duration.ofMinutes(15));
    private Limit authForgotPassword = new Limit(5, Duration.ofHours(1));
    private Limit authResendVerification = new Limit(5, Duration.ofHours(1));
    private Limit authVerify = new Limit(30, Duration.ofHours(1));
    private Limit authResetPassword = new Limit(10, Duration.ofHours(1));
    private Limit filesUploadPresigned = new Limit(1, Duration.ofMinutes(5));
    private Limit filesUploadLegacy = new Limit(1, Duration.ofMinutes(5));
    private Limit sharesPublicApi = new Limit(120, Duration.ofMinutes(1));
    private Limit sharesPublicShort = new Limit(120, Duration.ofMinutes(1));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public Limit getAuthLogin() {
        return authLogin;
    }

    public void setAuthLogin(Limit authLogin) {
        this.authLogin = authLogin;
    }

    public Limit getAuthRegister() {
        return authRegister;
    }

    public void setAuthRegister(Limit authRegister) {
        this.authRegister = authRegister;
    }

    public Limit getAuthForgotPassword() {
        return authForgotPassword;
    }

    public void setAuthForgotPassword(Limit authForgotPassword) {
        this.authForgotPassword = authForgotPassword;
    }

    public Limit getAuthResendVerification() {
        return authResendVerification;
    }

    public void setAuthResendVerification(Limit authResendVerification) {
        this.authResendVerification = authResendVerification;
    }

    public Limit getAuthVerify() {
        return authVerify;
    }

    public void setAuthVerify(Limit authVerify) {
        this.authVerify = authVerify;
    }

    public Limit getAuthResetPassword() {
        return authResetPassword;
    }

    public void setAuthResetPassword(Limit authResetPassword) {
        this.authResetPassword = authResetPassword;
    }

    public Limit getFilesUploadPresigned() {
        return filesUploadPresigned;
    }

    public void setFilesUploadPresigned(Limit filesUploadPresigned) {
        this.filesUploadPresigned = filesUploadPresigned;
    }

    public Limit getFilesUploadLegacy() {
        return filesUploadLegacy;
    }

    public void setFilesUploadLegacy(Limit filesUploadLegacy) {
        this.filesUploadLegacy = filesUploadLegacy;
    }

    public Limit getSharesPublicApi() {
        return sharesPublicApi;
    }

    public void setSharesPublicApi(Limit sharesPublicApi) {
        this.sharesPublicApi = sharesPublicApi;
    }

    public Limit getSharesPublicShort() {
        return sharesPublicShort;
    }

    public void setSharesPublicShort(Limit sharesPublicShort) {
        this.sharesPublicShort = sharesPublicShort;
    }

    public static class Limit {
        private int limit;
        private Duration window;

        public Limit() {
            this(1, Duration.ofMinutes(1));
        }

        public Limit(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}
