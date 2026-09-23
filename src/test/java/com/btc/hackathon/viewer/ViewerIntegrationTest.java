package com.btc.hackathon.viewer;

import com.btc.hackathon.viewer.control.CommandType;
import com.btc.hackathon.viewer.control.ModerationChannel;
import com.btc.hackathon.viewer.control.ModerationCommand;
import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.MapPreview;
import com.btc.hackathon.viewer.model.ServerMessage;
import com.btc.hackathon.viewer.net.ConnectionState;
import com.btc.hackathon.viewer.net.ServerConnection;
import com.btc.hackathon.viewer.state.ViewStateStore;
import com.btc.hackathon.viewer.tools.FakeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die gesamte Kette gegen den Fake-Server: WebSocket, JSON, Zustandsspeicher und
 * Steuerkanal.
 *
 * <p>Die Oberflaeche bleibt aussen vor - sie liest ausschliesslich aus dem
 * {@link ViewStateStore}, und genau dessen Inhalt wird hier geprueft. Damit ist der Teil
 * abgesichert, der sich am Bildschirm nur schwer nachvollziehen laesst.
 */
class ViewerIntegrationTest {

    private static final long TIMEOUT_MILLIS = 15_000;

    private FakeServer server;
    private ServerConnection connection;
    private ModerationChannel moderation;
    private ViewStateStore store;

    @BeforeEach
    void setUp() throws Exception {
        int port = freeTcpPort();

        server = new FakeServer(port);
        // Ohne Wartezeit beim Eintrudeln - der Test soll nicht sekundenlang zusehen.
        server.setJoinIntervalSeconds(0);
        server.start();

        store = new ViewStateStore();
        connection = new ServerConnection(
                ServerConnection.adminEndpoint("127.0.0.1", port),
                this::dispatch,
                state -> { });
        moderation = new ModerationChannel(connection);
        connection.start();
    }

    /** Dieselbe Verteilung wie in {@code ViewerApp}. */
    private void dispatch(ServerMessage message) {
        if (message instanceof CommandReply reply) {
            moderation.onReply(reply);
            return;
        }
        store.accept(message);
        if (message instanceof MapPreview) {
            moderation.onPreview();
        }
    }

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    @DisplayName("Die volle Moderationsstrecke laeuft vom Start bis zurueck in die Lobby")
    void fullModerationFlow() throws Exception {
        await("Verbindung steht", () -> connection.state() == ConnectionState.CONNECTED);
        await("Lobby trifft ein", () -> store.current().phase() != LobbyPhase.UNKNOWN);
        await("Teilnehmer melden sich an", () -> store.current().lobby().connectedCount() >= 2);
        await("Der Server erlaubt den Start", () -> store.current().lobby().canStart());

        CommandReply start = send(CommandType.START);
        assertTrue(start.accepted(), "Start wurde abgelehnt: " + start.message());

        await("Countdown laeuft", () -> store.current().phase() == LobbyPhase.COUNTDOWN);
        await("Match laeuft", () -> store.current().phase() == LobbyPhase.RUNNING);
        await("Spielfeld trifft ein", () -> store.current().hasBoard());
        await("Spielzustaende treffen ein", () -> store.current().hasState());

        // Sofort anhalten, damit die Simulation waehrend der Pruefungen stillsteht.
        assertTrue(send(CommandType.PAUSE).accepted());
        await("Pause ist wirksam", () -> store.current().paused());

        assertEquals(15, store.current().board().width());
        assertEquals(13, store.current().board().height());
        assertTrue(store.current().state().players().size() >= 2);
        assertEquals(60, store.current().tickRate());

        // Ein Start waehrend der Pause muss der Server ablehnen.
        CommandReply blocked = send(CommandType.START);
        assertFalse(blocked.accepted());

        assertTrue(send(CommandType.RESUME).accepted());
        await("Match laeuft weiter", () -> !store.current().paused());

        assertTrue(send(CommandType.END).accepted());
        await("Match ist beendet", () -> store.current().phase() == LobbyPhase.MATCH_OVER);
        await("Ergebnis trifft ein", () -> store.current().hasResult());

        assertTrue(send(CommandType.RESET).accepted());
        await("Zurueck in der Lobby", () -> store.current().phase() == LobbyPhase.OPEN);

        // Der Codec des Viewers und das JSON des Servers muessen sich ueber die gesamte
        // Strecke einig gewesen sein.
        assertEquals(0, connection.parseErrors(), "Es gab Lesefehler");
        assertTrue(store.stateMessages() > 30,
                "Zu wenige Spielzustaende angekommen: " + store.stateMessages());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Ein in der aktuellen Phase unmoeglicher Befehl wird begruendet abgelehnt")
    void impossibleCommandIsRejectedWithReason() throws Exception {
        await("Verbindung steht", () -> connection.state() == ConnectionState.CONNECTED);
        await("Lobby trifft ein", () -> store.current().phase() != LobbyPhase.UNKNOWN);

        // In der Lobby gibt es nichts zu beenden.
        CommandReply reply = send(CommandType.END);

        assertFalse(reply.accepted(), "Beenden haette abgelehnt werden muessen");
        assertFalse(reply.message().isBlank(), "Die Ablehnung braucht eine Begruendung");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Sperren, Umbenennen und Freigeben wirken auf die Aufstellung")
    void rosterCommandsTakeEffect() throws Exception {
        await("Verbindung steht", () -> connection.state() == ConnectionState.CONNECTED);
        await("Teilnehmer melden sich an", () -> store.current().lobby().connectedCount() >= 2);

        assertTrue(send(CommandType.LOCK).accepted());
        await("Lobby ist gesperrt", () -> store.current().phase() == LobbyPhase.LOCKED);

        assertTrue(moderation.send(ModerationCommand.rename(0, "team-rocket"))
                .get(10, TimeUnit.SECONDS).accepted());
        await("Der neue Name kommt an",
                () -> "team-rocket".equals(store.current().nameOf(0)));

        assertTrue(moderation.send(ModerationCommand.kick(1))
                .get(10, TimeUnit.SECONDS).accepted());
        await("Der Platz ist frei",
                () -> !store.current().lobby().slotById(1).connected());

        // Ein bereits freier Platz wird begruendet abgelehnt.
        CommandReply again = moderation.send(ModerationCommand.kick(1)).get(10, TimeUnit.SECONDS);
        assertFalse(again.accepted());

        assertTrue(send(CommandType.UNLOCK).accepted());
        await("Lobby ist wieder offen", () -> store.current().phase() == LobbyPhase.OPEN);
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Die Kartenvorschau liefert ein Feld, ohne etwas zu starten")
    void mapPreviewDeliversABoard() throws Exception {
        await("Verbindung steht", () -> connection.state() == ConnectionState.CONNECTED);
        await("Lobby trifft ein", () -> store.current().phase() != LobbyPhase.UNKNOWN);

        assertTrue(send(CommandType.PREVIEW_MAP).accepted());
        await("Vorschau trifft ein", () -> store.current().hasPreview());

        assertEquals(15, store.current().preview().board().width());
        // Gestartet wurde dabei nichts.
        assertEquals(LobbyPhase.OPEN, store.current().phase());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Muell auf dem Socket erhoeht nur den Fehlerzaehler")
    void garbageOnlyIncrementsTheErrorCount() throws Exception {
        await("Verbindung steht", () -> connection.state() == ConnectionState.CONNECTED);
        await("Nachrichten treffen ein", () -> connection.messagesReceived() > 3);

        // Der Server antwortet auf unlesbare Befehle mit einem Fehler statt zu schweigen.
        connection.send("das ist kein json").get(5, TimeUnit.SECONDS);

        long before = connection.messagesReceived();
        await("Empfang laeuft weiter", () -> connection.messagesReceived() > before + 3);
        assertEquals(ConnectionState.CONNECTED, connection.state());
    }

    // -------------------------------------------------------------------- Helfer

    private CommandReply send(CommandType type) throws Exception {
        return moderation.send(ModerationCommand.of(type)).get(10, TimeUnit.SECONDS);
    }

    private static int freeTcpPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Wartet, bis die Bedingung erfuellt ist, statt auf eine feste Zeit zu setzen. */
    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Zeitueberschreitung beim Warten darauf, dass: " + what);
    }
}
