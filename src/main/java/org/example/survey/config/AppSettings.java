package org.example.survey.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppSettings {

    private final String baseUrl;
    private final boolean emailVerificationRequired;
    private final int emailTokenHours;
    private final int apiTokenDays;
    private final String mailFrom;

    public AppSettings(@Value("${app.base-url}") String baseUrl,
                       @Value("${app.email-verification.required}") boolean emailVerificationRequired,
                       @Value("${app.email-verification.token-hours}") int emailTokenHours,
                       @Value("${app.api.token-days}") int apiTokenDays,
                       @Value("${spring.mail.username}") String mailFrom) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.emailVerificationRequired = emailVerificationRequired;
        this.emailTokenHours = emailTokenHours;
        this.apiTokenDays = apiTokenDays;
        this.mailFrom = mailFrom;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isEmailVerificationRequired() {
        return emailVerificationRequired;
    }

    public int getEmailTokenHours() {
        return emailTokenHours;
    }

    public int getApiTokenDays() {
        return apiTokenDays;
    }

    public String getMailFrom() {
        return mailFrom;
    }
}
