package com.creditscoring.gateway.graphql;

import com.creditscoring.common.clients.AnalysisClient;
import com.creditscoring.common.clients.ApplicationClient;
import com.creditscoring.common.clients.BorrowerClient;
import com.creditscoring.common.clients.NotificationClient;
import com.creditscoring.common.clients.ScoringClient;
import com.creditscoring.common.clients.StatementClient;
import com.creditscoring.common.security.Actor;
import com.creditscoring.common.security.Roles;
import com.creditscoring.contracts.CancelApplicationRequest;
import com.creditscoring.contracts.CreateApplicationRequest;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.MakeDecisionRequest;
import com.creditscoring.contracts.ProvideDocumentRequest;
import com.creditscoring.contracts.RequestDocumentsRequest;
import com.creditscoring.contracts.RequestedItem;
import com.creditscoring.contracts.SaveConclusionRequest;
import com.creditscoring.contracts.StatementLine;
import com.creditscoring.contracts.UpdateCompanyProfileRequest;
import com.creditscoring.contracts.UploadStatementRequest;
import com.creditscoring.gateway.auth.AuthClient;
import graphql.GraphQLContext;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

/**
 * Mutation - бизнес-операции. Контроллер проверяет роль и передаёт выполнение сервису;
 * проверки бизнес-условий и изменение состояния выполняются внутри сервисов.
 */
@Controller
public class MutationController {
    private final AuthClient auth;
    private final BorrowerClient borrowers;
    private final ApplicationClient applications;
    private final StatementClient statements;
    private final AnalysisClient analysis;
    private final ScoringClient scoring;
    private final NotificationClient notifications;

    public MutationController(AuthClient auth, BorrowerClient borrowers, ApplicationClient applications, StatementClient statements,
                              AnalysisClient analysis, ScoringClient scoring, NotificationClient notifications) {
        this.auth = auth;
        this.borrowers = borrowers;
        this.applications = applications;
        this.statements = statements;
        this.analysis = analysis;
        this.scoring = scoring;
        this.notifications = notifications;
    }

    // ---------- аутентификация (публичные операции, кроме logout) ----------

    @MutationMapping
    public Map<String, Object> login(@Argument String email, @Argument String password) {
        return auth.login(email, password);
    }

    @MutationMapping
    public Map<String, Object> register(@Argument Map<String, Object> input) {
        return auth.register((String) input.get("email"), (String) input.get("password"), (String) input.get("fullName"));
    }

    @MutationMapping
    public boolean logout(GraphQLContext ctx) {
        Ctx.actor(ctx);
        auth.logout(Ctx.token(ctx));
        return true;
    }

    // ---------- заёмщик ----------

    @MutationMapping
    public Map<String, Object> updateCompanyProfile(@Argument Map<String, Object> input, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        UpdateCompanyProfileRequest.Builder b = UpdateCompanyProfileRequest.newBuilder().setCompanyId(actor.companyId());
        if (input.get("address") != null) {
            b.setAddress((String) input.get("address"));
        }
        if (input.get("phone") != null) {
            b.setPhone((String) input.get("phone"));
        }
        return Protos.map(borrowers.updateCompanyProfile(actor, b.build()));
    }

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> uploadStatement(@Argument Map<String, Object> input, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        UploadStatementRequest.Builder b = UploadStatementRequest.newBuilder().setCompanyId(actor.companyId())
                .setFormType((String) input.get("formType")).setPeriodYear((Integer) input.get("periodYear"));
        for (Map<String, Object> l : (List<Map<String, Object>>) input.get("lines")) {
            b.addLines(StatementLine.newBuilder().setCode((String) l.get("code")).setName(l.get("name") == null ? "" : (String) l.get("name"))
                    .setValue(((Number) l.get("value")).doubleValue()));
        }
        return Protos.map(statements.uploadStatement(actor, b.build()));
    }

    @MutationMapping
    public Map<String, Object> createApplication(@Argument Map<String, Object> input, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        return Protos.map(applications.createApplication(actor, CreateApplicationRequest.newBuilder()
                .setProductId((String) input.get("productId")).setAmount(((Number) input.get("amount")).doubleValue())
                .setTermMonths((Integer) input.get("termMonths")).setPurpose((String) input.get("purpose"))
                .setCollateral(input.get("collateral") == null ? "" : (String) input.get("collateral")).build()));
    }

    @MutationMapping
    public Map<String, Object> submitApplication(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        return Protos.map(applications.submitApplication(actor, IdRequest.newBuilder().setId(id).build()));
    }

    @MutationMapping
    public Map<String, Object> cancelApplication(@Argument String id, @Argument String reason, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        return Protos.map(applications.cancelApplication(actor, CancelApplicationRequest.newBuilder().setId(id)
                .setReason(reason == null ? "" : reason).build()));
    }

    @MutationMapping
    public Map<String, Object> provideDocument(@Argument String itemId, @Argument String fileName, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.BORROWER);
        return Protos.map(applications.provideDocument(actor, ProvideDocumentRequest.newBuilder().setItemId(itemId).setFileName(fileName).build()));
    }

    // ---------- аналитик ----------

    @MutationMapping
    public Map<String, Object> assignApplicationToMe(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ANALYST);
        return Protos.map(applications.assignToMe(actor, IdRequest.newBuilder().setId(id).build()));
    }

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestDocuments(@Argument Map<String, Object> input, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ANALYST);
        RequestDocumentsRequest.Builder b = RequestDocumentsRequest.newBuilder().setApplicationId((String) input.get("applicationId"))
                .setDueDate((String) input.get("dueDate"));
        for (Map<String, Object> i : (List<Map<String, Object>>) input.get("items")) {
            b.addItems(RequestedItem.newBuilder().setTitle((String) i.get("title"))
                    .setIsRequired(i.get("isRequired") == null || (Boolean) i.get("isRequired")));
        }
        return Protos.map(applications.requestDocuments(actor, b.build()));
    }

    @MutationMapping
    public Map<String, Object> saveConclusion(@Argument Map<String, Object> input, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ANALYST);
        return Protos.map(applications.saveConclusion(actor, SaveConclusionRequest.newBuilder()
                .setApplicationId((String) input.get("applicationId")).setText((String) input.get("text"))
                .setRecommendation((String) input.get("recommendation")).build()));
    }

    @MutationMapping
    public Map<String, Object> sendToCommittee(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ANALYST);
        return Protos.map(applications.sendToCommittee(actor, IdRequest.newBuilder().setId(id).build()));
    }

    @MutationMapping
    public boolean recalculateScoring(@Argument String applicationId, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ANALYST, Roles.ADMIN);
        return applications.recalculateScoring(actor, IdRequest.newBuilder().setId(applicationId).build()).getOk();
    }

    // ---------- кредитный комитет ----------

    @MutationMapping
    public Map<String, Object> makeDecision(@Argument Map<String, Object> input, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.COMMITTEE);
        MakeDecisionRequest.Builder b = MakeDecisionRequest.newBuilder().setApplicationId((String) input.get("applicationId"))
                .setDecision((String) input.get("decision")).setComment(input.get("comment") == null ? "" : (String) input.get("comment"));
        if (input.get("approvedAmount") != null) {
            b.setApprovedAmount(((Number) input.get("approvedAmount")).doubleValue());
        }
        return Protos.map(applications.makeDecision(actor, b.build()));
    }

    // ---------- уведомления ----------

    @MutationMapping
    public Map<String, Object> markNotificationRead(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx);
        return Protos.map(notifications.markRead(actor, IdRequest.newBuilder().setId(id).build()));
    }

    // ---------- администратор ----------

    @MutationMapping
    public Map<String, Object> blockUser(@Argument String id, GraphQLContext ctx) {
        Ctx.actor(ctx, Roles.ADMIN);
        return auth.put(Ctx.token(ctx), "/users/" + id + "/block", null);
    }

    @MutationMapping
    public Map<String, Object> unblockUser(@Argument String id, GraphQLContext ctx) {
        Ctx.actor(ctx, Roles.ADMIN);
        return auth.put(Ctx.token(ctx), "/users/" + id + "/unblock", null);
    }

    @MutationMapping
    public Map<String, Object> changeUserRole(@Argument String id, @Argument String role, GraphQLContext ctx) {
        Ctx.actor(ctx, Roles.ADMIN);
        return auth.put(Ctx.token(ctx), "/users/" + id + "/role", Map.of("role", role));
    }

    @MutationMapping
    public Map<String, Object> assignUserCompany(@Argument String userId, @Argument String companyId, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ADMIN);
        // компания должна существовать в Borrower Service
        borrowers.getCompany(actor, IdRequest.newBuilder().setId(companyId).build());
        return auth.put(Ctx.token(ctx), "/users/" + userId + "/company", Map.of("companyId", companyId));
    }

    @MutationMapping
    public Map<String, Object> activateScoringModel(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ADMIN);
        return Protos.map(scoring.activateModel(actor, IdRequest.newBuilder().setId(id).build()));
    }

    @MutationMapping
    public Map<String, Object> activateBenchmarkSet(@Argument String id, GraphQLContext ctx) {
        Actor actor = Ctx.actor(ctx, Roles.ADMIN);
        return Protos.map(analysis.activateBenchmarkSet(actor, IdRequest.newBuilder().setId(id).build()));
    }
}
