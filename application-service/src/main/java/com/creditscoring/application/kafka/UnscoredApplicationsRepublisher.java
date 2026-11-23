package com.creditscoring.application.kafka;

import com.creditscoring.application.entity.CreditApplication;
import com.creditscoring.application.repository.CreditApplicationRepository;
import com.creditscoring.common.kafka.EventPublisher;
import com.creditscoring.common.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * При старте запрашивает расчёт для заявок, поданных, но ещё не получивших рейтинг
 * (тестовые данные, а также заявки, чей расчёт был потерян из-за сбоя).
 */
@Component
public class UnscoredApplicationsRepublisher implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(UnscoredApplicationsRepublisher.class);

    private final CreditApplicationRepository applications;
    private final EventPublisher events;
    private final boolean enabled;

    public UnscoredApplicationsRepublisher(CreditApplicationRepository applications, EventPublisher events,
                                           @Value("${app.republish-unscored-on-start:true}") boolean enabled) {
        this.applications = applications;
        this.events = events;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        List<CreditApplication> unscored = applications.findByLatestRatingIsNullAndStatusIn(List.of("REVIEW", "WAITING_DOCS", "COMMITTEE", "APPROVED", "REJECTED"));
        unscored.forEach(a -> events.publish(Topics.APPLICATION_EVENTS, Topics.ANALYSIS_REQUESTED, a.getId().toString(), a.getCompanyId().toString(),
                Map.of("companyId", a.getCompanyId().toString(), "companyName", a.getCompanyName(), "industryCode", a.getIndustryCode(),
                        "amount", a.getAmount().doubleValue())));
        log.info("STARTUP запрошен расчёт скоринга для {} заявок без рейтинга", unscored.size());
    }
}
