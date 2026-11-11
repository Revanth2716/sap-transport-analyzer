package com.revzz.sap.transport.web;

import com.revzz.sap.transport.domain.ObjectConflict;
import com.revzz.sap.transport.domain.ReleaseEntry;
import com.revzz.sap.transport.domain.TransportAssessment;
import com.revzz.sap.transport.domain.TransportFinding;
import com.revzz.sap.transport.service.ReleaseService;
import com.revzz.sap.transport.service.TransportAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for reporting and integrations.
 *
 * <p>Read-only on purpose: the API exposes the same analysis the screens
 * show, so a release dashboard or a ticket system can consume it without
 * scraping HTML.</p>
 *
 * <p>Every endpoint except the health check needs a signed-in session. The
 * data describes which changes are on their way to production, so it is not
 * something to serve to anyone who asks.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final TransportAnalysisService analysisService;
    private final ReleaseService releaseService;

    public ApiController(TransportAnalysisService analysisService, ReleaseService releaseService) {
        this.analysisService = analysisService;
        this.releaseService = releaseService;
    }

    /** The health check stays open - a load balancer has to be able to reach it. */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        Map<String, Object> health = new LinkedHashMap<String, Object>();
        health.put("status", "UP");
        health.put("requests", analysisService.requests().size());
        health.put("conflicts", analysisService.conflicts().size());
        health.put("averageScore", analysisService.averageScore());
        return health;
    }

    @GetMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> requests(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(analysisService.assessments());
    }

    @GetMapping(value = "/requests/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> request(@PathVariable String id, HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        TransportAssessment assessment = analysisService.assess(id);
        if (assessment == null) {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("error", "no such request");
            body.put("request", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        return ResponseEntity.ok(assessment);
    }

    @GetMapping(value = "/findings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> findings(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(analysisService.allFindings());
    }

    @GetMapping(value = "/conflicts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> conflicts(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(analysisService.conflicts());
    }

    @GetMapping(value = "/releases", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> releases(HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(releaseService.allTrains());
    }

    @GetMapping(value = "/releases/{systemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> release(@PathVariable String systemId, HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(releaseService.releaseTrain(systemId));
    }

    /**
     * The import checklist of one system, as Markdown.
     *
     * <p>The content type is set on the response rather than with
     * {@code produces}, so that the 401 body can still be written as JSON -
     * otherwise an anonymous caller would get a 500 instead of a 401.</p>
     */
    @GetMapping("/releases/{systemId}/checklist")
    public ResponseEntity<Object> checklist(@PathVariable String systemId, HttpSession session) {
        ResponseEntity<Object> denied = requireSession(session);
        if (denied != null) {
            return denied;
        }
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown",
                java.nio.charset.StandardCharsets.UTF_8));
        return new ResponseEntity<Object>(releaseService.checklist(systemId), headers,
                HttpStatus.OK);
    }

    /**
     * Rejects an anonymous caller.
     *
     * @return null when the caller is signed in, otherwise a 401 with a JSON body
     */
    private ResponseEntity<Object> requireSession(HttpSession session) {
        if (CurrentUser.isSignedIn(session)) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", "authentication required");
        body.put("hint", "sign in at /login and send the session cookie");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
