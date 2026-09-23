package com.btc.hackathon.viewer.model;

/** Warum ein Match endete. */
public enum EndReason {
    /** Nur noch einer stand. */
    LAST_STANDING,
    /** Die Rundenzeit lief ab. */
    TIMEOUT,
    /** Die Moderation hat abgebrochen. */
    ABORTED,
    UNKNOWN;

    public static EndReason fromJson(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        return switch (name) {
            case "last_standing" -> LAST_STANDING;
            case "timeout" -> TIMEOUT;
            case "aborted" -> ABORTED;
            default -> UNKNOWN;
        };
    }

    public String label() {
        return switch (this) {
            case LAST_STANDING -> "Letzter Ueberlebender";
            case TIMEOUT -> "Zeit abgelaufen";
            case ABORTED -> "Abgebrochen";
            case UNKNOWN -> "";
        };
    }
}
