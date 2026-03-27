package com.roadrunner.user.entity;

import java.util.List;
import java.util.UUID;

import com.roadrunner.user.entity.converter.JsonListConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "saved_routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedRoute {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Convert(converter = JsonListConverter.class)
    @Column(name = "ordered_place_ids", columnDefinition = "TEXT")
    private List<String> orderedPlaceIds;

    @Column(name = "route_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String routeSnapshotJson;

    @Column(name = "generate_request_json", nullable = false, columnDefinition = "TEXT")
    private String generateRequestJson;

    @Column(name = "travel_mode")
    private String travelMode;

    @Column(name = "total_duration_sec")
    private int totalDurationSec;

    @Column(name = "total_distance_m")
    private double totalDistanceM;

    @Column(nullable = false)
    private boolean feasible;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @PrePersist
    public void prePersist() {
        long now = System.currentTimeMillis();
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = System.currentTimeMillis();
    }
}
