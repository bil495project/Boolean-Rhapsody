package com.roadrunner.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.roadrunner.user.entity.TravelPlan;

@Repository
public interface TravelPlanRepository extends JpaRepository<TravelPlan, String> {
    List<TravelPlan> findByUserId(String userId);
}
