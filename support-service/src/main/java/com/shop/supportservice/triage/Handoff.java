package com.shop.supportservice.triage;

public record Handoff(boolean needsHuman, String reason) {

    static Handoff none() {
        return new Handoff(false, null);
    }

    static Handoff because(String reason) {
        return new Handoff(true, reason);
    }
}
