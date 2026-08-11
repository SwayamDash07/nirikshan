package com.nirikshan.config;
import com.nirikshan.model.*;
import com.nirikshan.repository.VenueRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;

@Configuration @Profile("dev")
public class DevDataSeeder {
    private final VenueRepository venueRepository;
    public DevDataSeeder(VenueRepository venueRepository) { this.venueRepository = venueRepository; }
    @Bean CommandLineRunner seedData() { return args -> {
        if (venueRepository.count() > 0) return;
        Venue venue = new Venue("KIIT Campus 25", "Development venue representing the KIIT Campus 25 college campus.", 20.3641, 85.8163);
        venue.getZones().add(new Zone(venue, "Main Gate", 20.363634315359995, 85.81627165681948, 80));
        venue.getZones().add(new Zone(venue, "Hostel 25 Gate", 20.364145031341526, 85.81619190942068, 70));
        venue.getZones().add(new Zone(venue, "Cafeteria", 20.36461975435873, 85.81587627107363, 75));
        venue.getZones().add(new Zone(venue, "A Block Entrance", 20.364354947887005, 85.81617608892412, 80));
        venue.getZones().add(new Zone(venue, "C Block Entrance", 20.36376025781561, 85.81713519590612, 70));
        venueRepository.save(venue);
    }; }
}
