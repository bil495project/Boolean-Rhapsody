package com.roadrunner.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.roadrunner.user.entity.SavedRoute;

@Repository
public interface SavedRouteRepository extends JpaRepository<SavedRoute, String> {

    List<SavedRoute> findByUserIdOrderByUpdatedAtDesc(String userId);
}
