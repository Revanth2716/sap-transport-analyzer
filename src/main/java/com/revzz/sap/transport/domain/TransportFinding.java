package com.revzz.sap.transport.domain;

/**
 * A transport finding.
 *
 * <p>Every finding carries the recommended action, because "two requests hold
 * the same object" is not something anybody can act on at 09:00.</p>
 */
public class TransportFinding {

    private Severity severity;
    private String category;
    private String requestId;
    private String message;
    private String recommendation;

    public TransportFinding() {
    }

    public TransportFinding(Severity severity, String category, String requestId,
                            String message, String recommendation) {
        this.severity = severity;
        this.category = category;
        this.requestId = requestId;
        this.message = message;
        this.recommendation = recommendation;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }
}
