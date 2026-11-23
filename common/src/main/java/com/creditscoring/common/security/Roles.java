package com.creditscoring.common.security;

public final class Roles {
    public static final String ADMIN = "ADMIN";
    public static final String ANALYST = "ANALYST";
    public static final String COMMITTEE = "COMMITTEE";
    public static final String BORROWER = "BORROWER";
    /** Внутренний вызов без пользователя (обработка Kafka-событий). */
    public static final String SYSTEM = "SYSTEM";

    private Roles() {
    }
}
