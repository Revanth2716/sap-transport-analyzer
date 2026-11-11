package com.revzz.sap.transport.service;

import com.revzz.sap.transport.domain.ImportRecord;
import com.revzz.sap.transport.domain.TransportDependency;
import com.revzz.sap.transport.domain.TransportObject;
import com.revzz.sap.transport.domain.TransportRequest;
import com.revzz.sap.transport.repo.TransportRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A consistent view of the whole transport landscape, loaded once.
 *
 * <p>Conflict detection needs to know which <em>other</em> requests hold the
 * same object, and the dependency analysis needs the whole graph. Loading
 * that per request would be an N+1 query per finding, so the landscape is
 * loaded once and every assessment reads from it.</p>
 */
public class TransportSnapshot {

    private final List<TransportRequest> requests;
    private final Map<String, TransportRequest> byId;
    private final Map<String, List<TransportObject>> objectsByRequest;
    private final Map<String, List<ImportRecord>> importsByRequest;
    private final Map<String, List<TransportDependency>> dependenciesByRequest;
    private final Map<String, List<String>> openRequestsByObjectKey;
    private final Map<String, List<String>> tasksByParent;

    private TransportSnapshot(List<TransportRequest> requests,
                              Map<String, List<TransportObject>> objectsByRequest,
                              Map<String, List<ImportRecord>> importsByRequest,
                              Map<String, List<TransportDependency>> dependenciesByRequest) {
        this.requests = requests;
        this.objectsByRequest = objectsByRequest;
        this.importsByRequest = importsByRequest;
        this.dependenciesByRequest = dependenciesByRequest;

        this.byId = new LinkedHashMap<String, TransportRequest>();
        for (TransportRequest request : requests) {
            byId.put(request.getTrkorr(), request);
        }

        // Which OPEN requests hold each object key. An imported request does
        // not conflict with anything - its objects are already in the target.
        this.openRequestsByObjectKey = new HashMap<String, List<String>>();
        for (TransportRequest request : requests) {
            if (!request.isOpen()) {
                continue;
            }
            List<TransportObject> objects = objectsByRequest.get(request.getTrkorr());
            if (objects == null) {
                continue;
            }
            for (TransportObject object : objects) {
                List<String> holders = openRequestsByObjectKey.get(object.getObjectKey());
                if (holders == null) {
                    holders = new ArrayList<String>();
                    openRequestsByObjectKey.put(object.getObjectKey(), holders);
                }
                if (!holders.contains(request.getTrkorr())) {
                    holders.add(request.getTrkorr());
                }
            }
        }

        this.tasksByParent = new HashMap<String, List<String>>();
        for (TransportRequest request : requests) {
            if (!request.isTask()) {
                continue;
            }
            String parent = request.getParentRequest();
            if (parent == null || parent.trim().isEmpty()) {
                continue;
            }
            List<String> tasks = tasksByParent.get(parent);
            if (tasks == null) {
                tasks = new ArrayList<String>();
                tasksByParent.put(parent, tasks);
            }
            tasks.add(request.getTrkorr());
        }
    }

    public static TransportSnapshot load(TransportRepository repository) {
        List<TransportRequest> requests = repository.findRequests();

        Map<String, List<TransportObject>> objectsByRequest =
                new HashMap<String, List<TransportObject>>();
        for (TransportObject object : repository.findAllObjects()) {
            List<TransportObject> list = objectsByRequest.get(object.getRequestId());
            if (list == null) {
                list = new ArrayList<TransportObject>();
                objectsByRequest.put(object.getRequestId(), list);
            }
            list.add(object);
        }

        Map<String, List<ImportRecord>> importsByRequest =
                new HashMap<String, List<ImportRecord>>();
        for (ImportRecord record : repository.findAllImports()) {
            List<ImportRecord> list = importsByRequest.get(record.getRequestId());
            if (list == null) {
                list = new ArrayList<ImportRecord>();
                importsByRequest.put(record.getRequestId(), list);
            }
            list.add(record);
        }

        Map<String, List<TransportDependency>> dependenciesByRequest =
                new HashMap<String, List<TransportDependency>>();
        for (TransportDependency dependency : repository.findAllDependencies()) {
            List<TransportDependency> list =
                    dependenciesByRequest.get(dependency.getRequestId());
            if (list == null) {
                list = new ArrayList<TransportDependency>();
                dependenciesByRequest.put(dependency.getRequestId(), list);
            }
            list.add(dependency);
        }

        return new TransportSnapshot(requests, objectsByRequest, importsByRequest,
                dependenciesByRequest);
    }

    public List<TransportRequest> requests() {
        return requests;
    }

    public TransportRequest request(String trkorr) {
        return byId.get(trkorr);
    }

    public List<TransportObject> objects(String requestId) {
        List<TransportObject> list = objectsByRequest.get(requestId);
        return list == null ? Collections.<TransportObject>emptyList() : list;
    }

    public List<ImportRecord> imports(String requestId) {
        List<ImportRecord> list = importsByRequest.get(requestId);
        return list == null ? Collections.<ImportRecord>emptyList() : list;
    }

    public List<TransportDependency> dependencies(String requestId) {
        List<TransportDependency> list = dependenciesByRequest.get(requestId);
        return list == null ? Collections.<TransportDependency>emptyList() : list;
    }

    /** The request ids of the other open requests that hold the same object. */
    public List<String> otherHolders(String objectKey, String requestId) {
        List<String> holders = openRequestsByObjectKey.get(objectKey);
        if (holders == null) {
            return Collections.emptyList();
        }
        List<String> others = new ArrayList<String>();
        for (String holder : holders) {
            if (!holder.equals(requestId)) {
                others.add(holder);
            }
        }
        return others;
    }

    /** Every object key that is held by more than one open request. */
    public List<String> conflictingObjectKeys() {
        List<String> keys = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : openRequestsByObjectKey.entrySet()) {
            if (entry.getValue().size() > 1) {
                keys.add(entry.getKey());
            }
        }
        Collections.sort(keys);
        return keys;
    }

    /** The first object found under an object key. */
    public TransportObject objectByKey(String objectKey) {
        for (TransportRequest request : requests) {
            for (TransportObject object : objects(request.getTrkorr())) {
                if (objectKey.equals(object.getObjectKey())) {
                    return object;
                }
            }
        }
        return null;
    }

    /** The open request ids holding an object key. */
    public List<String> holders(String objectKey) {
        List<String> holders = openRequestsByObjectKey.get(objectKey);
        return holders == null ? Collections.<String>emptyList() : holders;
    }

    /** The tasks that belong to a workbench request. */
    public List<String> tasksOf(String requestId) {
        List<String> tasks = tasksByParent.get(requestId);
        return tasks == null ? Collections.<String>emptyList() : tasks;
    }

    /** True when the request was imported into at least one system. */
    public boolean importedAnywhere(String requestId) {
        for (ImportRecord record : imports(requestId)) {
            if (record.isSuccessful()) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the request was imported into one specific system.
     *
     * <p>This is the check that matters for a dependency. A workbench request
     * that reached QA but not PRD does not unblock a customizing request that
     * is going to PRD - the target system is what matters, not the
     * landscape.</p>
     */
    public boolean importedInto(String requestId, String systemId) {
        for (ImportRecord record : imports(requestId)) {
            if (record.isSuccessful() && systemId.equals(record.getSystemId())) {
                return true;
            }
        }
        return false;
    }

    /** The newest successful import of a request, or null. */
    public ImportRecord lastSuccessfulImport(String requestId) {
        ImportRecord newest = null;
        for (ImportRecord record : imports(requestId)) {
            if (!record.isSuccessful()) {
                continue;
            }
            if (newest == null || record.getImportedAt().isAfter(newest.getImportedAt())) {
                newest = record;
            }
        }
        return newest;
    }

    /** The newest import attempt of a request, successful or not. */
    public ImportRecord lastImport(String requestId) {
        ImportRecord newest = null;
        for (ImportRecord record : imports(requestId)) {
            if (newest == null || record.getImportedAt().isAfter(newest.getImportedAt())) {
                newest = record;
            }
        }
        return newest;
    }
}
