package com.creditscoring.gateway.graphql;

import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.ApplicationFilter;
import com.creditscoring.contracts.ApplicationStatsRequest;
import com.creditscoring.contracts.ListApplicationsRequest;

import java.util.List;
import java.util.Map;

/** Преобразование аргументов GraphQL в запросы контрактов. Только преобразование формата, без бизнес-логики. */
final class Requests {

    @SuppressWarnings("unchecked")
    static ApplicationFilter filter(Map<String, Object> f) {
        ApplicationFilter.Builder b = ApplicationFilter.newBuilder();
        if (f == null) {
            return b.build();
        }
        if (f.get("statuses") != null) {
            b.addAllStatuses((List<String>) f.get("statuses"));
        }
        if (f.get("amountFrom") != null) {
            b.setAmountFrom(((Number) f.get("amountFrom")).doubleValue());
        }
        if (f.get("amountTo") != null) {
            b.setAmountTo(((Number) f.get("amountTo")).doubleValue());
        }
        if (f.get("companyNameContains") != null) {
            b.setCompanyNameContains((String) f.get("companyNameContains"));
        }
        if (f.get("rating") != null) {
            b.setRating((String) f.get("rating"));
        }
        if (f.get("createdFrom") != null) {
            b.setCreatedFrom((String) f.get("createdFrom"));
        }
        if (f.get("createdTo") != null) {
            b.setCreatedTo((String) f.get("createdTo"));
        }
        if (f.get("assignedToMe") != null) {
            b.setAssignedToMe((Boolean) f.get("assignedToMe"));
        }
        return b.build();
    }

    static ListApplicationsRequest list(String scope, Map<String, Object> filter, Map<String, Object> sort, Integer page, Integer pageSize) {
        ListApplicationsRequest.Builder b = ListApplicationsRequest.newBuilder().setScope(scope).setFilter(filter(filter))
                .setPage(page == null ? 1 : page).setPageSize(pageSize == null ? 20 : pageSize);
        if (sort != null) {
            if (sort.get("field") != null) {
                b.setSortField((String) sort.get("field"));
            }
            if (sort.get("direction") != null) {
                b.setSortDirection((String) sort.get("direction"));
            }
        }
        return b.build();
    }

    static ApplicationStatsRequest stats(Actor actor, String scope, Map<String, Object> filter) {
        String s = scope != null ? scope : switch (actor.role()) {
            case Roles.BORROWER -> "MY";
            case Roles.ANALYST -> "ANALYST_QUEUE";
            case Roles.COMMITTEE -> "COMMITTEE_QUEUE";
            default -> "ALL";
        };
        return ApplicationStatsRequest.newBuilder().setScope(s).setFilter(filter(filter)).build();
    }

    private Requests() {
    }
}
