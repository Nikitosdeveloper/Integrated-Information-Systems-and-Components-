package com.creditscoring.gateway.graphql;

import com.creditscoring.common.clients.AnalysisClient;
import com.creditscoring.common.clients.ApplicationClient;
import com.creditscoring.common.clients.BorrowerClient;
import com.creditscoring.common.clients.NotificationClient;
import com.creditscoring.common.clients.ScoringClient;
import com.creditscoring.common.clients.StatementClient;
import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.Empty;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.ListCompanyStatementsRequest;
import com.creditscoring.contracts.ListNotificationsRequest;
import com.creditscoring.contracts.ListProductsRequest;
import com.creditscoring.gateway.auth.AuthClient;
import graphql.GraphQLContext;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Query: проверка роли, передача параметров сервису через клиента, возврат результата.
 * Бизнес-логики и обращений к БД здесь нет.
 */
@Controller
public class QueryController {
    private final AuthClient auth;
    private final BorrowerClient borrowers;
    private final ApplicationClient applications;
    private final StatementClient statements;
    private final AnalysisClient analysis;
    private final ScoringClient scoring;
    private final NotificationClient notifications;

    public QueryController(AuthClient auth, BorrowerClient borrowers, ApplicationClient applications, StatementClient statements,
                           AnalysisClient analysis, ScoringClient scoring, NotificationClient notifications) {
        this.auth = auth;
        this.borrowers = borrowers;
        this.applications = applications;
        this.statements = statements;
        this.analysis = analysis;
        this.scoring = scoring;
        this.notifications = notifications;
    }

    @QueryMapping
    public Map<String, Object> me(GraphQLContext ctx) {
        Ctx.actor(ctx);
        return ctx.get("user");
    }

    /** Публичная операция: справочник не содержит персональных данных. */
    @QueryMapping
    public List<Map<String, Object>> industries() {
        return Protos.list(borrowers.listIndustries(Actor.SYSTEM, Empty.getDefaultInstance()).getItemsList());
    }

    @QueryMapping
    public List<Map<String, Object>> creditProducts(@Argument Boolean includeArchived, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        boolean archived = Boolean.TRUE.equals(includeArchived) && !actor.isBorrower();
        return Protos.list(applications.listCreditProducts(actor, ListProductsRequest.newBuilder().setIncludeArchived(archived).build()).getItemsList());
    }

    // ---------- компании ----------

    @QueryMapping
    public Map<String, Object> myCompany(GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        if (actor.companyId().isBlank()) {
            return null; // учётная запись ещё не привязана к компании
        }
        return Protos.map(borrowers.getCompany(actor, IdRequest.newBuilder().setId(actor.companyId()).build()));
    }

    @QueryMapping
    public Map<String, Object> company(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ANALYST, Roles.COMMITTEE, Roles.ADMIN);
        return Protos.map(borrowers.getCompany(actor, IdRequest.newBuilder().setId(id).build()));
    }

    // ---------- заявки ----------

    @QueryMapping
    public Map<String, Object> myApplications(@Argument Map<String, Object> filter, @Argument Map<String, Object> sort,
                                              @Argument Integer page, @Argument Integer pageSize, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        return Protos.map(applications.listApplications(actor, Requests.list("MY", filter, sort, page, pageSize)));
    }

    @QueryMapping
    public Map<String, Object> analystQueue(@Argument Map<String, Object> filter, @Argument Map<String, Object> sort,
                                            @Argument Integer page, @Argument Integer pageSize, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ANALYST);
        return Protos.map(applications.listApplications(actor, Requests.list("ANALYST_QUEUE", filter, sort, page, pageSize)));
    }

    @QueryMapping
    public Map<String, Object> committeeQueue(@Argument Map<String, Object> filter, @Argument Map<String, Object> sort,
                                              @Argument Integer page, @Argument Integer pageSize, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.COMMITTEE);
        return Protos.map(applications.listApplications(actor, Requests.list("COMMITTEE_QUEUE", filter, sort, page, pageSize)));
    }

    @QueryMapping
    public Map<String, Object> applications(@Argument Map<String, Object> filter, @Argument Map<String, Object> sort,
                                            @Argument Integer page, @Argument Integer pageSize, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ANALYST, Roles.COMMITTEE, Roles.ADMIN);
        return Protos.map(applications.listApplications(actor, Requests.list("ALL", filter, sort, page, pageSize)));
    }

    @QueryMapping
    public Map<String, Object> application(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        return Protos.map(applications.getApplication(actor, IdRequest.newBuilder().setId(id).build()));
    }

    @QueryMapping
    public Map<String, Object> applicationStats(@Argument String scope, @Argument Map<String, Object> filter, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        return Protos.map(applications.getApplicationStats(actor, Requests.stats(actor, scope, filter)));
    }

    // ---------- отчётность ----------

    @QueryMapping
    public List<Map<String, Object>> myStatements(GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        if (actor.companyId().isBlank()) {
            return List.of();
        }
        return Protos.list(statements.listCompanyStatements(actor, ListCompanyStatementsRequest.newBuilder()
                .setCompanyId(actor.companyId()).setIncludeLines(true).build()).getItemsList());
    }

    @QueryMapping
    public Map<String, Object> statement(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER, Roles.ANALYST, Roles.COMMITTEE);
        return Protos.map(statements.getStatement(actor, IdRequest.newBuilder().setId(id).build()));
    }

    // ---------- уведомления ----------

    @QueryMapping
    public List<Map<String, Object>> notifications(@Argument Boolean unreadOnly, @Argument Integer limit, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        return Protos.list(notifications.listNotifications(actor, ListNotificationsRequest.newBuilder()
                .setUnreadOnly(Boolean.TRUE.equals(unreadOnly)).setLimit(limit == null ? 20 : limit).build()).getItemsList());
    }

    // ---------- администрирование ----------

    @QueryMapping
    public Map<String, Object> users(@Argument String search, @Argument String role, @Argument String status,
                                     @Argument Integer page, @Argument Integer pageSize, GraphQLContext ctx) {
        Ctx.actor(ctx, Roles.ADMIN);
        return auth.users(Ctx.token(ctx), search, role, status, page == null ? 1 : page, pageSize == null ? 20 : pageSize);
    }

    @QueryMapping
    public List<Map<String, Object>> roles(GraphQLContext ctx) {
        Ctx.actor(ctx, Roles.ADMIN);
        return auth.roles(Ctx.token(ctx));
    }

    @QueryMapping
    public List<Map<String, Object>> scoringModels(GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ADMIN);
        return Protos.list(scoring.listModels(actor, Empty.getDefaultInstance()).getItemsList());
    }

    @QueryMapping
    public Map<String, Object> scoringModel(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ADMIN);
        return Protos.map(scoring.getModel(actor, IdRequest.newBuilder().setId(id).build()));
    }

    @QueryMapping
    public List<Map<String, Object>> ratioDefinitions(GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ADMIN);
        return Protos.list(analysis.listRatioDefinitions(actor, Empty.getDefaultInstance()).getItemsList());
    }

    @QueryMapping
    public List<Map<String, Object>> benchmarkSets(GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ADMIN);
        return Protos.list(analysis.listBenchmarkSets(actor, Empty.getDefaultInstance()).getItemsList());
    }

    @QueryMapping
    public Map<String, Object> benchmarkSet(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ADMIN);
        return Protos.map(analysis.getBenchmarkSet(actor, IdRequest.newBuilder().setId(id).build()));
    }
}
