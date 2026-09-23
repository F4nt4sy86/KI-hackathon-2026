package com.btc.hackathon.viewer.control;

import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.model.MapSettings;
import com.btc.hackathon.viewer.net.ConnectionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Bedienlogik der Moderationsknoepfe.
 *
 * <p>Diese Regeln sind der Schutz gegen Fehlbedienung vor Publikum - deshalb werden sie
 * hier durchgespielt, statt sich auf einen Klicktest zu verlassen.
 */
class CommandAvailabilityTest {

    private static final ConnectionState UP = ConnectionState.CONNECTED;

    private static LobbyState lobby(LobbyPhase phase, boolean paused, boolean canStart) {
        return new LobbyState(phase, paused, canStart, 2, 4, 0, 60,
                MapSettings.unknown(), List.of());
    }

    private static Set<CommandType> in(LobbyPhase phase) {
        return CommandAvailability.available(lobby(phase, false, false), UP);
    }

    // --------------------------------------------------------------------- Start

    @Test
    @DisplayName("Start folgt dem can_start des Servers, nicht einer eigenen Rechnung")
    void startFollowsTheServerFlag() {
        // Der Server liefert die Antwort mit; sie nachzubauen hiesse, frueher oder spaeter
        // von dem abzuweichen, was er tatsaechlich erlaubt.
        assertTrue(CommandAvailability.available(lobby(LobbyPhase.OPEN, false, true), UP)
                .contains(CommandType.START));
        assertFalse(CommandAvailability.available(lobby(LobbyPhase.OPEN, false, false), UP)
                .contains(CommandType.START));
    }

    @Test
    @DisplayName("Auch in gesperrter Lobby laesst sich starten, wenn der Server es erlaubt")
    void startWorksInLockedLobby() {
        assertTrue(CommandAvailability.available(lobby(LobbyPhase.LOCKED, false, true), UP)
                .contains(CommandType.START));
    }

    // ------------------------------------------------------------ Pause und Ende

    @Test
    @DisplayName("Im laufenden Match sind Pause und Beenden moeglich")
    void runningAllowsPauseAndEnd() {
        Set<CommandType> available = in(LobbyPhase.RUNNING);

        assertTrue(available.contains(CommandType.PAUSE));
        assertTrue(available.contains(CommandType.END));
        assertFalse(available.contains(CommandType.RESUME));
    }

    @Test
    @DisplayName("Auch im Countdown laesst sich pausieren und beenden")
    void countdownAllowsPauseAndEnd() {
        Set<CommandType> available = in(LobbyPhase.COUNTDOWN);

        assertTrue(available.contains(CommandType.PAUSE));
        assertTrue(available.contains(CommandType.END));
    }

    @Test
    @DisplayName("Ist pausiert, tritt Fortsetzen an die Stelle von Pausieren")
    void pausedOffersResume() {
        Set<CommandType> available =
                CommandAvailability.available(lobby(LobbyPhase.RUNNING, true, false), UP);

        assertTrue(available.contains(CommandType.RESUME));
        assertFalse(available.contains(CommandType.PAUSE));
    }

    @Test
    @DisplayName("Eine Pause laesst sich auch in der Lobby wieder aufheben")
    void resumeIsOfferedOutsideAMatch() {
        // Der Server lehnt den Start ab, solange pausiert ist. Ohne Fortsetzen-Knopf
        // sitzt die Moderation dann fest.
        Set<CommandType> available =
                CommandAvailability.available(lobby(LobbyPhase.OPEN, true, false), UP);

        assertTrue(available.contains(CommandType.RESUME));
    }

    @Test
    @DisplayName("In der Lobby gibt es nichts zu beenden")
    void noEndInLobby() {
        assertFalse(in(LobbyPhase.OPEN).contains(CommandType.END));
        assertFalse(in(LobbyPhase.MATCH_OVER).contains(CommandType.END));
    }

    // ------------------------------------------------------------------- Reset

    @Test
    @DisplayName("Zurueck zur Lobby geht, sobald es etwas wegzuraeumen gibt")
    void resetWhereverSomethingCanBeCleared() {
        assertTrue(in(LobbyPhase.MATCH_OVER).contains(CommandType.RESET));
        // Der Server nimmt "reset" auch waehrend eines Matches an. Wer erst beenden
        // muesste, um in die Lobby zurueckzukommen, braucht zwei Schritte fuer einen.
        assertTrue(in(LobbyPhase.RUNNING).contains(CommandType.RESET));
        assertTrue(in(LobbyPhase.COUNTDOWN).contains(CommandType.RESET));
    }

    @Test
    @DisplayName("In der Lobby raeumt Zurueck zur Lobby nichts weg")
    void noResetInTheLobby() {
        assertFalse(in(LobbyPhase.OPEN).contains(CommandType.RESET));
        assertFalse(in(LobbyPhase.LOCKED).contains(CommandType.RESET));
        assertNotNull(CommandAvailability.blockedReason(
                CommandType.RESET, lobby(LobbyPhase.OPEN, false, false), UP));
    }

    // ------------------------------------------------------------ Sperren, Karte

    @Test
    @DisplayName("Sperren und Oeffnen schliessen einander aus")
    void lockAndUnlockAreExclusive() {
        assertTrue(in(LobbyPhase.OPEN).contains(CommandType.LOCK));
        assertFalse(in(LobbyPhase.OPEN).contains(CommandType.UNLOCK));

        assertTrue(in(LobbyPhase.LOCKED).contains(CommandType.UNLOCK));
        assertFalse(in(LobbyPhase.LOCKED).contains(CommandType.LOCK));
    }

    @Test
    @DisplayName("Die Karte laesst sich nur ausserhalb eines laufenden Matches aendern")
    void mapOnlyOutsideARunningMatch() {
        assertTrue(in(LobbyPhase.OPEN).contains(CommandType.CONFIGURE_MAP));
        assertTrue(in(LobbyPhase.MATCH_OVER).contains(CommandType.CONFIGURE_MAP));
        // Der Server lehnt das im laufenden Match ausdruecklich ab.
        assertFalse(in(LobbyPhase.RUNNING).contains(CommandType.CONFIGURE_MAP));
    }

    @Test
    @DisplayName("Die Kartenvorschau ist jederzeit moeglich")
    void previewIsAlwaysPossible() {
        for (LobbyPhase phase : LobbyPhase.values()) {
            assertTrue(in(phase).contains(CommandType.PREVIEW_MAP), "fehlt in " + phase);
        }
    }

    // -------------------------------------------------------------- Verbindung

    @ParameterizedTest
    @EnumSource(LobbyPhase.class)
    @DisplayName("Ohne Verbindung ist in jeder Phase alles gesperrt")
    void nothingAvailableWhileDisconnected(LobbyPhase phase) {
        assertTrue(CommandAvailability.available(
                lobby(phase, false, true), ConnectionState.DISCONNECTED).isEmpty());
        assertTrue(CommandAvailability.available(
                lobby(phase, false, true), ConnectionState.CONNECTING).isEmpty());
    }

    @Test
    @DisplayName("Fehlende Angaben sperren alles, statt zu werfen")
    void missingInputsBlockEverything() {
        assertTrue(CommandAvailability.available(null, UP).isEmpty());
        assertTrue(CommandAvailability.available(lobby(LobbyPhase.OPEN, false, true), null).isEmpty());
    }

    // ---------------------------------------------------------------------- Kick

    @Test
    @DisplayName("Ein leerer Platz laesst sich nicht freigeben")
    void cannotKickAnEmptySeat() {
        // Der Server lehnt das ab - ein klickbarer Knopf waere nur eine Enttaeuschung.
        assertFalse(CommandAvailability.canKick(slot(false), UP));
        assertTrue(CommandAvailability.canKick(slot(true), UP));
        assertFalse(CommandAvailability.canKick(slot(true), ConnectionState.DISCONNECTED));
        assertFalse(CommandAvailability.canKick(null, UP));
    }

    private static com.btc.hackathon.viewer.model.Slot slot(boolean connected) {
        return new com.btc.hackathon.viewer.model.Slot(
                0, "bot-0", connected, connected ? "1.2.3.4:5" : null, 60, 0, 1, 0, false);
    }

    // ---------------------------------------------------------------- Begruendung

    @Test
    @DisplayName("Ein verfuegbarer Befehl braucht keine Begruendung")
    void noReasonWhenAvailable() {
        assertNull(CommandAvailability.blockedReason(
                CommandType.START, lobby(LobbyPhase.OPEN, false, true), UP));
    }

    @Test
    @DisplayName("Die fehlende Verbindung wird als Grund genannt")
    void reasonNamesMissingConnection() {
        String reason = CommandAvailability.blockedReason(
                CommandType.START, lobby(LobbyPhase.OPEN, false, true),
                ConnectionState.DISCONNECTED);

        assertNotNull(reason);
        assertTrue(reason.contains("Verbindung"), reason);
    }

    @Test
    @DisplayName("Die Pause wird als Startgrund genannt, weil sie den Start blockiert")
    void reasonNamesThePause() {
        String reason = CommandAvailability.blockedReason(
                CommandType.START, lobby(LobbyPhase.OPEN, true, false), UP);

        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains("fortsetzen"), reason);
    }

    @Test
    @DisplayName("Ein laufendes Match wird als Startgrund genannt")
    void reasonNamesTheRunningMatch() {
        String reason = CommandAvailability.blockedReason(
                CommandType.START, lobby(LobbyPhase.RUNNING, false, false), UP);

        assertNotNull(reason);
        assertTrue(reason.contains("Match"), reason);
    }
}
