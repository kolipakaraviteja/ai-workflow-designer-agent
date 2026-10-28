package com.aiworkflow.designer.security;

import com.aiworkflow.designer.persistence.entity.AppUser;
import com.aiworkflow.designer.persistence.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements ApplicationRunner {

    private static final String DEV_PASSWORD = "admin123";
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final Set<String> BANNED_PASSWORDS = Set.of(
            "admin123", "password", "changeme", "workflow_password");

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (appUserRepository.existsByUsername(adminUsername)) {
            return;
        }

        String password = resolveAdminPassword();

        AppUser admin = AppUser.builder()
                .username(adminUsername)
                .passwordHash(passwordEncoder.encode(password))
                .role("ADMIN")
                .enabled(true)
                .build();

        appUserRepository.save(admin);
        log.info("Created default admin user: {}", adminUsername);
    }

    /**
     * Outside local/test profiles the admin bootstrap password must be
     * provided and meet policy — startup fails rather than shipping a known
     * default credential.
     */
    private String resolveAdminPassword() {
        if (isLocalOrTest()) {
            return (adminPassword == null || adminPassword.isBlank()) ? DEV_PASSWORD : adminPassword;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "APP_ADMIN_PASSWORD is not set. Refusing to start — no admin can be "
                            + "bootstrapped and no default credential is allowed.");
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH || BANNED_PASSWORDS.contains(adminPassword)) {
            throw new IllegalStateException(
                    "APP_ADMIN_PASSWORD does not meet policy (min " + MIN_PASSWORD_LENGTH
                            + " characters, no well-known passwords).");
        }
        return adminPassword;
    }

    private boolean isLocalOrTest() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return Arrays.stream(profiles).anyMatch(p -> p.equals("local") || p.equals("test"));
    }
}
