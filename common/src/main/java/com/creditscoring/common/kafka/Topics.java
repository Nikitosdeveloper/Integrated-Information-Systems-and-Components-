package com.creditscoring.common.kafka;

/** Топики и типы событий (payload-структуры из проектирования системы, ЛР2). */
public final class Topics {
    public static final String APPLICATION_EVENTS = "application.events";
    public static final String ANALYSIS_EVENTS = "analysis.events";
    public static final String SCORING_EVENTS = "scoring.events";

    // application.events
    public static final String APPLICATION_SUBMITTED = "APPLICATION_SUBMITTED";
    public static final String ANALYSIS_REQUESTED = "ANALYSIS_REQUESTED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String DECISION_MADE = "DECISION_MADE";
    // analysis.events
    public static final String ANALYSIS_COMPLETED = "ANALYSIS_COMPLETED";
    // scoring.events
    public static final String SCORING_COMPLETED = "SCORING_COMPLETED";

    private Topics() {
    }
}
