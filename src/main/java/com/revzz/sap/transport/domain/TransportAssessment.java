package com.revzz.sap.transport.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The assessment of one transport request.
 *
 * <p>Same shape as the readiness assessment in the sibling project: a score
 * for sorting, and the findings that make it actionable. The score is never
 * the answer on its own.</p>
 */
public class TransportAssessment {

    private TransportRequest request;
    private int score;
    private Severity severity;
    private List<TransportFinding> findings = new ArrayList<TransportFinding>();
    private List<TransportObject> objects = new ArrayList<TransportObject>();
    private List<TransportObject> conflicts = new ArrayList<TransportObject>();
    private List<TransportRequest> dependents = new ArrayList<TransportRequest>();
    private List<ImportRecord> imports = new ArrayList<ImportRecord>();
    private int taskCount;
    private LocalDateTime lastImportedAt;
    private boolean modifiedAfterImport;

    public boolean hasFinding(String category) {
        for (TransportFinding finding : findings) {
            if (category.equals(finding.getCategory())) {
                return true;
            }
        }
        return false;
    }

    public int findingCount() {
        return findings.size();
    }

    /** The newest object change that happened after the last import. */
    public TransportObject newestConflict() {
        return conflicts.isEmpty() ? null : conflicts.get(0);
    }

    public String getLastImportedAtDisplay() {
        if (lastImportedAt == null) {
            return "never";
        }
        return lastImportedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public TransportRequest getRequest() {
        return request;
    }

    public void setRequest(TransportRequest request) {
        this.request = request;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public List<TransportFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<TransportFinding> findings) {
        this.findings = findings;
    }

    public List<TransportObject> getObjects() {
        return objects;
    }

    public void setObjects(List<TransportObject> objects) {
        this.objects = objects;
    }

    public List<TransportObject> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<TransportObject> conflicts) {
        this.conflicts = conflicts;
    }

    public List<TransportRequest> getDependents() {
        return dependents;
    }

    public void setDependents(List<TransportRequest> dependents) {
        this.dependents = dependents;
    }

    public List<ImportRecord> getImports() {
        return imports;
    }

    public void setImports(List<ImportRecord> imports) {
        this.imports = imports;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public void setTaskCount(int taskCount) {
        this.taskCount = taskCount;
    }

    public LocalDateTime getLastImportedAt() {
        return lastImportedAt;
    }

    public void setLastImportedAt(LocalDateTime lastImportedAt) {
        this.lastImportedAt = lastImportedAt;
    }

    public boolean isModifiedAfterImport() {
        return modifiedAfterImport;
    }

    public void setModifiedAfterImport(boolean modifiedAfterImport) {
        this.modifiedAfterImport = modifiedAfterImport;
    }
}
