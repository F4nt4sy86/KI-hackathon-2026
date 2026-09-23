package com.btc.hackathon.viewer.net;

import com.btc.hackathon.viewer.control.CommandType;
import com.btc.hackathon.viewer.control.ModerationChannel;
import com.btc.hackathon.viewer.control.ModerationCommand;
import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.model.ServerMessage;
import com.btc.hackathon.viewer.tools.FakeServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft den Wechsel der Serveradresse im laufenden Betrieb.
 *
 * <p>Die Oberflaeche dafuer ist ein Textfeld und ein Knopf - die Schwierigkeit liegt
 * darunter: beim Wechsel sind noch Rueckrufe der alten Verbindung unterwegs. Wird einer
 * davon nicht verworfen, plant er eine Neuverbindung auf die alte Adresse ein, und der
 * Viewer haengt an zwei Servern gleichzeitig. Das faellt am Bildschirm erst auf, wenn die
 * Anzeige zwischen zwei Spielstaenden springt - deshalb wird es hier geprueft und nicht
 * von Hand.
 */
class ServerSwitchTest {

    private static final long TIMEOUT_MILLIS = 15_000;

    private FakeServer first;
    private FakeServer second;
    private ServerConnection connection;

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.close();
        }
        if (first != null) {
            first.stop();
        }
        if (second != null) {
            second.stop();
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Nach dem Wechsel kommt alles vom neuen Server - und nichts mehr vom alten")
    void switchingServersLeavesTheOldOneBehind() throws Exception {
        first = startServer();
        second = startServer();

        AtomicInteger received = new AtomicInteger();
        connection = new ServerConnection(
                ServerConnection.adminEndpoint("127.0.0.1", first.port()),
                message -> received.incrementAndGet(),
                state -> { });
        connection.start();

        await("Verbindung zum ersten Server steht",
                () -> connection.state() == ConnectionState.CONNECTED);
        await("Nachrichten treffen ein", () -> received.get() > 3);

        connection.connectTo(ServerConnection.adminEndpoint("127.0.0.1", second.port()));

        await("Verbindung zum zweiten Server steht",
                () -> connection.state() == ConnectionState.CONNECTED);
        assertEquals(second.port(), connection.uri().getPort());

        // Der erste Server verschwindet. Lief die alte Verbindung noch, wuerde jetzt der
        // Zustand auf "getrennt" fallen oder der Nachrichtenstrom abreissen.
        first.stop();
        first = null;

        long mark = received.get();
        await("Der zweite Server liefert weiter", () -> received.get() > mark + 5);
        assertEquals(ConnectionState.CONNECTED, connection.state());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Ein Wechsel auf eine tote Adresse und wieder zurueck findet heim")
    void switchingToADeadAddressAndBackRecovers() throws Exception {
        first = startServer();
        int deadPort = freeTcpPort(); // niemand hoert hier zu

        AtomicInteger received = new AtomicInteger();
        connection = new ServerConnection(
                ServerConnection.adminEndpoint("127.0.0.1", first.port()),
                message -> received.incrementAndGet(),
                state -> { });
        connection.start();
        await("Verbindung steht", () -> connection.state() == ConnectionState.CONNECTED);

        connection.connectTo(ServerConnection.adminEndpoint("127.0.0.1", deadPort));
        await("Der Viewer merkt, dass dort nichts ist",
                () -> connection.state() == ConnectionState.DISCONNECTED);

        long mark = received.get();

        // Zurueck auf den lebenden Server. Der eingeplante Wiederholungsversuch auf die
        // tote Adresse darf diesen Wechsel nicht mehr ueberschreiben.
        connection.connectTo(ServerConnection.adminEndpoint("127.0.0.1", first.port()));
        await("Wieder verbunden", () -> connection.state() == ConnectionState.CONNECTED);
        await("Nachrichten kommen wieder an", () -> received.get() > mark + 5);

        // Ein spaeter Wiederholungsversuch auf die tote Adresse haette die Verbindung
        // inzwischen wieder zerrissen.
        Thread.sleep(3000);
        assertEquals(ConnectionState.CONNECTED, connection.state());
        assertEquals(first.port(), connection.uri().getPort());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Befehle gehen nach dem Wechsel an den neuen Server")
    void commandsFollowTheSwitch() throws Exception {
        first = startServer();
        second = startServer();

        // Die Antworten muessen denselben Weg nehmen wie in ViewerApp, sonst wartet der
        // Steuerkanal auf etwas, das nie bei ihm ankommt.
        ModerationChannel[] holder = new ModerationChannel[1];
        connection = new ServerConnection(
                ServerConnection.adminEndpoint("127.0.0.1", first.port()),
                message -> {
                    if (message instanceof CommandReply reply) {
                        holder[0].onReply(reply);
                    }
                },
                state -> { });
        ModerationChannel moderation = new ModerationChannel(connection);
        holder[0] = moderation;
        connection.start();
        await("Verbindung steht", () -> connection.state() == ConnectionState.CONNECTED);

        connection.connectTo(ServerConnection.adminEndpoint("127.0.0.1", second.port()));
        await("Verbindung zum zweiten Server steht",
                () -> connection.state() == ConnectionState.CONNECTED);

        // Der zweite Server ist frisch in der Lobby: sperren muss er annehmen.
        CommandReply reply = moderation.send(ModerationCommand.of(CommandType.LOCK))
                .get(10, TimeUnit.SECONDS);
        assertTrue(reply.accepted(), "Befehl wurde abgelehnt: " + reply.message());
    }

    // -------------------------------------------------------------------- Helfer

    private static FakeServer startServer() throws Exception {
        FakeServer server = new FakeServer(freeTcpPort());
        server.setJoinIntervalSeconds(0);
        server.start();
        return server;
    }

    private static int freeTcpPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

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
