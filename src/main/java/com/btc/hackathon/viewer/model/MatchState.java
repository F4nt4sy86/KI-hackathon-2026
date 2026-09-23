package com.btc.hackathon.viewer.model;

import java.util.List;

/**
 * Der Spielzustand eines Ticks - 60 davon je Sekunde.
 *
 * <p>Spieler, Bomben, Flammen und Power-ups sind jedes Mal vollstaendig: die eigenen
 * Kopien werden ersetzt, nicht zusammengefuehrt. Nur das Kachelraster kommt nicht mit;
 * es wird aus {@link MatchInit} fortgeschrieben, siehe {@link #tileChanges()}.
 *
 * @param receivedAtNanos Zeitpunkt des Eintreffens. Der Renderer bildet daraus den
 *                        Bruchteil eines Ticks und bewegt die Figuren auch zwischen
 *                        zwei Zustaenden weiter - sonst ruckelt es auf Schirmen mit
 *                        mehr als 60 Hz.
 */
public record MatchState(
        long tick,
        long ticksRemaining,
        List<PlayerState> players,
        List<Bomb> bombs,
        List<Flame> flames,
        List<Powerup> powerups,
        List<TileChange> tileChanges,
        List<GameEvent> events,
        long receivedAtNanos) implements ServerMessage {

    public MatchState {
        players = List.copyOf(players);
        bombs = List.copyOf(bombs);
        flames = List.copyOf(flames);
        powerups = List.copyOf(powerups);
        tileChanges = List.copyOf(tileChanges);
        events = List.copyOf(events);
    }

    public PlayerState playerById(int id) {
        for (PlayerState p : players) {
            if (p.id() == id) {
                return p;
            }
        }
        return null;
    }

    /** Verbleibende Rundenzeit in Sekunden. */
    public long secondsRemaining(int tickRate) {
        return tickRate <= 0 ? 0 : ticksRemaining / tickRate;
    }
}
