package com.revzz.sap.transport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * SAP Transport &amp; Change Management Analyzer.
 *
 * <p>Reads the transport management system and answers the question a release
 * manager actually has: <em>what can go into the next transport, in what
 * order, and what is stopping the rest</em>.</p>
 *
 * <p>It finds the conflicts that break an import — one object in two open
 * requests, where the second import wins and the first developer's work
 * disappears without an error — the dependencies that block a customizing
 * request, the requests nobody has touched in three months, and the objects
 * that were changed in development after the last transport went to
 * production.</p>
 */
@SpringBootApplication
public class TransportApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransportApplication.class, args);
    }

    /** Entry point for a WAR deployment on the SAP NetWeaver Java stack. */
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(TransportApplication.class);
    }
}
