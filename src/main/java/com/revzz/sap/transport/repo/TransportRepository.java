package com.revzz.sap.transport.repo;

import com.revzz.sap.transport.domain.ImportRecord;
import com.revzz.sap.transport.domain.ReleaseSystem;
import com.revzz.sap.transport.domain.TransportDependency;
import com.revzz.sap.transport.domain.TransportObject;
import com.revzz.sap.transport.domain.TransportRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * The transport data: the requests, the objects inside them, the dependency
 * edges and the import history.
 *
 * <p>All statements are portable ANSI SQL, so the same repository runs
 * against the local H2 database and against a live SAP HANA instance.</p>
 */
@Repository
public class TransportRepository {

    private final JdbcTemplate jdbc;

    public TransportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<TransportRequest> REQUEST_MAPPER =
            new RowMapper<TransportRequest>() {
                @Override
                public TransportRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
                    TransportRequest request = new TransportRequest();
                    request.setTrkorr(rs.getString("TRKORR"));
                    request.setDescription(rs.getString("DESCRIPTION"));
                    request.setRequestType(rs.getString("REQ_TYPE"));
                    request.setStatus(rs.getString("TRSTATUS"));
                    request.setStatusText(rs.getString("STATUS_TEXT"));
                    request.setOwner(rs.getString("TRUSER"));
                    Timestamp created = rs.getTimestamp("TRDATE");
                    request.setCreatedOn(created == null ? null : created.toLocalDateTime());
                    request.setTargetSystem(rs.getString("TARGET_SYSTEM"));
                    request.setSourceSystem(rs.getString("SOURCE_SYSTEM"));
                    request.setParentRequest(rs.getString("PARENT_REQUEST"));
                    return request;
                }
            };

    private static final RowMapper<TransportObject> OBJECT_MAPPER =
            new RowMapper<TransportObject>() {
                @Override
                public TransportObject mapRow(ResultSet rs, int rowNum) throws SQLException {
                    TransportObject object = new TransportObject();
                    object.setObjectId(rs.getString("OBJ_ID"));
                    object.setRequestId(rs.getString("TRKORR"));
                    object.setProgramId(rs.getString("PGMID"));
                    object.setObjectType(rs.getString("OBJ_TYPE"));
                    object.setObjectName(rs.getString("OBJ_NAME"));
                    object.setObjectKey(rs.getString("OBJ_KEY"));
                    object.setFieldName(rs.getString("FIELD_NAME"));
                    Timestamp changed = rs.getTimestamp("LAST_CHANGED");
                    object.setLastChanged(changed == null ? null : changed.toLocalDateTime());
                    return object;
                }
            };

    private static final RowMapper<ImportRecord> IMPORT_MAPPER =
            new RowMapper<ImportRecord>() {
                @Override
                public ImportRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
                    ImportRecord record = new ImportRecord();
                    record.setImportId(rs.getString("IMP_ID"));
                    record.setRequestId(rs.getString("TRKORR"));
                    record.setSystemId(rs.getString("SYSTEM_ID"));
                    Timestamp at = rs.getTimestamp("IMPORTED_AT");
                    record.setImportedAt(at == null ? null : at.toLocalDateTime());
                    record.setResult(rs.getString("RESULT"));
                    record.setMessage(rs.getString("MESSAGE"));
                    return record;
                }
            };

    private static final RowMapper<TransportDependency> DEPENDENCY_MAPPER =
            new RowMapper<TransportDependency>() {
                @Override
                public TransportDependency mapRow(ResultSet rs, int rowNum) throws SQLException {
                    TransportDependency dependency = new TransportDependency();
                    dependency.setDependencyId(rs.getString("DEP_ID"));
                    dependency.setRequestId(rs.getString("REQUEST_ID"));
                    dependency.setDependsOn(rs.getString("DEPENDS_ON"));
                    dependency.setKind(rs.getString("DEP_KIND"));
                    return dependency;
                }
            };

    private static final RowMapper<ReleaseSystem> SYSTEM_MAPPER =
            new RowMapper<ReleaseSystem>() {
                @Override
                public ReleaseSystem mapRow(ResultSet rs, int rowNum) throws SQLException {
                    ReleaseSystem system = new ReleaseSystem();
                    system.setSystemId(rs.getString("SYSTEM_ID"));
                    system.setDescription(rs.getString("DESCRIPTION"));
                    system.setSystemRole(rs.getString("SYS_ROLE"));
                    system.setTarget("Y".equalsIgnoreCase(rs.getString("IS_TARGET")));
                    return system;
                }
            };

    // -- the landscape --------------------------------------------------------

    public List<ReleaseSystem> findSystems() {
        return jdbc.query("SELECT SYSTEM_ID, DESCRIPTION, SYS_ROLE, IS_TARGET FROM TMS_SYSTEM "
                + "ORDER BY IS_TARGET DESC, SYSTEM_ID", SYSTEM_MAPPER);
    }

    public ReleaseSystem findSystem(String systemId) {
        List<ReleaseSystem> rows = jdbc.query("SELECT SYSTEM_ID, DESCRIPTION, SYS_ROLE, "
                        + "IS_TARGET FROM TMS_SYSTEM WHERE SYSTEM_ID = ?",
                SYSTEM_MAPPER, systemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // -- requests -------------------------------------------------------------

    public List<TransportRequest> findRequests() {
        return jdbc.query("SELECT TRKORR, DESCRIPTION, REQ_TYPE, TRSTATUS, STATUS_TEXT, "
                + "TRUSER, TRDATE, TARGET_SYSTEM, SOURCE_SYSTEM, PARENT_REQUEST "
                + "FROM TMS_REQUEST ORDER BY TRDATE DESC", REQUEST_MAPPER);
    }

    public List<TransportRequest> findRequestsByTarget(String systemId) {
        return jdbc.query("SELECT TRKORR, DESCRIPTION, REQ_TYPE, TRSTATUS, STATUS_TEXT, "
                        + "TRUSER, TRDATE, TARGET_SYSTEM, SOURCE_SYSTEM, PARENT_REQUEST "
                        + "FROM TMS_REQUEST WHERE TARGET_SYSTEM = ? ORDER BY TRDATE",
                REQUEST_MAPPER, systemId);
    }

    public TransportRequest findRequest(String trkorr) {
        List<TransportRequest> rows = jdbc.query("SELECT TRKORR, DESCRIPTION, REQ_TYPE, "
                        + "TRSTATUS, STATUS_TEXT, TRUSER, TRDATE, TARGET_SYSTEM, SOURCE_SYSTEM, "
                        + "PARENT_REQUEST FROM TMS_REQUEST WHERE TRKORR = ?",
                REQUEST_MAPPER, trkorr);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int countRequests() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM TMS_REQUEST", Integer.class);
        return count == null ? 0 : count;
    }

    /** Moves a request to a new status - this is the write path. */
    public void updateStatus(String trkorr, String status, String statusText) {
        jdbc.update("UPDATE TMS_REQUEST SET TRSTATUS = ?, STATUS_TEXT = ? WHERE TRKORR = ?",
                status, statusText, trkorr);
    }

    public void insertRequest(TransportRequest request) {
        jdbc.update("INSERT INTO TMS_REQUEST (TRKORR, DESCRIPTION, REQ_TYPE, TRSTATUS, "
                        + "STATUS_TEXT, TRUSER, TRDATE, TARGET_SYSTEM, SOURCE_SYSTEM, "
                        + "PARENT_REQUEST) VALUES (?,?,?,?,?,?,?,?,?,?)",
                request.getTrkorr(), request.getDescription(), request.getRequestType(),
                request.getStatus(), request.getStatusText(), request.getOwner(),
                Timestamp.valueOf(request.getCreatedOn()), request.getTargetSystem(),
                request.getSourceSystem(), request.getParentRequest());
    }

    // -- objects --------------------------------------------------------------

    public List<TransportObject> findObjects(String requestId) {
        return jdbc.query("SELECT OBJ_ID, TRKORR, PGMID, OBJ_TYPE, OBJ_NAME, OBJ_KEY, "
                        + "FIELD_NAME, LAST_CHANGED FROM TMS_OBJECT WHERE TRKORR = ? "
                        + "ORDER BY OBJ_TYPE, OBJ_NAME",
                OBJECT_MAPPER, requestId);
    }

    public List<TransportObject> findAllObjects() {
        return jdbc.query("SELECT OBJ_ID, TRKORR, PGMID, OBJ_TYPE, OBJ_NAME, OBJ_KEY, "
                + "FIELD_NAME, LAST_CHANGED FROM TMS_OBJECT", OBJECT_MAPPER);
    }

    public void insertObject(TransportObject object) {
        jdbc.update("INSERT INTO TMS_OBJECT (OBJ_ID, TRKORR, PGMID, OBJ_TYPE, OBJ_NAME, "
                        + "OBJ_KEY, FIELD_NAME, LAST_CHANGED) VALUES (?,?,?,?,?,?,?,?)",
                object.getObjectId(), object.getRequestId(), object.getProgramId(),
                object.getObjectType(), object.getObjectName(), object.getObjectKey(),
                object.getFieldName(), Timestamp.valueOf(object.getLastChanged()));
    }

    // -- imports --------------------------------------------------------------

    public List<ImportRecord> findImports(String requestId) {
        return jdbc.query("SELECT IMP_ID, TRKORR, SYSTEM_ID, IMPORTED_AT, RESULT, MESSAGE "
                        + "FROM TMS_IMPORT WHERE TRKORR = ? ORDER BY IMPORTED_AT DESC",
                IMPORT_MAPPER, requestId);
    }

    public List<ImportRecord> findAllImports() {
        return jdbc.query("SELECT IMP_ID, TRKORR, SYSTEM_ID, IMPORTED_AT, RESULT, MESSAGE "
                + "FROM TMS_IMPORT ORDER BY IMPORTED_AT DESC", IMPORT_MAPPER);
    }

    public void insertImport(ImportRecord record) {
        jdbc.update("INSERT INTO TMS_IMPORT (IMP_ID, TRKORR, SYSTEM_ID, IMPORTED_AT, RESULT, "
                        + "MESSAGE) VALUES (?,?,?,?,?,?)",
                record.getImportId(), record.getRequestId(), record.getSystemId(),
                Timestamp.valueOf(record.getImportedAt()), record.getResult(), record.getMessage());
    }

    // -- dependencies ---------------------------------------------------------

    public List<TransportDependency> findDependencies(String requestId) {
        return jdbc.query("SELECT DEP_ID, REQUEST_ID, DEPENDS_ON, DEP_KIND FROM TMS_DEPENDENCY "
                + "WHERE REQUEST_ID = ?", DEPENDENCY_MAPPER, requestId);
    }

    public List<TransportDependency> findAllDependencies() {
        return jdbc.query("SELECT DEP_ID, REQUEST_ID, DEPENDS_ON, DEP_KIND FROM TMS_DEPENDENCY",
                DEPENDENCY_MAPPER);
    }

    public void insertDependency(TransportDependency dependency) {
        jdbc.update("INSERT INTO TMS_DEPENDENCY (DEP_ID, REQUEST_ID, DEPENDS_ON, DEP_KIND) "
                        + "VALUES (?,?,?,?)",
                dependency.getDependencyId(), dependency.getRequestId(),
                dependency.getDependsOn(), dependency.getKind());
    }
}
