package com.revzz.sap.transport.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the release train: a request on its way to a target system.
 *
 * <p>The state is what an administrator needs to know before the weekend —
 * can this go, or is it blocked?</p>
 */
public class ReleaseEntry {

    public static final String STATE_READY = "READY";
    public static final String STATE_BLOCKED = "BLOCKED";
    public static final String STATE_IN_DEVELOPMENT = "IN_DEVELOPMENT";
    public static final String STATE_IMPORTED = "IMPORTED";
    public static final String STATE_STALE = "STALE";

    private TransportRequest request;
    private ReleaseSystem system;
    private String state;
    private int order;
    private List<String> blockers = new ArrayList<String>();
    private int objectCount;

    public boolean isImportable() {
        return STATE_READY.equals(state);
    }

    public String getStateCssClass() {
        if (state == null) {
            return "info";
        }
        return state.toLowerCase();
    }

    public TransportRequest getRequest() {
        return request;
    }

    public void setRequest(TransportRequest request) {
        this.request = request;
    }

    public ReleaseSystem getSystem() {
        return system;
    }

    public void setSystem(ReleaseSystem system) {
        this.system = system;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public List<String> getBlockers() {
        return blockers;
    }

    public void setBlockers(List<String> blockers) {
        this.blockers = blockers;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(int objectCount) {
        this.objectCount = objectCount;
    }
}
