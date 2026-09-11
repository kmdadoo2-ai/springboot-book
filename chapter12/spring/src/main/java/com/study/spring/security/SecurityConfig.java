package com.study.spring.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
					.csrf(csrf -> csrf
									.ignoringRequestMatchers("/api/**"))
					.authorizeHttpRequests(auth -> auth
									.requestMatchers("/", "/login", "/css/**", "/js/**", "/firebase-messaging-sw.js").permitAll()
									.requestMatchers(HttpMethod.GET, "/jpa/boards", "/jpa/boards/*")
									.permitAll()
									.requestMatchers(HttpMethod.GET, "/api/boards", "/api/boards/*")
									.permitAll()
									.requestMatchers("/admin/**").hasRole("ADMIN")
									.requestMatchers("/jpa/boards/new", "/jpa/boards/*/edit",
													"/jpa/boards/*/delete")
									.authenticated()
									.requestMatchers(HttpMethod.POST, "/api/boards").authenticated()
									.requestMatchers(HttpMethod.PUT, "/api/boards/*").authenticated()
									.requestMatchers(HttpMethod.DELETE, "/api/boards/*").authenticated()
									.anyRequest().authenticated())
					.formLogin(form -> form
									.loginPage("/login")
									.defaultSuccessUrl("/jpa/boards", true)
									.permitAll())
					.logout(logout -> logout
									.logoutUrl("/logout")
									.logoutSuccessUrl("/jpa/boards")
									.permitAll())
					.httpBasic(basic -> {
					});

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
