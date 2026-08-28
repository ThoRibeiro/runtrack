package com.runtrack.user;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Point d'entrée unique du module {@code user} pour les autres modules. */
public interface UserApi {

    /**
     * Crée le profil et publie {@code UserRegistered}.
     *
     * <p>Appelé par {@code auth} à l'inscription. C'est bien {@code user} qui publie
     * l'événement : si {@code auth} le publiait et que {@code user} l'écoutait, les deux
     * modules formeraient un cycle.
     */
    UserId register(NewUser newUser);

    /** Fait passer le compte de {@code PENDING_VERIFICATION} à {@code ACTIVE}. */
    void confirmEmail(UserId id);

    /** Résout une adresse e-mail en identifiant, pour la connexion et la réinitialisation. */
    Optional<UserId> idOfEmail(String email);

    boolean exists(UserId id);

    Optional<UserSummary> summary(UserId id);

    /**
     * Les résumés de plusieurs profils en un appel.
     *
     * <p>Existe pour que le fil d'actualité et les listes d'abonnés n'aient pas à boucler :
     * un {@code summary} par ligne affichée est exactement le N+1 que le §10 interdit.
     * Les identifiants inconnus sont simplement absents de la réponse.
     */
    Map<UserId, UserSummary> summaries(Collection<UserId> ids);

    /** La portée du compte, à composer avec celle de la course (§5.1). */
    Optional<AudienceScope> accountScope(UserId id);

    /** Absente tant que l'utilisateur n'a pas renseigné sa masse. */
    Optional<RunnerMass> massOf(UserId id);
}
