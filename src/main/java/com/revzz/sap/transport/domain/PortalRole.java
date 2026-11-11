package com.revzz.sap.transport.domain;

/** The portal's own roles - not SAP authorisation roles. */
public enum PortalRole {

    /** Signs in and sees the dashboard. */
    VIEWER,

    /** Everything a viewer sees, plus the request detail and the release train. */
    ANALYST,

    /** Everything, including releasing requests and exporting the checklist. */
    CHANGE_MANAGER;

    public boolean canAnalyse() {
        return this != VIEWER;
    }

    public boolean canOperate() {
        return this == CHANGE_MANAGER;
    }

    public String cssClass() {
        return name().toLowerCase();
    }
}
