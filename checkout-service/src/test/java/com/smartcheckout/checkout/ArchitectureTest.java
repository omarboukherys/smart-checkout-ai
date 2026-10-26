package com.smartcheckout.checkout;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hexagonal architecture rules, checked on every build: a violation fails the build
 * instead of relying on code review.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.smartcheckout.checkout");

    @Test
    void domain_does_not_depend_on_any_framework() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "org.hibernate..",
                        "tools.jackson..", "com.fasterxml..", "org.apache.kafka..")
                .check(CLASSES);
    }

    @Test
    void domain_does_not_depend_on_outer_layers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void application_does_not_depend_on_infrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(CLASSES);
    }

    @Test
    void web_layer_goes_through_the_application_layer() {
        noClasses().that().resideInAPackage("..infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence..", "..infrastructure.messaging..")
                .check(CLASSES);
    }
}
