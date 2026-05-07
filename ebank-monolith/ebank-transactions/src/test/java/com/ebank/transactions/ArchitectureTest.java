package com.ebank.transactions;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.ebank.transactions", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainHasNoSpringAnnotations =
        noClasses().that().resideInAPackage("..transactions.domain..")
            .should().beAnnotatedWith(Service.class)
            .orShould().beAnnotatedWith(Component.class)
            .orShould().beAnnotatedWith(Repository.class)
            .as("Domain classes must not depend on Spring annotations");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure =
        noClasses().that().resideInAPackage("..transactions.application..")
            .should().dependOnClassesThat().resideInAPackage("..transactions.infrastructure..")
            .as("Application layer must not depend on infrastructure");
}
