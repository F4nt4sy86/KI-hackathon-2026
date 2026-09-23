package com.btc.hackathon.viewer.model;

/**
 * Die Antwort auf einen Moderationsbefehl: {@code ack} oder {@code error}.
 *
 * <p>Jeder Befehl bekommt genau eine - nie ein stilles Nichts, denn eine Oberflaeche
 * muss erklaeren koennen, warum ein Knopf nichts bewirkt hat.
 *
 * <p>Zugeordnet wird ueber den <em>Befehlsnamen</em>, nicht ueber eine laufende Nummer:
 * das Protokoll sieht keine vor. Weil der Server die Befehle eines Sockets der Reihe
 * nach beantwortet, ordnet der Steuerkanal sie in Eingangsreihenfolge zu.
 */
public record CommandReply(String cmd, boolean accepted, String message) implements ServerMessage {

    public CommandReply {
        cmd = cmd == null ? "" : cmd;
        message = message == null ? "" : message;
    }

    public static CommandReply ack(String cmd) {
        return new CommandReply(cmd, true, "");
    }

    public static CommandReply error(String cmd, String message) {
        return new CommandReply(cmd, false, message);
    }
}
