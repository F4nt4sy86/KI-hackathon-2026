package com.btc.hackathon.viewer.model;

import java.util.List;

/**
 * Aufstellung und Ablaufzustand.
 *
 * <p>Kommt beim Verbinden, bei jeder Aenderung und zusaetzlich zweimal pro Sekunde.
 *
 * @param canStart die Antwort des Servers auf "wuerde Start jetzt durchgehen". Die
 *                 Oberflaeche benutzt dieses Flag, statt die Regel selbst herzuleiten -
 *                 sonst driften Anzeige und tatsaechliches Verhalten auseinander.
 */
public record LobbyState(
        LobbyPhase phase,
        boolean paused,
        boolean canStart,
        int minPlayers,
        int maxPlayers,
        int countdownTicks,
        int tickRate,
        MapSettings map,
        List<Slot> slots) implements ServerMessage {

    public LobbyState {
        slots = List.copyOf(slots);
    }

    /** Ausgangszustand, solange nichts empfangen wurde. */
    public static LobbyState waiting() {
        return new LobbyState(LobbyPhase.UNKNOWN, false, false, 2, 4, 0, 60,
                MapSettings.unknown(), List.of());
    }

    public int connectedCount() {
        return (int) slots.stream().filter(Slot::connected).count();
    }

    public Slot slotById(int id) {
        for (Slot s : slots) {
            if (s.id() == id) {
                return s;
            }
        }
        return null;
    }

    /** Name eines Spielers, oder ein Ersatzname, wenn der Platz unbekannt ist. */
    public String nameOf(int playerId) {
        Slot slot = slotById(playerId);
        return slot == null ? "bot-" + playerId : slot.displayName();
    }
}
