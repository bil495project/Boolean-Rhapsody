package com.roadrunner.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;
import java.util.List;
import com.roadrunner.user.entity.converter.JsonListConverter;

@Entity
@Table(name = "travel_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelPlan {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "selected_place_ids", columnDefinition = "TEXT")
    private List<String> selectedPlaceIds;

    @Column(name = "created_at")
    private long createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = System.currentTimeMillis();
    }

    @jakarta.persistence.Transient
    @org.springframework.beans.factory.annotation.Value("${places.service.base-url:http://localhost:8081}")
    private String placesServiceBaseUrl;

    @jakarta.persistence.Transient
    @lombok.Builder.Default
    private org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    public List<com.roadrunner.place.entity.Place> fetchPlaceDetails() {
        if (selectedPlaceIds == null || selectedPlaceIds.isEmpty()) return new java.util.ArrayList<>();
        String url = (placesServiceBaseUrl != null ? placesServiceBaseUrl : "http://localhost:8081") + "/api/places/batch";
        String idsParam = String.join(",", selectedPlaceIds);
        String fullUrl = url + "?ids=" + idsParam;
        try {
            com.roadrunner.place.entity.Place[] response = restTemplate.getForObject(fullUrl, com.roadrunner.place.entity.Place[].class);
            return response != null ? java.util.Arrays.asList(response) : new java.util.ArrayList<>();
        } catch (Exception e) {
            throw new RuntimeException("Places Service is unreachable", e);
        }
    }
}
