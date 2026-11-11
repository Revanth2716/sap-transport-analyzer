package com.revzz.sap.transport.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders every screen.
 *
 * <p>A template can compile and the service layer can be fully tested while
 * one screen still returns HTTP 500, because a single expression in the
 * markup does not resolve. This test renders each page the way a browser
 * would, so that class of bug fails the build.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} renders")
    @ValueSource(strings = {
            "/",
            "/requests/K900001",
            "/requests/K900003",
            "/requests/K900004",
            "/requests/K900009",
            "/releases",
            "/releases/PRD",
            "/releases/QAS",
    })
    @DisplayName("every screen renders for the change manager")
    void everyScreenRenders(String path) throws Exception {
        mockMvc.perform(get(path).session(sessionFor("CHANGE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))))
                .andExpect(content().string(not(containsString("TemplateProcessingException"))));
    }

    @Test
    @DisplayName("the login screen renders")
    void loginRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
    }

    @Test
    @DisplayName("the overview shows the score chart and the conflict chart")
    void overviewShowsTheCharts() throws Exception {
        mockMvc.perform(get("/").session(sessionFor("CHANGE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("ZCL_PAYMENT")))
                .andExpect(content().string(containsString("K900001")));
    }

    @Test
    @DisplayName("the request screen shows the objects and the findings")
    void requestScreenShowsTheObjects() throws Exception {
        mockMvc.perform(get("/requests/K900001").session(sessionFor("CHANGE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ZCL_PAYMENT")))
                .andExpect(content().string(containsString("OBJECT_CONFLICT")))
                .andExpect(content().string(containsString("conflict")));
    }

    @Test
    @DisplayName("the release screen shows the train and the checklist")
    void releaseScreenShowsTheChecklist() throws Exception {
        mockMvc.perform(get("/releases/PRD").session(sessionFor("CHANGE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RELEASE TRAIN")))
                .andExpect(content().string(containsString("tp import request K900009 PRD")));
    }

    @Test
    @DisplayName("an unknown request shows the error page, not a stack trace")
    void unknownRequestShowsErrorPage() throws Exception {
        mockMvc.perform(get("/requests/K900999").session(sessionFor("CHANGE_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Something went wrong")));
    }

    @ParameterizedTest(name = "the viewer cannot open {0}")
    @ValueSource(strings = {"/releases", "/releases/PRD"})
    @DisplayName("the viewer role is blocked from the release train")
    void viewerIsBlocked(String path) throws Exception {
        mockMvc.perform(get(path).session(sessionFor("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page")
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String location = result.getResponse().getRedirectedUrl();
                    org.junit.jupiter.api.Assertions.assertNotNull(location);
                    org.junit.jupiter.api.Assertions.assertTrue(location.contains("/login"),
                            "expected a redirect to the login page but got " + location);
                });
    }

    @Test
    @DisplayName("the release analyst may read the train but may not release a request")
    void analystCannotReleaseARequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/requests/K900006/release")
                .session(sessionFor("ANALYST")))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession sessionFor(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUser.SESSION_ATTRIBUTE, role);
        session.setAttribute("DISPLAY_NAME", role);
        return session;
    }
}
