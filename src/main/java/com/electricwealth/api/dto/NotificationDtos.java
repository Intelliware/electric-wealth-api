package com.electricwealth.api.dto;

import com.electricwealth.api.dto.ApiEnums.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response bodies for /api/notifications (schemas/notifications.ts in the Node API). */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record Notification(
            @Schema(example = "no1") String id,
            String title,
            String body,
            NotificationType type,
            String createdAt,
            boolean read) {
    }

    public record NotificationReadResponse(String id, @Schema(allowableValues = "true") boolean read) {
    }

    public record NotificationsReadAllResponse(
            @Schema(description = "How many notifications were unread") int updated) {
    }
}
