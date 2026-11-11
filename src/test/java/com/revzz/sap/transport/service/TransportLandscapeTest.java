package com.revzz.sap.transport.service;

import com.revzz.sap.transport.domain.ObjectConflict;
import com.revzz.sap.transport.domain.ReleaseEntry;
import com.revzz.sap.transport.domain.Severity;
import com.revzz.sap.transport.domain.TransportAssessment;
import com.revzz.sap.transport.domain.TransportFinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seeded transport landscape.
 *
 * <p>These tests check the story the demo data has to tell. If the seeding
 * changes and the story changes with it, these tests are the ones that say
 * so first.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TransportLandscapeTest {

    @Autowired
    private TransportAnalysisService analysisService;

    @Autowired
    private ReleaseService releaseService;

    @Test
    @DisplayName("the landscape holds nine requests")
    void nineRequests() {
        assertEquals(9, analysisService.requests().size());
        assertEquals(9, analysisService.assessments().size());
    }

    @Test
    @DisplayName("K900001 is critical - it shares ZCL_PAYMENT with K900002")
    void k900001IsCritical() {
        TransportAssessment assessment = analysisService.assess("K900001");

        assertNotNull(assessment);
        assertEquals(Severity.CRITICAL, assessment.getSeverity());
        assertTrue(assessment.hasFinding("OBJECT_CONFLICT"));
        assertEquals(1, assessment.getConflicts().size());
        assertEquals("ZCL_PAYMENT", assessment.getConflicts().get(0).getObjectName());
        assertEquals(45, assessment.getScore());
    }

    @Test
    @DisplayName("K900002 is critical and was never imported anywhere")
    void k900002IsCritical() {
        TransportAssessment assessment = analysisService.assess("K900002");

        assertNotNull(assessment);
        assertEquals(Severity.CRITICAL, assessment.getSeverity());
        assertTrue(assessment.hasFinding("OBJECT_CONFLICT"));
        assertTrue(assessment.hasFinding("NO_IMPORT_EVIDENCE"));
        assertEquals(35, assessment.getScore());
    }

    @Test
    @DisplayName("K900003 is blocked - K900001 reached QA but not PRD")
    void k900003IsBlocked() {
        TransportAssessment assessment = analysisService.assess("K900003");

        assertNotNull(assessment);
        assertEquals(Severity.CRITICAL, assessment.getSeverity());
        assertTrue(assessment.hasFinding("BLOCKED_DEPENDENCY"));
        assertEquals(1, assessment.getDependents().size());
        assertEquals("K900001", assessment.getDependents().get(0).getTrkorr());
    }

    @Test
    @DisplayName("K900004 is high - an object changed after the import")
    void k900004IsHigh() {
        TransportAssessment assessment = analysisService.assess("K900004");

        assertNotNull(assessment);
        assertEquals(Severity.HIGH, assessment.getSeverity());
        assertTrue(assessment.hasFinding("MODIFIED_AFTER_IMPORT"));
        assertTrue(assessment.isModifiedAfterImport());
        assertEquals(70, assessment.getScore());
    }

    @Test
    @DisplayName("K900005 is high - the task has been in development for 70 days")
    void k900005IsHigh() {
        TransportAssessment assessment = analysisService.assess("K900005");

        assertNotNull(assessment);
        assertEquals(Severity.HIGH, assessment.getSeverity());
        assertTrue(assessment.hasFinding("UNRELEASED_TOO_LONG"));
    }

    @Test
    @DisplayName("K900006 is high - its task is still open")
    void k900006IsHigh() {
        TransportAssessment assessment = analysisService.assess("K900006");

        assertNotNull(assessment);
        assertEquals(Severity.HIGH, assessment.getSeverity());
        assertTrue(assessment.hasFinding("TASK_IN_DEVELOPMENT"));
        assertTrue(assessment.hasFinding("UNRELEASED_TOO_LONG"));
        assertTrue(assessment.hasFinding("STALE_REQUEST"));
        assertEquals(30, assessment.getScore());
    }

    @Test
    @DisplayName("K900007 is high - released, but still pointing at QA")
    void k900007IsHigh() {
        TransportAssessment assessment = analysisService.assess("K900007");

        assertNotNull(assessment);
        assertEquals(Severity.HIGH, assessment.getSeverity());
        assertTrue(assessment.hasFinding("NOT_TARGETED"));
        assertEquals(65, assessment.getScore());
    }

    @Test
    @DisplayName("K900008 is clean - imported and untouched since")
    void k900008IsClean() {
        TransportAssessment assessment = analysisService.assess("K900008");

        assertNotNull(assessment);
        assertEquals(100, assessment.getScore());
        assertEquals(Severity.INFO, assessment.getSeverity());
        assertTrue(assessment.getFindings().isEmpty(),
                "K900008 should have no findings but had " + assessment.getFindings());
    }

    @Test
    @DisplayName("K900009 is clean and ready to import")
    void k900009IsCleanAndReady() {
        TransportAssessment assessment = analysisService.assess("K900009");

        assertNotNull(assessment);
        assertEquals(100, assessment.getScore());
        assertEquals(Severity.INFO, assessment.getSeverity());
        assertTrue(assessment.getFindings().isEmpty());
    }

    @Test
    @DisplayName("exactly one object is held by two open requests")
    void exactlyOneConflict() {
        List<ObjectConflict> conflicts = analysisService.conflicts();

        assertEquals(1, conflicts.size());
        assertEquals("ZCL_PAYMENT", conflicts.get(0).getObjectName());
        assertEquals(2, conflicts.get(0).requestCount());
        assertTrue(conflicts.get(0).getRequestList().contains("K900001"));
        assertTrue(conflicts.get(0).getRequestList().contains("K900002"));
    }

    @Test
    @DisplayName("the counts add up to the landscape")
    void countsAddUp() {
        int counted = analysisService.countBySeverity(Severity.CRITICAL)
                + analysisService.countBySeverity(Severity.HIGH)
                + analysisService.countBySeverity(Severity.MEDIUM)
                + analysisService.countBySeverity(Severity.LOW)
                + analysisService.countBySeverity(Severity.INFO);

        assertEquals(analysisService.requests().size(), counted);
        assertEquals(3, analysisService.countBySeverity(Severity.CRITICAL));
        assertEquals(4, analysisService.countBySeverity(Severity.HIGH));
        assertEquals(2, analysisService.countBySeverity(Severity.INFO));
        assertEquals(61, analysisService.averageScore());
    }

    @Test
    @DisplayName("the landscape is sorted worst first")
    void sortedWorstFirst() {
        List<TransportAssessment> assessments = analysisService.assessments();

        assertEquals("K900002", assessments.get(0).getRequest().getTrkorr());
        assertEquals("K900008", assessments.get(8).getRequest().getTrkorr());
    }

    @Test
    @DisplayName("every finding carries a recommended action")
    void everyFindingHasAnAction() {
        for (TransportFinding finding : analysisService.allFindings()) {
            assertNotNull(finding.getRecommendation());
            assertFalse(finding.getRecommendation().trim().isEmpty(),
                    "finding " + finding.getCategory() + " has no recommendation");
            assertFalse(finding.getMessage().trim().isEmpty());
        }
    }

    @Test
    @DisplayName("the report names every request and every finding")
    void reportCoversEverything() {
        String report = analysisService.renderReport();

        assertTrue(report.contains("# Change and transport report"));
        for (String id : new String[]{"K900001", "K900002", "K900003", "K900004",
                "K900005", "K900006", "K900007", "K900008", "K900009"}) {
            assertTrue(report.contains(id), "the report does not mention " + id);
        }
        assertTrue(report.contains("OBJECT_CONFLICT"));
        assertTrue(report.contains("## Object conflicts"));
        assertTrue(report.contains("## Import evidence"));
    }

    // -- the release train ---------------------------------------------------

    @Test
    @DisplayName("the PRD train has exactly one request that can go")
    void prdTrainHasOneReadyRequest() {
        List<ReleaseEntry> train = releaseService.releaseTrain("PRD");

        int ready = 0;
        int blocked = 0;
        int imported = 0;
        int inDevelopment = 0;
        for (ReleaseEntry entry : train) {
            if (ReleaseEntry.STATE_READY.equals(entry.getState())) {
                ready++;
            } else if (ReleaseEntry.STATE_BLOCKED.equals(entry.getState())) {
                blocked++;
            } else if (ReleaseEntry.STATE_IMPORTED.equals(entry.getState())) {
                imported++;
            } else {
                inDevelopment++;
            }
        }

        assertEquals(7, train.size());
        assertEquals(1, ready);
        assertEquals(3, blocked);
        assertEquals(2, imported);
        assertEquals(1, inDevelopment);
        assertEquals("K900009", train.get(0).getRequest().getTrkorr(),
                "the ready request comes first");
    }

    @Test
    @DisplayName("a task never appears on the train - it travels with its parent")
    void tasksAreNotOnTheTrain() {
        for (ReleaseEntry entry : releaseService.releaseTrain("PRD")) {
            assertFalse(entry.getRequest().isTask(),
                    "a task must not be on the release train");
        }
    }

    @Test
    @DisplayName("the blocked requests say what is blocking them")
    void blockedRequestsExplainThemselves() {
        for (ReleaseEntry entry : releaseService.releaseTrain("PRD")) {
            if (ReleaseEntry.STATE_BLOCKED.equals(entry.getState())) {
                assertFalse(entry.getBlockers().isEmpty(),
                        entry.getRequest().getTrkorr() + " is blocked but says why not");
            }
        }
    }

    @Test
    @DisplayName("the checklist names the request that can go and the command to run")
    void checklistIsUsable() {
        String checklist = releaseService.checklist("PRD");

        assertTrue(checklist.contains("# Import checklist - PRD"));
        assertTrue(checklist.contains("K900009"));
        assertTrue(checklist.contains("tp import request K900009 PRD"));
        assertTrue(checklist.contains("## Ready to import (1)"));
        assertTrue(checklist.contains("## Blocked (3)"));
        assertTrue(checklist.contains("ZCL_PAYMENT"),
                "the pre-flight checks must name the conflicting object");
    }
}
