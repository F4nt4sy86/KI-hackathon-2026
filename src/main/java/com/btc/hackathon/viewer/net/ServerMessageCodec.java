package com.btc.hackathon.viewer.net;

import com.btc.hackathon.viewer.model.Board;
import com.btc.hackathon.viewer.model.Bomb;
import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.model.Direction;
import com.btc.hackathon.viewer.model.EndReason;
import com.btc.hackathon.viewer.model.Flame;
import com.btc.hackathon.viewer.model.GameEvent;
import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.model.MapPreview;
import com.btc.hackathon.viewer.model.MapSettings;
import com.btc.hackathon.viewer.model.MatchEnd;
import com.btc.hackathon.viewer.model.MatchInit;
import com.btc.hackathon.viewer.model.MatchPlayer;
import com.btc.hackathon.viewer.model.MatchResult;
import com.btc.hackathon.viewer.model.MatchState;
import com.btc.hackathon.viewer.model.PlayerState;
import com.btc.hackathon.viewer.model.Powerup;
import com.btc.hackathon.viewer.model.PowerupKind;
import com.btc.hackathon.viewer.model.Rules;
import com.btc.hackathon.viewer.model.ServerMessage;
import com.btc.hackathon.viewer.model.Slot;
import com.btc.hackathon.viewer.model.Tile;
import com.btc.hackathon.viewer.model.TileChange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uebersetzt das JSON des Servers in das Modell.
 *
 * <p>Bewusst ueber den Baum ({@link JsonNode}) statt ueber Datenbindung: das Protokoll
 * darf wachsen, und ein unbekanntes Feld oder ein unbekannter Nachrichtentyp muss
 * folgenlos bleiben, statt eine Ausnahme auszuloesen. Ausserdem bleiben die
 * Modell-Records damit frei von Bibliotheksanmerkungen.
 *
 * <p>Jede Feldabfrage geht durch die Hilfsmethoden unten, die bei fehlendem oder
 * falsch getyptem Feld einen Vorgabewert liefern. Ein Teilausfall des Protokolls
 * kostet so ein Detail der Anzeige, nicht die Anwendung.
 */
public final class ServerMessageCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @return die Nachricht, oder {@code Optional.empty()} bei einem Typ, den diese
     *         Version nicht kennt
     * @throws ProtocolException wenn der Text kein auswertbares JSON-Objekt ist
     */
    public Optional<ServerMessage> parse(String json) throws ProtocolException {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new ProtocolException("Nachricht ist kein gueltiges JSON: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new ProtocolException("Nachricht ist kein JSON-Objekt");
        }

        String type = text(root, "type", "");
        return switch (type) {
            case "lobby" -> Optional.of(lobby(root));
            case "match_init" -> Optional.of(matchInit(root));
            case "state" -> Optional.of(state(root));
            case "match_end" -> Optional.of(matchEnd(root));
            case "map_preview" -> Optional.of(new MapPreview(matchInit(root)));
            case "ack" -> Optional.of(CommandReply.ack(text(root, "cmd", "")));
            case "error" -> Optional.of(
                    CommandReply.error(text(root, "cmd", ""), text(root, "message", "")));
            // Unbekannter Typ: der Leitfaden sagt ausdruecklich, dass neue hinzukommen
            // koennen und ignoriert werden sollen.
            default -> Optional.empty();
        };
    }

    // --------------------------------------------------------------------- lobby

    private static LobbyState lobby(JsonNode root) {
        List<Slot> slots = new ArrayList<>();
        for (JsonNode s : array(root, "slots")) {
            slots.add(new Slot(
                    integer(s, "id", 0),
                    text(s, "name", ""),
                    bool(s, "connected", false),
                    nullableText(s, "addr"),
                    integer(s, "packets_per_sec", 0),
                    real(s, "loss_pct", 0),
                    nullableInteger(s, "last_seen_tick"),
                    nullableInteger(s, "stale_ms"),
                    bool(s, "stale", false)));
        }

        JsonNode map = root.path("map");
        MapSettings settings = new MapSettings(
                integer(map, "width", 0),
                integer(map, "height", 0),
                real(map, "density", 0),
                text(map, "symmetry", "quad"),
                text(map, "seed", "0"));

        return new LobbyState(
                LobbyPhase.fromJson(text(root, "state", null)),
                bool(root, "paused", false),
                bool(root, "can_start", false),
                integer(root, "min_players", 2),
                integer(root, "max_players", 4),
                integer(root, "countdown_ticks", 0),
                integer(root, "tick_rate", 60),
                settings,
                slots);
    }

    // ---------------------------------------------------------------- match_init

    private static MatchInit matchInit(JsonNode root) {
        int width = integer(root, "width", 0);
        int height = integer(root, "height", 0);

        JsonNode tiles = root.path("tiles");
        byte[] cells = new byte[Math.max(0, width * height)];
        for (int i = 0; i < cells.length && i < tiles.size(); i++) {
            cells[i] = (byte) tiles.get(i).asInt(0);
        }
        Board board = width > 0 && height > 0
                ? new Board(width, height, cells)
                : Board.empty(1, 1);

        List<int[]> spawns = new ArrayList<>();
        for (JsonNode spawn : array(root, "spawns")) {
            if (spawn.isArray() && spawn.size() >= 2) {
                spawns.add(new int[] {spawn.get(0).asInt(), spawn.get(1).asInt()});
            }
        }

        List<MatchPlayer> players = new ArrayList<>();
        for (JsonNode p : array(root, "players")) {
            players.add(new MatchPlayer(integer(p, "id", 0), text(p, "name", "")));
        }

        return new MatchInit(
                root.path("match_id").asLong(0),
                text(root, "seed", "0"),
                integer(root, "tick_rate", 60),
                board,
                spawns,
                players,
                rules(root.path("rules")));
    }

    private static Rules rules(JsonNode node) {
        Rules defaults = Rules.defaults();
        if (!node.isObject()) {
            return defaults;
        }
        return new Rules(
                integer(node, "bomb_fuse_ticks", defaults.bombFuseTicks()),
                integer(node, "flame_duration_ticks", defaults.flameDurationTicks()),
                integer(node, "ticks_per_cell", defaults.ticksPerCell()),
                integer(node, "speed_step_ticks", defaults.speedStepTicks()),
                integer(node, "start_bombs", defaults.startBombs()),
                integer(node, "start_flame", defaults.startFlame()),
                integer(node, "max_flame", defaults.maxFlame()),
                integer(node, "max_speed", defaults.maxSpeed()),
                integer(node, "powerup_chance_pct", defaults.powerupChancePct()),
                integer(node, "round_time_ticks", defaults.roundTimeTicks()),
                integer(node, "sudden_death_tick", defaults.suddenDeathTick()));
    }

    // --------------------------------------------------------------------- state

    private static MatchState state(JsonNode root) {
        List<PlayerState> players = new ArrayList<>();
        for (JsonNode p : array(root, "players")) {
            players.add(new PlayerState(
                    integer(p, "id", 0),
                    bool(p, "alive", true),
                    bool(p, "moving", false),
                    integer(p, "x", 0),
                    integer(p, "y", 0),
                    Direction.fromJson(text(p, "dir", null)),
                    integer(p, "move_progress", 0),
                    integer(p, "move_total", 0),
                    integer(p, "bombs_max", 0),
                    integer(p, "flame", 0),
                    integer(p, "speed", 0),
                    integer(p, "score", 0)));
        }

        List<Bomb> bombs = new ArrayList<>();
        for (JsonNode b : array(root, "bombs")) {
            bombs.add(new Bomb(
                    integer(b, "id", 0),
                    integer(b, "owner", 0),
                    integer(b, "x", 0),
                    integer(b, "y", 0),
                    integer(b, "fuse", 0)));
        }

        List<Flame> flames = new ArrayList<>();
        for (JsonNode f : array(root, "flames")) {
            flames.add(new Flame(integer(f, "x", 0), integer(f, "y", 0), integer(f, "ticks", 0)));
        }

        List<Powerup> powerups = new ArrayList<>();
        for (JsonNode p : array(root, "powerups")) {
            powerups.add(new Powerup(
                    integer(p, "id", 0),
                    integer(p, "x", 0),
                    integer(p, "y", 0),
                    PowerupKind.fromJson(text(p, "kind", null))));
        }

        List<TileChange> tileChanges = new ArrayList<>();
        for (JsonNode c : array(root, "tile_changes")) {
            tileChanges.add(new TileChange(
                    integer(c, "x", 0),
                    integer(c, "y", 0),
                    Tile.fromJson(text(c, "tile", null))));
        }

        List<GameEvent> events = new ArrayList<>();
        for (JsonNode e : array(root, "events")) {
            events.add(event(e));
        }

        return new MatchState(
                root.path("tick").asLong(0),
                root.path("ticks_remaining").asLong(0),
                players,
                bombs,
                flames,
                powerups,
                tileChanges,
                events,
                System.nanoTime());
    }

    private static GameEvent event(JsonNode node) {
        String type = text(node, "type", "");
        return switch (type) {
            case "bomb_placed" -> new GameEvent.BombPlaced(
                    integer(node, "bomb", 0), integer(node, "player", 0),
                    integer(node, "x", 0), integer(node, "y", 0));
            case "explosion" -> new GameEvent.Explosion(
                    integer(node, "bomb", 0), integer(node, "x", 0), integer(node, "y", 0),
                    integer(node, "up", 0), integer(node, "down", 0),
                    integer(node, "left", 0), integer(node, "right", 0));
            case "block_destroyed" -> new GameEvent.BlockDestroyed(
                    integer(node, "x", 0), integer(node, "y", 0));
            case "powerup_spawned" -> new GameEvent.PowerupSpawned(
                    integer(node, "powerup", 0), integer(node, "x", 0), integer(node, "y", 0),
                    PowerupKind.fromJson(text(node, "kind", null)));
            case "powerup_taken" -> new GameEvent.PowerupTaken(
                    integer(node, "powerup", 0), integer(node, "player", 0),
                    PowerupKind.fromJson(text(node, "kind", null)));
            case "powerup_burned" -> new GameEvent.PowerupBurned(
                    integer(node, "powerup", 0), integer(node, "x", 0), integer(node, "y", 0));
            case "death" -> new GameEvent.Death(integer(node, "player", 0));
            case "wall_closed" -> new GameEvent.WallClosed(
                    integer(node, "x", 0), integer(node, "y", 0));
            default -> new GameEvent.Unknown(type);
        };
    }

    // ----------------------------------------------------------------- match_end

    private static MatchEnd matchEnd(JsonNode root) {
        List<MatchResult> results = new ArrayList<>();
        for (JsonNode r : array(root, "results")) {
            results.add(new MatchResult(
                    integer(r, "id", 0),
                    integer(r, "placement", 0),
                    integer(r, "score", 0)));
        }
        return new MatchEnd(
                root.path("tick").asLong(0),
                EndReason.fromJson(text(root, "reason", null)),
                nullableInteger(root, "winner"),
                results);
    }

    // ------------------------------------------------------------------- Helfer

    private static Iterable<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isArray() ? value : List.of();
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : fallback;
    }

    /** Wie {@link #text}, aber {@code null} statt eines Vorgabewerts - etwa fuer {@code addr}. */
    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private static Integer nullableInteger(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static double real(JsonNode node, String field, double fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : fallback;
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.path(field);
        return value.isBoolean() ? value.asBoolean() : fallback;
    }
}
