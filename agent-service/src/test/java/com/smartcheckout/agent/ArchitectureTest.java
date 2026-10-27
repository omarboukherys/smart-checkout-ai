package com.smartcheckout.agent;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.smartcheckout.agent");

    @Test
    void domain_does_not_depend_on_any_framework() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "org.bson..", "com.mongodb..",
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
    void application_does_not_depend_on_infrastructure_nor_on_the_ai_framework() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "org.springframework.ai..")
                .check(CLASSES);
    }

    /** Only the ai adapter talks to the model: switching from Gemini to another provider changes one package. */
    @Test
    void only_the_ai_adapter_uses_spring_ai_and_the_gemini_sdk() {
        noClasses().that().resideOutsideOfPackage("..infrastructure.ai..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..", "com.google.genai..")
                .check(CLASSES);
    }

    @Test
    void web_layer_goes_through_the_application_layer() {
        noClasses().that().resideInAPackage("..infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.persistence..", "..infrastructure.messaging..",
                        "..infrastructure.client..", "..infrastructure.ai..")
                .check(CLASSES);
    }
}
