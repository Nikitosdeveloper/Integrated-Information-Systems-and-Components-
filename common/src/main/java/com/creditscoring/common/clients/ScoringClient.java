package com.creditscoring.common.clients;

import com.creditscoring.common.grpc.GrpcClientFactory;
import com.creditscoring.common.grpc.ServiceCaller;
import com.creditscoring.common.security.Actor;
import com.creditscoring.contracts.ScoringServiceGrpc;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.ModelList;
import com.creditscoring.contracts.ScoringModel;
import com.creditscoring.contracts.ScoringResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** gRPC-клиент scoring: вызовы с timeout, ограниченным retry и логированием (см. ServiceCaller). */
@Component
public class ScoringClient {
    private final ServiceCaller<ScoringServiceGrpc.ScoringServiceBlockingStub> caller;

    public ScoringClient(GrpcClientFactory factory,
                         @Value("${clients.scoring.host:localhost}") String host,
                         @Value("${clients.scoring.port:9105}") int port) {
        this.caller = factory.create("scoring", host, port, ScoringServiceGrpc::newBlockingStub);
    }

    public ScoringResult getScoringResult(Actor actor, IdRequest request) {
        return caller.call("getScoringResult", actor, true, s -> s.getScoringResult(request));
    }

    public ModelList listModels(Actor actor, Empty request) {
        return caller.call("listModels", actor, true, s -> s.listModels(request));
    }

    public ScoringModel getModel(Actor actor, IdRequest request) {
        return caller.call("getModel", actor, true, s -> s.getModel(request));
    }

    public ScoringModel activateModel(Actor actor, IdRequest request) {
        return caller.call("activateModel", actor, false, s -> s.activateModel(request));
    }
}
