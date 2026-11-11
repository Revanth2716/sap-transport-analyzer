package com.revzz.sap.transport.seed;

import com.revzz.sap.transport.config.AppProperties;
import com.revzz.sap.transport.domain.ImportRecord;
import com.revzz.sap.transport.domain.ReleaseSystem;
import com.revzz.sap.transport.domain.TransportDependency;
import com.revzz.sap.transport.domain.TransportObject;
import com.revzz.sap.transport.domain.TransportRequest;
import com.revzz.sap.transport.repo.TransportRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Seeds a transport landscape with a story.
 *
 * <p>Nine requests, each with the problem a real customer has:</p>
 *
 * <ul>
 *   <li><b>K900001</b> - a big workbench request that has been open for
 *       45 days and <b>shares an object with K900002</b>. The conflict.</li>
 *   <li><b>K900002</b> - the other half of the conflict: released, never
 *       imported, and holding the same class.</li>
 *   <li><b>K900003</b> - a customizing request that references K900001.
 *       K900001 reached QA but not PRD, so this one is <b>blocked</b>.</li>
 *   <li><b>K900004</b> - imported to production 60 days ago, and an object
 *       in it was <b>changed ten days ago</b>. The change that never went out.</li>
 *   <li><b>K900005</b> - a task still in development after 70 days.</li>
 *   <li><b>K900006</b> - the workbench request of that task, so it cannot be
 *       released either.</li>
 *   <li><b>K900007</b> - released, but still pointing at QA. It never made
 *       it to production.</li>
 *   <li><b>K900008</b> - imported cleanly and untouched since. The control.</li>
 *   <li><b>K900009</b> - released, clean, and the <b>only request that can
 *       go into the next transport</b>.</li>
 * </ul>
 *
 * <p>Random noise would prove nothing. Every number here exists because it
 * demonstrates one specific finding.</p>
 */
@Component
@Order(2)
public class TransportSeeder implements CommandLineRunner {

    private int objectCounter = 1;
    private int importCounter = 1;
    private int dependencyCounter = 1;

    private final JdbcTemplate jdbc;
    private final TransportRepository repository;
    private final AppProperties properties;

    public TransportSeeder(JdbcTemplate jdbc, TransportRepository repository,
                           AppProperties properties) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.isSeedDemoData() || repository.countRequests() > 0) {
            return;
        }
        seedUsers();
        LocalDateTime now = LocalDateTime.now();

        // --- the landscape -------------------------------------------------
        system("DE1", "Development system", ReleaseSystem.ROLE_DEVELOPMENT, false);
        system("QAS", "Quality system", ReleaseSystem.ROLE_QUALITY, false);
        system("PRD", "Production ERP", ReleaseSystem.ROLE_PRODUCTION, true);

        // --- K900001: the big workbench request, open for 45 days, and it
        //     shares ZCL_PAYMENT with K900002 --------------------------------
        request("K900001", "Payment handling rework - customer settlement",
                TransportRequest.TYPE_WORKBENCH, TransportRequest.STATUS_RELEASED,
                "Released for transport", "REVANTH.BASIS", now.minusDays(45), "PRD", "DE1", "");
        workbench("K900001", "CLAS", "ZCL_PAYMENT", 44);
        workbench("K900001", "CLAS", "ZCL_VENDOR", 43);
        workbench("K900001", "CLAS", "ZCL_ORDER", 43);
        workbench("K900001", "TABL", "ZTABLE_PAYMENT", 42);
        workbench("K900001", "TABL", "ZTABLE_VENDOR", 41);
        workbench("K900001", "TABL", "ZTABLE_ORDER", 41);
        workbench("K900001", "ENQU", "ZIF_PAYMENT", 40);
        workbench("K900001", "PROG", "ZREPORT_PAYMENT", 40);
        workbench("K900001", "XSLT", "ZDOMAIN_PAYMENT", 39);
        workbench("K900001", "CLAS", "ZCLASS_PAYMENT", 38);
        workbench("K900001", "CLAS", "ZMETHOD_PAYMENT", 38);
        workbench("K900001", "PROG", "ZFORM_PAYMENT", 37);
        importRecord("K900001", "QAS", now.minusDays(10), ImportRecord.RESULT_OK,
                "Imported into the quality system, 12 objects, no errors.");

        // --- K900002: the other half of the conflict -------------------------
        request("K900002", "Payment refund handling - new business case",
                TransportRequest.TYPE_WORKBENCH, TransportRequest.STATUS_RELEASED,
                "Released for transport", "ANIL.DEV", now.minusDays(20), "PRD", "DE1", "");
        workbench("K900002", "CLAS", "ZCL_PAYMENT", 19);
        workbench("K900002", "CLAS", "ZCL_REFUND", 18);
        workbench("K900002", "TABL", "ZTABLE_REFUND", 17);
        workbench("K900002", "ENQU", "ZIF_REFUND", 16);
        workbench("K900002", "PROG", "ZREPORT_REFUND", 15);
        workbench("K900002", "PROG", "ZFORM_REFUND", 15);

        // --- K900003: a customizing request blocked by K900001 ---------------
        request("K900003", "Payment BADI activation for the settlement run",
                TransportRequest.TYPE_CUSTOMIZING, TransportRequest.STATUS_RELEASED,
                "Released for transport", "CONFIG.OWNER", now.minusDays(12), "PRD", "DE1", "");
        customizing("K900003", "ZPAYMENT_BADI", "ACTIVE", 11);
        customizing("K900003", "ZPAYMENT_BADI", "PARTNER", 11);
        customizing("K900003", "ZCUSTOM_PAYMENT_TAB", "MANDT", 10);
        dependency("K900003", "K900001", TransportDependency.KIND_CONFIG_REFERENCE);

        // --- K900004: imported, and changed afterwards ------------------------
        request("K900004", "Invoice number range extension",
                TransportRequest.TYPE_WORKBENCH, TransportRequest.STATUS_IMPORTED,
                "Imported", "REVANTH.BASIS", now.minusDays(90), "PRD", "DE1", "");
        workbench("K900004", "TABL", "ZTABLE_NUMBER_RANGE", 88);
        workbench("K900004", "TABL", "ZTABLE_INVOICE", 87);
        // The hotfix that was never taken: ZCL_INVOICE was changed ten days
        // ago, long after the request went to production.
        workbench("K900004", "CLAS", "ZCL_INVOICE", 10);
        workbench("K900004", "PROG", "ZREPORT_INVOICE", 85);
        workbench("K900004", "ENQU", "ZIF_INVOICE", 84);
        importRecord("K900004", "PRD", now.minusDays(60), ImportRecord.RESULT_OK,
                "Imported into production, 5 objects, no errors.");

        // --- K900005: the task that is still in development -------------------
        request("K900005", "Task - settlement batch performance",
                TransportRequest.TYPE_TASK, TransportRequest.STATUS_IN_DEVELOPMENT,
                "In development", "DEV.ONE", now.minusDays(70), "PRD", "DE1", "K900006");
        workbench("K900005", "PROG", "ZREPORT_SETTLEMENT", 69);
        workbench("K900005", "CLAS", "ZCL_SETTLEMENT", 68);
        dependency("K900005", "K900006", TransportDependency.KIND_TASK_PARENT);

        // --- K900006: the workbench request held back by that task ----------
        request("K900006", "Settlement batch - vendor statement rework",
                TransportRequest.TYPE_WORKBENCH, TransportRequest.STATUS_IN_DEVELOPMENT,
                "In development", "DEV.TWO", now.minusDays(80), "PRD", "DE1", "");
        workbench("K900006", "TABL", "ZTABLE_SETTLEMENT", 78);
        workbench("K900006", "CLAS", "ZCL_STATEMENT", 77);
        workbench("K900006", "PROG", "ZREPORT_STATEMENT", 76);

        // --- K900007: released, but still pointing at QA ---------------------
        request("K900007", "Housekeeping report - variant cleanup",
                TransportRequest.TYPE_WORKBENCH, TransportRequest.STATUS_RELEASED,
                "Released for transport", "REVANTH.BASIS", now.minusDays(5), "QAS", "DE1", "");
        workbench("K900007", "PROG", "ZREPORT_HOUSEKEEPING", 4);
        workbench("K900007", "CLAS", "ZCL_HOUSEKEEPING", 4);
        workbench("K900007", "TABL", "ZTABLE_VARIANT", 3);

        // --- K900008: the control - imported cleanly and untouched -----------
        request("K900008", "Address master - validation tightening",
                TransportRequest.TYPE_WORKBENCH, TransportRequest.STATUS_IMPORTED,
                "Imported", "REVANTH.BASIS", now.minusDays(120), "PRD", "DE1", "");
        workbench("K900008", "TABL", "ZTABLE_ADDRESS", 118);
        workbench("K900008", "CLAS", "ZCL_ADDRESS", 117);
        workbench("K900008", "ENQU", "ZIF_ADDRESS", 116);
        workbench("K900008", "PROG", "ZREPORT_ADDRESS", 115);
        importRecord("K900008", "PRD", now.minusDays(100), ImportRecord.RESULT_OK,
                "Imported into production, 4 objects, no errors.");

        // --- K900009: the only request that can go into the next transport ----
        request("K900009", "Bank reconciliation - fee rounding",
                TransportRequest.TYPE_WORKBENCH, TransportRequest.STATUS_RELEASED,
                "Released for transport", "REVANTH.BASIS", now.minusDays(6), "PRD", "DE1", "");
        workbench("K900009", "CLAS", "ZCL_BANK_RECON", 5);
        workbench("K900009", "TABL", "ZTABLE_BANK_FEE", 5);
        workbench("K900009", "PROG", "ZREPORT_BANK_RECON", 4);
        importRecord("K900009", "QAS", now.minusDays(2), ImportRecord.RESULT_OK,
                "Imported into the quality system, 3 objects, no errors.");
    }

    private void seedUsers() {
        Object[][] users = {
                {"REVANTH.BASIS", "Revanth Basis", "A", "IT-Basis"},
                {"ANIL.DEV", "Anil Developer", "A", "IT-Basis"},
                {"DEV.ONE", "Developer One", "A", "IT-Basis"},
                {"DEV.TWO", "Developer Two", "A", "IT-Basis"},
                {"CONFIG.OWNER", "Configuration Owner", "A", "IT-Basis"},
        };
        for (Object[] user : users) {
            jdbc.update("INSERT INTO SAPUSER (USERID, FULLNAME, UFLAG, DEPT) VALUES (?,?,?,?)",
                    user[0], user[1], user[2], user[3]);
        }
    }

    private void system(String id, String description, String role, boolean target) {
        ReleaseSystem system = new ReleaseSystem();
        system.setSystemId(id);
        system.setDescription(description);
        system.setSystemRole(role);
        system.setTarget(target);
        jdbc.update("INSERT INTO TMS_SYSTEM (SYSTEM_ID, DESCRIPTION, SYS_ROLE, IS_TARGET) "
                + "VALUES (?,?,?,?)", id, description, role, target ? "Y" : "N");
    }

    private void request(String trkorr, String description, String type, String status,
                         String statusText, String owner, LocalDateTime created,
                         String target, String source, String parent) {
        TransportRequest request = new TransportRequest();
        request.setTrkorr(trkorr);
        request.setDescription(description);
        request.setRequestType(type);
        request.setStatus(status);
        request.setStatusText(statusText);
        request.setOwner(owner);
        request.setCreatedOn(created);
        request.setTargetSystem(target);
        request.setSourceSystem(source);
        request.setParentRequest(parent);
        repository.insertRequest(request);
    }

    /**
     * A workbench object.
     *
     * <p>The conflict key is the name plus the type: two requests changing
     * the same class conflict, two requests changing a class and a table
     * with the same name do not.</p>
     */
    private void workbench(String requestId, String type, String name, int daysAgo) {
        TransportObject object = new TransportObject();
        object.setObjectId("O" + objectCounter++);
        object.setRequestId(requestId);
        object.setProgramId("R");
        object.setObjectType(type);
        object.setObjectName(name);
        object.setObjectKey(name + " (" + type + ")");
        object.setFieldName("");
        object.setLastChanged(LocalDateTime.now().minusDays(daysAgo)
                .withHour(11).withMinute(15).withSecond(0).withNano(0));
        repository.insertObject(object);
    }

    /**
     * A customizing entry.
     *
     * <p>For customizing the conflict key includes the field, because two
     * requests setting the same field of the same table is the conflict -
     * changing two different fields of one table is not.</p>
     */
    private void customizing(String requestId, String name, String field, int daysAgo) {
        TransportObject object = new TransportObject();
        object.setObjectId("O" + objectCounter++);
        object.setRequestId(requestId);
        object.setProgramId("C");
        object.setObjectType("TABU");
        object.setObjectName(name);
        object.setObjectKey(name + "." + field);
        object.setFieldName(field);
        object.setLastChanged(LocalDateTime.now().minusDays(daysAgo)
                .withHour(14).withMinute(40).withSecond(0).withNano(0));
        repository.insertObject(object);
    }

    private void importRecord(String requestId, String systemId, LocalDateTime at,
                              String result, String message) {
        ImportRecord record = new ImportRecord();
        record.setImportId("IM" + importCounter++);
        record.setRequestId(requestId);
        record.setSystemId(systemId);
        record.setImportedAt(at);
        record.setResult(result);
        record.setMessage(message);
        repository.insertImport(record);
    }

    private void dependency(String requestId, String dependsOn, String kind) {
        TransportDependency dependency = new TransportDependency();
        dependency.setDependencyId("DP" + dependencyCounter++);
        dependency.setRequestId(requestId);
        dependency.setDependsOn(dependsOn);
        dependency.setKind(kind);
        repository.insertDependency(dependency);
    }
}
