package com.revzz.sap.transport.integration;

import com.revzz.sap.transport.domain.TransportObject;
import com.revzz.sap.transport.domain.TransportRequest;
import com.revzz.sap.transport.repo.TransportRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The default gateway: reads the transport data from the local database.
 *
 * <p>This is what the application runs with when there is no SAP system to
 * talk to. It is also the implementation the unit tests exercise, which is
 * why the rest of the code never depends on RFC being available.</p>
 */
@Component
@ConditionalOnProperty(name = "transport.gateway", havingValue = "local", matchIfMissing = true)
public class LocalTransportGateway implements TransportGateway {

    private final TransportRepository repository;

    public LocalTransportGateway(TransportRepository repository) {
        this.repository = repository;
    }

    @Override
    public String description() {
        return "Local database";
    }

    @Override
    public List<TransportRequest> fetchRequests() {
        return repository.findRequests();
    }

    @Override
    public List<TransportObject> fetchObjects(String requestId) {
        return repository.findObjects(requestId);
    }
}
