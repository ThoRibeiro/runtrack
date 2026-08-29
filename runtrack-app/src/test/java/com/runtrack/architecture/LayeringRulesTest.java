package com.runtrack.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Les règles <em>intra</em>-module, celles que Spring Modulith ne regarde pas : il vérifie
 * les frontières entre modules, pas la façon dont chacun est bâti à l'intérieur.
 *
 * <p>Depuis que chaque module est un artefact Maven séparé, ces règles portent aussi la
 * garantie que le classpath ne donne plus : rien n'empêche techniquement
 * {@code auth} d'importer {@code user.internal}, seul {@code ApplicationModules.verify()}
 * l'attrape.
 */
@AnalyzeClasses(packages = "com.runtrack", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringRulesTest {

    @ArchTest
    static final ArchRule domainStaysPlainJava = noClasses()
            .that().resideInAPackage("..internal.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "com.fasterxml.jackson..", "reactor..")
            .because("un domaine sans framework se teste sans contexte, sans mock et en millisecondes");

    @ArchTest
    static final ArchRule domainIgnoresOuterLayers = noClasses()
            .that().resideInAPackage("..internal.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..internal.application..", "..internal.infra..")
            .because("la dépendance va vers le centre, jamais l'inverse");

    @ArchTest
    static final ArchRule applicationIgnoresInfrastructure = noClasses()
            .that().resideInAPackage("..internal.application..")
            .should().dependOnClassesThat().resideInAPackage("..internal.infra..")
            .because("un port se déclare dans application et s'implémente dans infra");

    @ArchTest
    static final ArchRule domainNeverCallsAnotherModule = noClasses()
            .that().resideInAPackage("..internal.domain..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Api")
            .because("§5.2 : le domaine décide sur des faits qu'on lui passe, l'application les résout");

    /**
     * {@code com.runtrack.platform} en est exclu : c'est le noyau technique partagé, et sa
     * passerelle de cache parle forcément à Spring Data Redis. La règle protège les modules
     * métier d'une fuite de la persistance, elle n'a pas à s'appliquer à une couche dont
     * c'est précisément le rôle.
     */
    @ArchTest
    static final ArchRule persistenceStaysInInfrastructure = noClasses()
            .that().resideOutsideOfPackage("..internal.infra..")
            .and().resideOutsideOfPackage("com.runtrack.platform..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
            .because("le modèle JPA ne sort jamais de internal/infra");

    @ArchTest
    static final ArchRule cachingStaysInInfraCache = noClasses()
            .that().resideOutsideOfPackage("..internal.infra.cache..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.cache..")
            .because("§6 : le cache est un décorateur de port, pas une annotation dispersée");

    @ArchTest
    static final ArchRule noReactorAnywhere = noClasses()
            .should().dependOnClassesThat().resideInAPackage("reactor..")
            .because("§1 : modèle bloquant sur virtual threads. Reactor reste en dépendance "
                    + "transitive de Lettuce, c'est notre code qui ne doit pas y toucher");

    @ArchTest
    static final ArchRule noLombok = noClasses()
            .should().dependOnClassesThat().resideInAPackage("lombok..")
            .because("record et constructeurs explicites suffisent en Java 25");

    @ArchTest
    static final ArchRule injectionIsConstructorOnly = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("un champ injecté cache une dépendance et empêche de construire l'objet en test");

    @ArchTest
    static final ArchRule timeComesFromTheClock = noClasses()
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .because("sans horloge injectable, aucun test temporel n'est possible");

    @ArchTest
    static final ArchRule noBareVisibilityType = noClasses()
            .should().haveSimpleName("Visibility")
            .because("§5.1 : AccountVisibility et ActivityVisibility ont les mêmes valeurs et "
                    + "se composent — un type nommé Visibility rend le bug invisible");
}
