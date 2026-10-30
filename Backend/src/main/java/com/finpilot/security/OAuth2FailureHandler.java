package com.finpilot.security;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(client[_-]?secret|access[_-]?token|refresh[_-]?token|authorization[_-]?code|code)(\\s*[=:]\\s*)([^&\\s,\\\"'}]+)");

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String errorCode = exception instanceof OAuth2AuthenticationException oauthException
            ? oauthException.getError().getErrorCode()
            : exception.getClass().getSimpleName();
        String reason = exception.getMessage() == null ? "No failure details provided" : exception.getMessage();
        reason = SENSITIVE_VALUE.matcher(reason).replaceAll("$1$2[REDACTED]");
        if (reason.length() > 300) {
            reason = reason.substring(0, 300) + "...";
        }
        log.warn("Google OAuth authentication failed ({}): {}", errorCode, reason);
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/login")
                .queryParam("error", "google")
                .build()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
