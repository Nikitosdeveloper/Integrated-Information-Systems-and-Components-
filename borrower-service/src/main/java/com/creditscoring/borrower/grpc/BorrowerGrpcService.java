package com.creditscoring.borrower.grpc;

import com.creditscoring.borrower.service.BorrowerService;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.grpc.Grpc;
import com.creditscoring.contracts.BorrowerServiceGrpc;
import com.creditscoring.contracts.Company;
import com.creditscoring.contracts.CompanyList;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.IdsRequest;
import com.creditscoring.contracts.IndustryList;
import com.creditscoring.contracts.UpdateCompanyProfileRequest;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Тонкий gRPC-слой: принимает запрос и передаёт выполнение сервисному слою. */
@Component
public class BorrowerGrpcService extends BorrowerServiceGrpc.BorrowerServiceImplBase {
    private final BorrowerService service;

    public BorrowerGrpcService(BorrowerService service) {
        this.service = service;
    }

    @Override
    public void getCompany(IdRequest r, StreamObserver<Company> o) {
        Grpc.reply(o, () -> service.getCompany(Grpc.actor(), uuid(r.getId())));
    }

    @Override
    public void getCompanies(IdsRequest r, StreamObserver<CompanyList> o) {
        Grpc.reply(o, () -> service.getCompanies(Grpc.actor(), r.getIdsList().stream().map(BorrowerGrpcService::uuid).toList()));
    }

    @Override
    public void listIndustries(Empty r, StreamObserver<IndustryList> o) {
        Grpc.reply(o, () -> {
            Grpc.actor();
            return service.listIndustries();
        });
    }

    @Override
    public void updateCompanyProfile(UpdateCompanyProfileRequest r, StreamObserver<Company> o) {
        Grpc.reply(o, () -> service.updateProfile(Grpc.actor(), uuid(r.getCompanyId()), r.getAddress(), r.getPhone()));
    }

    static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Некорректный идентификатор: " + s);
        }
    }
}
