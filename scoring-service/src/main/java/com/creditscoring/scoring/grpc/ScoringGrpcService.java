package com.creditscoring.scoring.grpc;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.grpc.Grpc;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.ModelList;
import com.creditscoring.contracts.ScoringModel;
import com.creditscoring.contracts.ScoringResult;
import com.creditscoring.contracts.ScoringServiceGrpc;
import com.creditscoring.scoring.service.ScoringService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ScoringGrpcService extends ScoringServiceGrpc.ScoringServiceImplBase {
    private final ScoringService service;

    public ScoringGrpcService(ScoringService service) {
        this.service = service;
    }

    @Override
    public void getScoringResult(IdRequest r, StreamObserver<ScoringResult> o) {
        Grpc.reply(o, () -> service.getResult(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void listModels(Empty r, StreamObserver<ModelList> o) {
        Grpc.reply(o, () -> service.listModels(Grpc.actor()));
    }

    @Override
    public void getModel(IdRequest r, StreamObserver<ScoringModel> o) {
        Grpc.reply(o, () -> service.getModel(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void activateModel(IdRequest r, StreamObserver<ScoringModel> o) {
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
