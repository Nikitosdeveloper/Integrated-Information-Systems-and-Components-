package com.creditscoring.application.grpc;

import com.creditscoring.application.service.ApplicationCommandService;
import com.creditscoring.application.service.ApplicationQueryService;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.grpc.Grpc;
import com.creditscoring.contracts.Ack;
import com.creditscoring.contracts.AnalystConclusion;
import com.creditscoring.contracts.ApplicationPage;
import com.creditscoring.contracts.ApplicationServiceGrpc;
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
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Тонкий gRPC-слой: вся бизнес-логика - в ApplicationCommandService / ApplicationQueryService. */
@Component
public class ApplicationGrpcService extends ApplicationServiceGrpc.ApplicationServiceImplBase {
    private final ApplicationQueryService queries;
    private final ApplicationCommandService commands;

    public ApplicationGrpcService(ApplicationQueryService queries, ApplicationCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @Override
    public void listCreditProducts(ListProductsRequest r, StreamObserver<ProductList> o) {
        Grpc.reply(o, () -> {
            Grpc.actor();
            return queries.listProducts(r.getIncludeArchived());
        });
    }

    @Override
    public void getApplication(IdRequest r, StreamObserver<CreditApplication> o) {
        Grpc.reply(o, () -> queries.get(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void listApplications(ListApplicationsRequest r, StreamObserver<ApplicationPage> o) {
        Grpc.reply(o, () -> queries.list(Grpc.actor(), r));
    }

    @Override
    public void getApplicationStats(ApplicationStatsRequest r, StreamObserver<ApplicationStats> o) {
        Grpc.reply(o, () -> queries.stats(Grpc.actor(), r));
    }

    @Override
    public void getHistory(IdRequest r, StreamObserver<HistoryList> o) {
        Grpc.reply(o, () -> queries.history(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void getDocumentRequests(IdRequest r, StreamObserver<DocumentRequestList> o) {
        Grpc.reply(o, () -> queries.documentRequests(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void getConclusion(IdRequest r, StreamObserver<AnalystConclusion> o) {
        Grpc.reply(o, () -> queries.conclusion(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void getDecision(IdRequest r, StreamObserver<CreditDecision> o) {
        Grpc.reply(o, () -> queries.decision(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void createApplication(CreateApplicationRequest r, StreamObserver<CreditApplication> o) {
        Grpc.reply(o, () -> commands.create(Grpc.actor(), r));
    }

    @Override
    public void submitApplication(IdRequest r, StreamObserver<CreditApplication> o) {
        Grpc.reply(o, () -> commands.submit(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void cancelApplication(CancelApplicationRequest r, StreamObserver<CreditApplication> o) {
        Grpc.reply(o, () -> commands.cancel(Grpc.actor(), r));
    }

    @Override
    public void assignToMe(IdRequest r, StreamObserver<CreditApplication> o) {
        Grpc.reply(o, () -> commands.assignToMe(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void requestDocuments(RequestDocumentsRequest r, StreamObserver<DocumentRequest> o) {
        Grpc.reply(o, () -> commands.requestDocuments(Grpc.actor(), r));
    }

    @Override
    public void provideDocument(ProvideDocumentRequest r, StreamObserver<DocumentRequest> o) {
        Grpc.reply(o, () -> commands.provideDocument(Grpc.actor(), r));
    }

    @Override
    public void saveConclusion(SaveConclusionRequest r, StreamObserver<AnalystConclusion> o) {
        Grpc.reply(o, () -> commands.saveConclusion(Grpc.actor(), r));
    }

    @Override
    public void sendToCommittee(IdRequest r, StreamObserver<CreditApplication> o) {
        Grpc.reply(o, () -> commands.sendToCommittee(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void makeDecision(MakeDecisionRequest r, StreamObserver<CreditDecision> o) {
        Grpc.reply(o, () -> commands.makeDecision(Grpc.actor(), r));
    }

    @Override
    public void recalculateScoring(IdRequest r, StreamObserver<Ack> o) {
        Grpc.reply(o, () -> {
            commands.recalculateScoring(Grpc.actor(), uuid(r.getId()));
            return Ack.newBuilder().setOk(true).build();
        });
    }

    static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Некорректный идентификатор: " + s);
        }
    }
}
