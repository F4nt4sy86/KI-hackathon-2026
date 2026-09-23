package com.btc.hackathon.viewer.state;

import com.btc.hackathon.viewer.model.Board;
import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.model.MatchEnd;
import com.btc.hackathon.viewer.model.MatchInit;
import com.btc.hackathon.viewer.model.MatchState;

/**
 * Der gesamte darstellbare Zustand in einem unveraenderlichen Objekt.
 *
 * <p>Die Teile treffen als getrennte Nachrichten ein und ueberschreiben einander nicht:
 * waehrend eines Matches bleibt die Lobby-Aufstellung erhalten, damit die
 * Teilnehmertabelle weiter Namen und Gesundheitswerte zeigt.
 *
 * <p>{@code board} ist der <b>fortgeschriebene</b> Stand des Kachelrasters. Der Server
 * schickt das Raster nur einmal in {@code match_init}; jede spaetere Aenderung kommt als
 * Delta und wird hier eingearbeitet. Wer stattdessen immer wieder das urspruengliche
 * Raster zeichnet, sieht zerstoerte Kisten nach einiger Zeit wieder auftauchen.
 *
 * <p>Instanzen werden vom Netzthread erzeugt und vom JavaFX-Thread gelesen. Sie sind
 * deshalb strikt unveraenderlich; die Sichtbarkeit zwischen den Threads stellt die
 * {@link java.util.concurrent.atomic.AtomicReference} im {@link ViewStateStore} her.
 *
 * @param preview Ergebnis eines {@code preview_map}-Befehls, sonst {@code null}
 */
public record ViewState(
        LobbyState lobby,
        MatchInit matchInit,
        Board board,
        MatchState state,
        MatchEnd result,
        MatchInit preview) {

    /** Ausgangszustand, solange nichts empfangen wurde. */
    public static ViewState initial() {
        return new ViewState(LobbyState.waiting(), null, null, null, null, null);
    }

    public LobbyPhase phase() {
        return lobby.phase();
    }

    public boolean paused() {
        return lobby.paused();
    }

    public boolean hasBoard() {
        return board != null;
    }

    public boolean hasState() {
        return state != null;
    }

    public boolean hasResult() {
        return result != null;
    }

    public boolean hasPreview() {
        return preview != null;
    }

    /** Taktrate des Servers; die Lobby-Nachricht ist die verlaesslichere Quelle. */
    public int tickRate() {
        if (matchInit != null && matchInit.tickRate() > 0) {
            return matchInit.tickRate();
        }
        return lobby.tickRate() > 0 ? lobby.tickRate() : 60;
    }

    /**
     * Anzeigename eines Spielers.
     *
     * <p>Zuerst aus der Lobby, weil die Moderation den Namen dort jederzeit aendern kann;
     * {@code match_init} traegt nur den Stand vom Matchbeginn.
     */
    public String nameOf(int playerId) {
        if (lobby.slotById(playerId) != null) {
            return lobby.nameOf(playerId);
        }
        return matchInit != null ? matchInit.nameOf(playerId) : "bot-" + playerId;
    }

    // ------------------------------------------------------------ Fortschreiben

    public ViewState withLobby(LobbyState newLobby) {
        return new ViewState(newLobby, matchInit, board, state, result, preview);
    }

    /** Ein neues Match beginnt: Raster uebernehmen, altes Ergebnis wegraeumen. */
    public ViewState withMatchInit(MatchInit init) {
        return new ViewState(lobby, init, init.board(), null, null, preview);
    }

    /** Ein Tick: Raster fortschreiben und den neuen Spielzustand uebernehmen. */
    public ViewState withState(MatchState newState) {
        Board updated = board == null ? null : board.withChanges(newState.tileChanges());
        return new ViewState(lobby, matchInit, updated, newState, result, preview);
    }

    public ViewState withResult(MatchEnd newResult) {
        return new ViewState(lobby, matchInit, board, state, newResult, preview);
    }

    public ViewState withPreview(MatchInit newPreview) {
        return new ViewState(lobby, matchInit, board, state, result, newPreview);
    }

    public ViewState withoutPreview() {
        return new ViewState(lobby, matchInit, board, state, result, null);
    }
}
