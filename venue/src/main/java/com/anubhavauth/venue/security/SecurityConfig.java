package com.anubhavauth.venue.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Forbidden\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth

                        // ── Public ────────────────────────────────────────────
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/student/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // ── TEAMLEAD-accessible admin endpoints ───────────────
                        // MUST come before the /api/admin/** catch-all
                        .requestMatchers(HttpMethod.POST, "/api/admin/volunteers/*/mark-absent")
                        .hasAnyRole("ADMIN", "TEAMLEAD")
                        .requestMatchers(HttpMethod.POST, "/api/admin/volunteers/scan")
                        .hasAnyRole("ADMIN", "TEAMLEAD")
                        .requestMatchers(HttpMethod.GET, "/api/admin/volunteers")
                        .hasAnyRole("ADMIN", "TEAMLEAD")

                        // ── Admin catch-all ───────────────────────────────────
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ── Verifier / TeamLead routes ────────────────────────
                        .requestMatchers("/api/checkin/*/review").hasAnyRole("VERIFIER", "TEAMLEAD", "ADMIN")
                        .requestMatchers("/api/checkin/**").hasAnyRole("VERIFIER", "TEAMLEAD", "ADMIN")
                        .requestMatchers("/api/verifier/**").hasAnyRole("VERIFIER", "TEAMLEAD", "ADMIN")

                        // ── Student portal ────────────────────────────────────
                        .requestMatchers("/api/student/**").hasAnyRole("AUDIENCE", "VOLUNTEER")

                        // ── Everything else requires auth ─────────────────────
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
