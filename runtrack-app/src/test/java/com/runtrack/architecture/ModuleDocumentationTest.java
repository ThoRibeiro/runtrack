package com.runtrack.architecture;

import com.runtrack.RunTrackApplication;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Génère la documentation Modulith dans {@code target/spring-modulith-docs} : diagrammes
 * de composants et canevas de module. Générée par le build, jamais écrite à la main —
 * une doc d'architecture maintenue à la main est fausse au bout de trois semaines.
 */
class ModuleDocumentationTest {

    @Test
    void writesDocumentation() throws IOException {
        new Documenter(ApplicationModules.of(RunTrackApplication.class))
                .writeDocumentation()
                .writeIndividualModulesAsPlantUml();
    }
}
