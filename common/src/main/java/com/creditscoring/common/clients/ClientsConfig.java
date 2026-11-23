package com.creditscoring.common.clients;

import com.creditscoring.common.grpc.GrpcClientFactory;
import org.springframework.context.annotation.Import;

/**
 * Подключает gRPC-клиентов сервисов. Адреса задаются свойствами clients.&lt;сервис&gt;.host / .port.
 * Клиенты создаются только теми приложениями, которые импортируют нужный клиент.
 */
@Import(GrpcClientFactory.class)
public class ClientsConfig {
}
