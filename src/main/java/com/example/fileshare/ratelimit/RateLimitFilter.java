package com.example.fileshare.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter redisRateLimiter;
    private final boolean enabled;
    private final List<Rule> rules;
    private static final Pattern EMAIL_JSON_PATTERN =
            Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public RateLimitFilter(
            RedisRateLimiter redisRateLimiter,
            RateLimitProperties properties
    ) {
        this.redisRateLimiter = redisRateLimiter;
        this.enabled = properties.isEnabled();
        LimitValue uploadPresigned = readLimit(properties.getFilesUploadPresigned());
        LimitValue uploadLegacy = readLimit(properties.getFilesUploadLegacy());
        this.rules = List.of(
                Rule.exact("auth_login", HttpMethod.POST, "/api/auth/login", readLimit(properties.getAuthLogin()).limit(), readLimit(properties.getAuthLogin()).window()),
                Rule.exact("auth_register", HttpMethod.POST, "/api/auth/register", readLimit(properties.getAuthRegister()).limit(), readLimit(properties.getAuthRegister()).window()),
                Rule.exact("auth_forgot_password", HttpMethod.POST, "/api/auth/forgot-password", readLimit(properties.getAuthForgotPassword()).limit(), readLimit(properties.getAuthForgotPassword()).window()),
                Rule.exact("auth_resend_verification", HttpMethod.POST, "/api/auth/resend-verification", readLimit(properties.getAuthResendVerification()).limit(), readLimit(properties.getAuthResendVerification()).window()),
                Rule.exact("auth_verify", HttpMethod.GET, "/api/auth/verify", readLimit(properties.getAuthVerify()).limit(), readLimit(properties.getAuthVerify()).window()),
                Rule.exact("auth_reset_password", HttpMethod.POST, "/api/auth/reset-password", readLimit(properties.getAuthResetPassword()).limit(), readLimit(properties.getAuthResetPassword()).window()),
                Rule.exact("files_upload_presigned", HttpMethod.POST, "/api/files/upload/presigned", uploadPresigned.limit(), uploadPresigned.window()),
                Rule.exact("files_upload_presigned", HttpMethod.POST, "/api/files/upload/presigned/", uploadPresigned.limit(), uploadPresigned.window()),
                Rule.exact("files_upload_legacy", HttpMethod.POST, "/api/files/upload", uploadLegacy.limit(), uploadLegacy.window()),
                Rule.exact("files_upload_legacy", HttpMethod.POST, "/api/files/upload/", uploadLegacy.limit(), uploadLegacy.window()),
                Rule.prefix("shares_public_api", HttpMethod.GET, "/api/shares/public/", readLimit(properties.getSharesPublicApi()).limit(), readLimit(properties.getSharesPublicApi()).window()),
                Rule.prefix("shares_public_short", HttpMethod.GET, "/s/", readLimit(properties.getSharesPublicShort()).limit(), readLimit(properties.getSharesPublicShort()).window())
        );
    }

    private static LimitValue readLimit(RateLimitProperties.Limit limit) {
        int safeLimit = (limit != null && limit.getLimit() > 0) ? limit.getLimit() : 1;
        Duration safeWindow = (limit != null && limit.getWindow() != null && !limit.getWindow().isNegative() && !limit.getWindow().isZero())
                ? limit.getWindow()
                : Duration.ofMinutes(1);
        return new LimitValue(safeLimit, safeWindow);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletRequest effectiveRequest = shouldWrapForBodyRead(request)
                ? new CachedBodyHttpServletRequest(request)
                : request;

        if (!enabled || HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(effectiveRequest, response);
            return;
        }

        Rule matched = resolveRule(effectiveRequest);
        if (matched == null) {
            filterChain.doFilter(effectiveRequest, response);
            return;
        }

        String clientIp = resolveClientIp(effectiveRequest);
        String email = resolveEmailIdentity(effectiveRequest);
        String key = buildRateLimitKey(matched.name(), clientIp, email);
        boolean allowed = redisRateLimiter.allow(key, matched.limit(), matched.window());
        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(1, matched.window().getSeconds())));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(effectiveRequest, response);
    }

    private boolean shouldWrapForBodyRead(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && request.getRequestURI().startsWith("/api/auth/");
    }

    private Rule resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        for (Rule rule : rules) {
            if (!rule.method().matches(method)) {
                continue;
            }
            if (rule.prefixMatch()) {
                if (path.startsWith(rule.path())) {
                    return rule;
                }
            } else if (path.equals(rule.path())) {
                return rule;
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveEmailIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().isBlank()
                && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName().trim().toLowerCase(Locale.ROOT);
        }

        if (!(request instanceof CachedBodyHttpServletRequest wrapped)) {
            return "";
        }
        if (!request.getRequestURI().startsWith("/api/auth/")) {
            return "";
        }

        byte[] body = wrapped.getCachedBody();
        if (body.length == 0) {
            return "";
        }
        String json = new String(body, StandardCharsets.UTF_8);
        Matcher matcher = EMAIL_JSON_PATTERN.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private String buildRateLimitKey(String ruleName, String ip, String email) {
        String safeIp = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        if (email == null || email.isBlank()) {
            return "rl:%s:ip:%s".formatted(ruleName, safeIp);
        }
        return "rl:%s:ip:%s:email:%s".formatted(ruleName, safeIp, email);
    }

    private record Rule(
            String name,
            HttpMethod method,
            String path,
            int limit,
            Duration window,
            boolean prefixMatch
    ) {
        static Rule exact(String name, HttpMethod method, String path, int limit, Duration window) {
            return new Rule(name, method, path, limit, window, false);
        }

        static Rule prefix(String name, HttpMethod method, String path, int limit, Duration window) {
            return new Rule(name, method, path, limit, window, true);
        }
    }

    private record LimitValue(int limit, Duration window) {}

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        private byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // No-op for sync request processing.
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
