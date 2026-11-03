package org.example.survey.config;

import org.example.survey.api.ApiAuthInterceptor;
import org.example.survey.security.AuthInterceptor;
import org.example.survey.security.CsrfInterceptor;
import org.example.survey.service.AppService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppService appService;
    private final ApiAuthInterceptor apiAuthInterceptor;

    public WebConfig(AppService appService, ApiAuthInterceptor apiAuthInterceptor) {
        this.appService = appService;
        this.apiAuthInterceptor = apiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CsrfInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/api/**", "/css/**", "/js/**", "/error", "/favicon.ico");
        registry.addInterceptor(new AuthInterceptor(appService))
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/index", "/login", "/registration", "/verify-email", "/resend-verification",
                        "/a/**", "/api/**", "/css/**", "/js/**", "/error", "/favicon.ico");
        registry.addInterceptor(apiAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/register",
                        "/api/v1/auth/resend-verification", "/api/v1/public/**");
    }
}
