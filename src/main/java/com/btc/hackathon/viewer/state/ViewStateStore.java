package com.btc.hackathon.viewer.state;

import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.model.MapPreview;
import com.btc.hackathon.viewer.model.MatchEnd;
import com.btc.hackathon.viewer.model.MatchInit;
import com.btc.hackathon.viewer.model.MatchState;
import com.btc.hackathon.viewer.model.ServerMessage;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Die Uebergabestelle zwischen Netzthread und Zeichenthread.
 *
 * <p>Der Netzthread schreibt ausschliesslich hier hinein, der Renderer liest im Bildtakt
 * heraus. Bewusst kein {@code Platform.runLater} je Nachricht: dessen Warteschlange ist
 * unbegrenzt, und sobald der FX-Thread kurz stockt - Speicherbereinigung, Fenster
 * verschieben, Groesse aendern - staut sie sich auf und die Anzeige laeuft der Realitaet
 * hinterher. Dieser Speicher hat Kapazitaet eins; Veraltetes wird schlicht ueberschrieben.
 * Bei 60 Zustaenden je Sekunde ist genau das die richtige Semantik.
 *
 * <p>Anders als bei einer UDP-Anbindung ist hier keine Behandlung vertauschter
 * Reihenfolge noetig: darunter liegt TCP.
 */
public final class ViewStateStore {

    private final AtomicReference<ViewState> state = new AtomicReference<>(ViewState.initial());

    private final LongAdder lobbyMessages = new LongAdder();
    private final LongAdder stateMessages = new LongAdder();
    private volatile long lastStateNanos;

    // Nur vom FX-Thread benutzt: gleitendes Fenster fuer die Zustandsrate.
    private long rateWindowStartNanos = System.nanoTime();
    private long rateWindowStartCount;
    private double statesPerSecond;

    /**
     * Uebernimmt eine Nachricht des Servers.
     *
     * <p>{@link CommandReply} gehoert nicht hierher - Antworten auf Moderationsbefehle
     * gehen an den Steuerkanal, nicht in die Darstellung.
     *
     * @return {@code true}, wenn die Nachricht den Zustand veraendert hat
     */
    public boolean accept(ServerMessage message) {
        return switch (message) {
            case LobbyState lobby -> {
                lobbyMessages.increment();
                state.updateAndGet(s -> s.withLobby(lobby));
                yield true;
            }
            case MatchInit init -> {
                state.updateAndGet(s -> s.withMatchInit(init));
                yield true;
            }
            case MatchState tick -> {
                stateMessages.increment();
                lastStateNanos = tick.receivedAtNanos();
                state.updateAndGet(s -> s.withState(tick));
                yield true;
            }
            case MatchEnd end -> {
                state.updateAndGet(s -> s.withResult(end));
                yield true;
            }
            case MapPreview preview -> {
                state.updateAndGet(s -> s.withPreview(preview.board()));
                yield true;
            }
            // Gehoert dem Steuerkanal; hier bewusst folgenlos.
            case CommandReply ignored -> false;
        };
    }

    /** Der aktuelle Zustand. Wird vom JavaFX-Thread im Bildtakt geholt. */
    public ViewState current() {
        return state.get();
    }

    /** Verwirft die Kartenvorschau, nachdem sie angezeigt wurde. */
    public void clearPreview() {
        state.updateAndGet(ViewState::withoutPreview);
    }

    /** Zuruecksetzen, etwa nach einem Verbindungsabbruch. */
    public void reset() {
        state.set(ViewState.initial());
    }

    // ------------------------------------------------------------------ Kennzahlen

    /** Aktualisiert und liefert die Zustandsrate. Nur vom JavaFX-Thread aufrufen. */
    public double updateStatesPerSecond(long nowNanos) {
        long elapsed = nowNanos - rateWindowStartNanos;
        if (elapsed >= 1_000_000_000L) {
            long count = stateMessages.sum();
            statesPerSecond = (count - rateWindowStartCount) * 1_000_000_000.0 / elapsed;
            rateWindowStartNanos = nowNanos;
            rateWindowStartCount = count;
        }
        return statesPerSecond;
    }

    public double statesPerSecond() {
        return statesPerSecond;
    }

    public long stateMessages() {
        return stateMessages.sum();
    }

    public long lobbyMessages() {
        return lobbyMessages.sum();
    }

    /**
     * Bruchteil eines Ticks seit dem letzten Spielzustand.
     *
     * <p>Damit bewegen sich die Figuren auch zwischen zwei Zustaenden weiter. Der Server
     * schickt 60 Zustaende je Sekunde; ohne diesen Zwischenschritt ruckelt es auf jedem
     * Schirm mit hoeherer Bildwiederholrate.
     *
     * @return ein Wert von 0 bis 1
     */
    public double subTickProgress(long nowNanos, int tickRate) {
        long last = lastStateNanos;
        if (last == 0 || tickRate <= 0) {
            return 0;
        }
        double tickNanos = 1_000_000_000.0 / tickRate;
        double elapsed = nowNanos - last;
        if (elapsed <= 0) {
            return 0;
        }
        // Bei stockender Verbindung nicht ueber einen Tick hinauslaufen lassen - die
        // Figur soll an der letzten bekannten Stelle stehen bleiben, nicht davonwandern.
        return Math.min(1.0, elapsed / tickNanos);
    }

    /** Millisekunden seit dem letzten Spielzustand, oder -1, wenn noch keiner kam. */
    public long millisSinceLastState() {
        long last = lastStateNanos;
        return last == 0 ? -1 : (System.nanoTime() - last) / 1_000_000L;
    }
}
