package cz.uhk.ordinace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Závislosti vedou jen api → service → repository; controller na databázi nesahá. */
@AnalyzeClasses(packages = "cz.uhk.ordinace", importOptions = ImportOption.DoNotIncludeTests.class)
class LayersTest {

    @ArchTest
    static final ArchRule apiDoesNotUseRepositories = noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule apiDoesNotUseJdbcOrJpa = noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "jakarta.persistence..",
                    "org.springframework.jdbc..", "org.springframework.data..");

    @ArchTest
    static final ArchRule servicesDoNotUseApi = noClasses().that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule repositoriesUseNoUpperLayer = noClasses().that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..api..");
}
