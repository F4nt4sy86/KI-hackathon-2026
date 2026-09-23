package com.btc.hackathon.viewer.control;

/**
 * Die Befehle, die die Moderation an den Server schicken kann.
 *
 * <p>Der Name auf der Leitung ist zugleich der Name, unter dem die Antwort zurueckkommt
 * ({@code {"type":"ack","cmd":"start"}}) - das Protokoll kennt keine laufende Nummer.
 */
public enum CommandType {

    /** Startet den Countdown. Scheitert, solange nicht genug Plaetze belegt sind. */
    START("start", "Spiel starten"),
    /** Haelt die Tick-Schleife an. Die Bots behalten ihre Verbindung, die Uhr steht. */
    PAUSE("pause", "Pausieren"),
    RESUME("resume", "Fortsetzen"),
    /** Beendet das laufende Match sofort und laesst das Ergebnis stehen. */
    END("end", "Match beenden"),
    /** Raeumt das Ergebnis weg und kehrt in die Lobby zurueck. */
    RESET("reset", "Zurueck zur Lobby"),
    /** Keine neuen Bots mehr aufnehmen. */
    LOCK("lock", "Lobby sperren"),
    UNLOCK("unlock", "Lobby oeffnen"),
    /** Gibt einen Platz frei. Der Bot muss sich neu anmelden. */
    KICK("kick", "Platz freigeben"),
    /**
     * Setzt den Anzeigenamen eines Platzes und ueberschreibt damit dauerhaft den Namen,
     * den der Bot sich bei der Anmeldung selbst gegeben hat.
     */
    RENAME("rename", "Umbenennen"),
    /** Einstellungen fuer das <em>naechste</em> Match. */
    CONFIGURE_MAP("configure_map", "Karte einstellen"),
    /** Erzeugt ein Feld mit den aktuellen Einstellungen, ohne etwas zu starten. */
    PREVIEW_MAP("preview_map", "Karte vorschauen");

    private final String wire;
    private final String label;

    CommandType(String wire, String label) {
        this.wire = wire;
        this.label = label;
    }

    /** Der Wert des {@code cmd}-Feldes. */
    public String wire() {
        return wire;
    }

    /** Beschriftung fuer die Oberflaeche. */
    public String label() {
        return label;
    }

    public static CommandType fromWire(String wire) {
        for (CommandType type : values()) {
            if (type.wire.equals(wire)) {
                return type;
            }
        }
        return null;
    }
}
