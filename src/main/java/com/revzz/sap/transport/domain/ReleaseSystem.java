package com.revzz.sap.transport.domain;

/** One system in the transport landscape. */
public class ReleaseSystem {

    public static final String ROLE_DEVELOPMENT = "DEVELOPMENT";
    public static final String ROLE_QUALITY = "QUALITY";
    public static final String ROLE_PRODUCTION = "PRODUCTION";

    private String systemId;
    private String description;
    private String systemRole;
    private boolean target;

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
    }

    public boolean isTarget() {
        return target;
    }

    public void setTarget(boolean target) {
        this.target = target;
    }
}
