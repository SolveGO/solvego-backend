package com.kdh.solvego.domain.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteCheckoutRequest(@NotBlank @Size(max = 300) String authKey,
                                      @NotBlank @Size(max = 50) String customerKey) {
    @Override public String toString() { return "CompleteCheckoutRequest[REDACTED]"; }
}
