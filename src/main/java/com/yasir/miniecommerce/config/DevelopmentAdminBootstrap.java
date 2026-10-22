package com.yasir.miniecommerce.config;

import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("dev")
@ConditionalOnProperty(
        prefix = "shopapi.bootstrap-admin",
        name = "enabled",
        havingValue = "true"
)
public class DevelopmentAdminBootstrap implements ApplicationRunner {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public DevelopmentAdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${shopapi.bootstrap-admin.email}") String email,
            @Value("${shopapi.bootstrap-admin.password}") String password) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateConfiguration();

        User existingUser = userRepository.findByEmail(email.trim())
                .orElse(null);

        if (existingUser != null) {
            if (ADMIN_ROLE.equals(existingUser.getRole())) {
                return;
            }

            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_EMAIL mevcut bir ADMIN olmayan hesaba ait. "
                            + "Mevcut kullanıcının rolü otomatik olarak değiştirilemez."
            );
        }

        User admin = new User();
        admin.setEmail(email.trim());
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(ADMIN_ROLE);

        userRepository.save(admin);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(email)) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_EMAIL development admin bootstrap için tanımlanmalıdır."
            );
        }

        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD development admin bootstrap için tanımlanmalıdır."
            );
        }

        if (password.length() < 12) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD en az 12 karakter olmalıdır."
            );
        }
    }
}
