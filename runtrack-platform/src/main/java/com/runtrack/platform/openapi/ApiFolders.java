package com.runtrack.platform.openapi;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Les dossiers du sélecteur Swagger, et la carte de l'API en un fichier.
 *
 * <p>Un contrôleur porte {@code @ApiFolders.Races} plutôt qu'un {@code @Tag} recopié : le nom
 * d'un dossier n'existe alors qu'à un seul endroit, et une faute de frappe ne peut plus en créer
 * un second, presque identique, à côté du premier. C'est le mode de panne d'un tag écrit à la
 * main, et rien ne l'attrape à la compilation.
 *
 * <p>Les dossiers suivent les préfixes d'URL, pas les modules : {@link Races} rassemble ce que
 * {@code course}, {@code engagement} et {@code sharing} servent tous les trois sous
 * {@code /race/v1}.
 */
public final class ApiFolders {

    public static final String AUTHENTICATION = "Authentification";
    public static final String ACCOUNTS = "Comptes";
    public static final String RACES = "Courses";
    public static final String NOTIFICATIONS = "Notifications";
    public static final String FEED = "Fil";

    private ApiFolders() {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Tag(name = AUTHENTICATION,
            description = "Créer un compte, ouvrir une session, la renouveler, la fermer.")
    public @interface Authentication {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Tag(name = ACCOUNTS,
            description = "Profil, abonnements, blocages, appareils, préférences et bilans d'un coureur.")
    public @interface Accounts {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Tag(name = RACES,
            description = "Le cycle de vie d'une course, sa trace, son direct et ce qui s'y accroche.")
    public @interface Races {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Tag(name = NOTIFICATIONS,
            description = "Ce que le coureur a manqué : lecture, compteur et flux en direct.")
    public @interface Notifications {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Tag(name = FEED, description = "Les courses des comptes suivis, les plus récentes d'abord.")
    public @interface Feed {
    }
}
