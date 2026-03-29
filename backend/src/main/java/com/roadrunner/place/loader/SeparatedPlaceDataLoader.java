package com.roadrunner.place.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds categorized CSV files from {@code ankara_places/} into separate tables
 * corresponding to their filenames on application startup.
 * Creates the tables dynamically if they don't exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test") // skip during automated tests
public class SeparatedPlaceDataLoader implements ApplicationRunner {

    private static final String CSV_DIR = "ankara_places/";
    /** Number of records to insert per batch to avoid huge transactions. */
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Seeding separated places tables from {}...", CSV_DIR);

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
                if (filename == null) continue;
                
                // e.g. "bars_nightclubs.csv" -> "bars_nightclubs_places"
                String tableName = filename.replace(".csv", "") + "_places";
                String categoryHint = extractCategory(filename);
                log.info("Processing {} into table {} (category hint: {})...", filename, tableName, categoryHint);
                
                createTableIfNotExists(tableName);
                clearTable(tableName);
                
                int count = seedFromResource(resource, tableName, categoryHint);
                grandTotal += count;
            }

            log.info("Separated places seeding complete. Total inserted: {}", grandTotal);
        } catch (Exception e) {
            log.error("Failed to seed separated places: {}", e.getMessage(), e);
        }
    }

    private void createTableIfNotExists(String tableName) {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id VARCHAR(64) PRIMARY KEY, " +
                "name VARCHAR(255) NOT NULL, " +
                "formatted_address TEXT, " +
                "lat DOUBLE PRECISION NOT NULL, " +
                "lng DOUBLE PRECISION NOT NULL, " +
                "types TEXT, " +
                "rating_score DOUBLE PRECISION, " +
                "rating_count INTEGER, " +
                "price_level VARCHAR(64), " +
                "business_status VARCHAR(64)" +
                ")";
        jdbcTemplate.execute(sql);
        log.info("Ensured table {} exists", tableName);
    }

    private void clearTable(String tableName) {
        try {
            jdbcTemplate.execute("DELETE FROM " + tableName);
        } catch (Exception e) {
            log.warn("Could not clear table {}: {}", tableName, e.getMessage());
        }
    }

    private int seedFromResource(Resource resource, String tableName, String categoryHint) {
        int total = 0;
        int skipped = 0;

        String insertSql = "INSERT INTO " + tableName + " " +
                "(id, name, formatted_address, lat, lng, types, rating_score, rating_count, price_level) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "name = EXCLUDED.name, formatted_address = EXCLUDED.formatted_address, " +
                "lat = EXCLUDED.lat, lng = EXCLUDED.lng, types = EXCLUDED.types, " +
                "rating_score = EXCLUDED.rating_score, rating_count = EXCLUDED.rating_count, " +
                "price_level = EXCLUDED.price_level";

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // skip header row
            if (header == null) {
                return 0;
            }

            List<Object[]> batchArgs = new ArrayList<>(BATCH_SIZE);
            String line;

            while ((line = reader.readLine()) != null) {
                try {
                    Object[] placeArgs = parseLineToArgs(line, categoryHint);
                    if (placeArgs != null) {
                        batchArgs.add(placeArgs);
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    log.warn("Skipping malformed CSV line in {}: {} | error: {}", 
                        resource.getFilename(), line, e.getMessage());
                    skipped++;
                }

                if (batchArgs.size() == BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(insertSql, batchArgs);
                    total += batchArgs.size();
                    batchArgs.clear();
                }
            }

            if (!batchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(insertSql, batchArgs);
                total += batchArgs.size();
            }

            log.info("Table {} complete. Inserted/Updated: {}, Skipped: {}", 
                tableName, total, skipped);

        } catch (Exception e) {
            log.error("Error reading {}: {}", resource.getFilename(), e.getMessage());
        }
        return total;
    }

    private Object[] parseLineToArgs(String line, String categoryHint) {
        String[] tokens = splitCsvLine(line);
        if (tokens.length < 5) return null;

        String id = clean(tokens[0]);
        if (id.isBlank()) return null;

        String name = clean(tokens[1]);
        String formattedAddress = tokens.length > 2 ? clean(tokens[2]) : null;
        Double lat = parseDouble(tokens.length > 3 ? tokens[3] : null);
        Double lng = parseDouble(tokens.length > 4 ? tokens[4] : null);
        
        String types = tokens.length > 5 ? flattenTypes(clean(tokens[5])) : "";
        if (categoryHint != null && !types.toLowerCase().contains(categoryHint.toLowerCase())) {
            types = categoryHint + (types.isEmpty() ? "" : "," + types);
        }

        Double rating = tokens.length > 6 ? parseDouble(tokens[6]) : null;
        Integer ratingCount = tokens.length > 7 ? parseInt(tokens[7]) : null;
        String priceLevel = tokens.length > 8 ? clean(tokens[8]) : "";

        return new Object[]{id, name, formattedAddress, lat, lng, types, rating, ratingCount, priceLevel};
    }

    private String extractCategory(String filename) {
        if (filename == null) return null;
        String lower = filename.toLowerCase();
        if (lower.contains("historic")) return "Historic Places";
        if (lower.contains("cafe") || lower.contains("dessert")) return "Cafes & Desserts";
        if (lower.contains("restaurant")) return "Restaurants";
        if (lower.contains("park")) return "Parks";
        if (lower.contains("landmark")) return "Landmarks";
        if (lower.contains("bar") || lower.contains("nightclub")) return "Bars & Nightclubs";
        if (lower.contains("hotel")) return "Hotels";
        return null;
    }

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
        if (raw == null || raw.isBlank()) return "";
        return raw.replaceAll("[\\[\\]\"]", "").trim();
    }

    private String clean(String s) {
        return s == null ? "" : s.strip();
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return (int) Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
