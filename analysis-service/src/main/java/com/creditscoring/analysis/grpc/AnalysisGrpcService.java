package com.creditscoring.analysis.grpc;

import com.creditscoring.analysis.service.AnalysisService;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.grpc.Grpc;
import com.creditscoring.contracts.AnalysisServiceGrpc;
import com.creditscoring.contracts.BenchmarkSet;
import com.creditscoring.contracts.BenchmarkSetList;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.FinancialAnalysis;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.RatioDefinitionList;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AnalysisGrpcService extends AnalysisServiceGrpc.AnalysisServiceImplBase {
    private final AnalysisService service;

    public AnalysisGrpcService(AnalysisService service) {
        this.service = service;
    }

    @Override
    public void getAnalysis(IdRequest r, StreamObserver<FinancialAnalysis> o) {
        Grpc.reply(o, () -> service.get(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void listRatioDefinitions(Empty r, StreamObserver<RatioDefinitionList> o) {
        Grpc.reply(o, () -> {
            Grpc.actor();
            return service.ratioDefinitions();
        });
    }

    @Override
    public void listBenchmarkSets(Empty r, StreamObserver<BenchmarkSetList> o) {
        Grpc.reply(o, () -> service.listSets(Grpc.actor()));
    }

    @Override
    public void getBenchmarkSet(IdRequest r, StreamObserver<BenchmarkSet> o) {
        Grpc.reply(o, () -> service.getSet(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void activateBenchmarkSet(IdRequest r, StreamObserver<BenchmarkSet> o) {
        Grpc.reply(o, () -> service.activate(Grpc.actor(), uuid(r.getId())));
    }

    static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Некорректный идентификатор: " + s);
        }
    }
}
