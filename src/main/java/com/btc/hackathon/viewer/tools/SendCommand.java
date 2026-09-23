package com.btc.hackathon.viewer.tools;

import com.btc.hackathon.viewer.control.CommandType;
import com.btc.hackathon.viewer.control.ModerationChannel;
import com.btc.hackathon.viewer.control.ModerationCommand;
import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.model.ServerMessage;
import com.btc.hackathon.viewer.net.ConnectionState;
import com.btc.hackathon.viewer.net.ServerConnection;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Setzt einen einzelnen Moderationsbefehl von der Kommandozeile ab.
 *
 * <p>Das Gegenstueck zu {@code curl} fuer den Steuerkanal: damit laesst sich pruefen, ob
 * der Server einen Befehl annimmt, ohne die Oberflaeche zu starten.
 *
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.btc.hackathon.viewer.tools.SendCommand \
 *               -Dexec.args="start"
 * mvn exec:java -Dexec.mainClass=com.btc.hackathon.viewer.tools.SendCommand \
 *               -Dexec.args="kick 2 10.0.0.5 8080"
 * </pre>
 */
public final class SendCommand {

    private static final long CONNECT_TIMEOUT_SECONDS = 5;

    private SendCommand() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Aufruf: SendCommand <befehl> [id] [host] [port]");
            System.out.println("Befehle: " + String.join(", ", commandNames()));
            System.exit(2);
        }

        CommandType type = parse(args[0]);
        int argIndex = 1;

        // kick und rename brauchen eine Platznummer.
        Integer playerId = null;
        if (type == CommandType.KICK || type == CommandType.RENAME) {
            if (args.length <= argIndex) {
                System.err.println(type.wire() + " braucht eine Platznummer");
                System.exit(2);
            }
            playerId = Integer.parseInt(args[argIndex++]);
        }
        String newName = null;
        if (type == CommandType.RENAME) {
            if (args.length <= argIndex) {
                System.err.println("rename braucht einen Namen");
                System.exit(2);
            }
            newName = args[argIndex++];
        }

        String host = args.length > argIndex ? args[argIndex++] : "127.0.0.1";
        int port = args.length > argIndex ? Integer.parseInt(args[argIndex]) : 8080;

        ModerationCommand command = switch (type) {
            case KICK -> ModerationCommand.kick(playerId);
            case RENAME -> ModerationCommand.rename(playerId, newName);
            default -> ModerationCommand.of(type);
        };

        int exitCode = run(command, host, port);
        System.exit(exitCode);
    }

    private static int run(ModerationCommand command, String host, int port) throws Exception {
        AtomicReference<ModerationChannel> channelRef = new AtomicReference<>();
        ServerConnection connection = new ServerConnection(
                ServerConnection.adminEndpoint(host, port),
                message -> dispatch(channelRef, message),
                state -> { });
        ModerationChannel channel = new ModerationChannel(connection);
        channelRef.set(channel);

        try {
            connection.start();
            if (!awaitConnection(connection)) {
                System.err.println("Keine Verbindung zu " + connection.uri());
                return 1;
            }

            CommandReply reply = channel.send(command).get(10, TimeUnit.SECONDS);
            System.out.println(command.describe() + " -> "
                    + (reply.accepted() ? "angenommen" : "abgelehnt")
                    + (reply.message().isBlank() ? "" : ": " + reply.message()));
            return reply.accepted() ? 0 : 1;
        } finally {
            // Erst schliessen, dann beenden: ein System.exit() im try-Zweig risse die
            // Verbindung hart ab und der Server protokollierte einen Fehler.
            connection.close();
        }
    }

    private static void dispatch(AtomicReference<ModerationChannel> channelRef,
                                 ServerMessage message) {
        if (message instanceof CommandReply reply) {
            ModerationChannel channel = channelRef.get();
            if (channel != null) {
                channel.onReply(reply);
            }
        }
        // Lobby- und Spielzustaende interessieren dieses Werkzeug nicht.
    }

    private static boolean awaitConnection(ServerConnection connection) throws InterruptedException {
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_SECONDS * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (connection.state() == ConnectionState.CONNECTED) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private static CommandType parse(String raw) {
        CommandType type = CommandType.fromWire(raw.toLowerCase(Locale.ROOT));
        if (type == null) {
            throw new IllegalArgumentException("Unbekannter Befehl: " + raw
                    + " (bekannt: " + String.join(", ", commandNames()) + ")");
        }
        return type;
    }

    private static String[] commandNames() {
        return java.util.Arrays.stream(CommandType.values())
                .map(CommandType::wire)
                .toArray(String[]::new);
    }
}
