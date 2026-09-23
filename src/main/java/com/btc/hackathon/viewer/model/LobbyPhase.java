package com.btc.hackathon.viewer.model;

/**
 * Ablaufzustand des Servers.
 *
 * <p>Achtung: "pausiert" ist <em>keine</em> Phase, sondern ein eigenes Flag neben
 * diesem Zustand - siehe {@link LobbyState#paused()}. Ein pausiertes Match steht
 * weiterhin auf {@link #RUNNING}.
 */
public enum LobbyPhase {
    /** Nimmt neue Bots auf. */
    OPEN,
    /** Aufstellung eingefroren, keine neuen Bots. */
    LOCKED,
    /** Countdown bis zum Matchbeginn laeuft. */
    COUNTDOWN,
    RUNNING,
    /** Match beendet, Ergebnis steht - bis die Moderation zuruecksetzt. */
    MATCH_OVER,
    UNKNOWN;

    public static LobbyPhase fromJson(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        return switch (name) {
            case "open" -> OPEN;
            case "locked" -> LOCKED;
            case "countdown" -> COUNTDOWN;
            case "running" -> RUNNING;
            case "match_over" -> MATCH_OVER;
            default -> UNKNOWN;
        };
    }

    /** Beschriftung fuer die Anzeige. */
    public String label() {
        return switch (this) {
            case OPEN -> "Lobby offen";
            case LOCKED -> "Lobby gesperrt";
            case COUNTDOWN -> "Countdown";
            case RUNNING -> "Match laeuft";
            case MATCH_OVER -> "Match beendet";
            case UNKNOWN -> "unbekannt";
        };
    }

    /** In diesen Phasen wird das Spielfeld gezeichnet, nicht die Lobby. */
    public boolean showsBoard() {
        return this == RUNNING || this == MATCH_OVER;
    }
}
