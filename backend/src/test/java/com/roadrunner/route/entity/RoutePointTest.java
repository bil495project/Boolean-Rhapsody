package com.roadrunner.route.entity;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;


import com.roadrunner.place.entity.Place;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RoutePoint}.
 */
@ExtendWith(MockitoExtension.class)
@Epic("Route Generation")
@Feature("Unit Tests")
@DisplayName("Unit Tests - RoutePoint")
class RoutePointTest {

    private Place buildPlace(String id, String name) {
        return Place.builder()
                .id(id)
                .name(name)
                .latitude(39.9208)
                .longitude(32.8541)
                .types("restaurant")
                .ratingScore(4.0)
                .ratingCount(100)
                .businessStatus("OPERATIONAL")
                .build();
    }

    @Test
    @DisplayName("TC-RGU-001: assignPOI metodu mevcut POI'yi yenisiyle değiştiriyor mu")
    void shouldAssignPOI_whenAssignPOIIsCalled() {
        // given
        Place initial = buildPlace("p1", "Old Place");
        Place replacement = buildPlace("p2", "New Place");
        RoutePoint rp = RoutePoint.builder()
                .index(0)
                .poi(initial)
                .plannedVisitMin(45)
                .build();

        // when
        rp.assignPOI(replacement);

        // then
        assertThat(rp.getPoi()).isEqualTo(replacement);
        assertThat(rp.getPoi().getId()).isEqualTo("p2");
    }

    @Test
    @DisplayName("TC-RGU-002: POI atandığında index değeri korunuyor mu")
    void shouldPreserveIndex_whenPoiIsAssigned() {
        // given
        Place initial = buildPlace("p1", "Old Place");
        Place replacement = buildPlace("p2", "New Place");
        RoutePoint rp = RoutePoint.builder()
                .index(3)
                .poi(initial)
                .plannedVisitMin(30)
                .build();

        // when
        rp.assignPOI(replacement);

        // then
        assertThat(rp.getIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-RGU-003: POI atandığında planlanan ziyaret süresi korunuyor mu")
    void shouldPreservePlannedVisitMin_whenPoiIsAssigned() {
        // given
        Place initial = buildPlace("p1", "Old Place");
        Place replacement = buildPlace("p2", "New Place");
        RoutePoint rp = RoutePoint.builder()
                .index(0)
                .poi(initial)
                .plannedVisitMin(90)
                .build();

        // when
        rp.assignPOI(replacement);

        // then
        assertThat(rp.getPlannedVisitMin()).isEqualTo(90);
    }
}
