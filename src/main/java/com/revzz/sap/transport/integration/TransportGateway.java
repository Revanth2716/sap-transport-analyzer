package com.revzz.sap.transport.integration;

import com.revzz.sap.transport.domain.TransportObject;
import com.revzz.sap.transport.domain.TransportRequest;

import java.util.List;

/**
 * The boundary between the analyzer and a real SAP landscape.
 *
 * <p>Everything above this interface works on plain domain objects and knows
 * nothing about RFC, JCo or ABAP. Everything below it knows how to ask a
 * real system for its transport requests.</p>
 *
 * <p>Two implementations exist:</p>
 * <ul>
 *   <li>{@code LocalTransportGateway} - the default, reads the local
 *       database. It is what the demo runs on.</li>
 *   <li>{@code RfcTransportGateway} - the live path, calls the RFC-enabled
 *       ABAP function module {@code Z_TRANSPORT_LIST} through the SAP Java
 *       Connector. It is only compiled when the {@code with-jco} Maven
 *       profile is active, because the SAP Java Connector is not published on
 *       Maven Central.</li>
 * </ul>
 */
public interface TransportGateway {

    /** A short description of where the data comes from, shown in the UI. */
    String description();

    /** The transport requests of the landscape. */
    List<TransportRequest> fetchRequests();

    /** The objects of one request. */
    List<TransportObject> fetchObjects(String requestId);
}
