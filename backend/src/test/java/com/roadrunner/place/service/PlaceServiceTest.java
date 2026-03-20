package com.roadrunner.place.service;

import com.roadrunner.place.dto.response.PlaceResponse;
import com.roadrunner.place.entity.Place;
import com.roadrunner.place.exception.PlaceNotFoundException;
import com.roadrunner.place.mapper.PlaceMapper;
import com.roadrunner.place.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlaceServiceImpl}.
 * All repository and mapper calls are mocked; no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests - PlaceService")
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceMapper placeMapper;

    @InjectMocks
    private PlaceServiceImpl placeService;

    private Pageable pageable;
    private Place samplePlace;
    private PlaceResponse sampleResponse;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 20);

        samplePlace = Place.builder()
                .id("ChIJtest")
                .name("Test Place")
                .types("restaurant,food")
                .ratingScore(4.2)
                .priceLevel("PRICE_LEVEL_MODERATE")
                .businessStatus("OPERATIONAL")
                .build();

        sampleResponse = PlaceResponse.builder()
                .id("ChIJtest")
                .name("Test Place")
                .types("restaurant,food")
                .ratingScore(4.2)
                .priceLevel("PRICE_LEVEL_MODERATE")
                .businessStatus("OPERATIONAL")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getAllPlaces
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POIU-001: Tüm place’leri getirince veri varsa boş olmayan liste dönüyor mu")
    void getAllPlaces_returnsNonEmptyPage() {
        when(placeRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(samplePlace)));
        when(placeMapper.toResponse(samplePlace)).thenReturn(sampleResponse);

        Page<PlaceResponse> result = placeService.getAllPlaces(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("ChIJtest");
    }

    @Test
    @DisplayName("TC-POIU-002: Veri yoksa getAllPlaces() boş liste dönüyor mu")
    void getAllPlaces_returnsEmptyPage() {
        when(placeRepository.findAll(pageable)).thenReturn(Page.empty());

        Page<PlaceResponse> result = placeService.getAllPlaces(pageable);

        assertThat(result.isEmpty()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPlacesByType
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POIU-003: Exact type filtresi doğru çalışıyor mu (örn: restaurant)")
    void getPlacesByType_matchingType() {
        when(placeRepository.findByTypesContaining("restaurant", pageable))
                .thenReturn(new PageImpl<>(List.of(samplePlace)));
        when(placeMapper.toResponse(samplePlace)).thenReturn(sampleResponse);

        Page<PlaceResponse> result = placeService.getPlacesByType("restaurant", pageable);

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getTypes()).contains("restaurant");
    }

    @Test
    @DisplayName("TC-POIU-004: Geçersiz veya eşleşmeyen type için boş liste dönüyor mu")
    void getPlacesByType_noMatches() {
        when(placeRepository.findByTypesContaining("unknown_type", pageable))
                .thenReturn(Page.empty());

        assertThat(placeService.getPlacesByType("unknown_type", pageable).isEmpty()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPlacesByRatingScore
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POIU-005: Minimum rating filtresi doğru çalışıyor mu")
    void getPlacesByRatingScore_aboveThreshold() {
        when(placeRepository.findByRatingScoreGreaterThanEqual(4.0, pageable))
                .thenReturn(new PageImpl<>(List.of(samplePlace)));
        when(placeMapper.toResponse(samplePlace)).thenReturn(sampleResponse);

        Page<PlaceResponse> result = placeService.getPlacesByRatingScore(4.0, pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getContent().get(0).getRatingScore()).isGreaterThanOrEqualTo(4.0);
    }

    @Test
    @DisplayName("TC-POIU-006: Çok yüksek rating threshold verildiğinde sonuç boş dönüyor mu")
    void getPlacesByRatingScore_noMatches() {
        when(placeRepository.findByRatingScoreGreaterThanEqual(5.0, pageable))
                .thenReturn(Page.empty());

        assertThat(placeService.getPlacesByRatingScore(5.0, pageable).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("TC-POIS-MinRating: throws IllegalArgumentException for score > 5")
    void getPlacesByRatingScore_invalidScore_throws() {
        assertThatThrownBy(() -> placeService.getPlacesByRatingScore(6.0, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minScore must be between");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPlaceByName
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POIU-007: Exact isimle aramada doğru place bulunuyor mu")
    void getPlaceByName_exactMatch() {
        when(placeRepository.findByNameIgnoreCase("Test Place"))
                .thenReturn(Optional.of(samplePlace));
        when(placeMapper.toResponse(samplePlace)).thenReturn(sampleResponse);

        PlaceResponse result = placeService.getPlaceByName("Test Place");

        assertThat(result.getName()).isEqualTo("Test Place");
    }

    @Test
    @DisplayName("TC-POIU-009: Olmayan isim arandığında exception (veya null) dönüyor mu")
    void getPlaceByName_notFound() {
        when(placeRepository.findByNameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeService.getPlaceByName("ghost"))
                .isInstanceOf(PlaceNotFoundException.class);
    }

    @Test
    @DisplayName("TC-POIU-008: Kısmi/fuzzy isimle arama eşleşme bulabiliyor mu")
    void searchPlacesByName_partialMatch() {
        when(placeRepository.findByNameContainingIgnoreCase("Test", pageable))
                .thenReturn(new PageImpl<>(List.of(samplePlace)));
        when(placeMapper.toResponse(samplePlace)).thenReturn(sampleResponse);

        Page<PlaceResponse> result = placeService.searchPlacesByName("Test", pageable);

        assertThat(result).isNotEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPlacesByPriceLevel
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POIU-010: Price level filtresi doğru çalışıyor mu")
    void getPlacesByPriceLevel_exactMatch() {
        when(placeRepository.findByPriceLevel("PRICE_LEVEL_MODERATE", pageable))
                .thenReturn(new PageImpl<>(List.of(samplePlace)));
        when(placeMapper.toResponse(samplePlace)).thenReturn(sampleResponse);

        Page<PlaceResponse> result = placeService.getPlacesByPriceLevel("PRICE_LEVEL_MODERATE", pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getContent().get(0).getPriceLevel()).isEqualTo("PRICE_LEVEL_MODERATE");
    }

    @Test
    @DisplayName("TC-POIU-011: Eşleşmeyen price level için boş liste dönüyor mu")
    void getPlacesByPriceLevel_noMatches() {
        when(placeRepository.findByPriceLevel("UNKNOWN", pageable)).thenReturn(Page.empty());

        assertThat(placeService.getPlacesByPriceLevel("UNKNOWN", pageable).isEmpty()).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPlaceById
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POIU-012: Geçerli ID ile tek bir place doğru dönüyor mu")
    void getPlaceById_validId() {
        when(placeRepository.findById("ChIJtest")).thenReturn(Optional.of(samplePlace));
        when(placeMapper.toResponse(samplePlace)).thenReturn(sampleResponse);

        PlaceResponse result = placeService.getPlaceById("ChIJtest");

        assertThat(result.getId()).isEqualTo("ChIJtest");
    }

    @Test
    @DisplayName("TC-POIU-013: Olmayan ID verildiğinde not-found exception/wrapper dönüyor mu")
    void getPlaceById_invalidId() {
        when(placeRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeService.getPlaceById("bad-id"))
                .isInstanceOf(PlaceNotFoundException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPlacesByIds
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-POIU-014: ID listesiyle toplu getirmede tüm geçerli ID’ler dönüyor mu")
    void getPlacesByIds_bulkRetrieval() {
        when(placeRepository.findAllByIdIn(List.of("ChIJtest")))
                .thenReturn(List.of(samplePlace));
        when(placeMapper.toResponse(samplePlace)).thenReturn(sampleResponse);

        List<PlaceResponse> result = placeService.getPlacesByIds(List.of("ChIJtest"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("ChIJtest");
    }

    @Test
    @DisplayName("TC-POIU-015: Kısmen geçerli ID listesinde sadece bulunan veya boş sonuç dönüyor mu")
    void getPlacesByIds_unknownIds_returnsEmpty() {
        when(placeRepository.findAllByIdIn(List.of("bad1", "bad2"))).thenReturn(List.of());

        List<PlaceResponse> result = placeService.getPlacesByIds(List.of("bad1", "bad2"));

        assertThat(result).isEmpty();
    }
}
