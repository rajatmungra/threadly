package com.threadly.notification.repository;

import com.threadly.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsBySourceEventId(UUID sourceEventId);

    Optional<Notification> findBySourceEventId(UUID sourceEventId);
}
