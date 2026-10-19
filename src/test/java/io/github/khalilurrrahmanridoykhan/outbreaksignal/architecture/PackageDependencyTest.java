package io.github.khalilurrrahmanridoykhan.outbreaksignal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Checks the one-directional dependency the plan calls for: a sync job may
 * trigger detection (see {@code Dhis2SyncScheduler}), but the detection
 * algorithms themselves stay independent of how the data they score arrived,
 * so they can be tested, and eventually reused, without any of that.
 */
class PackageDependencyTest {

    private static final String BASE_PACKAGE = "io.github.khalilurrrahmanridoykhan.outbreaksignal";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void detectionMustNotDependOnDatasource() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + ".detection..")
                .should().dependOnClassesThat().resideInAPackage(BASE_PACKAGE + ".datasource..");

        rule.check(CLASSES);
    }
}
