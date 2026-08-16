package com.nirikshan.config;

import com.nirikshan.model.User;
import com.nirikshan.model.UserRole;
import com.nirikshan.model.Venue;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.UserRepository;
import com.nirikshan.repository.VenueRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
@Profile({"dev", "local-postgres"})
public class DevDataSeeder {
    private static final String VENUE_NAME = "KIIT Campus 25";

    private final VenueRepository venueRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwords;

    @Value("${nirikshan.auth.admin-seed-email:}")
    private String adminEmail;

    @Value("${nirikshan.auth.admin-seed-password:}")
    private String adminPassword;

    public DevDataSeeder(VenueRepository venueRepository, ZoneRepository zoneRepository,
                         UserRepository userRepository, PasswordEncoder passwords) {
        this.venueRepository = venueRepository;
        this.zoneRepository = zoneRepository;
        this.userRepository = userRepository;
        this.passwords = passwords;
    }

    @Bean
    CommandLineRunner seedData() {
        return args -> {
            Venue venue = venueRepository.findByNameIgnoreCase(VENUE_NAME)
                    .orElseGet(() -> venueRepository.save(new Venue(
                            VENUE_NAME,
                            "Development venue representing the KIIT Campus 25 college campus.",
                            20.3641,
                            85.8163)));

            seedZones(venue);
            seedAdmin();
        };
    }

    private void seedZones(Venue venue) {
        List<ZoneSeed> zones = List.of(
                new ZoneSeed("Main Gate", 20.36366814775126, 85.81626264649513, 80),
                new ZoneSeed("Hostel 25 Gate", 20.364145031341526, 85.81619190942068, 70),
                new ZoneSeed("Cafeteria", 20.36461975435873, 85.81587627107363, 75),
                new ZoneSeed("A Block Entrance", 20.364354947887005, 85.81617608892412, 80),
                new ZoneSeed("C Block Gate", 20.36376025781561, 85.81713519590612, 70),
                new ZoneSeed("Main Gate Exit", 20.36360968378996, 85.81631763177884, 70));

        for (ZoneSeed seed : zones) {
            if (!zoneRepository.existsByVenueIdAndNameIgnoreCase(venue.getId(), seed.name())) {
                zoneRepository.save(new Zone(venue, seed.name(), seed.latitude(), seed.longitude(), seed.radiusMeters()));
            }
        }
    }

    private void seedAdmin() {
        if (adminEmail.isBlank()) {
            return;
        }
        var existing = userRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
        if (existing != null) {
            if (existing.getRole() == UserRole.ADMIN && !existing.isProtectedAdmin()) {
                existing.setProtectedAdmin(true);
                userRepository.save(existing);
            }
            return;
        }
        if (!adminPassword.isBlank()) {
            User admin = new User();
            admin.setName("Nirikshan Admin");
            admin.setEmail(adminEmail.toLowerCase());
            admin.setPasswordHash(passwords.encode(adminPassword));
            admin.setRole(UserRole.ADMIN);
            admin.setMustChangePassword(false);
            admin.setProtectedAdmin(true);
            userRepository.save(admin);
        }
    }

    private record ZoneSeed(String name, double latitude, double longitude, double radiusMeters) {}
}
