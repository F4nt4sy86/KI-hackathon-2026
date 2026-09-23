package com.btc.hackathon.viewer.net;

import com.btc.hackathon.viewer.model.ServerMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die einzige Verbindung zum Server: ein WebSocket auf {@code /ws/admin}.
 *
 * <p>Der Server bietet zwei Endpunkte an - {@code /ws/spectate} nur lesend und
 * {@code /ws/admin} mit allem, was spectate sendet, zuzueglich der Befehle. Diese
 * Anwendung soll beides, also genuegt eine Verbindung auf {@code /ws/admin}.
 *
 * <p>Der Client kommt aus dem JDK ({@link java.net.http.WebSocket}); es braucht keine
 * Netzwerkbibliothek. Darunter liegt TCP, also gibt es - anders als bei den Bots -
 * weder Paketverlust noch vertauschte Reihenfolge.
 *
 * <p>Reisst die Verbindung ab, wird mit wachsendem Abstand erneut versucht (1 s, 2 s,
 * 4 s, danach alle 5 s). Der Zustand wird nach aussen gemeldet, damit die Knopfleiste
 * sich sperrt, solange nichts ankommen kann.
 */
public final class ServerConnection implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ServerConnection.class.getName());

    private static final long RECONNECT_START_MILLIS = 1000;
    private static final long RECONNECT_MAX_MILLIS = 5000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private volatile URI uri;
    private final ServerMessageCodec codec = new ServerMessageCodec();
    private final Consumer<ServerMessage> sink;
    private final Consumer<ConnectionState> stateListener;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "server-reconnect");
                t.setDaemon(true);
                return t;
            });

    private volatile WebSocket socket;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile boolean closed;
    private volatile long reconnectDelayMillis = RECONNECT_START_MILLIS;

    /**
     * Zaehlt jeden Adresswechsel mit.
     *
     * <p>Wird die Adresse umgestellt, sind noch Rueckrufe der alten Verbindung unterwegs:
     * ein laufender Verbindungsversuch, ein {@code onClose} des abgebrochenen Sockets.
     * Ohne diese Nummer wuerde jeder davon eine Neuverbindung einplanen - und der Viewer
     * haette am Ende zwei Verbindungen, eine davon auf die alte Adresse. Jeder Rueckruf
     * traegt die Nummer seiner Verbindung und wird verworfen, sobald sie veraltet ist.
     */
    private volatile int generation;

    /** Zaehler fuer die technische Anzeige. */
    private volatile long messagesReceived;
    private volatile long parseErrors;
    private volatile long lastMessageNanos;

    /**
     * @param sink          bekommt jede erkannte Nachricht, auf dem Thread des
     *                      WebSocket-Clients
     * @param stateListener wird bei jedem Zustandswechsel gerufen, ebenfalls nicht auf
     *                      dem JavaFX-Thread
     */
    public ServerConnection(URI uri, Consumer<ServerMessage> sink,
                            Consumer<ConnectionState> stateListener) {
        this.uri = uri;
        this.sink = sink;
        this.stateListener = stateListener == null ? s -> { } : stateListener;
    }

    /** Baut die Verbindung auf und haelt sie selbsttaetig aufrecht. */
    public void start() {
        connect();
    }

    /**
     * Stellt auf eine andere Adresse um, ohne die Anwendung neu zu starten.
     *
     * <p>Die bestehende Verbindung wird abgebrochen und sofort - ohne Wartezeit - auf die
     * neue Adresse aufgebaut. Ein Druck auf "Verbinden" ist eine ausdrueckliche Ansage;
     * darauf erst eine Sekunde zu warten, waehrend vor Publikum nichts passiert, waere die
     * falsche Antwort.
     */
    public void connectTo(URI target) {
        if (closed) {
            return;
        }
        generation++;
        WebSocket previous = socket;
        socket = null;
        if (previous != null) {
            previous.abort();
        }
        uri = target;
        reconnectDelayMillis = RECONNECT_START_MILLIS;
        LOG.info("Neue Serveradresse: " + target);
        // Ein Wechsel auf dieselbe Adresse soll trotzdem sichtbar neu verbinden.
        setState(ConnectionState.DISCONNECTED);
        connect();
    }

    private void connect() {
        if (closed) {
            return;
        }
        final int gen = generation;
        final URI target = uri;
        setState(ConnectionState.CONNECTING);
        http.newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(target, new Handler(gen))
                .whenComplete((ws, error) -> {
                    if (closed || gen != generation) {
                        // Veraltet: entweder beendet oder inzwischen umgestellt.
                        if (ws != null) {
                            ws.abort();
                        }
                        return;
                    }
                    if (error != null) {
                        LOG.log(Level.FINE, "Server nicht erreichbar: " + target, error);
                        setState(ConnectionState.DISCONNECTED);
                        scheduleReconnect(gen);
                    } else {
                        socket = ws;
                        reconnectDelayMillis = RECONNECT_START_MILLIS;
                        setState(ConnectionState.CONNECTED);
                        LOG.info("Verbunden mit " + target);
                    }
                });
    }

    private void scheduleReconnect(int gen) {
        if (closed || gen != generation || scheduler.isShutdown()) {
            return;
        }
        long delay = reconnectDelayMillis;
        reconnectDelayMillis = Math.min(delay * 2, RECONNECT_MAX_MILLIS);
        try {
            scheduler.schedule(() -> {
                // Zwischen Einplanen und Ausfuehren kann die Adresse gewechselt haben -
                // dann hat connectTo() bereits verbunden und dieser Versuch entfaellt.
                if (gen == generation) {
                    connect();
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Neuverbindung liess sich nicht einplanen", e);
        }
    }

    /**
     * Schickt einen Text an den Server.
     *
     * @return ein Future, das fehlschlaegt, wenn gerade keine Verbindung steht
     */
    public CompletableFuture<Void> send(String text) {
        WebSocket ws = socket;
        if (ws == null || ws.isOutputClosed() || !state.isConnected()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Keine Verbindung zum Server"));
        }
        return ws.sendText(text, true).thenApply(unused -> null);
    }

    public ConnectionState state() {
        return state;
    }

    public URI uri() {
        return uri;
    }

    public long messagesReceived() {
        return messagesReceived;
    }

    public long parseErrors() {
        return parseErrors;
    }

    /** Millisekunden seit der letzten Nachricht, oder -1, wenn noch keine kam. */
    public long millisSinceLastMessage() {
        long last = lastMessageNanos;
        return last == 0 ? -1 : (System.nanoTime() - last) / 1_000_000L;
    }

    private void setState(ConnectionState newState) {
        if (state != newState) {
            state = newState;
            try {
                stateListener.accept(newState);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Beobachter des Verbindungszustands hat geworfen", e);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Viewer beendet");
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "Schliessen des WebSockets", e);
            }
            ws.abort();
        }
        scheduler.shutdownNow();
        setState(ConnectionState.DISCONNECTED);
        LOG.info("Verbindung beendet");
    }

    /**
     * Empfaengt und wertet aus.
     *
     * <p>Laeuft auf einem Thread des HTTP-Clients, nicht auf dem JavaFX-Thread.
     */
    private final class Handler implements WebSocket.Listener {

        /** Die Verbindung, zu der dieser Handler gehoert. */
        private final int gen;

        Handler(int gen) {
            this.gen = gen;
        }

        /** Rueckrufe einer inzwischen abgeloesten Verbindung werden verworfen. */
        private boolean outdated() {
            return closed || gen != generation;
        }

        /**
         * Eine Textnachricht kann in mehreren Teilen ankommen.
         *
         * <p>Genau hier geht es sonst schief: wer den ersten Teil schon auswertet,
         * bekommt bei grossen Zustandsnachrichten sporadisch Parserfehler, die sich
         * unter Last haeufen und bei kleinen Feldern nie auftreten.
         */
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            // Unbegrenzte Nachfrage: der Zustandsspeicher verwirft ohnehin Veraltetes,
            // eine Flusskontrolle brauchen wir nicht.
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (outdated()) {
                return null;
            }
            buffer.append(data);
            if (!last) {
                return null;
            }
            String message = buffer.toString();
            buffer.setLength(0);
            dispatch(message);
            return null;
        }

        private void dispatch(String message) {
            messagesReceived++;
            lastMessageNanos = System.nanoTime();
            try {
                Optional<ServerMessage> parsed = codec.parse(message);
                parsed.ifPresent(sink);
            } catch (ProtocolException e) {
                parseErrors++;
                LOG.log(Level.WARNING, "Unlesbare Nachricht verworfen: " + e.getMessage());
            } catch (RuntimeException e) {
                // Auffangnetz: kein Fehler in der Auswertung darf die Anzeige beenden.
                parseErrors++;
                LOG.log(Level.WARNING, "Unerwarteter Fehler beim Auswerten - Nachricht verworfen", e);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (outdated()) {
                return null;
            }
            LOG.info("Server hat die Verbindung geschlossen (" + statusCode + ")");
            socket = null;
            setState(ConnectionState.DISCONNECTED);
            scheduleReconnect(gen);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (outdated()) {
                return;
            }
            LOG.log(Level.FINE, "Fehler im WebSocket", error);
            socket = null;
            setState(ConnectionState.DISCONNECTED);
            scheduleReconnect(gen);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, java.nio.ByteBuffer data,
                                           boolean last) {
            // Der Server spricht nur Text; Binaeres wird stillschweigend verworfen.
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, java.nio.ByteBuffer message) {
            webSocket.sendPong(message);
            return null;
        }
    }

    /** Endpunkte des Servers, aus einer Basis-Adresse abgeleitet. */
    public static URI adminEndpoint(String host, int port) {
        return URI.create("ws://" + host + ":" + port + "/ws/admin");
    }

    public static URI spectateEndpoint(String host, int port) {
        return URI.create("ws://" + host + ":" + port + "/ws/spectate");
    }

    /** Nur fuer Protokollausgaben. */
    static List<String> describe(URI uri) {
        return List.of(uri.getHost(), String.valueOf(uri.getPort()), uri.getPath());
    }
}
