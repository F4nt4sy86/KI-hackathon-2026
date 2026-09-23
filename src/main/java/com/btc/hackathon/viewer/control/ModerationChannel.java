package com.btc.hackathon.viewer.control;

import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.net.ServerConnection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Setzt Moderationsbefehle ab und ordnet die Antworten zu.
 *
 * <p>Das Protokoll kennt keine laufende Nummer: die Antwort nennt nur den Befehlsnamen.
 * Zugeordnet wird deshalb nach Name und Eingangsreihenfolge - der Server beantwortet die
 * Befehle eines Sockets der Reihe nach, also ist das eindeutig, solange nicht zwei
 * gleichnamige Befehle gleichzeitig unterwegs sind. Selbst dann stimmt die Zuordnung,
 * weil beide dieselbe Warteschlange in der Reihenfolge ihres Absendens verlassen.
 *
 * <p>Bleibt eine Antwort aus, schlaegt der Befehl nach kurzer Zeit sichtbar fehl. Ein
 * stillschweigend verlorener Moderationsbefehl waere der schlechteste denkbare Ausgang.
 */
public final class ModerationChannel {

    private static final Logger LOG = Logger.getLogger(ModerationChannel.class.getName());

    /** Laenger als so wartet die Oberflaeche nicht auf eine Antwort. */
    private static final long REPLY_TIMEOUT_SECONDS = 5;

    private final ServerConnection connection;
    private final Deque<Pending> pending = new ArrayDeque<>();

    public ModerationChannel(ServerConnection connection) {
        this.connection = connection;
    }

    private record Pending(String cmd, CompletableFuture<CommandReply> future) {
    }

    /**
     * Setzt einen Befehl ab.
     *
     * @return ein Future mit der Antwort des Servers. Eine Ablehnung ist kein Fehler des
     *         Futures, sondern eine Antwort mit {@code accepted == false} und Begruendung.
     */
    public CompletableFuture<CommandReply> send(ModerationCommand command) {
        CompletableFuture<CommandReply> answer = new CompletableFuture<>();
        String wire = command.type().wire();

        synchronized (pending) {
            pending.addLast(new Pending(wire, answer));
        }
        answer.orTimeout(REPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((reply, error) -> forget(answer));

        connection.send(command.toJson()).whenComplete((unused, error) -> {
            if (error != null) {
                answer.completeExceptionally(error);
            }
        });
        return answer;
    }

    /**
     * Meldet, dass eine Kartenvorschau eingetroffen ist.
     *
     * <p>{@code preview_map} ist der eine Befehl, der <em>nicht</em> mit {@code ack}
     * beantwortet wird: der Server schickt statt einer Bestaetigung direkt die
     * {@code map_preview}-Nachricht. Ohne diesen Sonderweg liefe der Befehl in die
     * Zeitueberschreitung, obwohl er einwandfrei ausgefuehrt wurde.
     */
    public void onPreview() {
        onReply(CommandReply.ack(CommandType.PREVIEW_MAP.wire()));
    }

    /**
     * Uebernimmt eine eingegangene Antwort.
     *
     * <p>Wird vom Nachrichtenverteiler gerufen, sobald eine {@code ack}- oder
     * {@code error}-Nachricht eintrifft.
     */
    public void onReply(CommandReply reply) {
        Pending match = takeMatching(reply.cmd());
        if (match == null) {
            // Antwort auf einen bereits abgelaufenen Befehl, oder ein zweiter Moderator
            // an derselben Sitzung - nur protokollieren.
            LOG.fine("Antwort ohne wartenden Befehl: " + reply.cmd());
            return;
        }
        match.future().complete(reply);
    }

    private Pending takeMatching(String cmd) {
        synchronized (pending) {
            for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
                Pending candidate = it.next();
                if (candidate.cmd().equals(cmd)) {
                    it.remove();
                    return candidate;
                }
            }
            // Der Server konnte den Befehl nicht einmal lesen: dann ist cmd leer, und die
            // Antwort gehoert zum aeltesten offenen Befehl.
            if (cmd.isEmpty()) {
                return pending.pollFirst();
            }
            return null;
        }
    }

    private void forget(CompletableFuture<CommandReply> future) {
        synchronized (pending) {
            pending.removeIf(p -> p.future() == future);
        }
    }

    /** Bricht alle offenen Befehle ab, etwa wenn die Verbindung abreisst. */
    public void failAllPending(Throwable cause) {
        Deque<Pending> open;
        synchronized (pending) {
            open = new ArrayDeque<>(pending);
            pending.clear();
        }
        for (Pending p : open) {
            p.future().completeExceptionally(cause);
        }
        if (!open.isEmpty()) {
            LOG.log(Level.FINE, "Offene Befehle abgebrochen: " + open.size());
        }
    }

    public int pendingCount() {
        synchronized (pending) {
            return pending.size();
        }
    }
}
