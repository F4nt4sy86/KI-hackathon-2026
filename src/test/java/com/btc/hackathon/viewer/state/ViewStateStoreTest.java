package com.btc.hackathon.viewer.state;

import com.btc.hackathon.viewer.model.Board;
import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.model.EndReason;
import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.model.MapSettings;
import com.btc.hackathon.viewer.model.MatchEnd;
import com.btc.hackathon.viewer.model.MatchInit;
import com.btc.hackathon.viewer.model.MatchState;
import com.btc.hackathon.viewer.model.Rules;
import com.btc.hackathon.viewer.model.Slot;
import com.btc.hackathon.viewer.model.Tile;
import com.btc.hackathon.viewer.model.TileChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueft die Uebergabestelle zwischen Netz- und Zeichenthread. */
class ViewStateStoreTest {

    private final ViewStateStore store = new ViewStateStore();

    private static LobbyState lobby(LobbyPhase phase) {
        return new LobbyState(phase, false, phase == LobbyPhase.OPEN, 2, 4, 0, 60,
                MapSettings.unknown(),
                List.of(new Slot(0, "team-rocket", true, "1.2.3.4:5", 60, 0, 1, 0, false)));
    }

    /** 3x3, alles Kisten ausser der Mitte. */
    private static MatchInit matchInit(long id) {
        byte[] tiles = new byte[9];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = (byte) Tile.SOFT.code();
        }
        tiles[4] = (byte) Tile.EMPTY.code();
        return new MatchInit(id, "42", 60, new Board(3, 3, tiles), List.of(), List.of(),
                Rules.defaults());
    }

    private static MatchState state(long tick, List<TileChange> changes) {
        return new MatchState(tick, 100, List.of(), List.of(), List.of(), List.of(),
                changes, List.of(), System.nanoTime());
    }

    @Test
    @DisplayName("Ausgangszustand ist die leere Lobby")
    void startsEmpty() {
        ViewState view = store.current();

        assertSame(LobbyPhase.UNKNOWN, view.phase());
        assertFalse(view.hasBoard());
        assertFalse(view.hasState());
        assertFalse(view.hasResult());
    }

    @Test
    @DisplayName("match_init legt das Spielfeld an")
    void matchInitEstablishesTheBoard() {
        store.accept(matchInit(1));

        assertTrue(store.current().hasBoard());
        assertEquals(3, store.current().board().width());
        assertSame(Tile.SOFT, store.current().board().tileAt(0, 0));
    }

    @Test
    @DisplayName("Kachelaenderungen werden auf das Spielfeld fortgeschrieben")
    void tileChangesAreApplied() {
        store.accept(matchInit(1));

        store.accept(state(1, List.of(new TileChange(0, 0, Tile.EMPTY))));

        assertSame(Tile.EMPTY, store.current().board().tileAt(0, 0));
    }

    @Test
    @DisplayName("Zerstoerte Kisten tauchen nicht wieder auf")
    void destroyedCratesStayDestroyed() {
        // Genau diese Falle nennt der Server-Leitfaden: das Raster kommt nur einmal, und
        // wer die Deltas nicht fortschreibt, sieht die Kiste beim naechsten Bild wieder.
        store.accept(matchInit(1));
        store.accept(state(1, List.of(new TileChange(0, 0, Tile.EMPTY))));

        for (int tick = 2; tick < 50; tick++) {
            store.accept(state(tick, List.of()));
        }

        assertSame(Tile.EMPTY, store.current().board().tileAt(0, 0));
    }

    @Test
    @DisplayName("Ein Tick ohne Aenderung erzeugt keine Kopie des Rasters")
    void unchangedBoardIsNotCopied() {
        // tile_changes ist fast immer leer; bei 60 Zustaenden je Sekunde waere eine Kopie
        // je Tick unnoetiger Aufwand.
        store.accept(matchInit(1));
        Board before = store.current().board();

        store.accept(state(1, List.of()));

        assertSame(before, store.current().board());
    }

    @Test
    @DisplayName("Ein neues Match raeumt das alte Ergebnis weg")
    void newMatchClearsTheOldResult() {
        store.accept(matchInit(1));
        store.accept(new MatchEnd(10, EndReason.LAST_STANDING, 0, List.of()));
        assertTrue(store.current().hasResult());

        store.accept(matchInit(2));

        assertFalse(store.current().hasResult());
        assertFalse(store.current().hasState());
    }

    @Test
    @DisplayName("Die Lobby ueberlebt den Matchbeginn")
    void lobbySurvivesIntoTheMatch() {
        store.accept(lobby(LobbyPhase.OPEN));
        store.accept(matchInit(1));
        store.accept(lobby(LobbyPhase.RUNNING));
        store.accept(state(1, List.of()));

        // Waehrend des Matches braucht die Tabelle weiter Namen und Messwerte.
        assertEquals(1, store.current().lobby().slots().size());
        assertSame(LobbyPhase.RUNNING, store.current().phase());
    }

    @Test
    @DisplayName("Der Name kommt aus der Lobby, weil er dort geaendert werden kann")
    void nameComesFromTheLobby() {
        store.accept(lobby(LobbyPhase.RUNNING));

        assertEquals("team-rocket", store.current().nameOf(0));
        // Fuer einen unbekannten Platz bleibt ein brauchbarer Ersatzname.
        assertEquals("bot-9", store.current().nameOf(9));
    }

    @Test
    @DisplayName("Antworten auf Befehle gehoeren nicht in die Darstellung")
    void commandRepliesDoNotTouchTheState() {
        store.accept(matchInit(1));

        assertFalse(store.accept(CommandReply.ack("start")));

        assertTrue(store.current().hasBoard());
    }

    @Test
    @DisplayName("Die Kartenvorschau laesst sich anzeigen und wieder wegraeumen")
    void previewCanBeShownAndCleared() {
        store.accept(new com.btc.hackathon.viewer.model.MapPreview(matchInit(0)));
        assertTrue(store.current().hasPreview());

        store.clearPreview();

        assertFalse(store.current().hasPreview());
    }

    @Test
    @DisplayName("Zuruecksetzen leert den gesamten Zustand")
    void resetClearsEverything() {
        store.accept(lobby(LobbyPhase.RUNNING));
        store.accept(matchInit(1));
        store.accept(state(1, List.of()));

        store.reset();

        assertNull(store.current().board());
        assertNull(store.current().state());
        assertSame(LobbyPhase.UNKNOWN, store.current().phase());
    }

    // ------------------------------------------------------------------ Zwischentakt

    @Test
    @DisplayName("Ohne Zustand gibt es keinen Zwischentakt")
    void noSubTickWithoutState() {
        assertEquals(0, store.subTickProgress(System.nanoTime(), 60), 1e-9);
    }

    @Test
    @DisplayName("Der Zwischentakt waechst mit der Zeit und bleibt bei eins stehen")
    void subTickGrowsAndIsCapped() {
        store.accept(state(1, List.of()));
        long received = store.current().state().receivedAtNanos();
        long tickNanos = 1_000_000_000L / 60;

        assertEquals(0.5, store.subTickProgress(received + tickNanos / 2, 60), 0.01);
        assertEquals(1.0, store.subTickProgress(received + tickNanos, 60), 0.01);
        // Bei stockender Verbindung nicht weiterlaufen lassen.
        assertEquals(1.0, store.subTickProgress(received + 50 * tickNanos, 60), 1e-9);
    }

    @Test
    @DisplayName("Eine Taktrate von null fuehrt nicht zu einer Division durch null")
    void zeroTickRateIsHandled() {
        store.accept(state(1, List.of()));

        assertEquals(0, store.subTickProgress(System.nanoTime(), 0), 1e-9);
    }
}
