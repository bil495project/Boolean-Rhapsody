package com.roadrunner.place.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.place.dto.request.PlaceBulkRequest;
import com.roadrunner.place.entity.Place;
import com.roadrunner.place.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests for {@link PlaceController}.
 * Loads the complete Spring context with the H2 test database.
 * Each test runs in a transaction that is rolled back afterwards.
 * POI endpoints are publicly accessible (no JWT required).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Tests - PlaceController")
class PlaceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlaceRepository placeRepository;

    private Place restaurantPlace;
    private Place cafePlace;

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll();

        // Seed: a restaurant-type place with a rating
        restaurantPlace = placeRepository.save(Place.builder()
                .id("ChIJtest-restaurant")
                .name("Test Restaurant")
                .formattedAddress("Kızılay, Ankara, Türkiye")
                .latitude(39.92)
                .longitude(32.85)
                .types("restaurant,food,point_of_interest")
                .ratingScore(4.2)
                .ratingCount(150)
                .priceLevel("PRICE_LEVEL_MODERATE")
                .businessStatus("OPERATIONAL")
                .build());

        // Seed: a café-type place with a high rating
        cafePlace = placeRepository.save(Place.builder()
                .id("ChIJtest-cafe")
                .name("Test Cafe")
                .formattedAddress("Çankaya, Ankara, Türkiye")
                .latitude(39.90)
                .longitude(32.86)
                .types("cafe,food,point_of_interest")
                .ratingScore(4.8)
                .ratingCount(300)
                .priceLevel("PRICE_LEVEL_INEXPENSIVE")
                .businessStatus("OPERATIONAL")
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/places — list + pagination
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POII-009: GET /api/places tüm POI’leri döndürüyor mu (paginated)")
    void getAllPlaces_returns200() throws Exception {
        mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("TC-POII-013: Pagination geçerli page/size ile doğru sayıda sonuç dönüyor mu")
    void getAllPlaces_paginationParams() throws Exception {
        mockMvc.perform(get("/api/places")
                .param("page", "0")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/places?type=...
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POII-004: Type filtresi endpoint seviyesinde doğru çalışıyor mu")
    void getPlacesByType_returns200() throws Exception {
        mockMvc.perform(get("/api/places").param("type", "restaurant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("ChIJtest-restaurant"));
    }

    @Test
    @DisplayName("TC-POII-016: Geçersiz type verildiğinde boş liste dönüyor mu")
    void getPlacesByType_unknownType_emptyPage() throws Exception {
        mockMvc.perform(get("/api/places").param("type", "unknown_type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/places?minRating=...
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POII-005: Minimum rating filtresi endpoint seviyesinde doğru çalışıyor mu")
    void getPlacesByMinRating_returns200() throws Exception {
        mockMvc.perform(get("/api/places").param("minRating", "4.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("ChIJtest-cafe"));
    }

    @Test
    @DisplayName("TC-POII-015: Geçersiz minimum rating değeri için 400 dönüyor mu")
    void getPlacesByMinRating_invalidValue_returns400() throws Exception {
        mockMvc.perform(get("/api/places").param("minRating", "6.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-POII-015: Geçersiz minimum rating değeri için 400 dönüyor mu (non-numeric)")
    void getPlacesByMinRating_nonNumeric_returns400() throws Exception {
        mockMvc.perform(get("/api/places").param("minRating", "abc"))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/places?priceLevel=...
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POII-006: Price level filtresi endpoint seviyesinde doğru çalışıyor mu")
    void getPlacesByPriceLevel_returns200() throws Exception {
        mockMvc.perform(get("/api/places").param("priceLevel", "PRICE_LEVEL_MODERATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].priceLevel").value("PRICE_LEVEL_MODERATE"));
    }

    @Test
    @DisplayName("TC-POII-017: Geçersiz price level için ya 400 ya da boş liste dönüyor mu (Boş liste)")
    void getPlacesByPriceLevel_unknown_emptyPage() throws Exception {
        mockMvc.perform(get("/api/places").param("priceLevel", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/places/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POII-010: GET /api/places/{id} geçerli ID için 200 ve doğru nesne dönüyor mu")
    void getPlaceById_returns200() throws Exception {
        mockMvc.perform(get("/api/places/ChIJtest-restaurant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ChIJtest-restaurant"))
                .andExpect(jsonPath("$.name").value("Test Restaurant"));
    }

    @Test
    @DisplayName("TC-POII-011: GET /api/places/{id} olmayan ID için 404 dönüyor mu")
    void getPlaceById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/places/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/places/search?name=...
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POII-002: İsimle arama endpoint’i fuzzy/partial search için doğru sonucu veriyor mu")
    void searchByName_returns200() throws Exception {
        mockMvc.perform(get("/api/places/search").param("name", "Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("TC-POII-001: İsimle arama endpoint’i exact name için doğru sonucu veriyor mu")
    void searchByName_specificPartial() throws Exception {
        mockMvc.perform(get("/api/places/search").param("name", "Restaurant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("ChIJtest-restaurant"));
    }

    @Test
    @DisplayName("TC-POII-003: İsim aramasında eşleşme yoksa boş sonuç dönüyor mu")
    void searchByName_noMatch_emptyPage() throws Exception {
        mockMvc.perform(get("/api/places/search").param("name", "ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/places/bulk
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POII-012: Bulk ID listesi ile çoklu POI retrieval çalışıyor mu")
    void bulkGetPlaces_returns200() throws Exception {
        PlaceBulkRequest request = new PlaceBulkRequest(
                List.of("ChIJtest-restaurant", "ChIJtest-cafe"));

        mockMvc.perform(post("/api/places/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("TC-POII-012b: Bulk ID listesi ile çoklu POI retrieval kısmi valid ise")
    void bulkGetPlaces_partialMatch() throws Exception {
        PlaceBulkRequest request = new PlaceBulkRequest(
                List.of("ChIJtest-restaurant", "nonexistent-id"));

        mockMvc.perform(post("/api/places/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("TC-POII-020: Boş search parametresi (veya empty ID list) verildiğinde 400 dönüyor mu")
    void bulkGetPlaces_emptyIds_returns400() throws Exception {
        PlaceBulkRequest emptyRequest = new PlaceBulkRequest(List.of());

        mockMvc.perform(post("/api/places/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response payload structure
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POII-018: Response payload yapısında gerekli alanlar var mı")
    void responsePayload_hasAllRequiredFields() throws Exception {
        mockMvc.perform(get("/api/places/ChIJtest-restaurant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.formattedAddress").exists())
                .andExpect(jsonPath("$.latitude").exists())
                .andExpect(jsonPath("$.longitude").exists())
                .andExpect(jsonPath("$.types").exists())
                .andExpect(jsonPath("$.ratingScore").exists())
                .andExpect(jsonPath("$.ratingCount").exists())
                .andExpect(jsonPath("$.priceLevel").exists())
                .andExpect(jsonPath("$.businessStatus").exists());
    }
}
