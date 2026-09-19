package com.kdh.solvego.domain.subscription.controller;

import com.kdh.solvego.domain.subscription.dto.SubscriptionResponse;
import com.kdh.solvego.domain.subscription.service.SubscriptionManagementService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions/me")
public class SubscriptionController {
    private final SubscriptionManagementService service;

    public SubscriptionController(SubscriptionManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SubscriptionResponse> current(Authentication authentication) {
        return response(service.current((Long) authentication.getPrincipal()));
    }

    @PostMapping("/cancel-at-period-end")
    public ResponseEntity<SubscriptionResponse> cancelAtPeriodEnd(Authentication authentication) {
        return response(service.cancelAtPeriodEnd((Long) authentication.getPrincipal()));
    }

    @PostMapping("/reactivate")
    public ResponseEntity<SubscriptionResponse> reactivate(Authentication authentication) {
        return response(service.reactivate((Long) authentication.getPrincipal()));
    }

    private ResponseEntity<SubscriptionResponse> response(SubscriptionResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
