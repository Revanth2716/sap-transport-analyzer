package com.revzz.sap.transport.service;

import com.revzz.sap.transport.config.AppProperties;
import com.revzz.sap.transport.domain.ImportRecord;
import com.revzz.sap.transport.domain.ReleaseSystem;
import com.revzz.sap.transport.domain.Severity;
import com.revzz.sap.transport.domain.TransportAssessment;
import com.revzz.sap.transport.domain.TransportDependency;
import com.revzz.sap.transport.domain.TransportFinding;
import com.revzz.sap.transport.domain.TransportObject;
import com.revzz.sap.transport.domain.TransportRequest;
import com.revzz.sap.transport.repo.TransportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The transport rules, tested one at a time.
 *
 * <p>Each test builds exactly one situation, so that a failure says which
 * rule broke rather than "the score changed".</p>
 */
class TransportAnalysisServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.now();

    private TransportRepository repository;
    private final List<TransportRequest> requests = new ArrayList<TransportRequest>();
    private final List<TransportObject> objects = new ArrayList<TransportObject>();
    private final List<ImportRecord> imports = new ArrayList<ImportRecord>();
    private final List<TransportDependency> dependencies = new ArrayList<TransportDependency>();

    @BeforeEach
    void setUp() {
        repository = mock(TransportRepository.class);
        when(repository.findRequests()).thenReturn(requests);
        when(repository.findAllObjects()).thenReturn(objects);
        when(repository.findAllImports()).thenReturn(imports);
        when(repository.findAllDependencies()).thenReturn(dependencies);
        when(repository.findSystem(anyString())).thenReturn(system("PRD", true));
    }

    // -- the rules -----------------------------------------------------------

    @Test
    @DisplayName("one object in two open requests is a critical conflict")
    void objectInTwoOpenRequestsIsAConflict() {
        TransportRequest first = request("K900001", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 10);
        TransportRequest second = request("K900002", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 5);
        requests.add(first);
        requests.add(second);
        objects.add(object("K900001", "ZCL_PAYMENT (CLAS)", "CLAS", "ZCL_PAYMENT", 9));
        objects.add(object("K900002", "ZCL_PAYMENT (CLAS)", "CLAS", "ZCL_PAYMENT", 4));

        TransportAssessment assessment = service().assess(first, TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("OBJECT_CONFLICT"));
        assertEquals(Severity.CRITICAL, assessment.getSeverity());
        assertEquals(1, assessment.getConflicts().size());
        assertTrue(finding(assessment, "OBJECT_CONFLICT").getMessage().contains("K900002"));
    }

    @Test
    @DisplayName("an object in a request that was already imported is not a conflict")
    void closedRequestDoesNotConflict() {
        TransportRequest open = request("K900001", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 10);
        TransportRequest imported = request("K900002", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_IMPORTED, "PRD", 40);
        requests.add(open);
        requests.add(imported);
        objects.add(object("K900001", "ZCL_PAYMENT (CLAS)", "CLAS", "ZCL_PAYMENT", 9));
        objects.add(object("K900002", "ZCL_PAYMENT (CLAS)", "CLAS", "ZCL_PAYMENT", 39));

        TransportAssessment assessment = service().assess(open, TransportSnapshot.load(repository));

        assertFalse(assessment.hasFinding("OBJECT_CONFLICT"));
        assertTrue(assessment.getConflicts().isEmpty());
    }

    @Test
    @DisplayName("a customizing request is blocked until its dependency reaches the target")
    void dependencyBlocksTheRequest() {
        TransportRequest customizing = request("K900003", TransportRequest.TYPE_CUSTOMIZING,
                TransportRequest.STATUS_RELEASED, "PRD", 5);
        TransportRequest parent = request("K900001", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 10);
        requests.add(customizing);
        requests.add(parent);
        dependencies.add(dependency("K900003", "K900001",
                TransportDependency.KIND_CONFIG_REFERENCE));

        TransportAssessment assessment = service().assess(customizing,
                TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("BLOCKED_DEPENDENCY"));
        assertEquals(Severity.CRITICAL, assessment.getSeverity());
    }

    @Test
    @DisplayName("a dependency that reached the target no longer blocks")
    void satisfiedDependencyDoesNotBlock() {
        TransportRequest customizing = request("K900003", TransportRequest.TYPE_CUSTOMIZING,
                TransportRequest.STATUS_RELEASED, "PRD", 5);
        TransportRequest parent = request("K900001", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_IMPORTED, "PRD", 10);
        requests.add(customizing);
        requests.add(parent);
        dependencies.add(dependency("K900003", "K900001",
                TransportDependency.KIND_CONFIG_REFERENCE));
        imports.add(importRecord("K900001", "PRD", 3, ImportRecord.RESULT_OK));

        TransportAssessment assessment = service().assess(customizing,
                TransportSnapshot.load(repository));

        assertFalse(assessment.hasFinding("BLOCKED_DEPENDENCY"));
    }

    @Test
    @DisplayName("a dependency that only reached another system still blocks")
    void dependencyInTheWrongSystemStillBlocks() {
        TransportRequest customizing = request("K900003", TransportRequest.TYPE_CUSTOMIZING,
                TransportRequest.STATUS_RELEASED, "PRD", 5);
        TransportRequest parent = request("K900001", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 10);
        requests.add(customizing);
        requests.add(parent);
        dependencies.add(dependency("K900003", "K900001",
                TransportDependency.KIND_CONFIG_REFERENCE));
        imports.add(importRecord("K900001", "QAS", 3, ImportRecord.RESULT_OK));

        TransportAssessment assessment = service().assess(customizing,
                TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("BLOCKED_DEPENDENCY"),
                "the dependency reached QA, not the target PRD");
    }

    @Test
    @DisplayName("a task still in development holds the workbench request back")
    void openTaskBlocksTheWorkbenchRequest() {
        TransportRequest workbench = request("K900006", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_IN_DEVELOPMENT, "PRD", 10);
        TransportRequest task = request("K900005", TransportRequest.TYPE_TASK,
                TransportRequest.STATUS_IN_DEVELOPMENT, "PRD", 10);
        task.setParentRequest("K900006");
        requests.add(workbench);
        requests.add(task);

        TransportAssessment assessment = service().assess(workbench,
                TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("TASK_IN_DEVELOPMENT"));
        assertEquals(Severity.HIGH, assessment.getSeverity());
    }

    @Test
    @DisplayName("a released request that was never imported is a finding")
    void releasedWithoutImportEvidenceIsAFinding() {
        TransportRequest request = request("K900002", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 5);
        requests.add(request);

        TransportAssessment assessment = service().assess(request, TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("NO_IMPORT_EVIDENCE"));
        assertEquals(Severity.HIGH, assessment.getSeverity());
    }

    @Test
    @DisplayName("an object changed after the import is a finding")
    void modifiedAfterImportIsAFinding() {
        TransportRequest request = request("K900004", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_IMPORTED, "PRD", 90);
        requests.add(request);
        objects.add(object("K900004", "ZCL_INVOICE (CLAS)", "CLAS", "ZCL_INVOICE", 10));
        imports.add(importRecord("K900004", "PRD", 60, ImportRecord.RESULT_OK));

        TransportAssessment assessment = service().assess(request, TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("MODIFIED_AFTER_IMPORT"));
        assertTrue(assessment.isModifiedAfterImport());
        assertEquals(Severity.HIGH, assessment.getSeverity());
    }

    @Test
    @DisplayName("a failed import is critical")
    void failedImportIsCritical() {
        TransportRequest request = request("K900004", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 20);
        requests.add(request);
        imports.add(importRecord("K900004", "PRD", 2, ImportRecord.RESULT_FAILED));

        TransportAssessment assessment = service().assess(request, TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("FAILED_IMPORT"));
        assertEquals(Severity.CRITICAL, assessment.getSeverity());
    }

    @Test
    @DisplayName("an old request that was never released is a release risk")
    void unreleasedForTooLongIsAFinding() {
        TransportRequest request = request("K900006", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_IN_DEVELOPMENT, "PRD", 80);
        requests.add(request);

        TransportAssessment assessment = service().assess(request, TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("UNRELEASED_TOO_LONG"));
        assertEquals(Severity.HIGH, assessment.getSeverity());
    }

    @Test
    @DisplayName("an old open request is stale")
    void oldOpenRequestIsStale() {
        TransportRequest request = request("K900001", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 45);
        requests.add(request);
        // Imported into the quality system, so there is no import-evidence
        // finding on top - this test is only about the age.
        imports.add(importRecord("K900001", "QAS", 10, ImportRecord.RESULT_OK));

        TransportAssessment assessment = service().assess(request, TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("STALE_REQUEST"));
        assertEquals(Severity.MEDIUM, assessment.getSeverity());
    }

    @Test
    @DisplayName("a released request that still points at a non-target system is a finding")
    void notTargetedIsAFinding() {
        when(repository.findSystem("QAS")).thenReturn(system("QAS", false));
        TransportRequest request = request("K900007", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "QAS", 5);
        requests.add(request);
        imports.add(importRecord("K900007", "DE1", 2, ImportRecord.RESULT_OK));

        TransportAssessment assessment = service().assess(request, TransportSnapshot.load(repository));

        assertTrue(assessment.hasFinding("NOT_TARGETED"));
        assertEquals(Severity.MEDIUM, assessment.getSeverity());
    }

    @Test
    @DisplayName("a request with nothing wrong produces no findings at all")
    void cleanRequestHasNoFindings() {
        TransportRequest request = request("K900009", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 6);
        requests.add(request);
        objects.add(object("K900009", "ZCL_BANK_RECON (CLAS)", "CLAS", "ZCL_BANK_RECON", 5));
        imports.add(importRecord("K900009", "QAS", 2, ImportRecord.RESULT_OK));

        TransportAssessment assessment = service().assess(request, TransportSnapshot.load(repository));

        assertTrue(assessment.getFindings().isEmpty(),
                "expected no findings but got " + assessment.getFindings());
        assertEquals(100, assessment.getScore());
        assertEquals(Severity.INFO, assessment.getSeverity());
    }

    @Test
    @DisplayName("the severity is the worst finding, not the score band")
    void severityIsTheWorstFindingNotTheScoreBand() {
        // A failed import costs 35 points, so the score is 65 - and 65 sits
        // in the "medium" band. The severity has to say "critical".
        TransportRequest request = request("K900004", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 20);
        requests.add(request);
        imports.add(importRecord("K900004", "PRD", 2, ImportRecord.RESULT_FAILED));

        TransportAssessment assessment = service().assess(request, TransportSnapshot.load(repository));

        assertEquals(65, assessment.getScore());
        assertEquals(Severity.CRITICAL, assessment.getSeverity());
    }

    @Test
    @DisplayName("the score never goes below zero")
    void scoreNeverGoesNegative() {
        TransportRequest first = request("K900001", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_IN_DEVELOPMENT, "PRD", 200);
        TransportRequest second = request("K900002", TransportRequest.TYPE_WORKBENCH,
                TransportRequest.STATUS_RELEASED, "PRD", 200);
        requests.add(first);
        requests.add(second);
        objects.add(object("K900001", "ZCL_PAYMENT (CLAS)", "CLAS", "ZCL_PAYMENT", 199));
        objects.add(object("K900002", "ZCL_PAYMENT (CLAS)", "CLAS", "ZCL_PAYMENT", 199));

        TransportAssessment assessment = service().assess(second, TransportSnapshot.load(repository));

        assertTrue(assessment.getScore() >= 0);
    }

    // -- helpers -------------------------------------------------------------

    private TransportAnalysisService service() {
        return new TransportAnalysisService(repository, new AppProperties());
    }

    private ReleaseSystem system(String id, boolean target) {
        ReleaseSystem system = new ReleaseSystem();
        system.setSystemId(id);
        system.setDescription("System " + id);
        system.setSystemRole(target ? ReleaseSystem.ROLE_PRODUCTION : ReleaseSystem.ROLE_QUALITY);
        system.setTarget(target);
        return system;
    }

    private TransportRequest request(String trkorr, String type, String status,
                                     String target, int ageDays) {
        TransportRequest request = new TransportRequest();
        request.setTrkorr(trkorr);
        request.setDescription("Request " + trkorr);
        request.setRequestType(type);
        request.setStatus(status);
        request.setStatusText("status");
        request.setOwner("OWNER");
        request.setCreatedOn(NOW.minusDays(ageDays));
        request.setTargetSystem(target);
        request.setSourceSystem("DE1");
        request.setParentRequest("");
        return request;
    }

    private TransportObject object(String requestId, String key, String type, String name,
                                   int ageDays) {
        TransportObject object = new TransportObject();
        object.setObjectId("O" + requestId + name);
        object.setRequestId(requestId);
        object.setProgramId("R");
        object.setObjectType(type);
        object.setObjectName(name);
        object.setObjectKey(key);
        object.setFieldName("");
        object.setLastChanged(NOW.minusDays(ageDays));
        return object;
    }

    private ImportRecord importRecord(String requestId, String systemId, int ageDays,
                                       String result) {
        ImportRecord record = new ImportRecord();
        record.setImportId("IM" + requestId + systemId + ageDays);
        record.setRequestId(requestId);
        record.setSystemId(systemId);
        record.setImportedAt(NOW.minusDays(ageDays));
        record.setResult(result);
        record.setMessage("message");
        return record;
    }

    private TransportDependency dependency(String requestId, String dependsOn, String kind) {
        TransportDependency dependency = new TransportDependency();
        dependency.setDependencyId("DP" + requestId + dependsOn);
        dependency.setRequestId(requestId);
        dependency.setDependsOn(dependsOn);
        dependency.setKind(kind);
        return dependency;
    }

    private TransportFinding finding(TransportAssessment assessment, String category) {
        for (TransportFinding finding : assessment.getFindings()) {
            if (category.equals(finding.getCategory())) {
                return finding;
            }
        }
        throw new AssertionError("no finding with category " + category);
    }
}
