package com.creditscoring.common.clients;

import com.creditscoring.common.grpc.GrpcClientFactory;
import com.creditscoring.common.grpc.ServiceCaller;
import com.creditscoring.common.security.Actor;
import com.creditscoring.contracts.BorrowerServiceGrpc;
import com.creditscoring.contracts.Company;
import com.creditscoring.contracts.CompanyList;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.IdsRequest;
import com.creditscoring.contracts.IndustryList;
import com.creditscoring.contracts.UpdateCompanyProfileRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** gRPC-клиент borrower: вызовы с timeout, ограниченным retry и логированием (см. ServiceCaller). */
@Component
public class BorrowerClient {
    private final ServiceCaller<BorrowerServiceGrpc.BorrowerServiceBlockingStub> caller;

    public BorrowerClient(GrpcClientFactory factory,
                          @Value("${clients.borrower.host:localhost}") String host,
                          @Value("${clients.borrower.port:9101}") int port) {
        this.caller = factory.create("borrower", host, port, BorrowerServiceGrpc::newBlockingStub);
    }

    public Company getCompany(Actor actor, IdRequest request) {
        return caller.call("getCompany", actor, true, s -> s.getCompany(request));
    }

    public CompanyList getCompanies(Actor actor, IdsRequest request) {
        return caller.call("getCompanies", actor, true, s -> s.getCompanies(request));
    }

    public IndustryList listIndustries(Actor actor, Empty request) {
        return caller.call("listIndustries", actor, true, s -> s.listIndustries(request));
    }

    public Company updateCompanyProfile(Actor actor, UpdateCompanyProfileRequest request) {
        return caller.call("updateCompanyProfile", actor, false, s -> s.updateCompanyProfile(request));
    }
}
