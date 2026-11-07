package com.studyroom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.studyroom.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * HTTP security defaults for the Study Room API.
 *
 * Anonymous identities are issued by a dedicated endpoint in a later phase.
 * WebSocket authorization must also be enforced at the STOMP message level;
 * allowing the HTTP handshake alone does not authorize room operations.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The JWT filter lives only inside the security chain; do not also register it as a servlet filter. */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // The API uses short-lived bearer tokens, not cookie-based sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/anonymous")
                        .permitAll()
                        .requestMatchers("/ws", "/ws/**")
                        .permitAll()
                        // Authenticated (anonymous-JWT) API access.
                        .requestMatchers("/api/v1/**")
                        .authenticated()
                        // Built web client served by this service (static assets + SPA entry points).
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**",
                                "/favicon.*", "/room/*")
                        .permitAll()
                        // Everything else is closed by default.
                        .anyRequest().denyAll())
                .build();
    }
}
