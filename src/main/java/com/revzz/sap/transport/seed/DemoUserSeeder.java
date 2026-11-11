package com.revzz.sap.transport.seed;

import com.revzz.sap.transport.config.AppProperties;
import com.revzz.sap.transport.domain.AppUser;
import com.revzz.sap.transport.domain.PortalRole;
import com.revzz.sap.transport.repo.PlatformRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the demo accounts.
 *
 * <p>The password is hashed with BCrypt at runtime rather than stored as a
 * literal hash in a SQL file, so no reusable hash is committed to the
 * repository.</p>
 */
@Component
@Order(1)
public class DemoUserSeeder implements CommandLineRunner {

    private final PlatformRepository platform;
    private final PasswordEncoder encoder;
    private final AppProperties properties;

    public DemoUserSeeder(PlatformRepository platform, PasswordEncoder encoder,
                           AppProperties properties) {
        this.platform = platform;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.isSeedDemoUsers() || platform.countAccounts() > 0) {
            return;
        }
        create("change.manager", "Change Manager", PortalRole.CHANGE_MANAGER);
        create("release.analyst", "Release Analyst", PortalRole.ANALYST);
        create("viewer", "Viewer", PortalRole.VIEWER);
    }

    private void create(String username, String displayName, PortalRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setEnabled(true);
        user.setPasswordHash(encoder.encode(properties.getDemoPassword()));
        platform.insertAccount(user);
    }
}
