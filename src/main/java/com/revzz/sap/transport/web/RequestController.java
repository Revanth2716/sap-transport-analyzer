package com.revzz.sap.transport.web;

import com.revzz.sap.transport.domain.TransportAssessment;
import com.revzz.sap.transport.domain.TransportRequest;
import com.revzz.sap.transport.repo.TransportRepository;
import com.revzz.sap.transport.service.AuditService;
import com.revzz.sap.transport.service.TransportAnalysisService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * One transport request, in detail.
 *
 * <p>The detail screen is where a BASIS team actually works: the objects it
 * carries, the other requests that hold the same objects, the requests it
 * depends on, and the import history that proves whether it ever arrived.
 * It is also the only place a request can be released - releasing is the
 * write path of this application.</p>
 */
@Controller
public class RequestController {

    private final TransportAnalysisService analysisService;
    private final TransportRepository repository;
    private final AuditService auditService;

    public RequestController(TransportAnalysisService analysisService,
                             TransportRepository repository,
                             AuditService auditService) {
        this.analysisService = analysisService;
        this.repository = repository;
        this.auditService = auditService;
    }

    @GetMapping("/requests/{id}")
    public String request(@PathVariable String id, Model model) {
        TransportAssessment assessment = analysisService.assess(id);
        if (assessment == null) {
            model.addAttribute("error", "There is no transport request called " + id + ".");
            return "error";
        }
        model.addAttribute("assessment", assessment);
        model.addAttribute("request", assessment.getRequest());
        return "request";
    }

    /**
     * Releases a request for transport.
     *
     * <p>Releasing is the moment a change is declared ready to move to the
     * next system, so it is written to the audit trail.</p>
     */
    @PostMapping("/requests/{id}/release")
    @RequireRole("CHANGE_MANAGER")
    public String release(@PathVariable String id, Model model, HttpServletRequest request) {
        TransportAssessment assessment = analysisService.assess(id);
        if (assessment == null) {
            model.addAttribute("error", "There is no transport request called " + id + ".");
            return "error";
        }
        TransportRequest target = assessment.getRequest();
        if (target.isReleased()) {
            return "redirect:/requests/" + id + "?already=1";
        }

        repository.updateStatus(id, TransportRequest.STATUS_RELEASED, "Released for transport");
        auditService.record(CurrentUser.username(request.getSession(false)), "REQUEST_RELEASED",
                id + " released for transport to " + target.getTargetSystem());
        return "redirect:/requests/" + id + "?released=1";
    }
}
