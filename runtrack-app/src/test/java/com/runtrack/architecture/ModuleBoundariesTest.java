package com.runtrack.architecture;

import com.runtrack.RunTrackApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Les frontières inter-modules. Maven les garantit déjà à la compilation — un module ne
 * voit que les {@code -api} qu'il déclare — mais ce test couvre ce que Maven ignore :
 * les cycles, les interfaces nommées, et le respect des {@code allowedDependencies}.
 */
class ModuleBoundariesTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(RunTrackApplication.class);

    @Test
    void moduleStructureIsValid() {
        MODULES.verify();
    }

    @Test
    void moduleStructureIsPrintedForReview() {
        MODULES.forEach(System.out::println);
    }
}
