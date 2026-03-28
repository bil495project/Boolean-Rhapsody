package com.roadrunner.user.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(columnDefinition = "TEXT")
    private String avatar;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TravelPersona> travelPersonas = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TravelPlan> travelPlans = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SavedRoute> savedRoutes = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Chat> chats = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    public Chat giveFeedback(String feedback) {
        String chatId = UUID.randomUUID().toString();
        Chat chat = Chat.builder()
                .id(chatId)
                .title("Feedback - " + System.currentTimeMillis())
                .user(this)
                .build();
        Message msg = Message.builder()
                .id(UUID.randomUUID().toString())
                .role("user")
                .content(feedback)
                .timestamp(System.currentTimeMillis())
                .chat(chat)
                .build();
        chat.getMessages().add(msg);
        if (this.chats == null) this.chats = new ArrayList<>();
        this.chats.add(chat);
        return chat;
    }

    public TravelPlan createTravelPlan(com.roadrunner.place.entity.Place[] places) {
        TravelPlan plan = TravelPlan.builder()
                .id(UUID.randomUUID().toString())
                .user(this)
                .selectedPlaceIds(new ArrayList<>())
                .build();
        if (places != null) {
            for (com.roadrunner.place.entity.Place p : places) {
                plan.getSelectedPlaceIds().add(p.getId());
            }
        }
        if (this.travelPlans == null) this.travelPlans = new ArrayList<>();
        this.travelPlans.add(plan);
        return plan;
    }
}
