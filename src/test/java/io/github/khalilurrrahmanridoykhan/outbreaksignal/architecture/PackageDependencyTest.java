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

    /**
     * {@code notification} reads alert data through {@code AlertEventPayload}
     * (built by {@code detection} itself, from its own already-resolved
     * {@code AlertSummary} — see DECISIONS.md), never through
     * {@code observation}'s entities directly or through however the data
     * that fed detection arrived.
     */
    @Test
    void notificationMustNotDependOnDatasourceOrObservation() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + ".notification..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".datasource..", BASE_PACKAGE + ".observation..");

        rule.check(CLASSES);
    }

    /**
     * The plan's own architecture section states this one by name: FHIR
     * output is a read-only view over alert data, never a second channel
     * that notifications also feed through. {@code fhir} reads alert data
     * through {@code AlertService}/{@code AlertSummary} — the same
     * already-resolved DTO {@code notification} reuses — so it has no
     * reason to depend on {@code datasource} or {@code observation} either.
     */
    @Test
    void fhirMustNotDependOnNotificationDatasourceOrObservation() {
        ArchRule rule = noClasses().that().resideInAPackage(BASE_PACKAGE + ".fhir..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".notification..", BASE_PACKAGE + ".datasource..", BASE_PACKAGE + ".observation..");

        rule.check(CLASSES);
    }
}
