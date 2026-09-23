package com.btc.hackathon.viewer.control;

import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.net.ConnectionState;

import java.util.EnumSet;
import java.util.Set;

/**
 * Entscheidet, welche Befehle im aktuellen Zustand sinnvoll sind.
 *
 * <p>Bewusst eine reine Funktion ohne Oberflaechenbezug: sie laesst sich vollstaendig
 * testen, ohne die JavaFX-Laufzeit zu starten, und die Knopfleiste bindet sich nur noch
 * an das Ergebnis. Kein Knopf ist anklickbar, der in der aktuellen Phase nichts bewirken
 * wuerde - das verhindert Fehlbedienung waehrend einer laufenden Vorfuehrung.
 *
 * <p>Fuer "Start" wird die Regel <em>nicht</em> nachgebaut: der Server liefert mit
 * {@code can_start} seine eigene Antwort auf "wuerde Start jetzt durchgehen" mit. Wer
 * das nachrechnet, driftet frueher oder spaeter von dem ab, was der Server tatsaechlich
 * erlaubt.
 *
 * <p>Die uebrigen Regeln bilden nach, was die Sitzungsverwaltung des Servers annimmt
 * oder ablehnt, damit ein Knopf nicht erst nach dem Klick als unmoeglich auffaellt.
 */
public final class CommandAvailability {

    private CommandAvailability() {
    }

    /**
     * @param lobby      der zuletzt empfangene Lobby-Zustand
     * @param connection Zustand der Serververbindung
     */
    public static Set<CommandType> available(LobbyState lobby, ConnectionState connection) {
        EnumSet<CommandType> allowed = EnumSet.noneOf(CommandType.class);
        if (lobby == null || connection == null || !connection.isConnected()) {
            // Ohne Verbindung kann kein Befehl ankommen - dann bleibt alles gesperrt.
            return allowed;
        }

        LobbyPhase phase = lobby.phase();
        boolean live = phase == LobbyPhase.RUNNING || phase == LobbyPhase.COUNTDOWN;

        // Der Server hat "paused" bereits eingerechnet.
        if (lobby.canStart()) {
            allowed.add(CommandType.START);
        }
        if (lobby.paused()) {
            // Auch ausserhalb eines Matches anbieten: eine Pause blockiert den Start,
            // also muss sie sich jederzeit aufheben lassen.
            allowed.add(CommandType.RESUME);
        } else if (live) {
            allowed.add(CommandType.PAUSE);
        }
        if (live) {
            allowed.add(CommandType.END);
        }
        // Zurueck zur Lobby geht ueberall dort, wo es etwas wegzuraeumen gibt: ein
        // anlaufendes oder laufendes Match und ein fertiges Ergebnis. Der Server nimmt
        // "reset" zwar in jedem Zustand an, aber in der offenen Lobby raeumt es nichts
        // weg - ein Knopf, der nichts bewirkt, ist nur eine Gelegenheit zum Fehlklick.
        if (live || phase == LobbyPhase.MATCH_OVER) {
            allowed.add(CommandType.RESET);
        }
        if (phase == LobbyPhase.OPEN) {
            allowed.add(CommandType.LOCK);
        }
        if (phase == LobbyPhase.LOCKED) {
            allowed.add(CommandType.UNLOCK);
        }
        // Kartenwechsel lehnt der Server nur waehrend eines laufenden Matches ab.
        if (phase != LobbyPhase.RUNNING) {
            allowed.add(CommandType.CONFIGURE_MAP);
        }
        allowed.add(CommandType.PREVIEW_MAP);
        allowed.add(CommandType.RENAME);
        allowed.add(CommandType.KICK);
        return allowed;
    }

    /**
     * Begruendung, warum ein Befehl gerade gesperrt ist - als Hinweis am Knopf.
     *
     * @return der Hinweistext, oder {@code null}, wenn der Befehl verfuegbar ist
     */
    public static String blockedReason(CommandType type, LobbyState lobby,
                                       ConnectionState connection) {
        if (available(lobby, connection).contains(type)) {
            return null;
        }
        if (connection == null || !connection.isConnected()) {
            return "Keine Verbindung zum Server";
        }
        if (lobby == null) {
            return "Noch kein Zustand vom Server empfangen";
        }
        if (type == CommandType.START) {
            if (lobby.paused()) {
                return "Erst fortsetzen, dann starten";
            }
            if (lobby.phase() == LobbyPhase.RUNNING || lobby.phase() == LobbyPhase.COUNTDOWN) {
                return "Es laeuft bereits ein Match";
            }
            if (lobby.phase() == LobbyPhase.MATCH_OVER) {
                return "Erst zurueck zur Lobby, dann neu starten";
            }
            return "Mindestens " + lobby.minPlayers() + " Teilnehmer noetig (aktuell "
                    + lobby.connectedCount() + ")";
        }
        if (type == CommandType.RESET) {
            return "Es laeuft kein Match und es liegt kein Ergebnis vor";
        }
        if (type == CommandType.END) {
            return "Es laeuft kein Match";
        }
        if (type == CommandType.PAUSE) {
            return lobby.paused() ? "Bereits pausiert" : "Es laeuft kein Match";
        }
        if (type == CommandType.RESUME) {
            return "Nicht pausiert";
        }
        if (type == CommandType.CONFIGURE_MAP) {
            return "Waehrend eines laufenden Matches nicht moeglich";
        }
        return "In Phase " + lobby.phase().label() + " nicht moeglich";
    }

    /**
     * Ob ein Platz freigegeben werden kann.
     *
     * <p>Getrennt von der Befehlsliste, weil die Antwort je Platz verschieden ausfaellt:
     * einen leeren Platz lehnt der Server ab.
     */
    public static boolean canKick(com.btc.hackathon.viewer.model.Slot slot,
                                  ConnectionState connection) {
        return slot != null && slot.connected() && connection != null && connection.isConnected();
    }
}
