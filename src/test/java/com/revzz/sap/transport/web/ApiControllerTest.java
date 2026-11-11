package com.revzz.sap.transport.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST API.
 *
 * <p>Read-only, and authenticated - the data describes which changes are on
 * their way to production, so it is not served to anyone who asks.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the health check is open")
    void healthIsOpen() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")))
                .andExpect(jsonPath("$.requests").value(9))
                .andExpect(jsonPath("$.conflicts").value(1));
    }

    @Test
    @DisplayName("the request data needs a session")
    void requestsNeedASession() throws Exception {
        mockMvc.perform(get("/api/v1/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("authentication required")));
    }

    @Test
    @DisplayName("the request data is served to a signed-in caller")
    void requestsAreServedToASignedInCaller() throws Exception {
        mockMvc.perform(get("/api/v1/requests").session(sessionFor("CHANGE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(content().string(containsString("K900001")))
                .andExpect(content().string(containsString("OBJECT_CONFLICT")));
    }

    @Test
    @DisplayName("one request can be read on its own")
    void oneRequestCanBeRead() throws Exception {
        mockMvc.perform(get("/api/v1/requests/K900009").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"score\":100")))
                .andExpect(content().string(containsString("\"severity\":\"INFO\"")));
    }

    @Test
    @DisplayName("an unknown request is a 404 with a JSON body")
    void unknownRequestIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/requests/K900999").session(sessionFor("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("no such request")));
    }

    @Test
    @DisplayName("the findings list is served")
    void findingsAreServed() throws Exception {
        mockMvc.perform(get("/api/v1/findings").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(14)))
                .andExpect(content().string(containsString("recommendation")));
    }

    @Test
    @DisplayName("the conflicts list is served")
    void conflictsAreServed() throws Exception {
        mockMvc.perform(get("/api/v1/conflicts").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(content().string(containsString("ZCL_PAYMENT")));
    }

    @Test
    @DisplayName("the release trains are served")
    void releasesAreServed() throws Exception {
        mockMvc.perform(get("/api/v1/releases").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PRD")))
                .andExpect(content().string(containsString("READY")));
    }

    @Test
    @DisplayName("one release train can be read on its own")
    void oneReleaseTrainCanBeRead() throws Exception {
        mockMvc.perform(get("/api/v1/releases/PRD").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(content().string(containsString("K900009")));
    }

    @Test
    @DisplayName("the checklist is served as markdown")
    void checklistIsServed() throws Exception {
        mockMvc.perform(get("/api/v1/releases/PRD/checklist").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("# Import checklist - PRD")));
    }

    @Test
    @DisplayName("the checklist needs a session too")
    void checklistNeedsASession() throws Exception {
        mockMvc.perform(get("/api/v1/releases/PRD/checklist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("signing in with the demo password works, and a wrong password does not")
    void signInWorks() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "change.manager")
                        .param("password", "demo1234"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/login")
                        .param("username", "change.manager")
                        .param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/login?error=invalid"));
    }

    @Test
    @DisplayName("the API never returns a stack trace")
    void apiNeverLeaksAStackTrace() throws Exception {
        mockMvc.perform(get("/api/v1/requests/K900999").session(sessionFor("ANALYST")))
                .andExpect(content().string(not(containsString("at com.revzz"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    private MockHttpSession sessionFor(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUser.SESSION_ATTRIBUTE, role);
        session.setAttribute("DISPLAY_NAME", role);
        return session;
    }
}
