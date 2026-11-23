package com.creditscoring.common.clients;

import com.creditscoring.common.grpc.GrpcClientFactory;
import com.creditscoring.common.grpc.ServiceCaller;
import com.creditscoring.common.security.Actor;
import com.creditscoring.contracts.StatementServiceGrpc;
import com.creditscoring.contracts.FinancialStatement;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.IdsRequest;
import com.creditscoring.contracts.IssueList;
import com.creditscoring.contracts.ListCompanyStatementsRequest;
import com.creditscoring.contracts.StatementLineList;
import com.creditscoring.contracts.StatementList;
import com.creditscoring.contracts.UploadStatementRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** gRPC-клиент statement: вызовы с timeout, ограниченным retry и логированием (см. ServiceCaller). */
@Component
public class StatementClient {
    private final ServiceCaller<StatementServiceGrpc.StatementServiceBlockingStub> caller;

    public StatementClient(GrpcClientFactory factory,
                           @Value("${clients.statement.host:localhost}") String host,
                           @Value("${clients.statement.port:9103}") int port) {
        this.caller = factory.create("statement", host, port, StatementServiceGrpc::newBlockingStub);
    }

    public FinancialStatement getStatement(Actor actor, IdRequest request) {
        return caller.call("getStatement", actor, true, s -> s.getStatement(request));
    }

    public StatementList getStatements(Actor actor, IdsRequest request) {
        return caller.call("getStatements", actor, true, s -> s.getStatements(request));
    }

    public StatementList listCompanyStatements(Actor actor, ListCompanyStatementsRequest request) {
        return caller.call("listCompanyStatements", actor, true, s -> s.listCompanyStatements(request));
    }

    public StatementLineList getLines(Actor actor, IdRequest request) {
        return caller.call("getLines", actor, true, s -> s.getLines(request));
    }

    public IssueList getIssues(Actor actor, IdRequest request) {
        return caller.call("getIssues", actor, true, s -> s.getIssues(request));
    }

    public FinancialStatement uploadStatement(Actor actor, UploadStatementRequest request) {
        return caller.call("uploadStatement", actor, false, s -> s.uploadStatement(request));
    }
}
