package com.runtrack.architecture;

import com.runtrack.RunTrackApplication;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Génère la documentation Modulith : diagrammes de composants et canevas de module.
 *
 * <p>Générée par le build, jamais écrite à la main — une doc d'architecture maintenue à la main
 * est fausse au bout de trois semaines, et pire que rien parce qu'on la croit.
 *
 * <p>Écrite dans <b>{@code docs/modules/}</b> et non dans {@code target/} : ce qui ne survit pas à
 * un {@code mvn clean} n'est lisible que par celui qui vient de lancer le build, alors que
 * l'intérêt de ces diagrammes est de se lire depuis le dépôt.
 */
class ModuleDocumentationTest {

    /** Relatif à {@code runtrack-app/}, d'où le test s'exécute. */
    private static final String OUTPUT = "../docs/modules";

    @Test
    void writesDocumentation() throws IOException {
        new Documenter(ApplicationModules.of(RunTrackApplication.class),
                        Documenter.Options.defaults().withOutputFolder(OUTPUT))
                .writeDocumentation()
                .writeIndividualModulesAsPlantUml();
    }
}
