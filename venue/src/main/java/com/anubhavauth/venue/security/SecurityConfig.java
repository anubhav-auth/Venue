package com.anubhavauth.venue.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        // Public endpoints
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/student/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // Admin — specific rules before the catch-all
                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/api/admin/volunteers/*/mark-absent"
                        ).hasAnyRole("ADMIN", "TEAM_LEAD")

                        // Admin — catch-all (must come after specific rules)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Check-in scan & reviews — verifiers, team leads, and admins
                        .requestMatchers("/api/checkin/*/review").hasAnyRole("VERIFIER", "TEAM_LEAD", "ADMIN")
                        .requestMatchers("/api/checkin/**").hasAnyRole("VERIFIER", "TEAM_LEAD", "ADMIN")

                        // Verifier dashboard — VERIFIER, TEAM_LEAD, and ADMIN
                        .requestMatchers("/api/verifier/**").hasAnyRole("VERIFIER", "TEAM_LEAD", "ADMIN")

                        // Student portal — audience and volunteers
                        .requestMatchers("/api/student/**").hasAnyRole("AUDIENCE", "VOLUNTEER")

                        // Everything else must be authenticated
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}