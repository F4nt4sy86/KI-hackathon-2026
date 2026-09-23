package com.btc.hackathon.viewer.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ein sehr kleiner WebSocket-Server nach RFC 6455 - gerade genug fuer den Fake-Server.
 *
 * <p>Absichtlich von Hand geschrieben statt mit einer Bibliothek: der Viewer selbst
 * braucht nur den WebSocket-<em>Client</em>, und den bringt das JDK mit. Eine
 * Netzwerkbibliothek nur fuer das Entwicklungswerkzeug mitzuschleppen waere ein hoher
 * Preis fuer etwas, das aus einem Handschlag und einem Rahmenformat besteht.
 *
 * <p>Entsprechend eng ist der Funktionsumfang: Textrahmen in beide Richtungen, Ping und
 * Schliessen. Keine Erweiterungen, keine Kompression, keine Teilrahmen beim Senden. Fuer
 * einen echten Dienst waere das zu wenig - fuer ein Testgegenstueck auf dem eigenen
 * Rechner genau richtig.
 */
public final class MiniWebSocketServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(MiniWebSocketServer.class.getName());

    /** Fest im Standard verankert; geht in die Antwort des Handschlags ein. */
    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final int OP_TEXT = 0x1;
    private static final int OP_CLOSE = 0x8;
    private static final int OP_PING = 0x9;
    private static final int OP_PONG = 0xA;

    private final int port;
    private final Consumer<Session> onConnect;
    private final BiConsumer<Session, String> onText;

    private final List<Session> sessions = new CopyOnWriteArrayList<>();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean closed;

    public MiniWebSocketServer(int port, Consumer<Session> onConnect,
                               BiConsumer<Session, String> onText) {
        this.port = port;
        this.onConnect = onConnect;
        this.onText = onText;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        acceptThread = new Thread(this::acceptLoop, "mini-ws-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                Thread worker = new Thread(() -> serve(socket), "mini-ws-client");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (!closed) {
                    LOG.log(Level.FINE, "Verbindung nicht angenommen", e);
                }
                return;
            }
        }
    }

    private void serve(Socket socket) {
        Session session = null;
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            String path = handshake(in, out);
            if (path == null) {
                socket.close();
                return;
            }

            session = new Session(socket, out, path);
            sessions.add(session);
            onConnect.accept(session);

            readLoop(in, session);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Verbindung beendet", e);
        } finally {
            if (session != null) {
                sessions.remove(session);
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // Beim Aufraeumen ist ein Fehler ohne Bedeutung.
            }
        }
    }

    /**
     * Fuehrt den HTTP-Upgrade durch.
     *
     * @return der angefragte Pfad, oder {@code null}, wenn es kein WebSocket-Upgrade war
     */
    private String handshake(InputStream in, OutputStream out) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || !requestLine.startsWith("GET ")) {
            writeHttpError(out, "400 Bad Request");
            return null;
        }
        String[] parts = requestLine.split(" ");
        String path = parts.length > 1 ? parts[1] : "/";

        String key = null;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (name.equals("sec-websocket-key")) {
                key = value;
            }
        }
        if (key == null) {
            // Kein Upgrade: eine erklaerende Seite ist hilfreicher als ein blankes 404.
            writeHttpError(out, "426 Upgrade Required");
            return null;
        }

        String accept = Base64.getEncoder().encodeToString(sha1(key + MAGIC));
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return path;
    }

    private static void writeHttpError(OutputStream out, String status) throws IOException {
        String body = "Dieser Port spricht nur WebSocket: /ws/admin oder /ws/spectate\n";
        String response = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "Connection: close\r\n\r\n" + body;
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void readLoop(InputStream in, Session session) throws IOException {
        StringBuilder continuation = new StringBuilder();
        while (!closed && !session.closed) {
            int b0 = in.read();
            if (b0 < 0) {
                return;
            }
            int b1 = in.read();
            if (b1 < 0) {
                return;
            }
            boolean fin = (b0 & 0x80) != 0;
            int opcode = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long length = b1 & 0x7F;

            if (length == 126) {
                length = ((long) in.read() << 8) | in.read();
            } else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) {
                    length = (length << 8) | in.read();
                }
            }
            byte[] mask = new byte[4];
            if (masked) {
                readFully(in, mask);
            }
            byte[] payload = new byte[(int) length];
            readFully(in, payload);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }

            switch (opcode) {
                case OP_CLOSE -> {
                    session.close();
                    return;
                }
                case OP_PING -> session.writeFrame(OP_PONG, payload);
                case OP_PONG -> { /* nichts zu tun */ }
                case OP_TEXT, 0x0 -> {
                    continuation.append(new String(payload, StandardCharsets.UTF_8));
                    if (fin) {
                        String text = continuation.toString();
                        continuation.setLength(0);
                        try {
                            onText.accept(session, text);
                        } catch (RuntimeException e) {
                            LOG.log(Level.WARNING, "Fehler beim Verarbeiten einer Nachricht", e);
                        }
                    }
                }
                default -> { /* unbekannter Rahmentyp: verwerfen */ }
            }
        }
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int read = 0;
        while (read < buffer.length) {
            int n = in.read(buffer, read, buffer.length - read);
            if (n < 0) {
                throw new IOException("Verbindung waehrend eines Rahmens beendet");
            }
            read += n;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') {
                int end = sb.length();
                if (end > 0 && sb.charAt(end - 1) == '\r') {
                    sb.setLength(end - 1);
                }
                return sb.toString();
            }
            sb.append((char) c);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static byte[] sha1(String value) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 fehlt in dieser Laufzeitumgebung", e);
        }
    }

    // ------------------------------------------------------------------ Senden

    /** Schickt einen Text an alle Verbindungen. */
    public void broadcast(String text) {
        for (Session session : sessions) {
            session.send(text);
        }
    }

    /** Schickt einen Text an alle Verbindungen auf einem bestimmten Pfad. */
    public void broadcast(String path, String text) {
        for (Session session : sessions) {
            if (session.path().equals(path)) {
                session.send(text);
            }
        }
    }

    public int sessionCount() {
        return sessions.size();
    }

    @Override
    public void close() {
        closed = true;
        for (Session session : sessions) {
            session.close();
        }
        sessions.clear();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Beim Herunterfahren ohne Bedeutung.
            }
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }

    /** Eine offene Verbindung. */
    public static final class Session {

        private final Socket socket;
        private final OutputStream out;
        private final String path;
        private volatile boolean closed;

        Session(Socket socket, OutputStream out, String path) {
            this.socket = socket;
            this.out = out;
            this.path = path;
        }

        public String path() {
            return path;
        }

        public String remoteAddress() {
            return socket.getRemoteSocketAddress().toString();
        }

        public void send(String text) {
            writeFrame(OP_TEXT, text.getBytes(StandardCharsets.UTF_8));
        }

        /** Rahmen vom Server werden nie maskiert - so schreibt es der Standard vor. */
        synchronized void writeFrame(int opcode, byte[] payload) {
            if (closed) {
                return;
            }
            try {
                out.write(0x80 | opcode);
                int length = payload.length;
                if (length < 126) {
                    out.write(length);
                } else if (length <= 0xFFFF) {
                    out.write(126);
                    out.write((length >> 8) & 0xFF);
                    out.write(length & 0xFF);
                } else {
                    out.write(127);
                    for (int shift = 56; shift >= 0; shift -= 8) {
                        out.write((int) (((long) length >> shift) & 0xFF));
                    }
                }
                out.write(payload);
                out.flush();
            } catch (IOException e) {
                closed = true;
            }
        }

        public void close() {
            if (closed) {
                return;
            }
            writeFrame(OP_CLOSE, new byte[0]);
            closed = true;
            try {
                socket.close();
            } catch (IOException ignored) {
                // Beim Schliessen ohne Bedeutung.
            }
        }

        public boolean isClosed() {
            return closed;
        }
    }
}
