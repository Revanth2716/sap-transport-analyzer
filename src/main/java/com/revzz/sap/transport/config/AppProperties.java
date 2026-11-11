package com.revzz.sap.transport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application configuration.
 *
 * <p>The policy lives here, not in the code: how old a request may get before
 * it is called forgotten, how long a request may stay in development, and
 * how stale the evidence of an import may be. These are the numbers a
 * release manager agrees with the development team, so they belong in
 * configuration.</p>
 */
@Component
@ConfigurationProperties(prefix = "transport")
public class AppProperties {

    private String applicationName = "SAP Transport & Change Management Analyzer";
    private String sapSystem = "TMS";
    private boolean seedDemoUsers = true;
    private boolean seedDemoData = true;
    private String demoPassword = "demo1234";
    private String gateway = "local";
    private Seed seed = new Seed();
    private Policy policy = new Policy();
    private Mail mail = new Mail();

    public static class Seed {
        private int historyDays = 180;
        private long randomSeed = 20261111L;

        public int getHistoryDays() {
            return historyDays;
        }

        public void setHistoryDays(int historyDays) {
            this.historyDays = historyDays;
        }

        public long getRandomSeed() {
            return randomSeed;
        }

        public void setRandomSeed(long randomSeed) {
            this.randomSeed = randomSeed;
        }
    }

    public static class Policy {
        private int staleRequestDays = 30;
        private int unreleasedRequestDays = 60;
        private int modifiedAfterImportDays = 1;

        public int getStaleRequestDays() {
            return staleRequestDays;
        }

        public void setStaleRequestDays(int staleRequestDays) {
            this.staleRequestDays = staleRequestDays;
        }

        public int getUnreleasedRequestDays() {
            return unreleasedRequestDays;
        }

        public void setUnreleasedRequestDays(int unreleasedRequestDays) {
            this.unreleasedRequestDays = unreleasedRequestDays;
        }

        public int getModifiedAfterImportDays() {
            return modifiedAfterImportDays;
        }

        public void setModifiedAfterImportDays(int modifiedAfterImportDays) {
            this.modifiedAfterImportDays = modifiedAfterImportDays;
        }
    }

    public static class Mail {
        private boolean enabled = false;
        private String from = "transport-analyzer@example.com";
        private String to = "basis-team@example.com";
        private String subjectPrefix = "[SAP Transport]";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getSubjectPrefix() {
            return subjectPrefix;
        }

        public void setSubjectPrefix(String subjectPrefix) {
            this.subjectPrefix = subjectPrefix;
        }
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getSapSystem() {
        return sapSystem;
    }

    public void setSapSystem(String sapSystem) {
        this.sapSystem = sapSystem;
    }

    public boolean isSeedDemoUsers() {
        return seedDemoUsers;
    }

    public void setSeedDemoUsers(boolean seedDemoUsers) {
        this.seedDemoUsers = seedDemoUsers;
    }

    public boolean isSeedDemoData() {
        return seedDemoData;
    }

    public void setSeedDemoData(boolean seedDemoData) {
        this.seedDemoData = seedDemoData;
    }

    public String getDemoPassword() {
        return demoPassword;
    }

    public void setDemoPassword(String demoPassword) {
        this.demoPassword = demoPassword;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public Seed getSeed() {
        return seed;
    }

    public void setSeed(Seed seed) {
        this.seed = seed;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }
}
