package com.study.spring.security;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Profile("dev")
public class SecurityDataInit implements CommandLineRunner {

    private final SiteUserRepository siteUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (siteUserRepository.count() == 0) {
            siteUserRepository.save(new SiteUser(
                    "user",
                    passwordEncoder.encode("1234"),
                    "USER"));

            siteUserRepository.save(new SiteUser(
                    "admin",
                    passwordEncoder.encode("1234"),
                    "ADMIN"));
        }
    }
}
