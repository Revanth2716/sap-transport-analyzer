package com.revzz.sap.transport.domain;

import java.time.LocalDateTime;

/**
 * One import of one request into one system.
 *
 * <p>This is the only evidence that a request actually reached production.
 * Without it, "released" means "somebody said so", which is not the same
 * thing.</p>
 */
public class ImportRecord {

    public static final String RESULT_OK = "OK";
    public static final String RESULT_FAILED = "FAILED";

    private String importId;
    private String requestId;
    private String systemId;
    private LocalDateTime importedAt;
    private String result;
    private String message;

    public boolean isSuccessful() {
        return RESULT_OK.equals(result);
    }

    public String getImportedAtDisplay() {
        if (importedAt == null) {
            return "";
        }
        return importedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public String getImportId() {
        return importId;
    }

    public void setImportId(String importId) {
        this.importId = importId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
