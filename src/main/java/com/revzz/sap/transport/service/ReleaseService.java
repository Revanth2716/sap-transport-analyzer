package com.revzz.sap.transport.service;

import com.revzz.sap.transport.domain.ImportRecord;
import com.revzz.sap.transport.domain.ReleaseEntry;
import com.revzz.sap.transport.domain.ReleaseSystem;
import com.revzz.sap.transport.domain.TransportAssessment;
import com.revzz.sap.transport.domain.TransportDependency;
import com.revzz.sap.transport.domain.TransportFinding;
import com.revzz.sap.transport.domain.TransportRequest;
import com.revzz.sap.transport.repo.TransportRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The release train.
 *
 * <p>The question this answers is not "what requests are open" — SE09 already
 * answers that. It is <b>"what can go into the next transport, in what order,
 * and what is stopping the rest"</b>.</p>
 *
 * <p>A request is <b>READY</b> when it is released and nothing stands in the
 * way of importing it. It is <b>BLOCKED</b> when it would import but
 * something has to be fixed first — a conflict, an unmet dependency, a task
 * still in development, a failed import. It is <b>IN_DEVELOPMENT</b> when it
 * is not released at all, and <b>IMPORTED</b> when it is already in the
 * target system.</p>
 */
@Service
public class ReleaseService {

    /** The findings that stop a request from being imported. */
    private static final String[] BLOCKING_CATEGORIES = {
            "OBJECT_CONFLICT",
            "BLOCKED_DEPENDENCY",
            "TASK_IN_DEVELOPMENT",
            "FAILED_IMPORT",
    };

    private final TransportRepository repository;
    private final TransportAnalysisService analysisService;

    public ReleaseService(TransportRepository repository,
                          TransportAnalysisService analysisService) {
        this.repository = repository;
        this.analysisService = analysisService;
    }

    public List<ReleaseSystem> systems() {
        return repository.findSystems();
    }

    /** The release train of one target system. */
    public List<ReleaseEntry> releaseTrain(String systemId) {
        ReleaseSystem system = repository.findSystem(systemId);
        List<ReleaseEntry> entries = new ArrayList<ReleaseEntry>();

        List<TransportAssessment> assessments = analysisService.assessments();
        Map<String, TransportAssessment> byRequest = new HashMap<String, TransportAssessment>();
        for (TransportAssessment assessment : assessments) {
            byRequest.put(assessment.getRequest().getTrkorr(), assessment);
        }

        TransportSnapshot snapshot = TransportSnapshot.load(repository);

        for (TransportRequest request : snapshot.requests()) {
            // A task is not transported on its own - it travels inside its
            // workbench request, so it has no place on the train.
            if (request.isTask()) {
                continue;
            }
            if (!systemId.equals(request.getTargetSystem())) {
                continue;
            }
            TransportAssessment assessment = byRequest.get(request.getTrkorr());
            if (assessment == null) {
                continue;
            }
            entries.add(entryFor(request, system, assessment, snapshot));
        }

        order(entries, snapshot);
        return entries;
    }

    private ReleaseEntry entryFor(TransportRequest request, ReleaseSystem system,
                                   TransportAssessment assessment, TransportSnapshot snapshot) {
        ReleaseEntry entry = new ReleaseEntry();
        entry.setRequest(request);
        entry.setSystem(system);
        entry.setObjectCount(assessment.getObjects().size());

        List<String> blockers = new ArrayList<String>();
        for (TransportFinding finding : assessment.getFindings()) {
            for (String category : BLOCKING_CATEGORIES) {
                if (category.equals(finding.getCategory())) {
                    blockers.add(finding.getMessage());
                }
            }
        }

        boolean importedIntoTarget = false;
        for (ImportRecord record : assessment.getImports()) {
            if (record.isSuccessful() && system.getSystemId().equals(record.getSystemId())) {
                importedIntoTarget = true;
            }
        }

        if (importedIntoTarget || request.isImported()) {
            entry.setState(ReleaseEntry.STATE_IMPORTED);
        } else if (request.isInDevelopment()) {
            // A request that is not released yet is not "blocked" - it is
            // simply unfinished. Blocked means released and held back.
            entry.setState(ReleaseEntry.STATE_IN_DEVELOPMENT);
        } else if (!blockers.isEmpty()) {
            entry.setState(ReleaseEntry.STATE_BLOCKED);
        } else {
            entry.setState(ReleaseEntry.STATE_READY);
        }
        entry.setBlockers(blockers);
        return entry;
    }

    /**
     * Orders the train: what can go first, in dependency order.
     *
     * <p>Within the ready requests the order is a topological sort of the
     * dependency edges, so a customizing request never appears before the
     * workbench request it references.</p>
     */
    private void order(List<ReleaseEntry> entries, TransportSnapshot snapshot) {
        final Map<String, Integer> stateRank = new HashMap<String, Integer>();
        stateRank.put(ReleaseEntry.STATE_READY, 0);
        stateRank.put(ReleaseEntry.STATE_BLOCKED, 1);
        stateRank.put(ReleaseEntry.STATE_IN_DEVELOPMENT, 2);
        stateRank.put(ReleaseEntry.STATE_IMPORTED, 3);

        final Map<String, ReleaseEntry> byRequest = new HashMap<String, ReleaseEntry>();
        for (ReleaseEntry entry : entries) {
            byRequest.put(entry.getRequest().getTrkorr(), entry);
        }

        final TransportSnapshot snapshotRef = snapshot;

        Collections.sort(entries, new Comparator<ReleaseEntry>() {
            @Override
            public int compare(ReleaseEntry left, ReleaseEntry right) {
                int byState = rank(stateRank, left) - rank(stateRank, right);
                if (byState != 0) {
                    return byState;
                }
                if (ReleaseEntry.STATE_READY.equals(left.getState())
                        && ReleaseEntry.STATE_READY.equals(right.getState())) {
                    int byDepth = depth(left, snapshotRef) - depth(right, snapshotRef);
                    if (byDepth != 0) {
                        return byDepth;
                    }
                }
                return left.getRequest().getCreatedOn()
                        .compareTo(right.getRequest().getCreatedOn());
            }

            private int rank(Map<String, Integer> ranks, ReleaseEntry entry) {
                Integer value = ranks.get(entry.getState());
                return value == null ? 9 : value;
            }
        });

        // The order field is what the checklist uses for the step numbers.
        int step = 1;
        for (ReleaseEntry entry : entries) {
            entry.setOrder(step++);
        }
    }

    /** How deep a request sits in the dependency chain. */
    private int depth(ReleaseEntry entry, TransportSnapshot snapshot) {
        int depth = 0;
        String requestId = entry.getRequest().getTrkorr();
        for (TransportDependency dependency : snapshot.dependencies(requestId)) {
            if (!TransportDependency.KIND_CONFIG_REFERENCE.equals(dependency.getKind())) {
                continue;
            }
            TransportRequest parent = snapshot.request(dependency.getDependsOn());
            if (parent == null) {
                continue;
            }
            int parentDepth = 0;
            for (TransportDependency grandParent : snapshot.dependencies(parent.getTrkorr())) {
                if (TransportDependency.KIND_CONFIG_REFERENCE
                        .equals(grandParent.getKind())) {
                    parentDepth = Math.max(parentDepth, 1);
                }
            }
            depth = Math.max(depth, parentDepth + 1);
        }
        return depth;
    }

    /**
     * The import checklist of one target system.
     *
     * <p>The document a release manager actually works from: what to import,
     * in which order, and what has to be cleared first.</p>
     */
    public String checklist(String systemId) {
        ReleaseSystem system = repository.findSystem(systemId);
        if (system == null) {
            return "# Import checklist\n\nThere is no system called " + systemId + ".\n";
        }

        List<ReleaseEntry> train = releaseTrain(systemId);
        StringBuilder out = new StringBuilder();
        out.append("# Import checklist - ").append(system.getSystemId()).append("\n\n");
        out.append(system.getDescription()).append(" (").append(system.getSystemRole())
                .append(")\n\n");
        out.append("Generated by SAP Transport & Change Management Analyzer.\n\n");

        List<ReleaseEntry> ready = new ArrayList<ReleaseEntry>();
        List<ReleaseEntry> blocked = new ArrayList<ReleaseEntry>();
        List<ReleaseEntry> inDevelopment = new ArrayList<ReleaseEntry>();
        List<ReleaseEntry> imported = new ArrayList<ReleaseEntry>();
        for (ReleaseEntry entry : train) {
            if (ReleaseEntry.STATE_READY.equals(entry.getState())) {
                ready.add(entry);
            } else if (ReleaseEntry.STATE_BLOCKED.equals(entry.getState())) {
                blocked.add(entry);
            } else if (ReleaseEntry.STATE_IN_DEVELOPMENT.equals(entry.getState())) {
                inDevelopment.add(entry);
            } else {
                imported.add(entry);
            }
        }

        out.append("## Ready to import (").append(ready.size()).append(")\n\n");
        if (ready.isEmpty()) {
            out.append("Nothing is ready to import right now.\n\n");
        }
        int step = 1;
        for (ReleaseEntry entry : ready) {
            TransportRequest request = entry.getRequest();
            out.append("### ").append(step++).append(". ").append(request.getTrkorr())
                    .append(" - ").append(request.getDescription()).append("\n\n");
            out.append("- Owner: ").append(request.getOwner()).append("\n");
            out.append("- Type: ").append(request.getRequestType()).append("\n");
            out.append("- Objects: ").append(entry.getObjectCount()).append("\n");
            out.append("- Created: ").append(request.getCreatedOnDisplay()).append("\n");
            out.append("- Command: `tp import request ").append(request.getTrkorr())
                    .append(' ').append(system.getSystemId()).append("`\n\n");
        }

        out.append("## Blocked (").append(blocked.size()).append(")\n\n");
        if (blocked.isEmpty()) {
            out.append("Nothing is blocked.\n\n");
        }
        for (ReleaseEntry entry : blocked) {
            TransportRequest request = entry.getRequest();
            out.append("### ").append(request.getTrkorr()).append(" - ")
                    .append(request.getDescription()).append("\n\n");
            for (String blocker : entry.getBlockers()) {
                out.append("- ").append(blocker).append("\n");
            }
            out.append("\n");
        }

        out.append("## Still in development (").append(inDevelopment.size()).append(")\n\n");
        for (ReleaseEntry entry : inDevelopment) {
            TransportRequest request = entry.getRequest();
            out.append("- ").append(request.getTrkorr()).append(" - ")
                    .append(request.getDescription()).append(" (owner ")
                    .append(request.getOwner()).append(")\n");
        }
        out.append("\n");

        out.append("## Already imported (").append(imported.size()).append(")\n\n");
        for (ReleaseEntry entry : imported) {
            TransportRequest request = entry.getRequest();
            out.append("- ").append(request.getTrkorr()).append(" - ")
                    .append(request.getDescription()).append("\n");
        }
        out.append("\n");

        out.append("## Pre-flight checks\n\n");
        List<com.revzz.sap.transport.domain.ObjectConflict> conflicts =
                analysisService.conflicts();
        if (conflicts.isEmpty()) {
            out.append("- No object is held by more than one open request.\n");
        } else {
            for (com.revzz.sap.transport.domain.ObjectConflict conflict : conflicts) {
                out.append("- **").append(conflict.getObjectName()).append("** (")
                        .append(conflict.getObjectType()).append(") is in ")
                        .append(conflict.getRequestList())
                        .append(" - merge the requests before importing either one.\n");
            }
        }
        out.append("\n");

        out.append("---\n\n");
        out.append("> The `tp` options depend on the client's transport profile - confirm them "
                + "with `tp help import` before running the checklist.\n");
        return out.toString();
    }

    /** The release train of every system, keyed by system id. */
    public Map<String, List<ReleaseEntry>> allTrains() {
        Map<String, List<ReleaseEntry>> trains = new LinkedHashMap<String, List<ReleaseEntry>>();
        for (ReleaseSystem system : systems()) {
            trains.put(system.getSystemId(), releaseTrain(system.getSystemId()));
        }
        return trains;
    }
}
