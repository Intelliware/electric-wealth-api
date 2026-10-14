package com.electricwealth.api.web;

import java.util.List;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.NotificationDtos.Notification;
import com.electricwealth.api.dto.NotificationDtos.NotificationReadResponse;
import com.electricwealth.api.dto.NotificationDtos.NotificationsReadAllResponse;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.NotificationRepository;
import com.electricwealth.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "All notifications, newest first")
    @ApiResponse(responseCode = "200", description = "Notifications")
    @ApiErrors({ 401 })
    public List<Notification> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return notifications.list(user.userId());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    @ApiResponse(responseCode = "200", description = "Marked as read")
    @ApiErrors({ 401, 404 })
    public NotificationReadResponse markRead(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "no1") @PathVariable String id) {
        if (!notifications.markRead(user.userId(), id)) {
            throw ApiException.notFound("Notification not found.");
        }
        return new NotificationReadResponse(id, true);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark every notification as read")
    @ApiResponse(responseCode = "200", description = "Marked as read")
    @ApiErrors({ 401 })
    public NotificationsReadAllResponse markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return new NotificationsReadAllResponse(notifications.markAllRead(user.userId()));
    }
}
