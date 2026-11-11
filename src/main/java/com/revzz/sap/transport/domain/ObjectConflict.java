package com.revzz.sap.transport.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One object that is held by more than one open request.
 *
 * <p>This is the conflict. Two developers changed the same object in two
 * requests; the second import overwrites the first, and the first developer's
 * work disappears without an error.</p>
 */
public class ObjectConflict {

    private String objectKey;
    private String objectType;
    private String objectName;
    private List<String> requestIds = new ArrayList<String>();
    private LocalDateTime lastChanged;

    public int requestCount() {
        return requestIds.size();
    }

    public String getRequestList() {
        StringBuilder out = new StringBuilder();
        for (String id : requestIds) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(id);
        }
        return out.toString();
    }

    public String getLastChangedDisplay() {
        if (lastChanged == null) {
            return "";
        }
        return lastChanged.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
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

    public List<String> getRequestIds() {
        return requestIds;
    }

    public void setRequestIds(List<String> requestIds) {
        this.requestIds = requestIds;
    }

    public LocalDateTime getLastChanged() {
        return lastChanged;
    }

    public void setLastChanged(LocalDateTime lastChanged) {
        this.lastChanged = lastChanged;
    }
}
