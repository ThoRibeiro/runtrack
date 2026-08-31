package com.runtrack.notification.internal.infra.jpa;

import com.runtrack.notification.internal.infra.jpa.entity.NotificationPreferencesEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPreferencesRepository
        extends JpaRepository<NotificationPreferencesEntity, UUID> {
}
