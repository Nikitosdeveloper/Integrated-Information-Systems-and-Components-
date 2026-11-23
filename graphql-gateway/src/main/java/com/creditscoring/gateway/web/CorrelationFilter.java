package com.creditscoring.gateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Присваивает каждому запросу сквозной correlation id (или принимает X-Correlation-Id клиента).
 * Он попадает в MDC, во все gRPC-вызовы и Kafka-сообщения, что позволяет проследить сценарий по логам всех сервисов.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("http");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cid = request.getHeader("X-Correlation-Id");
        if (cid == null || cid.isBlank() || cid.length() > 64 || !cid.matches("[A-Za-z0-9_.-]+")) {
            cid = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put("cid", cid);
        response.setHeader("X-Correlation-Id", cid);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/graphiql")) {
                log.info("HTTP {} {} -> {} {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        (System.nanoTime() - started) / 1_000_000);
            }
            MDC.remove("cid");
        }
    }
}
