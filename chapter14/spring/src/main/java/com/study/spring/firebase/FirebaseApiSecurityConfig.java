package com.study.spring.firebase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class FirebaseApiSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain firebaseSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http.securityMatcher(
                "/api/firebase/**", "/api/fcm/**",
                "/api/firestore/**", "/api/firebase-auth/**")
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole("ADMIN"))
                .csrf(Customizer.withDefaults())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
