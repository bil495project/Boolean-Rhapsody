package com.roadrunner.place.loader;

import com.roadrunner.place.entity.Place;
import com.roadrunner.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the {@code places} table from categorized CSV files in
 * {@code ankara_places/}
 * on application startup.
 * Clears the table before seeding to ensure a fresh state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test") // skip during automated tests
public class PlaceDataLoader implements ApplicationRunner {

    private static final String CSV_DIR = "ankara_places/";
    /** Number of records to insert per batch to avoid huge transactions. */
    private static final int BATCH_SIZE = 500;

    private final PlaceRepository placeRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Clearing places table for fresh seed...");
        try {
            placeRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Could not clear places table (it might be empty or not exists yet): {}", e.getMessage());
        }

        log.info("Seeding places from {}...", CSV_DIR);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:" + CSV_DIR + "*.csv");

            if (resources.length == 0) {
                log.warn("No CSV files found in classpath:{}", CSV_DIR);
                return;
            }

            int grandTotal = 0;
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String categoryHint = extractCategory(filename);
                log.info("Processing {} (category hint: {})...", filename, categoryHint);

                int count = seedFromResource(resource, categoryHint);
                grandTotal += count;
            }

            log.info("Places seeding complete. Total inserted: {}", grandTotal);
        } catch (Exception e) {
            log.error("Failed to seed places: {}", e.getMessage(), e);
        }
    }

    /**
     * Seeds data from a single resource.
     */
    private int seedFromResource(Resource resource, String categoryHint) {
        int total = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // skip header row
            if (header == null) {
                return 0;
            }

            List<Place> batch = new ArrayList<>(BATCH_SIZE);
            String line;

            while ((line = reader.readLine()) != null) {
                try {
                    Place place = parseLine(line, categoryHint);
                    if (place != null) {
                        batch.add(place);
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    log.warn("Skipping malformed CSV line in {}: {} | error: {}",
                            resource.getFilename(), line, e.getMessage());
                    skipped++;
                }

                if (batch.size() == BATCH_SIZE) {
                    placeRepository.saveAll(batch);
                    total += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                placeRepository.saveAll(batch);
                total += batch.size();
            }

            log.info("Resource {} complete. Inserted: {}, Skipped: {}",
                    resource.getFilename(), total, skipped);

        } catch (Exception e) {
            log.error("Error reading {}: {}", resource.getFilename(), e.getMessage());
        }
        return total;
    }

    /**
     * Extracts a category hint from the filename to inject into the types field.
     */
    private String extractCategory(String filename) {
        if (filename == null)
            return null;
        String lower = filename.toLowerCase();
        if (lower.contains("historic"))
            return "Historic Places";
        if (lower.contains("cafe") || lower.contains("dessert"))
            return "Cafes & Desserts";
        if (lower.contains("restaurant"))
            return "Restaurants";
        if (lower.contains("park"))
            return "Parks";
        if (lower.contains("landmark"))
            return "Landmarks";
        if (lower.contains("bar") || lower.contains("nightclub"))
            return "Bars & Nightclubs";
        if (lower.contains("hotel"))
            return "Hotels";
        return null;
    }

    /**
     * Parses a single CSV line into a {@link Place} entity.
     */
    private Place parseLine(String line, String categoryHint) {
        // Split on commas that are NOT inside double-quotes
        String[] tokens = splitCsvLine(line);
        if (tokens.length < 5)
            return null;

        String id = clean(tokens[0]);
        if (id.isBlank())
            return null;

        String name = clean(tokens[1]);
        String formattedAddress = tokens.length > 2 ? clean(tokens[2]) : null;
        Double lat = parseDouble(tokens.length > 3 ? tokens[3] : null);
        Double lng = parseDouble(tokens.length > 4 ? tokens[4] : null);

        // Flatten the Python-style list ["type1", "type2"] → "type1,type2"
        String types = tokens.length > 5 ? flattenTypes(clean(tokens[5])) : "";

        // Prepend category hint if not present
        if (categoryHint != null && !types.toLowerCase().contains(categoryHint.toLowerCase())) {
            types = categoryHint + (types.isEmpty() ? "" : "," + types);
        }

        Double rating = tokens.length > 6 ? parseDouble(tokens[6]) : null;
        Integer ratingCount = tokens.length > 7 ? parseInt(tokens[7]) : null;
        String priceLevel = tokens.length > 8 ? clean(tokens[8]) : "";

        return Place.builder()
                .id(id)
                .name(name)
                .formattedAddress(formattedAddress)
                .latitude(lat)
                .longitude(lng)
                .types(types)
                .ratingScore(rating)
                .ratingCount(ratingCount)
                .priceLevel(priceLevel)
                .build();
    }

    /**
     * Minimal RFC-4180 CSV splitter that handles quoted fields.
     */
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private String flattenTypes(String raw) {
        if (raw == null || raw.isBlank())
            return "";
        return raw.replaceAll("[\\[\\]\"]", "").trim();
    }

    private String clean(String s) {
        return s == null ? "" : s.strip();
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return (int) Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
