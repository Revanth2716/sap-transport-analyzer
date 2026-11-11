package com.revzz.sap.transport.domain;

/**
 * A dependency edge between two transport requests.
 *
 * <p>Two kinds, and they mean different things:</p>
 * <ul>
 *   <li>{@code TASK_PARENT} — the task belongs to the workbench request, and
 *       cannot be imported until the parent is.</li>
 *   <li>{@code CONFIG_REFERENCE} — the customizing request references the
 *       workbench request that carries the objects it configures.</li>
 * </ul>
 */
public class TransportDependency {

    public static final String KIND_TASK_PARENT = "TASK_PARENT";
    public static final String KIND_CONFIG_REFERENCE = "CONFIG_REFERENCE";

    private String dependencyId;
    private String requestId;
    private String dependsOn;
    private String kind;

    public String getDependencyId() {
        return dependencyId;
    }

    public void setDependencyId(String dependencyId) {
        this.dependencyId = dependencyId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(String dependsOn) {
        this.dependsOn = dependsOn;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
