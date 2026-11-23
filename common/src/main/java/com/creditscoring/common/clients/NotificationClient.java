package com.creditscoring.common.clients;

import com.creditscoring.common.grpc.GrpcClientFactory;
import com.creditscoring.common.grpc.ServiceCaller;
import com.creditscoring.common.security.Actor;
import com.creditscoring.contracts.NotificationServiceGrpc;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.ListNotificationsRequest;
import com.creditscoring.contracts.Notification;
import com.creditscoring.contracts.NotificationList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** gRPC-клиент notification: вызовы с timeout, ограниченным retry и логированием (см. ServiceCaller). */
@Component
public class NotificationClient {
    private final ServiceCaller<NotificationServiceGrpc.NotificationServiceBlockingStub> caller;

    public NotificationClient(GrpcClientFactory factory,
                              @Value("${clients.notification.host:localhost}") String host,
                              @Value("${clients.notification.port:9106}") int port) {
        this.caller = factory.create("notification", host, port, NotificationServiceGrpc::newBlockingStub);
    }

    public NotificationList listNotifications(Actor actor, ListNotificationsRequest request) {
        return caller.call("listNotifications", actor, true, s -> s.listNotifications(request));
    }

    public Notification markRead(Actor actor, IdRequest request) {
        return caller.call("markRead", actor, false, s -> s.markRead(request));
    }
}
