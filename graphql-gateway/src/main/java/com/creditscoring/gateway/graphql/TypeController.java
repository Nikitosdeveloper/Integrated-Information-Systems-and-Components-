package com.creditscoring.gateway.graphql;

import com.creditscoring.common.clients.AnalysisClient;
import com.creditscoring.common.clients.ApplicationClient;
import com.creditscoring.common.clients.BorrowerClient;
import com.creditscoring.common.clients.ScoringClient;
import com.creditscoring.common.clients.StatementClient;
import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import com.creditscoring.common.security.Actor;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.IdsRequest;
import com.creditscoring.contracts.ListCompanyStatementsRequest;
import com.creditscoring.gateway.auth.AuthClient;
import graphql.GraphQLContext;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Связанные поля типов. Данные других сервисов загружаются отдельными вызовами и только если клиент запросил поле.
 * Для списков используется пакетная загрузка (один вызов на тип, а не на каждую запись).
 * Если сервис недоступен, ошибка возвращается только для этого поля, остальной ответ сохраняется (поля nullable).
 */
@Controller
public class TypeController {
    private final AuthClient auth;
    private final BorrowerClient borrowers;
    private final ApplicationClient applications;
    private final StatementClient statements;
    private final AnalysisClient analysis;
    private final ScoringClient scoring;

    public TypeController(AuthClient auth, BorrowerClient borrowers, ApplicationClient applications, StatementClient statements,
                          AnalysisClient analysis, ScoringClient scoring) {
        this.auth = auth;
        this.borrowers = borrowers;
        this.applications = applications;
        this.statements = statements;
        this.analysis = analysis;
        this.scoring = scoring;
    }

    // ---------- CreditApplication: данные Application Service ----------

    @SchemaMapping(typeName = "CreditApplication", field = "history")
    public List<Map<String, Object>> history(Map<String, Object> app, GraphQLContext ctx) {
        return Protos.list(applications.getHistory(Ctx.actor(ctx), id(app)).getItemsList());
    }

    @SchemaMapping(typeName = "CreditApplication", field = "documentRequests")
    public List<Map<String, Object>> documentRequests(Map<String, Object> app, GraphQLContext ctx) {
        return Protos.list(applications.getDocumentRequests(Ctx.actor(ctx), id(app)).getItemsList());
    }

    @SchemaMapping(typeName = "CreditApplication", field = "conclusion")
    public Map<String, Object> conclusion(Map<String, Object> app, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        if (actor.isBorrower()) {
            return null; // внутреннее заключение банка заёмщику не показывается
        }
        return optional(() -> Protos.map(applications.getConclusion(actor, id(app))));
    }

    @SchemaMapping(typeName = "CreditApplication", field = "decision")
    public Map<String, Object> decision(Map<String, Object> app, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        return optional(() -> Protos.map(applications.getDecision(actor, id(app))));
    }

    // ---------- CreditApplication: данные других сервисов ----------

    @SchemaMapping(typeName = "CreditApplication", field = "statements")
    public List<Map<String, Object>> statements(Map<String, Object> app, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        return Protos.list(statements.listCompanyStatements(actor, ListCompanyStatementsRequest.newBuilder()
                .setCompanyId((String) app.get("companyId")).build()).getItemsList());
    }

    @SchemaMapping(typeName = "CreditApplication", field = "analysis")
    public Map<String, Object> analysis(Map<String, Object> app, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        if (actor.isBorrower()) {
            return null; // внутренний анализ банка заёмщику не показывается
        }
        return optional(() -> Protos.map(analysis.getAnalysis(actor, id(app))));
    }

    @SchemaMapping(typeName = "CreditApplication", field = "scoring")
    public Map<String, Object> scoring(Map<String, Object> app, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        if (actor.isBorrower()) {
            return null;
        }
        return optional(() -> Protos.map(scoring.getScoringResult(actor, id(app))));
    }

    @BatchMapping(typeName = "CreditApplication", field = "company")
    public Map<Map<String, Object>, Map<String, Object>> applicationCompany(List<Map<String, Object>> apps, GraphQLContext ctx) {
        return companies(apps, "companyId", ctx);
    }

    @BatchMapping(typeName = "User", field = "company")
    public Map<Map<String, Object>, Map<String, Object>> userCompany(List<Map<String, Object>> users, GraphQLContext ctx) {
        return companies(users, "companyId", ctx);
    }

    @BatchMapping(typeName = "CreditApplication", field = "assignedAnalyst")
    public Map<Map<String, Object>, Map<String, Object>> assignedAnalyst(List<Map<String, Object>> apps, GraphQLContext ctx) {
        return users(apps, "assignedAnalystId", ctx);
    }

    // ---------- пользователи в связанных полях (Auth Service, пакетно) ----------

    @BatchMapping(typeName = "StatusHistoryItem", field = "changedBy")
    public Map<Map<String, Object>, Map<String, Object>> historyChangedBy(List<Map<String, Object>> items, GraphQLContext ctx) {
        return users(items, "changedBy", ctx);
    }

    @BatchMapping(typeName = "DocumentRequest", field = "requestedBy")
    public Map<Map<String, Object>, Map<String, Object>> requestedBy(List<Map<String, Object>> items, GraphQLContext ctx) {
        return users(items, "requestedBy", ctx);
    }

    @BatchMapping(typeName = "AnalystConclusion", field = "analyst")
    public Map<Map<String, Object>, Map<String, Object>> conclusionAnalyst(List<Map<String, Object>> items, GraphQLContext ctx) {
        return users(items, "analystId", ctx);
    }

    @BatchMapping(typeName = "CreditDecision", field = "decidedBy")
    public Map<Map<String, Object>, Map<String, Object>> decidedBy(List<Map<String, Object>> items, GraphQLContext ctx) {
        return users(items, "decidedBy", ctx);
    }

    @BatchMapping(typeName = "ScoringModel", field = "createdBy")
    public Map<Map<String, Object>, Map<String, Object>> modelCreatedBy(List<Map<String, Object>> items, GraphQLContext ctx) {
        return users(items, "createdBy", ctx);
    }

    // ---------- отчётность и скоринг ----------

    @SchemaMapping(typeName = "FinancialStatement", field = "lines")
    public List<Map<String, Object>> lines(Map<String, Object> st, GraphQLContext ctx) {
        if (st.get("lines") instanceof List<?> l && !l.isEmpty()) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> loaded = (List<Map<String, Object>>) l;
            return loaded;
        }
        return Protos.list(statements.getLines(Ctx.actor(ctx), id(st)).getItemsList());
    }

    @SchemaMapping(typeName = "FinancialStatement", field = "issues")
    public List<Map<String, Object>> issues(Map<String, Object> st, GraphQLContext ctx) {
        if (st.get("issues") instanceof List<?> l && !l.isEmpty()) {
            @SuppressWarnings("unchecked") List<Map<String, Object>> loaded = (List<Map<String, Object>>) l;
            return loaded;
        }
        return Protos.list(statements.getIssues(Ctx.actor(ctx), id(st)).getItemsList());
    }

    @SchemaMapping(typeName = "ScoringResult", field = "model")
    public Map<String, Object> resultModel(Map<String, Object> result, GraphQLContext ctx) {
        return Protos.map(scoring.getModel(Ctx.actor(ctx), IdRequest.newBuilder().setId((String) result.get("modelId")).build()));
    }

    // ---------- вспомогательное ----------

    private Map<Map<String, Object>, Map<String, Object>> companies(List<Map<String, Object>> parents, String key, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        List<String> ids = parents.stream().map(p -> (String) p.get(key)).filter(i -> i != null).distinct().toList();
        Map<Map<String, Object>, Map<String, Object>> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<String, Map<String, Object>> byId = new HashMap<>();
        borrowers.getCompanies(actor, IdsRequest.newBuilder().addAllIds(ids).build()).getItemsList()
                .forEach(c -> byId.put(c.getId(), Protos.map(c)));
        parents.forEach(p -> out.put(p, byId.get((String) p.get(key))));
        return out;
    }

    /** Пользователи для связанных полей загружаются из Auth Service одним запросом. Заёмщикам данные сотрудников банка не выдаются. */
    private Map<Map<String, Object>, Map<String, Object>> users(List<Map<String, Object>> parents, String key, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        Map<Map<String, Object>, Map<String, Object>> out = new HashMap<>();
        if (actor.isBorrower()) {
            return out;
        }
        List<String> ids = parents.stream().map(p -> (String) p.get(key)).filter(i -> i != null).distinct().toList();
        if (ids.isEmpty()) {
            return out;
        }
        Map<String, Map<String, Object>> byId = new HashMap<>();
        auth.batch(Ctx.token(ctx), ids).forEach(u -> byId.put((String) u.get("id"), u));
        parents.forEach(p -> out.put(p, byId.get((String) p.get(key))));
        return out;
    }

    private static IdRequest id(Map<String, Object> obj) {
        return IdRequest.newBuilder().setId((String) obj.get("id")).build();
    }

    /** Отсутствие необязательных связанных данных (анализ, скоринг, заключение) - это null, а не ошибка. */
    private static <T> T optional(Supplier<T> action) {
        try {
            return action.get();
        } catch (AppException e) {
            if (e.code() == ErrorCode.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }
}
