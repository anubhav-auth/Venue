package com.anubhavauth.venue.config;

import com.anubhavauth.venue.repository.AdminUserRepository;
import com.anubhavauth.venue.entity.AdminUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {

        seedAdmin("anubhav", "anubhav2345", "Anubhav");
        seedAdmin("sai", "sai2345", "Sai");
        seedAdmin("iteradmin", "iteradmin7890", "ITER Admin");

        log.info("Admin seeding completed.");
    }

    private void seedAdmin(String username, String password, String fullName) {

        if (adminUserRepository.existsByUsername(username)) {
            log.info("Admin {} already exists — skipping.", username);
            return;
        }

        AdminUser admin = AdminUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .build();

        adminUserRepository.save(admin);

        log.info("Admin seeded → username: {}", username);
    }
}