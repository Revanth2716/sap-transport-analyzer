package com.revzz.sap.transport.domain;

import java.time.LocalDateTime;

/**
 * One object inside a transport request.
 *
 * <p>The {@code objectKey} is the conflict key: name plus type. Two open
 * requests holding the same key are the conflict that breaks an import,
 * because the second import overwrites the first.</p>
 */
public class TransportObject {

    private String objectId;
    private String requestId;
    private String programId;
    private String objectType;
    private String objectName;
    private String objectKey;
    private String fieldName;
    private LocalDateTime lastChanged;

    public boolean isCustomizingEntry() {
        return fieldName != null && !fieldName.trim().isEmpty();
    }

    public String getLastChangedDisplay() {
        if (lastChanged == null) {
            return "";
        }
        return lastChanged.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getProgramId() {
        return programId;
    }

    public void setProgramId(String programId) {
        this.programId = programId;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public LocalDateTime getLastChanged() {
        return lastChanged;
    }

    public void setLastChanged(LocalDateTime lastChanged) {
        this.lastChanged = lastChanged;
    }
}
