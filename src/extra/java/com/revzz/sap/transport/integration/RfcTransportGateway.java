package com.revzz.sap.transport.integration;

import com.revzz.sap.transport.domain.TransportObject;
import com.revzz.sap.transport.domain.TransportRequest;
import com.sap.jco.JCoDestination;
import com.sap.jco.JCoException;
import com.sap.jco.JCoFunction;
import com.sap.jco.JCoTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Live SAP gateway - RFC through the SAP Java Connector.
 *
 * <p><b>Only compiled when the {@code with-jco} Maven profile is active</b>,
 * because the SAP Java Connector is published on SAP's own Maven repository
 * rather than on Maven Central. The default build therefore compiles and
 * tests anywhere.</p>
 *
 * <pre>
 *   mvn -Pwith-jco clean package
 * </pre>
 *
 * <p>The remote function module {@code Z_TRANSPORT_LIST} must be
 * <b>Remote Enabled</b> in transaction SE37, otherwise SAP will refuse the
 * call with {@code RFC function module not found} - which is the single most
 * common reason an RFC adapter that "should work" does not.</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "transport.gateway", havingValue = "jco")
public class RfcTransportGateway implements TransportGateway {

    private static final Logger LOG = LoggerFactory.getLogger(RfcTransportGateway.class);

    /** RFC-enabled ABAP function module that reads the transport requests. */
    private static final String FUNCTION_TRANSPORT_LIST = "Z_TRANSPORT_LIST";

    private final String destinationName;

    public RfcTransportGateway() {
        this("SAPPRD");
    }

    public RfcTransportGateway(String destinationName) {
        this.destinationName = destinationName;
    }

    @Override
    public String description() {
        return "SAP system over RFC (" + destinationName + ")";
    }

    @Override
    public List<TransportRequest> fetchRequests() {
        List<TransportRequest> requests = new ArrayList<TransportRequest>();
        try {
            JCoDestination destination = buildDestination();
            JCoFunction function = JCoFunction.getInstance(destination, FUNCTION_TRANSPORT_LIST);
            function.execute(destination);

            JCoTable table = function.getTable("IT_REQUESTS");
            if (table == null) {
                return requests;
            }
            table.first();
            while (!table.isEof()) {
                TransportRequest request = new TransportRequest();
                request.setTrkorr(table.getField("TRKORR").getString());
                request.setDescription(table.getField("DESCRIPTION").getString());
                request.setRequestType(table.getField("REQ_TYPE").getString());
                request.setStatus(table.getField("TRSTATUS").getString());
                request.setStatusText(table.getField("STATUS_TEXT").getString());
                request.setOwner(table.getField("TRUSER").getString());
                request.setCreatedOn(toLocalDateTime(table.getField("TRDATE").getString()));
                request.setTargetSystem(table.getField("TARGET_SYSTEM").getString());
                request.setSourceSystem(table.getField("SOURCE_SYSTEM").getString());
                request.setParentRequest(table.getField("PARENT_REQUEST").getString());
                requests.add(request);
                table.next();
            }
            LOG.info("Fetched {} transport requests over RFC", requests.size());
        } catch (JCoException exception) {
            LOG.error("Could not read the transport requests over RFC: {}",
                    exception.getMessage());
        }
        return requests;
    }

    @Override
    public List<TransportObject> fetchObjects(String requestId) {
        List<TransportObject> objects = new ArrayList<TransportObject>();
        try {
            JCoDestination destination = buildDestination();
            JCoFunction function = JCoFunction.getInstance(destination, FUNCTION_TRANSPORT_LIST);
            function.getParameter("IV_REQUEST").setString(requestId);
            function.execute(destination);

            JCoTable table = function.getTable("IT_OBJECTS");
            if (table == null) {
                return objects;
            }
            table.first();
            while (!table.isEof()) {
                TransportObject object = new TransportObject();
                object.setObjectId(table.getField("OBJ_ID").getString());
                object.setRequestId(table.getField("TRKORR").getString());
                object.setProgramId(table.getField("PGMID").getString());
                object.setObjectType(table.getField("OBJ_TYPE").getString());
                object.setObjectName(table.getField("OBJ_NAME").getString());
                object.setObjectKey(table.getField("OBJ_KEY").getString());
                object.setFieldName(table.getField("FIELD_NAME").getString());
                object.setLastChanged(toLocalDateTime(table.getField("LAST_CHANGED").getString()));
                objects.add(object);
                table.next();
            }
            LOG.info("Fetched {} objects over RFC for {}", objects.size(), requestId);
        } catch (JCoException exception) {
            LOG.error("Could not read the objects of {} over RFC: {}",
                    requestId, exception.getMessage());
        }
        return objects;
    }

    private LocalDateTime toLocalDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private JCoDestination buildDestination() throws JCoException {
        Properties properties = new Properties();
        try {
            java.io.InputStream stream = new java.io.FileInputStream("jco.properties");
            try {
                properties.load(stream);
            } finally {
                stream.close();
            }
        } catch (java.io.IOException exception) {
            throw new JCoException("jco.properties not found - copy jco.properties.example");
        }

        JCoDestination destination = JCoDestination.createDestination(destinationName);
        destination.setApplicationServer(properties.getProperty("sap.applicationServer"));
        destination.setSystemNumber(properties.getProperty("sap.systemNumber"));
        destination.setClient(properties.getProperty("sap.client"));
        destination.setUser(properties.getProperty("sap.user"));
        destination.setPassword(properties.getProperty("sap.password"));
        destination.setPoolSize(2);
        return destination;
    }
}
