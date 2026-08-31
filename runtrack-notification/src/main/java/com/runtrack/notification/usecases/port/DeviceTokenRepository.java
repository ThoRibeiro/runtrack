package com.runtrack.notification.usecases.port;

import com.runtrack.notification.usecases.model.push.DeviceToken;
import com.runtrack.shared.id.UserId;
import java.util.Collection;
import java.util.List;

/** Les appareils déclarés par les comptes. */
public interface DeviceTokenRepository {

    /**
     * Enregistre l'appareil, ou le rattache à ce compte s'il était connu d'un autre.
     *
     * <p>Le second cas est le téléphone qu'on prête ou qu'on revend : le jeton est le même, le
     * propriétaire a changé. Le refuser laisserait des push partir vers l'ancien compte.
     */
    void register(DeviceToken device);

    /** @return {@code true} si l'appareil appartenait bien à ce compte et vient d'être retiré */
    boolean forget(UserId ownerId, String token);

    List<DeviceToken> of(UserId ownerId);

    List<DeviceToken> ofAll(Collection<UserId> ownerIds);

    /**
     * Efface les jetons que le service de push a déclarés invalides.
     *
     * <p>Sans cette purge, chaque envoi retente des appareils désinstallés depuis des mois : la
     * facture et la latence montent pour des destinataires qui n'existent plus.
     */
    int forgetAll(Collection<String> tokens);
}
