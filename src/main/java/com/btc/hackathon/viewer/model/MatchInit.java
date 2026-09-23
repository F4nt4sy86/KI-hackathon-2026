package com.btc.hackathon.viewer.model;

import java.util.List;

/**
 * Das unveraenderliche Bild eines Matches: Feld, Startplaetze, Regeln.
 *
 * <p>Kommt einmal beim Matchstart - und beim Verbinden noch einmal, wenn schon ein
 * Match laeuft. Beides muss die Anwendung zu jedem Zeitpunkt verkraften, nicht nur
 * am Matchbeginn.
 *
 * <p>Wichtig: <b>das Kachelraster wird danach nie wieder vollstaendig geschickt.</b>
 * Aenderungen kommen ausschliesslich als {@code tile_changes} in {@link MatchState}.
 *
 * @param seed als Zeichenkette - ein 64-Bit-Wert verloere als JSON-Zahl an Genauigkeit
 */
public record MatchInit(
        long matchId,
        String seed,
        int tickRate,
        Board board,
        List<int[]> spawns,
        List<MatchPlayer> players,
        Rules rules) implements ServerMessage {

    public MatchInit {
        seed = seed == null ? "0" : seed;
        spawns = List.copyOf(spawns);
        players = List.copyOf(players);
        rules = rules == null ? Rules.defaults() : rules;
    }

    public String nameOf(int playerId) {
        for (MatchPlayer p : players) {
            if (p.id() == playerId && !p.name().isBlank()) {
                return p.name();
            }
        }
        return "bot-" + playerId;
    }
}
