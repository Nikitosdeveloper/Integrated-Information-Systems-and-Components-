package com.creditscoring.common.clients;

import com.creditscoring.common.grpc.GrpcClientFactory;
import com.creditscoring.common.grpc.ServiceCaller;
import com.creditscoring.common.security.Actor;
import com.creditscoring.contracts.AnalysisServiceGrpc;
import com.creditscoring.contracts.BenchmarkSet;
import com.creditscoring.contracts.BenchmarkSetList;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.FinancialAnalysis;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.RatioDefinitionList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** gRPC-клиент analysis: вызовы с timeout, ограниченным retry и логированием (см. ServiceCaller). */
@Component
public class AnalysisClient {
    private final ServiceCaller<AnalysisServiceGrpc.AnalysisServiceBlockingStub> caller;

    public AnalysisClient(GrpcClientFactory factory,
                          @Value("${clients.analysis.host:localhost}") String host,
                          @Value("${clients.analysis.port:9104}") int port) {
        this.caller = factory.create("analysis", host, port, AnalysisServiceGrpc::newBlockingStub);
    }

    public FinancialAnalysis getAnalysis(Actor actor, IdRequest request) {
        return caller.call("getAnalysis", actor, true, s -> s.getAnalysis(request));
    }

    public RatioDefinitionList listRatioDefinitions(Actor actor, Empty request) {
        return caller.call("listRatioDefinitions", actor, true, s -> s.listRatioDefinitions(request));
    }

    public BenchmarkSetList listBenchmarkSets(Actor actor, Empty request) {
        return caller.call("listBenchmarkSets", actor, true, s -> s.listBenchmarkSets(request));
    }

    public BenchmarkSet getBenchmarkSet(Actor actor, IdRequest request) {
        return caller.call("getBenchmarkSet", actor, true, s -> s.getBenchmarkSet(request));
    }

    public BenchmarkSet activateBenchmarkSet(Actor actor, IdRequest request) {
        return caller.call("activateBenchmarkSet", actor, false, s -> s.activateBenchmarkSet(request));
    }
}
