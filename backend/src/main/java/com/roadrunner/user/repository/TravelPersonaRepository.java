package com.roadrunner.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.roadrunner.user.entity.TravelPersona;

@Repository
public interface TravelPersonaRepository extends JpaRepository<TravelPersona, String> {
    List<TravelPersona> findByUserId(String userId);
}
