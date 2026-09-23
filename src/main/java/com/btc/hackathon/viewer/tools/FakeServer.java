package com.btc.hackathon.viewer.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Ein simulierter Arena-Server fuer die Entwicklung.
 *
 * <p>Spricht dasselbe Protokoll wie der echte Server: WebSocket auf {@code /ws/spectate}
 * und {@code /ws/admin}, JSON in beide Richtungen, 60 Zustaende je Sekunde. Damit ist die
 * vollstaendige Kette - Lobby, Countdown, Match, Pause, Abbruch, Ergebnis - vorfuehrbar,
 * ohne dass der Rust-Server laufen muss.
 *
 * <p>Start:
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.btc.hackathon.viewer.tools.FakeServer
 * </pre>
 *
 * <p>Die Spiellogik ist bewusst grob: sie soll plausibel aussehen und alle Nachrichtenarten
 * erzeugen, nicht die echte Simulation nachbilden. Was hier zaehlt, ist die Form der
 * Nachrichten - nicht, ob ein Bot klug spielt.
 */
public final class FakeServer {

    private static final Logger LOG = Logger.getLogger(FakeServer.class.getName());

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int TICK_RATE = 60;
    private static final long TICK_MILLIS = 1000 / TICK_RATE;
    private static final int LOBBY_EVERY_TICKS = 30;
    private static final int MAX_PLAYERS = 4;
    private static final int MIN_PLAYERS = 2;
    private static final int COUNTDOWN_TICKS = 180;

    // Regelwerte, wie sie der echte Server in config/server.toml vorgibt.
    private static final int BOMB_FUSE_TICKS = 120;
    private static final int FLAME_DURATION_TICKS = 30;
    private static final int TICKS_PER_CELL = 8;
    private static final int SPEED_STEP_TICKS = 1;
    private static final int MAX_FLAME = 6;
    private static final int MAX_SPEED = 3;
    private static final int POWERUP_CHANCE_PCT = 30;
    private static final int ROUND_TIME_TICKS = 10800;
    private static final int SUDDEN_DEATH_TICK = 7200;

    private static final int WIDTH = 15;
    private static final int HEIGHT = 13;

    private static final String[] DIRECTIONS = {"up", "down", "left", "right"};
    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    private static final int TILE_EMPTY = 0;
    private static final int TILE_SOLID = 1;
    private static final int TILE_SOFT = 2;

    private final int port;
    private final Random random = new Random(20260923L);

    /** Der gesamte Zustand wird nur vom Taktgeber-Thread veraendert. */
    private final ScheduledExecutorService ticker =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fake-server-tick");
                t.setDaemon(true);
                return t;
            });

    private MiniWebSocketServer websocket;

    private String phase = "open";
    private boolean paused;
    private int countdownLeft;
    private long matchId;
    private long seed;
    private int tick;
    private int ticksRemaining;
    private int joinIntervalSeconds = 2;
    private long sessionTick;

    private int[] tiles = new int[WIDTH * HEIGHT];
    private final List<SimSlot> slots = new ArrayList<>();
    private final List<SimBomb> bombs = new ArrayList<>();
    private final Map<Long, Integer> flames = new HashMap<>();
    private final List<SimPowerup> powerups = new ArrayList<>();
    private final List<ObjectNode> pendingEvents = new ArrayList<>();
    private final List<ObjectNode> pendingTileChanges = new ArrayList<>();

    private int nextBombId = 1;
    private int nextPowerupId = 1;
    private ObjectNode lastMatchInit;
    private ObjectNode lastMatchEnd;

    public FakeServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("fake.port", 8080);
        FakeServer server = new FakeServer(port);
        server.setJoinIntervalSeconds(Integer.getInteger("fake.joinSeconds", 2));
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "fake-server-shutdown"));
        server.start();
        LOG.info("Die Lobby fuellt sich von selbst - danach im Viewer auf \"Spiel starten\" druecken.");
        Thread.currentThread().join();
    }

    /**
     * Abstand zwischen zwei eintreffenden Bots in Sekunden.
     *
     * <p>{@code 0} bedeutet: alle sind sofort da. Der Integrationstest nutzt das, um nicht
     * sekundenlang auf die Lobby warten zu muessen.
     */
    public void setJoinIntervalSeconds(int seconds) {
        this.joinIntervalSeconds = Math.max(0, seconds);
    }

    public void start() throws Exception {
        resetToLobby();

        websocket = new MiniWebSocketServer(port, this::onConnect, this::onCommand);
        websocket.start();

        ticker.scheduleAtFixedRate(this::safeTick, 0, TICK_MILLIS, TimeUnit.MILLISECONDS);
        LOG.info("Fake-Server laeuft auf ws://127.0.0.1:" + port + "/ws/admin");
    }

    public void stop() {
        ticker.shutdownNow();
        if (websocket != null) {
            websocket.close();
        }
        LOG.info("Fake-Server beendet");
    }

    public int port() {
        return port;
    }

    // ------------------------------------------------------------- Verbindungen

    /**
     * Ein Zuschauer oder Moderator verbindet sich.
     *
     * <p>Der Lobby-Zustand geht sofort raus, und wenn schon ein Match laeuft, auch
     * dessen {@code match_init} - sonst starrte ein spaet dazugekommener Zuschauer bis
     * zum naechsten Match auf ein leeres Bild.
     */
    private void onConnect(MiniWebSocketServer.Session session) {
        LOG.info("Verbunden: " + session.path() + " von " + session.remoteAddress());
        ticker.execute(() -> {
            session.send(lobbyJson().toString());
            if (lastMatchInit != null) {
                session.send(lastMatchInit.toString());
            }
            if (lastMatchEnd != null && phase.equals("match_over")) {
                session.send(lastMatchEnd.toString());
            }
        });
    }

    private void onCommand(MiniWebSocketServer.Session session, String text) {
        if (!session.path().startsWith("/ws/admin")) {
            // Der lesende Endpunkt ignoriert alles, was der Client sagt.
            return;
        }
        ticker.execute(() -> {
            ObjectNode reply = handle(text);
            session.send(reply.toString());
        });
    }

    private ObjectNode handle(String text) {
        String cmd;
        ObjectNode node;
        try {
            node = (ObjectNode) JSON.readTree(text);
            cmd = node.path("cmd").asText("");
        } catch (Exception e) {
            return error("", "could not parse command: " + e.getMessage());
        }

        return switch (cmd) {
            case "start" -> start_();
            case "pause" -> paused ? error(cmd, "already paused") : accept(cmd, () -> paused = true);
            case "resume" -> !paused ? error(cmd, "not paused") : accept(cmd, () -> paused = false);
            case "end" -> endMatch();
            case "reset" -> accept(cmd, this::resetToLobby);
            case "lock" -> accept(cmd, () -> {
                if (phase.equals("open")) {
                    phase = "locked";
                }
            });
            case "unlock" -> accept(cmd, () -> {
                if (phase.equals("locked")) {
                    phase = "open";
                }
            });
            case "kick" -> kick(node.path("id").asInt(-1));
            case "rename" -> rename(node.path("id").asInt(-1), node.path("name").asText(""));
            case "configure_map" -> phase.equals("running")
                    ? error(cmd, "cannot change the map while a match is running")
                    : ack(cmd);
            case "preview_map" -> previewMap();
            default -> error(cmd, "unknown command");
        };
    }

    private ObjectNode start_() {
        if (paused) {
            return error("start", "resume before starting");
        }
        if (!phase.equals("open") && !phase.equals("locked")) {
            return error("start", "a match is already starting or running");
        }
        int ready = connectedCount();
        if (ready < MIN_PLAYERS) {
            return error("start", "need at least " + MIN_PLAYERS + " players, have " + ready);
        }
        phase = "countdown";
        countdownLeft = COUNTDOWN_TICKS;
        return ack("start");
    }

    private ObjectNode endMatch() {
        if (!phase.equals("running") && !phase.equals("countdown")) {
            return error("end", "no match to end");
        }
        finishMatch("aborted");
        paused = false;
        return ack("end");
    }

    private ObjectNode kick(int id) {
        SimSlot slot = slotById(id);
        if (slot == null || !slot.connected) {
            return error("kick", "seat " + id + " is already empty");
        }
        slot.connected = false;
        slot.kicked = true;
        slot.alive = false;
        slot.packetsPerSec = 0;
        slot.staleMs = null;
        return ack("kick");
    }

    private ObjectNode rename(int id, String name) {
        SimSlot slot = slotById(id);
        if (slot == null) {
            return error("rename", "no seat " + id);
        }
        slot.name = name.length() > 32 ? name.substring(0, 32) : name;
        return ack("rename");
    }

    private ObjectNode previewMap() {
        long previewSeed = random.nextLong() & 0x7FFFFFFFFFFFFFFFL;
        int[] preview = buildBoard(new Random(previewSeed));
        ObjectNode node = boardJson("map_preview", 0, previewSeed, preview, List.of());
        return node;
    }

    private ObjectNode accept(String cmd, Runnable action) {
        action.run();
        return ack(cmd);
    }

    private static ObjectNode ack(String cmd) {
        ObjectNode node = JSON.createObjectNode();
        node.put("type", "ack");
        node.put("cmd", cmd);
        return node;
    }

    private static ObjectNode error(String cmd, String message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("type", "error");
        node.put("cmd", cmd);
        node.put("message", message);
        return node;
    }

    // ---------------------------------------------------------------- Simulation

    private void safeTick() {
        try {
            doTick();
        } catch (RuntimeException e) {
            LOG.warning("Fehler im Simulationstakt: " + e);
        }
    }

    private void doTick() {
        sessionTick++;
        pendingEvents.clear();
        pendingTileChanges.clear();

        if (!paused) {
            switch (phase) {
                case "open" -> advanceLobby();
                case "locked" -> refreshPresence();
                case "countdown" -> {
                    if (--countdownLeft <= 0) {
                        beginMatch();
                    }
                }
                case "running" -> advanceMatch();
                default -> { /* match_over: eingefroren */ }
            }
        }

        if (sessionTick % LOBBY_EVERY_TICKS == 0) {
            websocket.broadcast(lobbyJson().toString());
        }
        if (phase.equals("running") || phase.equals("match_over")) {
            websocket.broadcast(stateJson().toString());
        }
    }

    /**
     * Bots trudeln nach und nach ein.
     *
     * <p>Laeuft nur in der offenen Lobby: eine gesperrte nimmt niemanden mehr auf, und ein
     * freigegebener Platz bleibt frei, bis der Bot sich erneut meldet - beim echten Server
     * heisst das, er muss sein Hello neu schicken.
     */
    private void advanceLobby() {
        long seconds = joinIntervalSeconds == 0 ? Long.MAX_VALUE : sessionTick / TICK_RATE;
        int expected = joinIntervalSeconds == 0
                ? MAX_PLAYERS
                : (int) Math.min(MAX_PLAYERS, seconds / joinIntervalSeconds);
        for (SimSlot slot : slots) {
            if (!slot.connected && !slot.kicked && slot.id < expected) {
                slot.connected = true;
                slot.packetsPerSec = TICK_RATE;
                slot.lossPct = random.nextDouble() * 1.5;
                slot.staleMs = 0;
                slot.lastSeenTick = (int) sessionTick;
                LOG.info(slot.name + " hat sich angemeldet");
            }
        }
        refreshPresence();
    }

    /** Haelt die Messwerte der verbundenen Plaetze frisch. */
    private void refreshPresence() {
        for (SimSlot slot : slots) {
            if (slot.connected) {
                slot.staleMs = 0;
                slot.lastSeenTick = (int) sessionTick;
            }
        }
    }

    private void beginMatch() {
        matchId++;
        seed = random.nextLong() & 0x7FFFFFFFFFFFFFFFL;
        tiles = buildBoard(new Random(seed));
        bombs.clear();
        flames.clear();
        powerups.clear();
        lastMatchEnd = null;
        tick = 0;
        ticksRemaining = ROUND_TIME_TICKS;

        int[][] spawns = {{1, 1}, {WIDTH - 2, 1}, {1, HEIGHT - 2}, {WIDTH - 2, HEIGHT - 2}};
        for (SimSlot slot : slots) {
            int[] spawn = spawns[slot.id % spawns.length];
            slot.x = spawn[0];
            slot.y = spawn[1];
            slot.moving = false;
            slot.moveProgress = 0;
            slot.moveTotal = TICKS_PER_CELL;
            slot.dir = "down";
            slot.alive = slot.connected;
            slot.bombsMax = 1;
            slot.flame = 1;
            slot.speed = 0;
            slot.score = 0;
        }

        phase = "running";
        lastMatchInit = matchInitJson();
        websocket.broadcast(lastMatchInit.toString());
        LOG.info("Match " + matchId + " gestartet, Seed " + seed);
    }

    private void advanceMatch() {
        tick++;
        ticksRemaining = Math.max(0, ticksRemaining - 1);

        for (SimSlot slot : slots) {
            if (slot.alive) {
                movePlayer(slot);
            }
        }
        advanceBombs();
        advanceFlames();
        collectPowerups();
        checkDeaths();

        if (ticksRemaining == 0) {
            finishMatch("timeout");
        } else if (aliveCount() <= 1) {
            finishMatch("last_standing");
        }
    }

    private void movePlayer(SimSlot p) {
        if (p.moving) {
            if (++p.moveProgress >= p.moveTotal) {
                p.moving = false;
                p.moveProgress = 0;
            }
            return;
        }

        maybeDropBomb(p);

        // Richtung beibehalten, solange sie frei ist - sonst neu waehlen.
        int current = directionIndex(p.dir);
        if (!walkable(p.x + DX[current], p.y + DY[current]) || random.nextInt(100) < 15) {
            List<Integer> options = new ArrayList<>(4);
            for (int d = 0; d < 4; d++) {
                if (walkable(p.x + DX[d], p.y + DY[d])) {
                    options.add(d);
                }
            }
            if (options.isEmpty()) {
                return;
            }
            current = options.get(random.nextInt(options.size()));
            p.dir = DIRECTIONS[current];
        }
        if (!walkable(p.x + DX[current], p.y + DY[current])) {
            return;
        }

        // Der Schritt gilt ab sofort: x/y sind die Zielzelle, der Fortschritt zaehlt von
        // null hoch. Genau das muss der Viewer rueckwaerts interpolieren.
        p.x += DX[current];
        p.y += DY[current];
        p.moving = true;
        p.moveProgress = 0;
        p.moveTotal = Math.max(1, TICKS_PER_CELL - p.speed * SPEED_STEP_TICKS);
    }

    private void maybeDropBomb(SimSlot p) {
        if (random.nextInt(100) >= 4) {
            return;
        }
        long own = bombs.stream().filter(b -> b.owner == p.id).count();
        if (own >= p.bombsMax || bombs.stream().anyMatch(b -> b.x == p.x && b.y == p.y)) {
            return;
        }
        SimBomb bomb = new SimBomb(nextBombId++, p.id, p.x, p.y, BOMB_FUSE_TICKS, p.flame);
        bombs.add(bomb);

        ObjectNode event = JSON.createObjectNode();
        event.put("type", "bomb_placed");
        event.put("bomb", bomb.id);
        event.put("player", p.id);
        event.put("x", bomb.x);
        event.put("y", bomb.y);
        pendingEvents.add(event);
    }

    private void advanceBombs() {
        List<SimBomb> detonated = new ArrayList<>();
        for (SimBomb b : bombs) {
            if (--b.fuse <= 0) {
                detonated.add(b);
            }
        }
        bombs.removeAll(detonated);
        for (SimBomb b : detonated) {
            detonate(b);
        }
    }

    private void detonate(SimBomb bomb) {
        int[] arms = new int[4];
        addFlame(bomb.x, bomb.y);

        for (int d = 0; d < 4; d++) {
            for (int step = 1; step <= bomb.flame; step++) {
                int x = bomb.x + DX[d] * step;
                int y = bomb.y + DY[d] * step;
                if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
                    break;
                }
                int tile = tiles[y * WIDTH + x];
                if (tile == TILE_SOLID) {
                    break;
                }
                addFlame(x, y);
                arms[d] = step;
                if (tile == TILE_SOFT) {
                    destroyCrate(x, y);
                    break;
                }
            }
        }

        ObjectNode event = JSON.createObjectNode();
        event.put("type", "explosion");
        event.put("bomb", bomb.id);
        event.put("x", bomb.x);
        event.put("y", bomb.y);
        event.put("up", arms[0]);
        event.put("down", arms[1]);
        event.put("left", arms[2]);
        event.put("right", arms[3]);
        pendingEvents.add(event);
    }

    private void destroyCrate(int x, int y) {
        tiles[y * WIDTH + x] = TILE_EMPTY;

        ObjectNode change = JSON.createObjectNode();
        change.put("x", x);
        change.put("y", y);
        change.put("tile", "empty");
        pendingTileChanges.add(change);

        ObjectNode destroyed = JSON.createObjectNode();
        destroyed.put("type", "block_destroyed");
        destroyed.put("x", x);
        destroyed.put("y", y);
        pendingEvents.add(destroyed);

        if (random.nextInt(100) < POWERUP_CHANCE_PCT) {
            String[] kinds = {"extra_bomb", "flame", "speed"};
            String kind = kinds[random.nextInt(kinds.length)];
            SimPowerup powerup = new SimPowerup(nextPowerupId++, x, y, kind);
            powerups.add(powerup);

            ObjectNode spawned = JSON.createObjectNode();
            spawned.put("type", "powerup_spawned");
            spawned.put("powerup", powerup.id);
            spawned.put("x", x);
            spawned.put("y", y);
            spawned.put("kind", kind);
            pendingEvents.add(spawned);
        }
    }

    private void addFlame(int x, int y) {
        flames.put(cellKey(x, y), FLAME_DURATION_TICKS);

        // Ein Power-up im Feuer verbrennt - anderer Ausgang als eingesammelt.
        for (Iterator<SimPowerup> it = powerups.iterator(); it.hasNext(); ) {
            SimPowerup p = it.next();
            if (p.x == x && p.y == y) {
                it.remove();
                ObjectNode burned = JSON.createObjectNode();
                burned.put("type", "powerup_burned");
                burned.put("powerup", p.id);
                burned.put("x", x);
                burned.put("y", y);
                pendingEvents.add(burned);
            }
        }
    }

    private void advanceFlames() {
        flames.replaceAll((cell, ticks) -> ticks - 1);
        flames.values().removeIf(ticks -> ticks <= 0);
    }

    private void collectPowerups() {
        for (SimSlot p : slots) {
            if (!p.alive || p.moving) {
                continue;
            }
            for (Iterator<SimPowerup> it = powerups.iterator(); it.hasNext(); ) {
                SimPowerup item = it.next();
                if (item.x != p.x || item.y != p.y) {
                    continue;
                }
                it.remove();
                switch (item.kind) {
                    case "extra_bomb" -> p.bombsMax++;
                    case "flame" -> p.flame = Math.min(MAX_FLAME, p.flame + 1);
                    case "speed" -> p.speed = Math.min(MAX_SPEED, p.speed + 1);
                    default -> { }
                }
                p.score += 50;

                ObjectNode taken = JSON.createObjectNode();
                taken.put("type", "powerup_taken");
                taken.put("powerup", item.id);
                taken.put("player", p.id);
                taken.put("kind", item.kind);
                pendingEvents.add(taken);
            }
        }
    }

    private void checkDeaths() {
        for (SimSlot p : slots) {
            if (p.alive && flames.containsKey(cellKey(p.x, p.y))) {
                p.alive = false;
                ObjectNode death = JSON.createObjectNode();
                death.put("type", "death");
                death.put("player", p.id);
                pendingEvents.add(death);
                LOG.info(p.name + " wurde getroffen");
            }
        }
    }

    private void finishMatch(String reason) {
        List<SimSlot> ranked = slots.stream()
                .filter(s -> s.connected)
                .sorted((a, b) -> {
                    if (a.alive != b.alive) {
                        return a.alive ? -1 : 1;
                    }
                    return Integer.compare(b.score, a.score);
                })
                .toList();

        Integer winner = null;
        if (reason.equals("last_standing")) {
            winner = ranked.stream().filter(s -> s.alive).map(s -> s.id).findFirst().orElse(null);
        }

        ObjectNode node = JSON.createObjectNode();
        node.put("type", "match_end");
        node.put("tick", tick);
        node.put("reason", reason);
        if (winner == null) {
            node.putNull("winner");
        } else {
            node.put("winner", winner);
        }
        ArrayNode results = node.putArray("results");
        for (int i = 0; i < ranked.size(); i++) {
            SimSlot s = ranked.get(i);
            ObjectNode row = results.addObject();
            row.put("id", s.id);
            row.put("placement", i + 1);
            row.put("score", s.score + (s.alive ? 500 : 0));
        }

        phase = "match_over";
        lastMatchEnd = node;
        websocket.broadcast(node.toString());
        LOG.info("Match beendet (" + reason + "), Sieger: " + (winner == null ? "keiner" : winner));
    }

    private void resetToLobby() {
        phase = "open";
        paused = false;
        countdownLeft = 0;
        tick = 0;
        bombs.clear();
        flames.clear();
        powerups.clear();
        lastMatchInit = null;
        lastMatchEnd = null;
        tiles = new int[WIDTH * HEIGHT];

        if (slots.isEmpty()) {
            String[] names = {"bot-0", "bot-1", "bot-2", "bot-3"};
            for (int i = 0; i < MAX_PLAYERS; i++) {
                slots.add(new SimSlot(i, names[i]));
            }
        }
        for (SimSlot slot : slots) {
            slot.alive = slot.connected;
            slot.kicked = false;
        }
    }

    // --------------------------------------------------------------------- JSON

    private ObjectNode lobbyJson() {
        ObjectNode node = JSON.createObjectNode();
        node.put("type", "lobby");
        node.put("state", phase);
        node.put("paused", paused);
        node.put("can_start", canStart());
        node.put("min_players", MIN_PLAYERS);
        node.put("max_players", MAX_PLAYERS);
        node.put("countdown_ticks", countdownLeft);
        node.put("tick_rate", TICK_RATE);

        ObjectNode map = node.putObject("map");
        map.put("width", WIDTH);
        map.put("height", HEIGHT);
        map.put("density", 0.75);
        map.put("symmetry", "quad");
        map.put("seed", String.valueOf(seed));

        ArrayNode array = node.putArray("slots");
        for (SimSlot slot : slots) {
            ObjectNode s = array.addObject();
            s.put("id", slot.id);
            s.put("name", slot.name);
            s.put("connected", slot.connected);
            if (slot.connected) {
                s.put("addr", "127.0.0.1:" + (51000 + slot.id));
            } else {
                s.putNull("addr");
            }
            s.put("packets_per_sec", slot.packetsPerSec);
            s.put("loss_pct", slot.lossPct);
            if (slot.lastSeenTick == null) {
                s.putNull("last_seen_tick");
            } else {
                s.put("last_seen_tick", slot.lastSeenTick);
            }
            if (slot.staleMs == null) {
                s.putNull("stale_ms");
            } else {
                s.put("stale_ms", slot.staleMs);
            }
            s.put("stale", slot.staleMs != null && slot.staleMs > 2000);
        }
        return node;
    }

    private boolean canStart() {
        return !paused
                && (phase.equals("open") || phase.equals("locked"))
                && connectedCount() >= MIN_PLAYERS;
    }

    private ObjectNode matchInitJson() {
        List<SimSlot> playing = slots.stream().filter(s -> s.connected).toList();
        return boardJson("match_init", matchId, seed, tiles, playing);
    }

    private ObjectNode boardJson(String type, long id, long boardSeed, int[] board,
                                 List<SimSlot> playing) {
        ObjectNode node = JSON.createObjectNode();
        node.put("type", type);
        node.put("match_id", id);
        node.put("seed", String.valueOf(boardSeed));
        node.put("tick_rate", TICK_RATE);
        node.put("width", WIDTH);
        node.put("height", HEIGHT);

        ArrayNode tileArray = node.putArray("tiles");
        for (int value : board) {
            tileArray.add(value);
        }

        ArrayNode spawnArray = node.putArray("spawns");
        for (int[] spawn : new int[][] {{1, 1}, {WIDTH - 2, 1}, {1, HEIGHT - 2},
                {WIDTH - 2, HEIGHT - 2}}) {
            ArrayNode pair = spawnArray.addArray();
            pair.add(spawn[0]);
            pair.add(spawn[1]);
        }

        ArrayNode playerArray = node.putArray("players");
        for (SimSlot slot : playing) {
            ObjectNode p = playerArray.addObject();
            p.put("id", slot.id);
            p.put("name", slot.name);
        }

        ObjectNode rules = node.putObject("rules");
        rules.put("bomb_fuse_ticks", BOMB_FUSE_TICKS);
        rules.put("flame_duration_ticks", FLAME_DURATION_TICKS);
        rules.put("ticks_per_cell", TICKS_PER_CELL);
        rules.put("speed_step_ticks", SPEED_STEP_TICKS);
        rules.put("start_bombs", 1);
        rules.put("start_flame", 1);
        rules.put("max_flame", MAX_FLAME);
        rules.put("max_speed", MAX_SPEED);
        rules.put("powerup_chance_pct", POWERUP_CHANCE_PCT);
        rules.put("round_time_ticks", ROUND_TIME_TICKS);
        rules.put("sudden_death_tick", SUDDEN_DEATH_TICK);
        return node;
    }

    private ObjectNode stateJson() {
        ObjectNode node = JSON.createObjectNode();
        node.put("type", "state");
        node.put("tick", tick);
        node.put("ticks_remaining", ticksRemaining);

        ArrayNode playerArray = node.putArray("players");
        for (SimSlot p : slots) {
            if (!p.connected) {
                continue;
            }
            ObjectNode o = playerArray.addObject();
            o.put("id", p.id);
            o.put("alive", p.alive);
            o.put("moving", p.moving);
            o.put("x", p.x);
            o.put("y", p.y);
            o.put("dir", p.dir);
            o.put("move_progress", p.moveProgress);
            o.put("move_total", p.moveTotal);
            o.put("bombs_max", p.bombsMax);
            o.put("flame", p.flame);
            o.put("speed", p.speed);
            o.put("score", p.score);
        }

        ArrayNode bombArray = node.putArray("bombs");
        for (SimBomb b : bombs) {
            ObjectNode o = bombArray.addObject();
            o.put("id", b.id);
            o.put("owner", b.owner);
            o.put("x", b.x);
            o.put("y", b.y);
            o.put("fuse", Math.max(0, b.fuse));
        }

        ArrayNode flameArray = node.putArray("flames");
        flames.forEach((cell, ticks) -> {
            ObjectNode o = flameArray.addObject();
            o.put("x", (int) (cell >> 32));
            o.put("y", (int) (cell & 0xFFFFFFFFL));
            o.put("ticks", ticks);
        });

        ArrayNode powerupArray = node.putArray("powerups");
        for (SimPowerup p : powerups) {
            ObjectNode o = powerupArray.addObject();
            o.put("id", p.id);
            o.put("x", p.x);
            o.put("y", p.y);
            o.put("kind", p.kind);
        }

        ArrayNode changeArray = node.putArray("tile_changes");
        pendingTileChanges.forEach(changeArray::add);

        ArrayNode eventArray = node.putArray("events");
        pendingEvents.forEach(eventArray::add);

        return node;
    }

    // ------------------------------------------------------------------- Helfer

    /** Klassisches Layout: harter Rand, Saeulengitter, Kisten dazwischen, freie Ecken. */
    private static int[] buildBoard(Random rng) {
        int[] board = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean border = x == 0 || y == 0 || x == WIDTH - 1 || y == HEIGHT - 1;
                boolean pillar = x % 2 == 0 && y % 2 == 0;
                int value;
                if (border || pillar) {
                    value = TILE_SOLID;
                } else if (isSpawnArea(x, y)) {
                    value = TILE_EMPTY;
                } else {
                    value = rng.nextInt(100) < 75 ? TILE_SOFT : TILE_EMPTY;
                }
                board[y * WIDTH + x] = value;
            }
        }
        return board;
    }

    /** Die vier Ecken bleiben frei, sonst stuende eine Figur beim Start eingemauert. */
    private static boolean isSpawnArea(int x, int y) {
        int right = WIDTH - 2;
        int bottom = HEIGHT - 2;
        return (x <= 2 && y <= 2 && x + y <= 3)
                || (x >= right - 2 && y <= 2 && (right - x) + y <= 3)
                || (x <= 2 && y >= bottom - 2 && x + (bottom - y) <= 3)
                || (x >= right - 2 && y >= bottom - 2 && (right - x) + (bottom - y) <= 3);
    }

    private boolean walkable(int x, int y) {
        if (x < 0 || y < 0 || x >= WIDTH || y >= HEIGHT) {
            return false;
        }
        return tiles[y * WIDTH + x] == TILE_EMPTY;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int directionIndex(String dir) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i].equals(dir)) {
                return i;
            }
        }
        return 1;
    }

    private int connectedCount() {
        return (int) slots.stream().filter(s -> s.connected).count();
    }

    private int aliveCount() {
        return (int) slots.stream().filter(s -> s.connected && s.alive).count();
    }

    private SimSlot slotById(int id) {
        return slots.stream().filter(s -> s.id == id).findFirst().orElse(null);
    }

    // ----------------------------------------------------------- Simulationsobjekte

    private static final class SimSlot {
        final int id;
        String name;
        boolean connected;
        boolean alive;
        boolean kicked;
        int packetsPerSec;
        double lossPct;
        Integer lastSeenTick;
        Integer staleMs;

        int x;
        int y;
        boolean moving;
        String dir = "down";
        int moveProgress;
        int moveTotal = TICKS_PER_CELL;
        int bombsMax = 1;
        int flame = 1;
        int speed;
        int score;

        SimSlot(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class SimBomb {
        final int id;
        final int owner;
        final int x;
        final int y;
        final int flame;
        int fuse;

        SimBomb(int id, int owner, int x, int y, int fuse, int flame) {
            this.id = id;
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.fuse = fuse;
            this.flame = flame;
        }
    }

    private static final class SimPowerup {
        final int id;
        final int x;
        final int y;
        final String kind;

        SimPowerup(int id, int x, int y, String kind) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.kind = kind;
        }
    }
}
