package com.htac_backend.security;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    SecurityErrorWriter securityErrorWriter(ObjectMapper mapper) {
        return new SecurityErrorWriter(mapper);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
            CsrfTokenRepository csrfTokens, UserAccountRepository accounts,
            SecurityErrorWriter errors) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/auth/me").authenticated()
                .requestMatchers("/api/v1/auth/change-password").authenticated()
                .requestMatchers("/api/v1/admin/staff-accounts", "/api/v1/admin/staff-accounts/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/accounts/*/password-reset").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/hospital-config").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/specialties", "/api/v1/admin/specialties/**",
                        "/api/v1/admin/clinic-rooms", "/api/v1/admin/clinic-rooms/**",
                        "/api/v1/admin/doctor-profiles", "/api/v1/admin/doctor-profiles/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/clinic-slots", "/api/v1/admin/clinic-slots/**")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/appointments", "/api/v1/appointments/**")
                .hasRole("PATIENT")
                .requestMatchers("/api/v1/patients/me", "/api/v1/patients/me/**").hasRole("PATIENT")
                .requestMatchers("/api/v1/doctor/**").hasRole("DOCTOR")
                .requestMatchers("/api/v1/pharmacy/**").hasRole("PHARMACIST")
                .requestMatchers("/api/v1/reception/patients/registration-candidates",
                        "/api/v1/reception/patients/*/registration-verification")
                .hasRole("RECEPTIONIST")
                .requestMatchers("/api/v1/reception/appointments", "/api/v1/reception/appointments/**")
                .hasRole("RECEPTIONIST")
                .requestMatchers("/api/v1/reception/visits", "/api/v1/reception/visits/**")
                .hasRole("RECEPTIONIST")
                .requestMatchers("/api/v1/reception/patients", "/api/v1/reception/patients/**")
                .hasRole("RECEPTIONIST")
                .anyRequest().denyAll());
        http.securityContext(context -> context.securityContextRepository(contexts));
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens));
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> errors.write(request, response, 401,
                        "UNAUTHENTICATED", "Cần đăng nhập"))
                .accessDeniedHandler((request, response, exception) -> {
                    if (exception instanceof CsrfException) {
                        errors.write(request, response, 403, "FORBIDDEN", "CSRF token không hợp lệ");
                        return;
                    }
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth == null || auth instanceof AnonymousAuthenticationToken) {
                        errors.write(request, response, 401, "UNAUTHENTICATED", "Cần đăng nhập");
                    } else {
                        errors.write(request, response, 403, "FORBIDDEN", "Không có quyền truy cập");
                    }
                }));
        http.logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID"));
        http.httpBasic(basic -> basic.disable());
        http.formLogin(form -> form.disable());
        http.addFilterBefore(new ActiveAccountFilter(accounts, errors), AuthorizationFilter.class);
        return http.build();
    }
}
