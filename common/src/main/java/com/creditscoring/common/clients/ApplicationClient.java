package com.creditscoring.common.clients;

import com.creditscoring.common.grpc.GrpcClientFactory;
import com.creditscoring.common.grpc.ServiceCaller;
import com.creditscoring.common.security.Actor;
import com.creditscoring.contracts.ApplicationServiceGrpc;
import com.creditscoring.contracts.Ack;
import com.creditscoring.contracts.AnalystConclusion;
import com.creditscoring.contracts.ApplicationPage;
import com.creditscoring.contracts.ApplicationStats;
import com.creditscoring.contracts.ApplicationStatsRequest;
import com.creditscoring.contracts.CancelApplicationRequest;
import com.creditscoring.contracts.CreateApplicationRequest;
import com.creditscoring.contracts.CreditApplication;
import com.creditscoring.contracts.CreditDecision;
import com.creditscoring.contracts.DocumentRequest;
import com.creditscoring.contracts.DocumentRequestList;
import com.creditscoring.contracts.HistoryList;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.ListApplicationsRequest;
import com.creditscoring.contracts.ListProductsRequest;
import com.creditscoring.contracts.MakeDecisionRequest;
import com.creditscoring.contracts.ProductList;
import com.creditscoring.contracts.ProvideDocumentRequest;
import com.creditscoring.contracts.RequestDocumentsRequest;
import com.creditscoring.contracts.SaveConclusionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** gRPC-клиент application: вызовы с timeout, ограниченным retry и логированием (см. ServiceCaller). */
@Component
public class ApplicationClient {
    private final ServiceCaller<ApplicationServiceGrpc.ApplicationServiceBlockingStub> caller;

    public ApplicationClient(GrpcClientFactory factory,
                             @Value("${clients.application.host:localhost}") String host,
                             @Value("${clients.application.port:9102}") int port) {
        this.caller = factory.create("application", host, port, ApplicationServiceGrpc::newBlockingStub);
    }

    public ProductList listCreditProducts(Actor actor, ListProductsRequest request) {
        return caller.call("listCreditProducts", actor, true, s -> s.listCreditProducts(request));
    }

    public CreditApplication getApplication(Actor actor, IdRequest request) {
        return caller.call("getApplication", actor, true, s -> s.getApplication(request));
    }

    public ApplicationPage listApplications(Actor actor, ListApplicationsRequest request) {
        return caller.call("listApplications", actor, true, s -> s.listApplications(request));
    }

    public ApplicationStats getApplicationStats(Actor actor, ApplicationStatsRequest request) {
        return caller.call("getApplicationStats", actor, true, s -> s.getApplicationStats(request));
    }

    public HistoryList getHistory(Actor actor, IdRequest request) {
        return caller.call("getHistory", actor, true, s -> s.getHistory(request));
    }

    public DocumentRequestList getDocumentRequests(Actor actor, IdRequest request) {
        return caller.call("getDocumentRequests", actor, true, s -> s.getDocumentRequests(request));
    }

    public AnalystConclusion getConclusion(Actor actor, IdRequest request) {
        return caller.call("getConclusion", actor, true, s -> s.getConclusion(request));
    }

    public CreditDecision getDecision(Actor actor, IdRequest request) {
        return caller.call("getDecision", actor, true, s -> s.getDecision(request));
    }

    public CreditApplication createApplication(Actor actor, CreateApplicationRequest request) {
        return caller.call("createApplication", actor, false, s -> s.createApplication(request));
    }

    public CreditApplication submitApplication(Actor actor, IdRequest request) {
        return caller.call("submitApplication", actor, false, s -> s.submitApplication(request));
    }

    public CreditApplication cancelApplication(Actor actor, CancelApplicationRequest request) {
        return caller.call("cancelApplication", actor, false, s -> s.cancelApplication(request));
    }

    public CreditApplication assignToMe(Actor actor, IdRequest request) {
        return caller.call("assignToMe", actor, false, s -> s.assignToMe(request));
    }

    public DocumentRequest requestDocuments(Actor actor, RequestDocumentsRequest request) {
        return caller.call("requestDocuments", actor, false, s -> s.requestDocuments(request));
    }

    public DocumentRequest provideDocument(Actor actor, ProvideDocumentRequest request) {
        return caller.call("provideDocument", actor, false, s -> s.provideDocument(request));
    }

    public AnalystConclusion saveConclusion(Actor actor, SaveConclusionRequest request) {
        return caller.call("saveConclusion", actor, false, s -> s.saveConclusion(request));
    }

    public CreditApplication sendToCommittee(Actor actor, IdRequest request) {
        return caller.call("sendToCommittee", actor, false, s -> s.sendToCommittee(request));
    }

    public CreditDecision makeDecision(Actor actor, MakeDecisionRequest request) {
        return caller.call("makeDecision", actor, false, s -> s.makeDecision(request));
    }

    public Ack recalculateScoring(Actor actor, IdRequest request) {
        return caller.call("recalculateScoring", actor, false, s -> s.recalculateScoring(request));
    }
}
