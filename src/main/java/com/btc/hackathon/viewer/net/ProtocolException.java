package com.btc.hackathon.viewer.net;

/**
 * Eine Nachricht liess sich nicht auswerten.
 *
 * <p>Wird innerhalb der Netzwerkschicht geworfen und dort auch gefangen: die Anwendung
 * darf an keiner Nachricht des Servers scheitern.
 */
public class ProtocolException extends Exception {

    public ProtocolException(String message) {
        super(message);
    }
}
