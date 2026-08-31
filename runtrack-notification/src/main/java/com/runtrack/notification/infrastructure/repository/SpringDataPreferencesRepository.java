package com.runtrack.notification.infrastructure.repository;

import com.runtrack.notification.infrastructure.repository.entity.NotificationPreferencesEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPreferencesRepository
        extends JpaRepository<NotificationPreferencesEntity, UUID> {
}
