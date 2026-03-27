package com.roadrunner.route.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.request.RoutePreferencesRequest;

@Service
public class RoutePreferenceVectorMapper {

    public Map<String, String> buildGenerationUserVector(GenerateRoutesRequest req) {
        Map<String, String> merged = new HashMap<>();
        if (req.getPreferences() != null) {
            merged.putAll(mapPreferences(req.getPreferences()));
        }
        if (req.getUserVector() != null) {
            merged.putAll(req.getUserVector());
        }
        if (merged.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Route generation requires either preferences or userVector");
        }
        if (req.getCenterLat() != null) {
            merged.put("centerLat", String.valueOf(req.getCenterLat()));
        }
        if (req.getCenterLng() != null) {
            merged.put("centerLng", String.valueOf(req.getCenterLng()));
        }
        return merged;
    }

    Map<String, String> mapPreferences(RoutePreferencesRequest preferences) {
        double tempo = clamp01(preferences.getTempo(), 0.5);
        double social = clamp01(preferences.getSocialPreference(), 0.5);
        double nature = clamp01(preferences.getNaturePreference(), 0.5);
        double history = clamp01(preferences.getHistoryPreference(), 0.5);
        double food = clamp01(preferences.getFoodImportance(), 0.5);
        double alcohol = clamp01(preferences.getAlcoholPreference(), 0.0);
        double transport = clamp01(preferences.getTransportStyle(), 0.33);
        double budget = clamp01(preferences.getBudgetLevel(), 0.5);
        double tripLength = clamp01(preferences.getTripLength(), 0.5);
        double crowd = clamp01(preferences.getCrowdPreference(), 0.5);

        double parkVeSeyirNoktalari = clamp01(
                0.10 + (nature * 0.52) + ((1 - crowd) * 0.12) + (tripLength * 0.08));
        double geceHayati = clamp01(
                0.04 + (social * 0.38) + (alcohol * 0.42) + (crowd * 0.10) + (tempo * 0.06));
        double restoranToleransi = clamp01(
                0.12 + (food * 0.66) + (tripLength * 0.08) + (social * 0.06));
        double landmark = clamp01(
                0.18 + (history * 0.24) + (crowd * 0.10) + (tempo * 0.05) + ((1 - nature) * 0.04));
        double dogalAlanlar = clamp01(
                0.08 + (nature * 0.70) + ((1 - crowd) * 0.08));
        double tarihiAlanlar = clamp01(
                0.10 + (history * 0.74) + ((1 - tempo) * 0.06));
        double kafeTatli = clamp01(
                0.08 + (food * 0.38) + (social * 0.14) + ((1 - tempo) * 0.14));
        double toplamPoiYogunlugu = clamp01(
                0.18 + (tempo * 0.42) + (tripLength * 0.32) + (social * 0.06));
        double sparsity = clamp01(
                0.12 + (transport * 0.42) + (tripLength * 0.16) + (nature * 0.08) + ((1 - tempo) * 0.10));
        double hotelCenterBias = clamp01(
                0.88 - (transport * 0.72) + ((1 - crowd) * 0.05) + ((1 - tempo) * 0.03));

        Map<String, String> weights = new HashMap<>();
        weights.put("weight_parkVeSeyirNoktalari", format(parkVeSeyirNoktalari));
        weights.put("weight_geceHayati", format(geceHayati));
        weights.put("weight_restoranToleransi", format(restoranToleransi));
        weights.put("weight_landmark", format(landmark));
        weights.put("weight_dogalAlanlar", format(dogalAlanlar));
        weights.put("weight_tarihiAlanlar", format(tarihiAlanlar));
        weights.put("weight_kafeTatli", format(kafeTatli));
        weights.put("weight_toplamPoiYogunlugu", format(toplamPoiYogunlugu));
        weights.put("weight_sparsity", format(sparsity));
        weights.put("weight_hotelCenterBias", format(hotelCenterBias));
        weights.put("weight_butceSeviyesi", format(budget));
        return weights;
    }

    private static double clamp01(Double value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", clamp01(value));
    }
}
