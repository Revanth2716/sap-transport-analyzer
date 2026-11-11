package com.revzz.sap.transport.service;

import com.revzz.sap.transport.config.AppProperties;
import com.revzz.sap.transport.domain.ImportRecord;
import com.revzz.sap.transport.domain.ObjectConflict;
import com.revzz.sap.transport.domain.ReleaseSystem;
import com.revzz.sap.transport.domain.Severity;
import com.revzz.sap.transport.domain.TransportAssessment;
import com.revzz.sap.transport.domain.TransportDependency;
import com.revzz.sap.transport.domain.TransportFinding;
import com.revzz.sap.transport.domain.TransportObject;
import com.revzz.sap.transport.domain.TransportRequest;
import com.revzz.sap.transport.repo.TransportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The transport analysis.
 *
 * <p>Seven questions, one per request:</p>
 *
 * <ol>
 *   <li><b>Does it conflict?</b> — is one of its objects also in another
 *       open request? The second import wins, and the first developer's work
 *       disappears without an error.</li>
 *   <li><b>Can it go?</b> — is everything it depends on already in the
 *       target system?</li>
 *   <li><b>Did it get there?</b> — is there any evidence of an import? A
 *       released request with no import log is a request somebody believes
 *       went to production.</li>
 *   <li><b>Did it stay put?</b> — was an object changed after the import?
 *       That is the hotfix that was never taken.</li>
 *   <li><b>Is it still being worked on?</b> — a request still in development
 *       after two months is a release risk.</li>
 *   <li><b>Has it been forgotten?</b> — open for too long, nobody is coming
 *       back to it.</li>
 *   <li><b>Is it pointing at the right system?</b> — a released request that
 *       still targets QA never made it to production.</li>
 * </ol>
 *
 * <p>The score is 100 minus the penalties and exists only so a landscape of
 * requests can be sorted. The <em>severity</em> is the worst finding, so a
 * conflict can never be hidden behind a good average.</p>
 */
@Service
public class TransportAnalysisService {

    /** Penalty for each finding, and the severity it carries. */
    private static final int PENALTY_CONFLICT = 40;
    private static final int PENALTY_BLOCKED_DEPENDENCY = 35;
    private static final int PENALTY_FAILED_IMPORT = 35;
    private static final int PENALTY_MODIFIED_AFTER_IMPORT = 30;
    private static final int PENALTY_TASK_IN_DEVELOPMENT = 30;
    private static final int PENALTY_UNRELEASED = 25;
    private static final int PENALTY_NO_IMPORT_EVIDENCE = 25;
    private static final int PENALTY_STALE = 15;
    private static final int PENALTY_NOT_TARGETED = 10;
    private static final int PENALTY_NO_OWNER = 10;

    private final TransportRepository repository;
    private final AppProperties properties;

    public TransportAnalysisService(TransportRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<TransportRequest> requests() {
        return repository.findRequests();
    }

    public List<ReleaseSystem> systems() {
        return repository.findSystems();
    }

    /** The assessment of every request, worst first. */
    public List<TransportAssessment> assessments() {
        TransportSnapshot snapshot = TransportSnapshot.load(repository);
        List<TransportAssessment> results = new ArrayList<TransportAssessment>();
        for (TransportRequest request : snapshot.requests()) {
            results.add(assess(request, snapshot));
        }
        Collections.sort(results, new Comparator<TransportAssessment>() {
            @Override
            public int compare(TransportAssessment left, TransportAssessment right) {
                int bySeverity = left.getSeverity().rank() - right.getSeverity().rank();
                return bySeverity != 0 ? bySeverity : left.getScore() - right.getScore();
            }
        });
        return results;
    }

    /** The assessment of one request, or null when it does not exist. */
    public TransportAssessment assess(String trkorr) {
        TransportSnapshot snapshot = TransportSnapshot.load(repository);
        TransportRequest request = snapshot.request(trkorr);
        return request == null ? null : assess(request, snapshot);
    }

    /** The assessment of one request. */
    public TransportAssessment assess(TransportRequest request, TransportSnapshot snapshot) {
        TransportAssessment assessment = new TransportAssessment();
        assessment.setRequest(request);

        List<TransportFinding> findings = new ArrayList<TransportFinding>();
        int penalty = 0;
        LocalDateTime now = LocalDateTime.now();

        List<TransportObject> objects = snapshot.objects(request.getTrkorr());
        assessment.setObjects(objects);

        // -- 1. does it conflict with another open request? -----------------
        List<TransportObject> conflicts = new ArrayList<TransportObject>();
        for (TransportObject object : objects) {
            List<String> others = snapshot.otherHolders(object.getObjectKey(), request.getTrkorr());
            if (!others.isEmpty()) {
                conflicts.add(object);
            }
        }
        assessment.setConflicts(conflicts);

        if (!conflicts.isEmpty()) {
            penalty += PENALTY_CONFLICT;
            TransportObject first = conflicts.get(0);
            List<String> holders = snapshot.otherHolders(first.getObjectKey(), request.getTrkorr());
            findings.add(new TransportFinding(Severity.CRITICAL, "OBJECT_CONFLICT",
                    request.getTrkorr(),
                    "Object " + first.getObjectName() + " (" + first.getObjectType()
                            + ") is also in the open request "
                            + join(holders) + ". The second import overwrites the first.",
                    "Merge the two requests before the transport. Until then one developer's "
                            + "change to " + first.getObjectName() + " will be lost silently at "
                            + "import time."));
        }

        // -- 2. can it go? ---------------------------------------------------
        List<TransportRequest> blockers = new ArrayList<TransportRequest>();
        for (TransportDependency dependency : snapshot.dependencies(request.getTrkorr())) {
            if (!TransportDependency.KIND_CONFIG_REFERENCE.equals(dependency.getKind())) {
                continue;
            }
            TransportRequest parent = snapshot.request(dependency.getDependsOn());
            if (parent == null) {
                continue;
            }
            if (!snapshot.importedInto(parent.getTrkorr(), request.getTargetSystem())) {
                blockers.add(parent);
            }
        }
        assessment.setDependents(blockers);

        if (!blockers.isEmpty()) {
            penalty += PENALTY_BLOCKED_DEPENDENCY;
            findings.add(new TransportFinding(Severity.CRITICAL, "BLOCKED_DEPENDENCY",
                    request.getTrkorr(),
                    "The request depends on " + join(requestIds(blockers))
                            + ", which has not been imported into "
                            + request.getTargetSystem() + " yet.",
                    "Transport " + join(requestIds(blockers)) + " into "
                            + request.getTargetSystem() + " first. A customizing request cannot "
                            + "go in before the workbench request it references."));
        }

        // -- 3. is a task still holding the workbench request back? ----------
        if (!request.isTask()) {
            List<String> openTasks = new ArrayList<String>();
            for (String taskId : snapshot.tasksOf(request.getTrkorr())) {
                TransportRequest task = snapshot.request(taskId);
                if (task != null && task.isInDevelopment()) {
                    openTasks.add(taskId);
                }
            }
            if (!openTasks.isEmpty()) {
                penalty += PENALTY_TASK_IN_DEVELOPMENT;
                findings.add(new TransportFinding(Severity.HIGH, "TASK_IN_DEVELOPMENT",
                        request.getTrkorr(),
                        "Task " + join(openTasks) + " is still in development, so the request "
                                + "cannot be released yet.",
                        "Finish or move the task out of development. A workbench request with an "
                                + "open task cannot be released, and the release date slips with it."));
            }
        }

        // -- 4. did it get there? ---------------------------------------------
        List<ImportRecord> imports = snapshot.imports(request.getTrkorr());
        assessment.setImports(imports);

        ImportRecord lastImport = snapshot.lastImport(request.getTrkorr());
        ImportRecord lastGood = snapshot.lastSuccessfulImport(request.getTrkorr());
        assessment.setLastImportedAt(lastGood == null ? null : lastGood.getImportedAt());

        if (lastImport != null && !lastImport.isSuccessful()) {
            penalty += PENALTY_FAILED_IMPORT;
            findings.add(new TransportFinding(Severity.CRITICAL, "FAILED_IMPORT",
                    request.getTrkorr(),
                    "The last import of " + request.getTrkorr() + " into "
                            + lastImport.getSystemId() + " failed: " + lastImport.getMessage(),
                    "Read the transport log and fix the import before the next release - a failed "
                            + "import leaves the target system in an unknown state."));
        }

        if (request.isReleased() && imports.isEmpty()) {
            penalty += PENALTY_NO_IMPORT_EVIDENCE;
            findings.add(new TransportFinding(Severity.HIGH, "NO_IMPORT_EVIDENCE",
                    request.getTrkorr(),
                    request.getTrkorr() + " is released but there is no record of it ever being "
                            + "imported into a system.",
                    "Check the transport log. A released request with no import record is a "
                            + "request somebody believes went to production."));
        }

        // -- 5. did it stay put? ----------------------------------------------
        if (lastGood != null) {
            LocalDateTime modified = null;
            for (TransportObject object : objects) {
                if (object.getLastChanged() != null
                        && object.getLastChanged().isAfter(lastGood.getImportedAt())
                        && (modified == null || object.getLastChanged().isAfter(modified))) {
                    modified = object.getLastChanged();
                }
            }
            if (modified != null) {
                assessment.setModifiedAfterImport(true);
                penalty += PENALTY_MODIFIED_AFTER_IMPORT;
                findings.add(new TransportFinding(Severity.HIGH, "MODIFIED_AFTER_IMPORT",
                        request.getTrkorr(),
                        "An object in " + request.getTrkorr() + " was changed on "
                                + modified.toLocalDate() + ", after it was imported into "
                                + lastGood.getSystemId() + ".",
                        "Transport the change again. The target system is running an older "
                                + "version of the object than the development system, and nobody "
                                + "will notice until the next incident."));
            }
        }

        // -- 6. is it still being worked on, or forgotten? --------------------
        int age = request.ageDays(now);
        if (request.isInDevelopment()
                && age > properties.getPolicy().getUnreleasedRequestDays()) {
            penalty += PENALTY_UNRELEASED;
            findings.add(new TransportFinding(Severity.HIGH, "UNRELEASED_TOO_LONG",
                    request.getTrkorr(),
                    request.getTrkorr() + " has been in development for " + age + " days.",
                    "Decide what happens to it. A request that is never released is a request "
                            + "that quietly stops being anyone's priority."));
        }

        if (request.isOpen() && age > properties.getPolicy().getStaleRequestDays()) {
            penalty += PENALTY_STALE;
            findings.add(new TransportFinding(Severity.MEDIUM, "STALE_REQUEST",
                    request.getTrkorr(),
                    request.getTrkorr() + " has been open for " + age + " days.",
                    "Review the request with its owner. Old open requests are where the "
                            + "conflicting changes accumulate."));
        }

        // -- 7. is it pointing at the right system? ---------------------------
        ReleaseSystem target = repository.findSystem(request.getTargetSystem());
        if (request.isReleased() && target != null && !target.isTarget()) {
            penalty += PENALTY_NOT_TARGETED;
            findings.add(new TransportFinding(Severity.MEDIUM, "NOT_TARGETED",
                    request.getTrkorr(),
                    request.getTrkorr() + " is released but still targets " + target.getSystemId()
                            + " (" + target.getDescription() + "), not the production system.",
                    "Move it forward. A released request that never reached production is "
                            + "usually a change that went live by accident, or never at all."));
        }

        if (request.getOwner() == null || request.getOwner().trim().isEmpty()) {
            penalty += PENALTY_NO_OWNER;
            findings.add(new TransportFinding(Severity.LOW, "NO_OWNER",
                    request.getTrkorr(),
                    request.getTrkorr() + " has no owner.",
                    "Give it one. A request without an owner is a request nobody will move."));
        }

        // -- score and severity ------------------------------------------------
        int score = Math.max(0, 100 - penalty);
        assessment.setScore(score);
        assessment.setFindings(findings);
        assessment.setSeverity(worstSeverity(findings, score));
        return assessment;
    }

    /** The severity of a request is the worst finding it has. */
    private Severity worstSeverity(List<TransportFinding> findings, int score) {
        Severity worst = null;
        for (TransportFinding finding : findings) {
            if (worst == null || finding.getSeverity().rank() < worst.rank()) {
                worst = finding.getSeverity();
            }
        }
        if (worst != null) {
            return worst;
        }
        if (score >= 80) {
            return Severity.INFO;
        }
        if (score >= 60) {
            return Severity.MEDIUM;
        }
        if (score >= 35) {
            return Severity.HIGH;
        }
        return Severity.CRITICAL;
    }

    /** Every finding across the landscape, most severe first. */
    public List<TransportFinding> allFindings() {
        List<TransportFinding> findings = new ArrayList<TransportFinding>();
        for (TransportAssessment assessment : assessments()) {
            findings.addAll(assessment.getFindings());
        }
        Collections.sort(findings, new Comparator<TransportFinding>() {
            @Override
            public int compare(TransportFinding left, TransportFinding right) {
                int bySeverity = left.getSeverity().rank() - right.getSeverity().rank();
                return bySeverity != 0 ? bySeverity
                        : left.getRequestId().compareTo(right.getRequestId());
            }
        });
        return findings;
    }

    /** Every object that is held by more than one open request. */
    public List<ObjectConflict> conflicts() {
        TransportSnapshot snapshot = TransportSnapshot.load(repository);
        List<ObjectConflict> conflicts = new ArrayList<ObjectConflict>();
        for (String key : snapshot.conflictingObjectKeys()) {
            ObjectConflict conflict = new ObjectConflict();
            conflict.setObjectKey(key);
            TransportObject object = snapshot.objectByKey(key);
            if (object != null) {
                conflict.setObjectType(object.getObjectType());
                conflict.setObjectName(object.getObjectName());
                conflict.setLastChanged(object.getLastChanged());
            }
            conflict.setRequestIds(snapshot.holders(key));
            conflicts.add(conflict);
        }
        return conflicts;
    }

    /** How many requests are in each severity band. */
    public int countBySeverity(Severity severity) {
        int count = 0;
        for (TransportAssessment assessment : assessments()) {
            if (assessment.getSeverity() == severity) {
                count++;
            }
        }
        return count;
    }

    /** The average readiness score across the landscape. */
    public int averageScore() {
        List<TransportAssessment> all = assessments();
        if (all.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (TransportAssessment assessment : all) {
            total += assessment.getScore();
        }
        return Math.round((float) total / all.size());
    }

    private List<String> requestIds(List<TransportRequest> requests) {
        List<String> ids = new ArrayList<String>();
        for (TransportRequest request : requests) {
            ids.add(request.getTrkorr());
        }
        return ids;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.toString();
    }

    /** The change and transport report, as a Markdown document. */
    public String renderReport() {
        List<TransportAssessment> assessments = assessments();
        StringBuilder out = new StringBuilder();
        out.append("# Change and transport report\n\n");
        out.append("Generated by SAP Transport & Change Management Analyzer.\n\n");

        out.append("## Summary\n\n");
        out.append("| Request | Description | Type | Owner | Target | Status | Score | Findings |\n");
        out.append("|---|---|---|---|---|---|---|---|\n");
        for (TransportAssessment assessment : assessments) {
            TransportRequest request = assessment.getRequest();
            out.append("| ").append(request.getTrkorr())
                    .append(" | ").append(request.getDescription())
                    .append(" | ").append(request.getRequestType())
                    .append(" | ").append(request.getOwner())
                    .append(" | ").append(request.getTargetSystem())
                    .append(" | ").append(request.getStatusText())
                    .append(" | ").append(assessment.getScore())
                    .append(" | ").append(assessment.getSeverity())
                    .append(" | ").append(assessment.findingCount())
                    .append(" |\n");
        }
        out.append("\n");

        out.append("## Findings\n\n");
        for (TransportAssessment assessment : assessments) {
            if (assessment.getFindings().isEmpty()) {
                continue;
            }
            TransportRequest request = assessment.getRequest();
            out.append("### ").append(request.getTrkorr())
                    .append(" - ").append(request.getDescription()).append("\n\n");
            for (TransportFinding finding : assessment.getFindings()) {
                out.append("- **").append(finding.getSeverity()).append(" - ")
                        .append(finding.getCategory()).append("**: ")
                        .append(finding.getMessage()).append("  \n  ")
                        .append(finding.getRecommendation()).append("\n");
            }
            out.append("\n");
        }

        out.append("## Object conflicts\n\n");
        List<ObjectConflict> conflicts = conflicts();
        if (conflicts.isEmpty()) {
            out.append("No object is held by more than one open request.\n\n");
        } else {
            out.append("| Object | Type | Held by | Last changed |\n");
            out.append("|---|---|---|---|\n");
            for (ObjectConflict conflict : conflicts) {
                out.append("| ").append(conflict.getObjectName())
                        .append(" | ").append(conflict.getObjectType())
                        .append(" | ").append(conflict.getRequestList())
                        .append(" | ").append(conflict.getLastChangedDisplay())
                        .append(" |\n");
            }
            out.append("\n");
        }

        out.append("## Import evidence\n\n");
        out.append("| Request | Last import | Result | Objects |\n");
        out.append("|---|---|---|---|\n");
        for (TransportAssessment assessment : assessments) {
            ImportRecord last = null;
            for (ImportRecord record : assessment.getImports()) {
                if (last == null || record.getImportedAt().isAfter(last.getImportedAt())) {
                    last = record;
                }
            }
            out.append("| ").append(assessment.getRequest().getTrkorr())
                    .append(" | ")
                    .append(last == null ? "never"
                            : last.getSystemId() + " on " + last.getImportedAtDisplay())
                    .append(" | ")
                    .append(last == null ? "-" : last.getResult())
                    .append(" | ").append(assessment.getObjects().size())
                    .append(" |\n");
        }
        out.append("\n");

        out.append("---\n\n");
        out.append("> The second import wins, and the first developer's work disappears "
                + "without an error.\n");
        return out.toString();
    }
}
