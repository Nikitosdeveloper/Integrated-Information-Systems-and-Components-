package com.creditscoring.common.grpc;

import io.grpc.Context;
import io.grpc.Metadata;

public final class Meta {
    public static final Metadata.Key<String> USER_ID = key("x-user-id");
    public static final Metadata.Key<String> USER_EMAIL = key("x-user-email");
    public static final Metadata.Key<String> USER_ROLE = key("x-user-role");
    public static final Metadata.Key<String> COMPANY_ID = key("x-company-id");
    public static final Metadata.Key<String> CORRELATION_ID = key("x-correlation-id");

    public static final Context.Key<com.creditscoring.common.security.Actor> ACTOR_CTX = Context.key("actor");
    public static final Context.Key<String> CID_CTX = Context.key("cid");

    public static final String MDC_CID = "cid";

    private static Metadata.Key<String> key(String name) {
        return Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
    }

    private Meta() {
    }
}
