package com.revzz.sap.transport.web;

import com.revzz.sap.transport.domain.ObjectConflict;
import com.revzz.sap.transport.domain.Severity;
import com.revzz.sap.transport.domain.TransportAssessment;
import com.revzz.sap.transport.domain.TransportFinding;
import com.revzz.sap.transport.service.AuditService;
import com.revzz.sap.transport.service.TransportAnalysisService;
import com.revzz.sap.transport.support.TransportChartRenderer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * The transport dashboard.
 *
 * <p>The first screen answers one question: what is going to break the next
 * transport, and what has to be cleared before the release window opens.</p>
 */
@Controller
public class DashboardController {

    private final TransportAnalysisService analysisService;
    private final AuditService auditService;

    public DashboardController(TransportAnalysisService analysisService,
                               AuditService auditService) {
        this.analysisService = analysisService;
        this.auditService = auditService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<TransportAssessment> assessments = analysisService.assessments();
        List<TransportFinding> findings = analysisService.allFindings();
        List<ObjectConflict> conflicts = analysisService.conflicts();

        model.addAttribute("assessments", assessments);
        model.addAttribute("findings", findings);
        model.addAttribute("conflicts", conflicts);
        model.addAttribute("scoreChart", TransportChartRenderer.requestBars(assessments));
        model.addAttribute("conflictChart", TransportChartRenderer.conflictBars(conflicts));
        model.addAttribute("requestCount", assessments.size());
        model.addAttribute("conflictCount", conflicts.size());
        model.addAttribute("criticalCount", analysisService.countBySeverity(Severity.CRITICAL));
        model.addAttribute("highCount", analysisService.countBySeverity(Severity.HIGH));
        model.addAttribute("averageScore", analysisService.averageScore());
        model.addAttribute("findingCount", findings.size());
        model.addAttribute("audit", auditService.recent(8));
        return "overview";
    }
}
