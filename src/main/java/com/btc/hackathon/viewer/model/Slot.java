package com.btc.hackathon.viewer.model;

/**
 * Ein Sitzplatz der Lobby - belegt oder frei.
 *
 * <p>Leere Plaetze werden mitgeschickt, damit die Oberflaeche ein festes Raster
 * zeichnen kann statt einer wachsenden Liste.
 *
 * <p>Die Messwerte sind der Grund, warum diese Nachricht auch ohne Aenderung zweimal
 * pro Sekunde kommt: ein Bot, der verstummt, muss sichtbar tot aussehen, <em>bevor</em>
 * jemand Start drueckt.
 *
 * @param addr          Quelladresse, {@code null} bei freiem Platz
 * @param staleMs       Zeit seit dem letzten Paket dieses Platzes, {@code null} wenn nie
 *                      eines kam. Ausdruecklich <em>keine</em> Laufzeitmessung.
 * @param stale         der Server hat seine eigene Schwelle darauf angewandt
 */
public record Slot(
        int id,
        String name,
        boolean connected,
        String addr,
        int packetsPerSec,
        double lossPct,
        Integer lastSeenTick,
        Integer staleMs,
        boolean stale) {

    public Slot {
        name = name == null ? "" : name;
    }

    /** Anzeigename; Bots koennen keinen senden, der Server vergibt {@code bot-<id>}. */
    public String displayName() {
        return name.isBlank() ? "bot-" + id : name;
    }
}
