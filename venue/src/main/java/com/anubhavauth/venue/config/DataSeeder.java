package com.anubhavauth.venue.config;

import com.anubhavauth.venue.repository.AdminUserRepository;
import com.anubhavauth.venue.entity.AdminUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${admin.default.username:admin}")
    private String defaultUsername;

    @Value("${admin.default.password:admin123}")
    private String defaultPassword;

    @Value("${admin.full.name:System Administrator}")
    private String fullName;


    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() == 0) {
            AdminUser admin = AdminUser.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .fullName("System Administrator")
                    .build();
            adminUserRepository.save(admin);
            log.info("Default admin user seeded → username: admin");
        } else {
            log.info("Admin user already exists — skipping seed.");
        }
    }
}
