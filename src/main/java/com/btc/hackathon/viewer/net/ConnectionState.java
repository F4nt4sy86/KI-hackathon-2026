package com.btc.hackathon.viewer.net;

/** Zustand der Verbindung zum Server. */
public enum ConnectionState {
    /** Keine Verbindung, kein Versuch laeuft. */
    DISCONNECTED,
    /** Verbindungsversuch laeuft, gegebenenfalls als Wiederholung nach Abbruch. */
    CONNECTING,
    /** Verbunden - nur so kommen Zustaende an und Befehle durch. */
    CONNECTED;

    public boolean isConnected() {
        return this == CONNECTED;
    }

    public String label() {
        return switch (this) {
            case DISCONNECTED -> "getrennt";
            case CONNECTING -> "verbinde...";
            case CONNECTED -> "verbunden";
        };
    }
}
