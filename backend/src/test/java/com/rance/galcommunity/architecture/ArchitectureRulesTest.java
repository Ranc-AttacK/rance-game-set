package com.rance.galcommunity.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.rance.galcommunity", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    // Cross-module boundaries (including direct repository access) are enforced by
    // ApplicationModules.verify() in ModuleStructureTest.
    @ArchTest
    static final ArchRule webLayerDoesNotDependOnPersistence =
            noClasses().that().resideInAPackage("..web..")
                    .should().dependOnClassesThat().resideInAPackage("..repo..");

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles =
            slices().matching("com.rance.galcommunity.(*)..").should().beFreeOfCycles();
}
