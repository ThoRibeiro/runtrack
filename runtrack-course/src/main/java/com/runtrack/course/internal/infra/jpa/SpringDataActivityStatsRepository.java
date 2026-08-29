package com.runtrack.course.internal.infra.jpa;

import com.runtrack.course.internal.infra.jpa.entity.ActivityStatsEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Interface de premier niveau : Spring Data ne détecte pas les interfaces imbriquées. */
interface SpringDataActivityStatsRepository extends JpaRepository<ActivityStatsEntity, UUID> {
}
