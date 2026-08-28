package com.runtrack.auth.internal.infra.jpa;

import com.runtrack.auth.internal.infra.jpa.entity.CredentialsEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Interface Spring Data, confinée à {@code infra} : les cas d'usage ne voient que le port. */
interface SpringDataCredentialsRepository extends JpaRepository<CredentialsEntity, UUID> {
}
