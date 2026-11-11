-- ---------------------------------------------------------------------------
-- SAP Transport & Change Management Analyzer - database schema
--
-- The change and transport picture a BASIS team is accountable for:
--
--   TMS_SYSTEM    the landscape - which system is development, which is
--                 quality, which is the production import target
--   TMS_REQUEST   one transport request: its type, its status, its owner, what
--                 it targets and when it was created
--   TMS_OBJECT    the objects inside a request - the thing that actually
--                 conflicts when two requests touch the same object
--   TMS_IMPORT    what was imported where and when, which is the only
--                 evidence that a request actually reached production
--
-- In a live landscape the request rows come from the transport management
-- system (SE09 / SE10 / the E070* tables) and the object list from E070LIN
-- and TRDIR; the import log is the transport log written by tp.
--
-- All statements are portable ANSI SQL: valid on H2 (local) and SAP HANA.
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS TMS_IMPORT;
DROP TABLE IF EXISTS TMS_OBJECT;
DROP TABLE IF EXISTS TMS_DEPENDENCY;
DROP TABLE IF EXISTS TMS_REQUEST;
DROP TABLE IF EXISTS TMS_SYSTEM;
DROP TABLE IF EXISTS PORTAL_AUDIT;
DROP TABLE IF EXISTS PORTAL_USER;
DROP TABLE IF EXISTS SAPUSER;

CREATE TABLE SAPUSER (
    USERID   VARCHAR(20) NOT NULL,
    FULLNAME VARCHAR(60) NOT NULL,
    UFLAG    VARCHAR(1)  NOT NULL,
    DEPT     VARCHAR(40) NOT NULL,
    CONSTRAINT PK_TRM_SAPUSER PRIMARY KEY (USERID)
);

-- The landscape: which system receives the transports.
CREATE TABLE TMS_SYSTEM (
    SYSTEM_ID   VARCHAR(10) NOT NULL,
    DESCRIPTION VARCHAR(60) NOT NULL,
    SYS_ROLE    VARCHAR(20) NOT NULL,   -- DEVELOPMENT / QUALITY / PRODUCTION
    IS_TARGET   VARCHAR(1)  NOT NULL,   -- Y / N
    CONSTRAINT PK_TRM_TMS_SYSTEM PRIMARY KEY (SYSTEM_ID)
);

-- One row per transport request.
CREATE TABLE TMS_REQUEST (
    TRKORR         VARCHAR(12) NOT NULL,   -- the request number, e.g. K900001
    DESCRIPTION    VARCHAR(120) NOT NULL,
    REQ_TYPE       VARCHAR(16) NOT NULL,   -- WORKBENCH / CUSTOMIZING / TASK / TASK_CONTAINER
    TRSTATUS       VARCHAR(2)  NOT NULL,   -- D / R / L / O
    STATUS_TEXT    VARCHAR(40) NOT NULL,
    TRUSER         VARCHAR(20) NOT NULL,   -- the owner
    TRDATE         TIMESTAMP   NOT NULL,   -- when it was created
    TARGET_SYSTEM  VARCHAR(10) NOT NULL,   -- where it goes
    SOURCE_SYSTEM  VARCHAR(10) NOT NULL,   -- where it came from
    PARENT_REQUEST VARCHAR(12) NOT NULL,   -- the workbench request of a task
    CONSTRAINT PK_TRM_TMS_REQUEST PRIMARY KEY (TRKORR),
    CONSTRAINT FK_TRM_TMS_REQUEST FOREIGN KEY (TARGET_SYSTEM) REFERENCES TMS_SYSTEM (SYSTEM_ID)
);

-- The objects inside a request. Two requests holding the same OBJ_KEY is the
-- conflict that breaks an import.
CREATE TABLE TMS_OBJECT (
    OBJ_ID      VARCHAR(20) NOT NULL,
    TRKORR      VARCHAR(12) NOT NULL,
    PGMID       VARCHAR(4)  NOT NULL,   -- R / WS / C / S / T
    OBJ_TYPE    VARCHAR(8)  NOT NULL,   -- PROG / TABL / CLAS / ENQU / XSLT ...
    OBJ_NAME    VARCHAR(60) NOT NULL,
    OBJ_KEY     VARCHAR(90) NOT NULL,   -- name + type, the conflict key
    FIELD_NAME  VARCHAR(40) NOT NULL,   -- the customizing field, if any
    LAST_CHANGED TIMESTAMP  NOT NULL,
    CONSTRAINT PK_TRM_TMS_OBJECT PRIMARY KEY (OBJ_ID),
    CONSTRAINT FK_TRM_TMS_OBJECT FOREIGN KEY (TRKORR) REFERENCES TMS_REQUEST (TRKORR)
);

-- The dependency edges between requests: a task depends on its workbench
-- request, and a customizing request depends on the workbench request whose
-- objects it references.
CREATE TABLE TMS_DEPENDENCY (
    DEP_ID     VARCHAR(20) NOT NULL,
    REQUEST_ID VARCHAR(12) NOT NULL,
    DEPENDS_ON VARCHAR(12) NOT NULL,
    DEP_KIND   VARCHAR(20) NOT NULL,   -- TASK_PARENT / CONFIG_REFERENCE
    CONSTRAINT PK_TRM_TMS_DEP PRIMARY KEY (DEP_ID)
);

-- What was imported where and when.
CREATE TABLE TMS_IMPORT (
    IMP_ID     VARCHAR(20) NOT NULL,
    TRKORR     VARCHAR(12) NOT NULL,
    SYSTEM_ID  VARCHAR(10) NOT NULL,
    IMPORTED_AT TIMESTAMP  NOT NULL,
    RESULT     VARCHAR(8)  NOT NULL,   -- OK / FAILED
    MESSAGE    VARCHAR(200) NOT NULL,
    CONSTRAINT PK_TRM_TMS_IMPORT PRIMARY KEY (IMP_ID)
);

-- Portal accounts and the audit trail.
CREATE TABLE PORTAL_USER (
    USERNAME      VARCHAR(40) NOT NULL,
    PASSWORD_HASH VARCHAR(120) NOT NULL,
    DISPLAY_NAME  VARCHAR(60) NOT NULL,
    PORTAL_ROLE   VARCHAR(20) NOT NULL,
    ENABLED       VARCHAR(1)  NOT NULL,
    CONSTRAINT PK_TRM_PORTAL_USER PRIMARY KEY (USERNAME)
);

CREATE TABLE PORTAL_AUDIT (
    AUD_ID     VARCHAR(20) NOT NULL,
    USERNAME   VARCHAR(40) NOT NULL,
    ACTION     VARCHAR(40) NOT NULL,
    DETAIL_TXT VARCHAR(400) NOT NULL,
    AT_TS      TIMESTAMP   NOT NULL,
    CONSTRAINT PK_TRM_PORTAL_AUDIT PRIMARY KEY (AUD_ID)
);
