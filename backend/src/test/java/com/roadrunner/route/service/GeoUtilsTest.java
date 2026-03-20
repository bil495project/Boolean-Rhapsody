package com.roadrunner.route.service;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;


import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link GeoUtils}.
 * All methods are static — no mocking needed.
 */
@ExtendWith(MockitoExtension.class)
@Epic("Route Generation")
@Feature("Unit Tests")
@DisplayName("Unit Tests - GeoUtils")
class GeoUtilsTest {

    private static final double ANKARA_LAT = 39.9208;
    private static final double ANKARA_LNG = 32.8541;
    private static final double ISTANBUL_LAT = 41.0082;
    private static final double ISTANBUL_LNG = 28.9784;

    // ================================================================
    // haversineKm()
    // ================================================================


    @DisplayName("TC-RGU-008: İki nokta aynı olduğunda haversineKm sıfır dönüyor mu")
@Test
    void shouldReturnZero_whenBothPointsAreIdentical() {
        // given
        double lat = ANKARA_LAT;
        double lng = ANKARA_LNG;

        // when
        double result = GeoUtils.haversineKm(lat, lng, lat, lng);

        // then
        assertThat(result).isEqualTo(0.0);
    }

    @DisplayName("TC-RGU-009: Ankara - İstanbul arası mesafe hesaplandığında doğru sonuç dönüyor mu")
@Test
    void shouldReturnCorrectDistance_whenCalculatingAnkaraToIstanbul() {
        // given / when
        double result = GeoUtils.haversineKm(ANKARA_LAT, ANKARA_LNG, ISTANBUL_LAT, ISTANBUL_LNG);

        // then — Python reference returns ~351.8 km
        assertThat(result).isBetween(340.0, 360.0);
    }

    @DisplayName("TC-RGU-010: Noktalar yer değiştirdiğinde simetrik sonuç dönüyor mu")
@Test
    void shouldReturnSymmetricResult_whenPointsAreSwapped() {
        // given / when
        double ab = GeoUtils.haversineKm(ANKARA_LAT, ANKARA_LNG, ISTANBUL_LAT, ISTANBUL_LNG);
        double ba = GeoUtils.haversineKm(ISTANBUL_LAT, ISTANBUL_LNG, ANKARA_LAT, ANKARA_LNG);

        // then
        assertThat(ab).isCloseTo(ba, within(0.0001));
    }

    @DisplayName("TC-RGU-011: Noktalar farklı olduğunda pozitif değer dönüyor mu")
@Test
    void shouldReturnPositiveValue_whenPointsAreDifferent() {
        // given / when
        double result = GeoUtils.haversineKm(ANKARA_LAT, ANKARA_LNG, ISTANBUL_LAT, ISTANBUL_LNG);

        // then
        assertThat(result).isGreaterThan(0.0);
    }

    @DisplayName("TC-RGU-012: Noktalar zıt kutuplarda (antipodal) olduğunda doğru hesaplanıyor mu")
@Test
    void shouldHandleAntipodal_whenPointsAreOpposites() {
        // given — (0,0) and (0,180) are antipodal
        // when
        double result = GeoUtils.haversineKm(0.0, 0.0, 0.0, 180.0);

        // then — half circumference ≈ 20015 km
        assertThat(result).isCloseTo(20015.0, within(50.0));
    }

    // ================================================================
    // kmToMeters()
    // ================================================================


    @DisplayName("TC-RGU-013: Girdi 1 km olduğunda kmToMeters 1000 dönüyor mu")
@Test
    void shouldReturnOneThousand_whenInputIsOne() {
        // given / when / then
        assertThat(GeoUtils.kmToMeters(1.0)).isEqualTo(1000.0);
    }

    @DisplayName("TC-RGU-014: Girdi 0 olduğunda kmToMeters 0 dönüyor mu")
@Test
    void shouldReturnZero_whenInputIsZero() {
        // given / when / then
        assertThat(GeoUtils.kmToMeters(0.0)).isEqualTo(0.0);
    }

    @DisplayName("TC-RGU-015: Gelişigüzel girdiler için kmToMeters doğrusal ölçekleme yapıyor mu")
@Test
    void shouldScaleLinearlyForArbitraryInput() {
        // given / when / then
        assertThat(GeoUtils.kmToMeters(5.5)).isEqualTo(5500.0);
    }

    // ================================================================
    // travelSeconds()
    // ================================================================


    @DisplayName("TC-RGU-016: 1 km sürüş için travelSeconds doğru süre dönüyor mu")
@Test
    void shouldReturnCorrectDuration_whenDrivingOneKm() {
        // given / when
        int result = GeoUtils.travelSeconds(1.0, "driving");

        // then — 1.0/25.0 * 3600 = 144
        assertThat(result).isEqualTo((int) Math.round(1.0 / 25.0 * 3600));
    }

    @DisplayName("TC-RGU-017: 1 km yürüyüş için travelSeconds doğru süre dönüyor mu")
@Test
    void shouldReturnCorrectDuration_whenWalkingOneKm() {
        // given / when
        int result = GeoUtils.travelSeconds(1.0, "walking");

        // then — 1.0/4.8 * 3600 = 750
        assertThat(result).isEqualTo((int) Math.round(1.0 / 4.8 * 3600));
    }

    @DisplayName("TC-RGU-018: 1 km bisiklet için travelSeconds doğru süre dönüyor mu")
@Test
    void shouldReturnCorrectDuration_whenCyclingOneKm() {
        // given / when
        int result = GeoUtils.travelSeconds(1.0, "cycling");

        // then — 1.0/14.0 * 3600 = 257
        assertThat(result).isEqualTo((int) Math.round(1.0 / 14.0 * 3600));
    }

    @DisplayName("TC-RGU-019: Mode null olduğunda varsayılan olarak sürüş (driving) süresi hesaplanıyor mu")
@Test
    void shouldDefaultToDriving_whenModeIsNull() {
        // given / when
        int result = GeoUtils.travelSeconds(1.0, null);

        // then
        assertThat(result).isEqualTo(GeoUtils.travelSeconds(1.0, "driving"));
    }

    @DisplayName("TC-RGU-020: Mode bilinmeyen bir değer olduğunda varsayılan olarak sürüş (driving) süresi hesaplanıyor mu")
@Test
    void shouldDefaultToDriving_whenModeIsUnrecognized() {
        // given / when
        int result = GeoUtils.travelSeconds(1.0, "rocket");

        // then
        assertThat(result).isEqualTo(GeoUtils.travelSeconds(1.0, "driving"));
    }

    @DisplayName("TC-RGU-021: Mesafe sıfır olduğunda seyahat süresi 0 dönüyor mu")
@Test
    void shouldReturnZero_whenDistanceIsZero() {
        // given / when
        int result = GeoUtils.travelSeconds(0.0, "driving");

        // then
        assertThat(result).isEqualTo(0);
    }

    // ================================================================
    // safeFloat()
    // ================================================================


    @DisplayName("TC-RGU-022: Geçerli bir string safeFloat ile doğru parse ediliyor mu")
@Test
    void shouldParseValidString() {
        // given / when / then
        assertThat(GeoUtils.safeFloat("3.14", 0.0)).isEqualTo(3.14);
    }

    @DisplayName("TC-RGU-023: Girdi null olduğunda safeFloat varsayılan değeri dönüyor mu")
@Test
    void shouldReturnDefault_whenInputIsNull() {
        // given / when / then
        assertThat(GeoUtils.safeFloat(null, 99.0)).isEqualTo(99.0);
    }

    @DisplayName("TC-RGU-024: Girdi boş olduğunda safeFloat varsayılan değeri dönüyor mu")
@Test
    void shouldReturnDefault_whenInputIsBlank() {
        // given / when / then
        assertThat(GeoUtils.safeFloat("  ", 0.0)).isEqualTo(0.0);
    }

    @DisplayName("TC-RGU-025: Girdi sayısal olmadığında safeFloat varsayılan değeri dönüyor mu")
@Test
    void shouldReturnDefault_whenInputIsNonNumeric() {
        // given / when / then
        assertThat(GeoUtils.safeFloat("abc", 5.0)).isEqualTo(5.0);
    }

    @DisplayName("TC-RGU-026: safeFloat negatif sayıyı parse edebiliyor mu")
@Test
    void shouldParseNegativeNumber() {
        // given / when / then
        assertThat(GeoUtils.safeFloat("-1.5", 0.0)).isEqualTo(-1.5);
    }

    // ================================================================
    // safeInt()
    // ================================================================


    @DisplayName("TC-RGU-027: Geçerli bir string safeInt ile parse ediliyor mu")
@Test
    void shouldParseValidIntegerString() {
        // given / when / then
        assertThat(GeoUtils.safeInt("7", 0)).isEqualTo(7);
    }

    @DisplayName("TC-RGU-028: Ondalıklı string verildiğinde safeInt sadece tam sayıyı alıyor mu")
@Test
    void shouldTruncateDecimalString() {
        // given / when / then
        assertThat(GeoUtils.safeInt("3.9", 0)).isEqualTo(3);
    }

    @DisplayName("TC-RGU-023: Girdi null olduğunda safeFloat varsayılan değeri dönüyor mu")
@Test
    void shouldReturnDefault_whenInputIsNull_2() {
        // given / when / then
        assertThat(GeoUtils.safeInt(null, 10)).isEqualTo(10);
    }

    // ================================================================
    // parseTypesList()
    // ================================================================


    @DisplayName("TC-RGU-029: Girdi null olduğunda parseTypesList boş liste dönüyor mu")
@Test
    void shouldReturnEmptyList_whenInputIsNull() {
        // given / when
        List<String> result = GeoUtils.parseTypesList(null);

        // then
        assertThat(result).isEmpty();
    }

    @DisplayName("TC-RGU-030: Girdi boş olduğunda parseTypesList boş liste dönüyor mu")
@Test
    void shouldReturnEmptyList_whenInputIsBlank() {
        // given / when
        List<String> result = GeoUtils.parseTypesList("   ");

        // then
        assertThat(result).isEmpty();
    }

    @DisplayName("TC-RGU-031: Girdi JSON listesi olduğunda parseTypesList doğru parse ediyor mu")
@Test
    void shouldParseJsonArray_whenInputIsJsonList() {
        // given / when
        List<String> result = GeoUtils.parseTypesList("[\"museum\",\"restaurant\"]");

        // then
        assertThat(result).hasSize(2).containsExactly("museum", "restaurant");
    }

    @DisplayName("TC-RGU-032: Girdi pipe (|) ile ayrıldığında parseTypesList doğru parse ediyor mu")
@Test
    void shouldParsePipeSeparated_whenInputUsesPipes() {
        // given / when
        List<String> result = GeoUtils.parseTypesList("museum|park|cafe");

        // then
        assertThat(result).hasSize(3).containsExactly("museum", "park", "cafe");
    }

    @DisplayName("TC-RGU-033: Girdi virgül (,) ile ayrıldığında parseTypesList doğru parse ediyor mu")
@Test
    void shouldParseCommaSeparated_whenInputUsesCommas() {
        // given / when
        List<String> result = GeoUtils.parseTypesList("museum,park");

        // then
        assertThat(result).hasSize(2).containsExactly("museum", "park");
    }

    @DisplayName("TC-RGU-034: Girdi ayıraç içermediğinde parseTypesList tek eleman dönüyor mu")
@Test
    void shouldReturnSingleElement_whenInputHasNoDelimiter() {
        // given / when
        List<String> result = GeoUtils.parseTypesList("museum");

        // then
        assertThat(result).hasSize(1).containsExactly("museum");
    }

    @DisplayName("TC-RGU-035: parseTypesList ayrıştırma sırasında boşlukları temizliyor mu")
@Test
    void shouldTrimWhitespace_whenParsingTypes() {
        // given / when
        List<String> result = GeoUtils.parseTypesList("  museum , park  ");

        // then
        assertThat(result).containsExactly("museum", "park");
    }
}
