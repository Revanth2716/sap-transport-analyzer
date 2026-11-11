package com.revzz.sap.transport.domain;

import java.time.LocalDateTime;

/**
 * One transport request.
 *
 * <p>The status codes are the ones the transport system uses, because that is
 * what an administrator sees in SE09: <b>D</b> in development, <b>R</b>
 * released, <b>L</b> released for transport, <b>O</b> imported.</p>
 */
public class TransportRequest {

    public static final String TYPE_WORKBENCH = "WORKBENCH";
    public static final String TYPE_CUSTOMIZING = "CUSTOMIZING";
    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_TASK_CONTAINER = "TASK_CONTAINER";

    public static final String STATUS_IN_DEVELOPMENT = "D";
    public static final String STATUS_RELEASED = "R";
    public static final String STATUS_RELEASED_FOR_TRANSPORT = "L";
    public static final String STATUS_IMPORTED = "O";

    private String trkorr;
    private String description;
    private String requestType;
    private String status;
    private String statusText;
    private String owner;
    private LocalDateTime createdOn;
    private String targetSystem;
    private String sourceSystem;
    private String parentRequest;

    public boolean isTask() {
        return TYPE_TASK.equals(requestType);
    }

    public boolean isWorkbench() {
        return TYPE_WORKBENCH.equals(requestType);
    }

    public boolean isCustomizing() {
        return TYPE_CUSTOMIZING.equals(requestType);
    }

    /** Released, and therefore a candidate for the next transport. */
    public boolean isReleased() {
        return STATUS_RELEASED.equals(status) || STATUS_RELEASED_FOR_TRANSPORT.equals(status);
    }

    public boolean isInDevelopment() {
        return STATUS_IN_DEVELOPMENT.equals(status);
    }

    public boolean isImported() {
        return STATUS_IMPORTED.equals(status);
    }

    /** Still open - not yet imported anywhere. */
    public boolean isOpen() {
        return !isImported();
    }

    public int ageDays(LocalDateTime now) {
        if (createdOn == null) {
            return 0;
        }
        return (int) java.time.Duration.between(createdOn, now).toDays();
    }

    public String getCreatedOnDisplay() {
        if (createdOn == null) {
            return "";
        }
        return createdOn.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public String getTrkorr() {
        return trkorr;
    }

    public void setTrkorr(String trkorr) {
        this.trkorr = trkorr;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(String targetSystem) {
        this.targetSystem = targetSystem;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getParentRequest() {
        return parentRequest;
    }

    public void setParentRequest(String parentRequest) {
        this.parentRequest = parentRequest;
    }
}
