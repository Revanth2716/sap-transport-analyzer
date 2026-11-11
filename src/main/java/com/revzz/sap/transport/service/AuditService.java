package com.revzz.sap.transport.service;

import com.revzz.sap.transport.domain.AuditEntry;
import com.revzz.sap.transport.repo.PlatformRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The audit trail.
 *
 * <p>Releasing a request and exporting a checklist are the two actions worth
 * remembering: the first moves code towards production, the second is the
 * moment somebody was shown what can go and was asked to act on it.</p>
 */
@Service
public class AuditService {

    private final PlatformRepository platform;

    public AuditService(PlatformRepository platform) {
        this.platform = platform;
    }

    public void record(String username, String action, String detail) {
        AuditEntry entry = new AuditEntry();
        entry.setAuditId("AU" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        entry.setUsername(username);
        entry.setAction(action);
        entry.setDetail(detail);
        entry.setAt(LocalDateTime.now());
        platform.insertAudit(entry);
    }

    public List<AuditEntry> recent(int limit) {
        return platform.findRecentAudit(limit);
    }
}
