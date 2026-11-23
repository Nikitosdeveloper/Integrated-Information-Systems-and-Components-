package com.creditscoring.application.service;

import com.creditscoring.application.entity.AnalystConclusion;
import com.creditscoring.application.entity.CreditApplication;
import com.creditscoring.application.entity.CreditDecision;
import com.creditscoring.application.entity.CreditProduct;
import com.creditscoring.application.entity.DocumentRequest;
import com.creditscoring.application.entity.StatusHistory;

/** Преобразование сущностей в сообщения контракта: наружу сущности БД не выходят. */
final class Mapper {

    static com.creditscoring.contracts.CreditProduct toProto(CreditProduct p) {
        return com.creditscoring.contracts.CreditProduct.newBuilder().setId(p.getId().toString()).setName(p.getName())
                .setMinAmount(p.getMinAmount().doubleValue()).setMaxAmount(p.getMaxAmount().doubleValue())
                .setMaxTermMonths(p.getMaxTermMonths()).setInterestRate(p.getInterestRate().doubleValue()).setStatus(p.getStatus()).build();
    }

    static com.creditscoring.contracts.CreditApplication toProto(CreditApplication a) {
        var b = com.creditscoring.contracts.CreditApplication.newBuilder()
                .setId(a.getId().toString()).setCompanyId(a.getCompanyId().toString()).setCompanyName(a.getCompanyName())
                .setIndustryCode(a.getIndustryCode()).setProduct(toProto(a.getProduct())).setAmount(a.getAmount().doubleValue())
                .setTermMonths(a.getTermMonths()).setPurpose(nz(a.getPurpose())).setCollateral(nz(a.getCollateral()))
                .setStatus(a.getStatus()).setCreatedBy(a.getCreatedBy().toString()).setCreatedAt(a.getCreatedAt().toString())
                .setSubmittedAt(a.getSubmittedAt() == null ? "" : a.getSubmittedAt().toString())
                .setAssignedAnalystId(a.getAssignedAnalystId() == null ? "" : a.getAssignedAnalystId().toString())
                .setLatestRating(nz(a.getLatestRating()));
        if (a.getLatestScore() != null) {
            b.setLatestScore(a.getLatestScore().doubleValue());
        }
        if (a.getRecommendedLimit() != null) {
            b.setRecommendedLimit(a.getRecommendedLimit().doubleValue());
        }
        return b.build();
    }

    static com.creditscoring.contracts.HistoryItem toProto(StatusHistory h) {
        return com.creditscoring.contracts.HistoryItem.newBuilder().setFromStatus(nz(h.getFromStatus())).setToStatus(h.getToStatus())
                .setChangedBy(h.getChangedBy().toString()).setComment(nz(h.getComment())).setChangedAt(h.getChangedAt().toString()).build();
    }

    static com.creditscoring.contracts.DocumentRequest toProto(DocumentRequest r) {
        var b = com.creditscoring.contracts.DocumentRequest.newBuilder().setId(r.getId().toString())
                .setApplicationId(r.getApplicationId().toString()).setRequestedBy(r.getRequestedBy().toString())
                .setDueDate(r.getDueDate().toString()).setStatus(r.getStatus());
        r.getItems().forEach(i -> b.addItems(com.creditscoring.contracts.DocumentItem.newBuilder().setId(i.getId().toString())
                .setTitle(i.getTitle()).setIsRequired(i.isRequired()).setStatus(i.getStatus()).setFileName(nz(i.getFileName()))));
        return b.build();
    }

    static com.creditscoring.contracts.AnalystConclusion toProto(AnalystConclusion c) {
        return com.creditscoring.contracts.AnalystConclusion.newBuilder().setId(c.getId().toString())
                .setApplicationId(c.getApplicationId().toString()).setAnalystId(c.getAnalystId().toString()).setText(c.getText())
                .setRecommendation(c.getRecommendation()).setCreatedAt(c.getCreatedAt().toString()).build();
    }

    static com.creditscoring.contracts.CreditDecision toProto(CreditDecision d) {
        var b = com.creditscoring.contracts.CreditDecision.newBuilder().setId(d.getId().toString())
                .setApplicationId(d.getApplicationId().toString()).setDecidedBy(d.getDecidedBy().toString())
                .setDecision(d.getDecision()).setComment(nz(d.getComment())).setDecidedAt(d.getDecidedAt().toString());
        if (d.getApprovedAmount() != null) {
            b.setApprovedAmount(d.getApprovedAmount().doubleValue());
        }
        return b.build();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private Mapper() {
    }
}
