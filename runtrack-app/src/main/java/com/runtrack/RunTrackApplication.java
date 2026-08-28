package com.runtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Point d'entrée de l'application. La carte des modules et les décisions d'architecture
 * sont dans {@code docs/decisions-lot-1.md}.
 */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
public class RunTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunTrackApplication.class, args);
    }
}
