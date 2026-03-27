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
import java.util.Map;

import com.roadrunner.user.entity.converter.JsonMapConverter;

@Entity
@Table(name = "travel_personas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelPersona {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name")
    private String name;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "tempo")
    private Double tempo;

    @Column(name = "social_preference")
    private Double socialPreference;

    @Column(name = "nature_preference")
    private Double naturePreference;

    @Column(name = "history_preference")
    private Double historyPreference;

    @Column(name = "food_importance")
    private Double foodImportance;

    @Column(name = "alcohol_preference")
    private Double alcoholPreference;

    @Column(name = "transport_style")
    private Double transportStyle;

    @Column(name = "budget_level")
    private Double budgetLevel;

    @Column(name = "trip_length")
    private Double tripLength;

    @Column(name = "crowd_preference")
    private Double crowdPreference;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "user_vector", columnDefinition = "TEXT")
    private Map<String, String> userVector;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.isDefault == null) {
            this.isDefault = Boolean.FALSE;
        }
    }
}
