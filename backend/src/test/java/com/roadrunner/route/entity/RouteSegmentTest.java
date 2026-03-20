package com.roadrunner.route.entity;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RouteSegment}.
 */
@ExtendWith(MockitoExtension.class)
@Epic("Route Generation")
@Feature("Unit Tests")
@DisplayName("Unit Tests - RouteSegment")
class RouteSegmentTest {

    @Test
    @DisplayName("TC-RGU-004: Geometri bilgisi yokken hasGeometry false dönüyor mu")
    void shouldReturnFalse_whenNoGeometryIsStored() {
        // given
        RouteSegment seg = RouteSegment.builder()
                .fromIndex(0)
                .toIndex(1)
                .durationSec(300)
                .distanceM(1500.0)
                .build();

        // when
        boolean result = seg.hasGeometry();

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("TC-RGU-005: OSRM entegrasyonu tamamlanana kadar geometri her zaman false dönüyor mu (Disabled)")
    @Disabled("TC-RGU-005: geometry always false until OSRM integration is complete")
    void shouldReturnTrue_whenGeometryExists() {
        // This test is disabled because the current implementation always
        // returns false for hasGeometry(). OSRM integration is a future step.
    }

    @Test
    @DisplayName("TC-RGU-006: from ve to index değerleri doğru saklanıyor mu")
    void shouldStoreFromAndToIndicesCorrectly() {
        // given
        RouteSegment seg = RouteSegment.builder()
                .fromIndex(2)
                .toIndex(3)
                .durationSec(600)
                .distanceM(2000.0)
                .build();

        // when / then
        assertThat(seg.getFromIndex()).isEqualTo(2);
        assertThat(seg.getToIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-RGU-007: duration ve distance değerleri doğru saklanıyor mu")
    void shouldStoreDurationAndDistance() {
        // given
        RouteSegment seg = RouteSegment.builder()
                .fromIndex(0)
                .toIndex(1)
                .durationSec(300)
                .distanceM(1500.0)
                .build();

        // when / then
        assertThat(seg.getDurationSec()).isEqualTo(300);
        assertThat(seg.getDistanceM()).isEqualTo(1500.0);
    }
}
