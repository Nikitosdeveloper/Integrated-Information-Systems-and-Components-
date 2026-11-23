package com.creditscoring.notification.grpc;

import com.creditscoring.common.grpc.Grpc;
import com.creditscoring.contracts.IdRequest;
import com.creditscoring.contracts.ListNotificationsRequest;
import com.creditscoring.contracts.Notification;
import com.creditscoring.contracts.NotificationList;
import com.creditscoring.contracts.NotificationServiceGrpc;
import com.creditscoring.notification.service.NotificationService;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class NotificationGrpcService extends NotificationServiceGrpc.NotificationServiceImplBase {
    private final NotificationService service;

    public NotificationGrpcService(NotificationService service) {
        this.service = service;
    }

    @Override
    public void listNotifications(ListNotificationsRequest r, StreamObserver<NotificationList> o) {
        Grpc.reply(o, () -> service.list(Grpc.actor(), r));
    }

    @Override
    public void markRead(IdRequest r, StreamObserver<Notification> o) {
        Grpc.reply(o, () -> service.markRead(Grpc.actor(), r.getId()));
    }
}
