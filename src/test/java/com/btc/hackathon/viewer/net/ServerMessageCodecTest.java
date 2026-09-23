package com.btc.hackathon.viewer.net;

import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.model.Direction;
import com.btc.hackathon.viewer.model.EndReason;
import com.btc.hackathon.viewer.model.GameEvent;
import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.model.MapPreview;
import com.btc.hackathon.viewer.model.MatchEnd;
import com.btc.hackathon.viewer.model.MatchInit;
import com.btc.hackathon.viewer.model.MatchState;
import com.btc.hackathon.viewer.model.PlayerState;
import com.btc.hackathon.viewer.model.PowerupKind;
import com.btc.hackathon.viewer.model.ServerMessage;
import com.btc.hackathon.viewer.model.Slot;
import com.btc.hackathon.viewer.model.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Prueft das Einlesen des Server-JSON.
 *
 * <p>Die Beispiele sind woertlich aus {@code VISUALIZER_GUIDE.md} des Servers uebernommen.
 * Damit schlaegt dieser Test an, wenn sich das Protokoll aendert - und nicht erst die
 * Anzeige auf dem Beamer.
 */
class ServerMessageCodecTest {

    private final ServerMessageCodec codec = new ServerMessageCodec();

    private ServerMessage parse(String json) throws ProtocolException {
        return codec.parse(json).orElseThrow(() -> new AssertionError("Nachricht wurde verworfen"));
    }

    // --------------------------------------------------------------------- lobby

    private static final String LOBBY_JSON = """
            {
              "type": "lobby",
              "state": "open",
              "paused": false,
              "can_start": true,
              "min_players": 2,
              "max_players": 4,
              "countdown_ticks": 0,
              "tick_rate": 60,
              "map": { "width": 15, "height": 13, "density": 0.75,
                       "symmetry": "quad", "seed": "0" },
              "slots": [
                { "id": 0, "name": "bot-0", "connected": true, "addr": "10.12.3.44:51234",
                  "packets_per_sec": 60, "loss_pct": 0.4, "last_seen_tick": 1233,
                  "stale_ms": 16, "stale": false },
                { "id": 1, "name": "bot-1", "connected": false, "addr": null,
                  "packets_per_sec": 0, "loss_pct": 0, "last_seen_tick": null,
                  "stale_ms": null, "stale": false }
              ]
            }
            """;

    @Test
    @DisplayName("Lobby-Nachricht wird vollstaendig gelesen")
    void lobbyIsParsed() throws Exception {
        LobbyState lobby = (LobbyState) parse(LOBBY_JSON);

        assertSame(LobbyPhase.OPEN, lobby.phase());
        assertFalse(lobby.paused());
        assertTrue(lobby.canStart());
        assertEquals(2, lobby.minPlayers());
        assertEquals(4, lobby.maxPlayers());
        assertEquals(60, lobby.tickRate());
        assertEquals(15, lobby.map().width());
        assertEquals("quad", lobby.map().symmetry());
        assertTrue(lobby.map().randomSeed());
        assertEquals(2, lobby.slots().size());
    }

    @Test
    @DisplayName("Ein belegter Platz traegt seine Messwerte")
    void occupiedSlotCarriesHealth() throws Exception {
        LobbyState lobby = (LobbyState) parse(LOBBY_JSON);
        Slot slot = lobby.slots().getFirst();

        assertEquals("bot-0", slot.name());
        assertTrue(slot.connected());
        assertEquals("10.12.3.44:51234", slot.addr());
        assertEquals(60, slot.packetsPerSec());
        assertEquals(0.4, slot.lossPct(), 1e-9);
        assertEquals(1233, slot.lastSeenTick());
        assertEquals(16, slot.staleMs());
        assertFalse(slot.stale());
    }

    @Test
    @DisplayName("Bei einem freien Platz bleiben die Felder null statt null-Werte zu erfinden")
    void emptySlotKeepsNulls() throws Exception {
        LobbyState lobby = (LobbyState) parse(LOBBY_JSON);
        Slot slot = lobby.slots().get(1);

        assertFalse(slot.connected());
        assertNull(slot.addr());
        assertNull(slot.lastSeenTick());
        assertNull(slot.staleMs());
        // Der Name ist auch bei freien Plaetzen gesetzt - Bots koennen keinen senden.
        assertEquals("bot-1", slot.displayName());
    }

    @Test
    @DisplayName("Alle Lobby-Zustaende des Servers werden erkannt")
    void everyLobbyPhaseIsKnown() throws Exception {
        for (String state : new String[] {"open", "locked", "countdown", "running", "match_over"}) {
            LobbyState lobby = (LobbyState) parse(
                    "{\"type\":\"lobby\",\"state\":\"" + state + "\",\"slots\":[]}");
            assertFalse(lobby.phase() == LobbyPhase.UNKNOWN, "Unbekannt: " + state);
        }
    }

    // ---------------------------------------------------------------- match_init

    private static final String MATCH_INIT_JSON = """
            {
              "type": "match_init",
              "match_id": 42,
              "seed": "10873452098734512",
              "tick_rate": 60,
              "width": 3,
              "height": 2,
              "tiles": [1,1,1,0,2,1],
              "spawns": [[1,1],[13,1]],
              "players": [ { "id": 0, "name": "bot-0" }, { "id": 1, "name": "team-rocket" } ],
              "rules": {
                "bomb_fuse_ticks": 120, "flame_duration_ticks": 30, "ticks_per_cell": 8,
                "speed_step_ticks": 1, "start_bombs": 1, "start_flame": 1, "max_flame": 6,
                "max_speed": 3, "powerup_chance_pct": 30, "round_time_ticks": 10800,
                "sudden_death_tick": 7200
              }
            }
            """;

    @Test
    @DisplayName("match_init wird vollstaendig gelesen")
    void matchInitIsParsed() throws Exception {
        MatchInit init = (MatchInit) parse(MATCH_INIT_JSON);

        assertEquals(42, init.matchId());
        assertEquals(60, init.tickRate());
        assertEquals(3, init.board().width());
        assertEquals(2, init.board().height());
        assertEquals(2, init.spawns().size());
        assertEquals("team-rocket", init.nameOf(1));
        assertEquals(120, init.rules().bombFuseTicks());
        assertEquals(7200, init.rules().suddenDeathTick());
    }

    @Test
    @DisplayName("Das Kachelraster ist zeilenweise belegt")
    void tilesAreRowMajor() throws Exception {
        MatchInit init = (MatchInit) parse(MATCH_INIT_JSON);

        // [1,1,1, 0,2,1] bei 3x2 - Index ist y * width + x.
        assertSame(Tile.SOLID, init.board().tileAt(0, 0));
        assertSame(Tile.EMPTY, init.board().tileAt(0, 1));
        assertSame(Tile.SOFT, init.board().tileAt(1, 1));
        assertSame(Tile.SOLID, init.board().tileAt(2, 1));
    }

    @Test
    @DisplayName("Der Seed bleibt eine Zeichenkette und verliert keine Stellen")
    void seedKeepsItsPrecision() throws Exception {
        MatchInit init = (MatchInit) parse(MATCH_INIT_JSON);

        // Als JSON-Zahl gelesen wuerden die letzten Stellen eines 64-Bit-Werts wegfallen.
        assertEquals("10873452098734512", init.seed());
    }

    @Test
    @DisplayName("Fehlende Regeln fallen auf die Vorgaben zurueck")
    void missingRulesFallBack() throws Exception {
        MatchInit init = (MatchInit) parse(
                "{\"type\":\"match_init\",\"width\":1,\"height\":1,\"tiles\":[0]}");

        assertEquals(120, init.rules().bombFuseTicks());
    }

    // --------------------------------------------------------------------- state

    private static final String STATE_JSON = """
            {
              "type": "state",
              "tick": 1234,
              "ticks_remaining": 9566,
              "players": [
                { "id": 0, "alive": true, "moving": true, "x": 3, "y": 5, "dir": "left",
                  "move_progress": 4, "move_total": 8,
                  "bombs_max": 2, "flame": 3, "speed": 1, "score": 120 }
              ],
              "bombs":    [ { "id": 9, "owner": 0, "x": 3, "y": 6, "fuse": 77 } ],
              "flames":   [ { "x": 5, "y": 5, "ticks": 12 } ],
              "powerups": [ { "id": 4, "x": 7, "y": 7, "kind": "speed" } ],
              "tile_changes": [ { "x": 4, "y": 6, "tile": "empty" } ],
              "events": [
                { "type": "explosion", "bomb": 9, "x": 3, "y": 6,
                  "up": 1, "down": 2, "left": 0, "right": 3 },
                { "type": "death", "player": 3 }
              ]
            }
            """;

    @Test
    @DisplayName("state wird vollstaendig gelesen")
    void stateIsParsed() throws Exception {
        MatchState state = (MatchState) parse(STATE_JSON);

        assertEquals(1234, state.tick());
        assertEquals(9566, state.ticksRemaining());
        assertEquals(1, state.players().size());
        assertEquals(1, state.bombs().size());
        assertEquals(1, state.flames().size());
        assertEquals(1, state.powerups().size());
        assertEquals(1, state.tileChanges().size());
        assertEquals(2, state.events().size());
        assertTrue(state.receivedAtNanos() > 0);
    }

    @Test
    @DisplayName("Die Spielerangaben zur Bewegung kommen vollstaendig an")
    void playerCarriesMovementData() throws Exception {
        MatchState state = (MatchState) parse(STATE_JSON);
        PlayerState p = state.players().getFirst();

        assertTrue(p.moving());
        assertEquals(3, p.x());
        assertEquals(5, p.y());
        assertSame(Direction.LEFT, p.dir());
        assertEquals(4, p.moveProgress());
        assertEquals(8, p.moveTotal());
        assertEquals(2, p.bombsMax());
        assertEquals(120, p.score());
    }

    @Test
    @DisplayName("Die Kachelaenderung nennt die neue Art")
    void tileChangeCarriesTheNewTile() throws Exception {
        MatchState state = (MatchState) parse(STATE_JSON);

        assertSame(Tile.EMPTY, state.tileChanges().getFirst().tile());
    }

    @Test
    @DisplayName("Die Explosion liefert die Armlaengen mit")
    void explosionEventCarriesArmLengths() throws Exception {
        MatchState state = (MatchState) parse(STATE_JSON);
        GameEvent.Explosion explosion = assertInstanceOf(GameEvent.Explosion.class,
                state.events().getFirst());

        assertEquals(9, explosion.bomb());
        assertEquals(1, explosion.up());
        assertEquals(2, explosion.down());
        assertEquals(0, explosion.left());
        assertEquals(3, explosion.right());
    }

    @Test
    @DisplayName("Jeder dokumentierte Ereignistyp wird erkannt")
    void everyDocumentedEventIsKnown() throws Exception {
        String events = """
                {"type":"state","events":[
                  {"type":"bomb_placed","bomb":9,"player":0,"x":3,"y":6},
                  {"type":"explosion","bomb":9,"x":3,"y":6,"up":1,"down":2,"left":0,"right":3},
                  {"type":"block_destroyed","x":4,"y":6},
                  {"type":"powerup_spawned","powerup":5,"x":4,"y":6,"kind":"extra_bomb"},
                  {"type":"powerup_taken","powerup":4,"player":0,"kind":"speed"},
                  {"type":"powerup_burned","powerup":4,"x":7,"y":7},
                  {"type":"death","player":3},
                  {"type":"wall_closed","x":1,"y":1}
                ]}
                """;
        MatchState state = (MatchState) parse(events);

        assertEquals(8, state.events().size());
        for (GameEvent event : state.events()) {
            assertFalse(event instanceof GameEvent.Unknown,
                    "Nicht erkannt: " + event);
        }
    }

    @Test
    @DisplayName("Ein unbekanntes Ereignis wird mitgefuehrt statt verworfen")
    void unknownEventIsKept() throws Exception {
        MatchState state = (MatchState) parse(
                "{\"type\":\"state\",\"events\":[{\"type\":\"teleport\",\"player\":1}]}");

        GameEvent.Unknown unknown = assertInstanceOf(GameEvent.Unknown.class,
                state.events().getFirst());
        assertEquals("teleport", unknown.type());
    }

    @Test
    @DisplayName("Alle drei Power-up-Arten werden erkannt")
    void everyPowerupKindIsKnown() throws Exception {
        MatchState state = (MatchState) parse("""
                {"type":"state","powerups":[
                  {"id":1,"x":0,"y":0,"kind":"extra_bomb"},
                  {"id":2,"x":0,"y":0,"kind":"flame"},
                  {"id":3,"x":0,"y":0,"kind":"speed"}
                ]}
                """);

        assertSame(PowerupKind.EXTRA_BOMB, state.powerups().get(0).kind());
        assertSame(PowerupKind.FLAME, state.powerups().get(1).kind());
        assertSame(PowerupKind.SPEED, state.powerups().get(2).kind());
    }

    @Test
    @DisplayName("Eine unbekannte Power-up-Art wird zu UNKNOWN statt zu werfen")
    void unknownPowerupKind() throws Exception {
        MatchState state = (MatchState) parse(
                "{\"type\":\"state\",\"powerups\":[{\"id\":1,\"x\":0,\"y\":0,\"kind\":\"jetpack\"}]}");

        assertSame(PowerupKind.UNKNOWN, state.powerups().getFirst().kind());
    }

    // ----------------------------------------------------------------- match_end

    @Test
    @DisplayName("match_end wird gelesen, Sieger inbegriffen")
    void matchEndWithWinner() throws Exception {
        MatchEnd end = (MatchEnd) parse("""
                {"type":"match_end","tick":5000,"reason":"last_standing","winner":0,
                 "results":[{"id":0,"placement":1,"score":1300},
                            {"id":1,"placement":2,"score":400}]}
                """);

        assertEquals(5000, end.tick());
        assertSame(EndReason.LAST_STANDING, end.reason());
        assertTrue(end.hasWinner());
        assertEquals(0, end.winner());
        assertEquals(2, end.results().size());
    }

    @Test
    @DisplayName("Ein Unentschieden hat keinen Sieger")
    void matchEndWithoutWinner() throws Exception {
        MatchEnd end = (MatchEnd) parse(
                "{\"type\":\"match_end\",\"reason\":\"aborted\",\"winner\":null,\"results\":[]}");

        assertFalse(end.hasWinner());
        assertNull(end.winner());
        assertSame(EndReason.ABORTED, end.reason());
    }

    // ------------------------------------------------------------ ack und error

    @Test
    @DisplayName("Eine Bestaetigung nennt den Befehl")
    void ackIsParsed() throws Exception {
        CommandReply reply = (CommandReply) parse("{\"type\":\"ack\",\"cmd\":\"start\"}");

        assertTrue(reply.accepted());
        assertEquals("start", reply.cmd());
    }

    @Test
    @DisplayName("Eine Ablehnung traegt ihre Begruendung")
    void errorCarriesTheReason() throws Exception {
        CommandReply reply = (CommandReply) parse(
                "{\"type\":\"error\",\"cmd\":\"start\",\"message\":\"need at least 2 players\"}");

        assertFalse(reply.accepted());
        assertEquals("start", reply.cmd());
        assertTrue(reply.message().contains("2 players"));
    }

    @Test
    @DisplayName("Die Kartenvorschau hat dieselbe Gestalt wie match_init")
    void mapPreviewIsParsed() throws Exception {
        MapPreview preview = (MapPreview) parse("""
                {"type":"map_preview","match_id":0,"seed":"4242","tick_rate":60,
                 "width":3,"height":1,"tiles":[1,0,1],"spawns":[[1,1]],"players":[]}
                """);

        assertNotNull(preview.board());
        assertEquals("4242", preview.board().seed());
        assertEquals(3, preview.board().board().width());
    }

    // ------------------------------------------------------------------ Robustheit

    @Test
    @DisplayName("Ein unbekannter Nachrichtentyp wird stillschweigend verworfen")
    void unknownTypeIsIgnored() throws Exception {
        // Der Server-Leitfaden sagt ausdruecklich, dass neue Typen hinzukommen koennen.
        assertTrue(codec.parse("{\"type\":\"telemetry\",\"foo\":1}").isEmpty());
    }

    @Test
    @DisplayName("Kaputtes JSON endet in einer ProtocolException, nicht in einer Laufzeitausnahme")
    void brokenJsonThrowsProtocolException() {
        assertThrows(ProtocolException.class, () -> codec.parse("{ das ist kein json"));
        assertThrows(ProtocolException.class, () -> codec.parse("[1,2,3]"));
        assertThrows(ProtocolException.class, () -> codec.parse(""));
    }

    @Test
    @DisplayName("Fehlende Felder fuehren zu Vorgabewerten statt zu Ausnahmen")
    void missingFieldsFallBack() throws Exception {
        LobbyState lobby = (LobbyState) parse("{\"type\":\"lobby\"}");

        assertSame(LobbyPhase.UNKNOWN, lobby.phase());
        assertEquals(2, lobby.minPlayers());
        assertTrue(lobby.slots().isEmpty());

        MatchState state = (MatchState) parse("{\"type\":\"state\"}");
        assertTrue(state.players().isEmpty());
        assertTrue(state.events().isEmpty());
    }

    @Test
    @DisplayName("Falsch getypte Felder kippen die Auswertung nicht")
    void wrongTypesAreTolerated() throws Exception {
        Optional<ServerMessage> parsed = codec.parse(
                "{\"type\":\"lobby\",\"min_players\":\"zwei\",\"paused\":\"ja\",\"slots\":{}}");

        LobbyState lobby = (LobbyState) parsed.orElseThrow();
        assertEquals(2, lobby.minPlayers());
        assertFalse(lobby.paused());
        assertTrue(lobby.slots().isEmpty());
    }
}
