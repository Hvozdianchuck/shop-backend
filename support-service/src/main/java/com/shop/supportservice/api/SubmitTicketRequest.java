package com.shop.supportservice.api;

import jakarta.validation.constraints.NotBlank;

public record SubmitTicketRequest(@NotBlank String text) {
}
