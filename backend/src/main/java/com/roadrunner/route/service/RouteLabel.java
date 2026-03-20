package com.roadrunner.route.service;

/**
 * Semantic route labels for POI classification.
 * Each Place is assigned exactly one label by {@link PlaceLabelService}.
 * The {@code key} field matches the corresponding {@code weight_*} key
 * in the user vector (without the {@code weight_} prefix).
 */
public enum RouteLabel {

    PARK_VE_SEYIR_NOKTALARI("parkVeSeyirNoktalari"),
    GECE_HAYATI("geceHayati"),
    RESTORAN_TOLERANSI("restoranToleransi"),
    LANDMARK("landmark"),
    DOGAL_ALANLAR("dogalAlanlar"),
    TARIHI_ALANLAR("tarihiAlanlar"),
    KAFE_TATLI("kafeTatli"),
    HOTEL("hotel"),
    UNKNOWN("unknown");

    private final String key;

    RouteLabel(String key) {
        this.key = key;
    }

    /** Returns the weight key used in the user vector (e.g. "parkVeSeyirNoktalari"). */
    public String key() {
        return key;
    }

    /** The 7 visit categories (excludes HOTEL and UNKNOWN). */
    public static final RouteLabel[] VISIT_CATEGORIES = {
            PARK_VE_SEYIR_NOKTALARI,
            GECE_HAYATI,
            RESTORAN_TOLERANSI,
            LANDMARK,
            DOGAL_ALANLAR,
            TARIHI_ALANLAR,
            KAFE_TATLI
    };

    /** True if this label represents a visitable interior POI category. */
    public boolean isVisitCategory() {
        return this != HOTEL && this != UNKNOWN;
    }
}
