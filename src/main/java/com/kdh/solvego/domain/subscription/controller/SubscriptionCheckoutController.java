package com.kdh.solvego.domain.subscription.controller;

import com.kdh.solvego.domain.subscription.billing.InitialSubscriptionService;
import com.kdh.solvego.domain.subscription.dto.CheckoutResponse;
import com.kdh.solvego.domain.subscription.dto.CompleteCheckoutRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscriptions/pro/checkouts")
public class SubscriptionCheckoutController {
    private final InitialSubscriptionService service;

    public SubscriptionCheckoutController(InitialSubscriptionService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CheckoutResponse> prepare(Authentication authentication) {
        return response(service.prepare((Long) authentication.getPrincipal()));
    }

    @PostMapping("/{orderId}/complete")
    public ResponseEntity<CheckoutResponse> complete(Authentication authentication, @PathVariable String orderId,
            @Valid @RequestBody CompleteCheckoutRequest request) {
        return response(service.complete((Long) authentication.getPrincipal(), orderId, request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<CheckoutResponse> status(Authentication authentication, @PathVariable String orderId) {
        return response(service.status((Long) authentication.getPrincipal(), orderId));
    }

    private ResponseEntity<CheckoutResponse> response(CheckoutResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
