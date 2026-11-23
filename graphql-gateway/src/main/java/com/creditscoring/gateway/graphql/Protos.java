package com.creditscoring.gateway.graphql;

import com.creditscoring.common.error.AppException;
import com.creditscoring.common.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Преобразование ответов сервисов (protobuf) в структуры для GraphQL. Пустые строки (значение не задано) становятся null. */
public final class Protos {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonFormat.Printer PRINTER = JsonFormat.printer().includingDefaultValueFields().omittingInsignificantWhitespace();

    public static Map<String, Object> map(Message message) {
        try {
            Map<String, Object> m = MAPPER.readValue(PRINTER.print(message), new TypeReference<>() { });
            return clean(m);
        } catch (InvalidProtocolBufferException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AppException(ErrorCode.INTERNAL, "Не удалось преобразовать ответ сервиса");
        }
    }

    public static List<Map<String, Object>> list(List<? extends Message> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        messages.forEach(m -> out.add(map(m)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> clean(Map<String, Object> m) {
        m.replaceAll((k, v) -> {
            if (v instanceof String s && s.isEmpty()) {
                return null;
            }
            if (v instanceof Map<?, ?> nested) {
                return clean((Map<String, Object>) nested);
            }
            if (v instanceof List<?> l) {
                return l.stream().map(i -> i instanceof Map<?, ?> im ? clean((Map<String, Object>) im) : i).toList();
            }
            return v;
        });
        return m;
    }

    private Protos() {
    }
}
