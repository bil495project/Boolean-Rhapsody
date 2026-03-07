package com.roadrunner.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.roadrunner.user.entity.Chat;

@Repository
public interface ChatRepository extends JpaRepository<Chat, String> {
    List<Chat> findByUserIdOrderByUpdatedAtDesc(String userId);
}
