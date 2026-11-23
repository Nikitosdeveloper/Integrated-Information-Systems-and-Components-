package com.creditscoring.statement.grpc;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.grpc.Grpc;
import com.creditscoring.contracts.FinancialStatement;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.IdsRequest;
import com.creditscoring.contracts.IssueList;
import com.creditscoring.contracts.ListCompanyStatementsRequest;
import com.creditscoring.contracts.StatementLineList;
import com.creditscoring.contracts.StatementList;
import com.creditscoring.contracts.StatementServiceGrpc;
import com.creditscoring.contracts.UploadStatementRequest;
import com.creditscoring.statement.service.StatementService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StatementGrpcService extends StatementServiceGrpc.StatementServiceImplBase {
    private final StatementService service;

    public StatementGrpcService(StatementService service) {
        this.service = service;
    }

    @Override
    public void getStatement(IdRequest r, StreamObserver<FinancialStatement> o) {
        Grpc.reply(o, () -> service.get(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void getStatements(IdsRequest r, StreamObserver<StatementList> o) {
        Grpc.reply(o, () -> service.getMany(Grpc.actor(), r.getIdsList().stream().map(StatementGrpcService::uuid).toList()));
    }

    @Override
    public void listCompanyStatements(ListCompanyStatementsRequest r, StreamObserver<StatementList> o) {
        Grpc.reply(o, () -> {
            uuid(r.getCompanyId());
            return service.listForCompany(Grpc.actor(), r);
        });
    }

    @Override
    public void getLines(IdRequest r, StreamObserver<StatementLineList> o) {
        Grpc.reply(o, () -> service.lines(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void getIssues(IdRequest r, StreamObserver<IssueList> o) {
        Grpc.reply(o, () -> service.issues(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void uploadStatement(UploadStatementRequest r, StreamObserver<FinancialStatement> o) {
        Grpc.reply(o, () -> service.upload(Grpc.actor(), r));
    }

    static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Некорректный идентификатор: " + s);
        }
    }
}
