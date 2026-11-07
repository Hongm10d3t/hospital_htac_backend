package com.htac_backend.security;

import com.htac_backend.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/api/v1/public/**").permitAll()
                .anyRequest().denyAll());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                        writeError(mapper, response, request, 401, "UNAUTHENTICATED", "Cần đăng nhập"))
                .accessDeniedHandler((request, response, exception) -> {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
                        writeError(mapper, response, request, 401, "UNAUTHENTICATED", "Cần đăng nhập");
                    } else {
                        writeError(mapper, response, request, 403, "FORBIDDEN", "Không có quyền truy cập");
                    }
                }));
        http.httpBasic(httpBasic -> httpBasic.disable());
        http.formLogin(formLogin -> formLogin.disable());
        http.csrf(Customizer.withDefaults());
        return http.build();
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse response,
                                   HttpServletRequest request, int status, String code,
                                   String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(
                new ApiError(code, message, Instant.now(), request.getRequestURI(), Map.of())));
    }
}
