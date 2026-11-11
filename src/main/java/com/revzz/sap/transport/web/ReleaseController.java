package com.revzz.sap.transport.web;

import com.revzz.sap.transport.domain.ReleaseEntry;
import com.revzz.sap.transport.domain.ReleaseSystem;
import com.revzz.sap.transport.service.AuditService;
import com.revzz.sap.transport.service.ReleaseService;
import com.revzz.sap.transport.support.TransportChartRenderer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The release train and the import checklist.
 *
 * <p>The train is the document a release manager works from: what can go,
 * in what order, and what is stopping the rest. The checklist is the same
 * thing as something to carry into the transport window.</p>
 */
@Controller
@RequireRole({"ANALYST", "CHANGE_MANAGER"})
public class ReleaseController {

    private final ReleaseService releaseService;
    private final AuditService auditService;

    public ReleaseController(ReleaseService releaseService, AuditService auditService) {
        this.releaseService = releaseService;
        this.auditService = auditService;
    }

    @GetMapping("/releases")
    public String releases(Model model) {
        List<ReleaseSystem> systems = releaseService.systems();
        model.addAttribute("systems", systems);
        model.addAttribute("trains", releaseService.allTrains());
        return "releases";
    }

    @GetMapping("/releases/{systemId}")
    public String release(@PathVariable String systemId, Model model,
                          HttpServletRequest request) {
        ReleaseSystem system = null;
        for (ReleaseSystem candidate : releaseService.systems()) {
            if (candidate.getSystemId().equals(systemId)) {
                system = candidate;
            }
        }
        if (system == null) {
            model.addAttribute("error", "There is no system called " + systemId + ".");
            return "error";
        }

        List<ReleaseEntry> train = releaseService.releaseTrain(systemId);
        String checklist = releaseService.checklist(systemId);

        model.addAttribute("system", system);
        model.addAttribute("train", train);
        model.addAttribute("trainChart", TransportChartRenderer.releaseTrain(train));
        model.addAttribute("checklist", checklist);
        model.addAttribute("lineCount", checklist.split("\n").length);

        auditService.record(CurrentUser.username(request.getSession(false)), "RELEASE_TRAIN_VIEWED",
                "Release train of " + systemId + " opened");
        return "release";
    }

    /** The import checklist as a downloadable Markdown document. */
    @GetMapping("/releases/{systemId}/checklist.md")
    public ResponseEntity<String> checklist(@PathVariable String systemId,
                                             HttpServletRequest request) {
        String checklist = releaseService.checklist(systemId);

        auditService.record(CurrentUser.username(request.getSession(false)), "CHECKLIST_EXPORTED",
                "Import checklist of " + systemId + " exported as Markdown");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment",
                "import-checklist-" + systemId + ".md");
        return new ResponseEntity<String>(checklist, headers, HttpStatus.OK);
    }
}
