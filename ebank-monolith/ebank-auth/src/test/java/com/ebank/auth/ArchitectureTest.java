package com.ebank.auth;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.ebank.auth", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainHasNoSpringAnnotations =
        noClasses().that().resideInAPackage("..auth.domain..")
            .should().beAnnotatedWith(Service.class)
            .orShould().beAnnotatedWith(Component.class)
            .orShould().beAnnotatedWith(Repository.class)
            .as("Auth domain classes must not use Spring stereotypes");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..auth.application..")
            .should().dependOnClassesThat().resideInAPackage("..auth.infrastructure..")
            .as("Auth application layer must not depend on infrastructure");

    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..auth.domain..")
            .should().dependOnClassesThat().resideInAPackage("..auth.infrastructure..")
            .as("Auth domain must not depend on infrastructure");
}
