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

    /**
     * Anzeigename.
     *
     * <p>Er kommt aus dem Anmeldepaket des Bots, aus einem {@code rename} der Moderation,
     * oder - wenn beides fehlt - als {@code bot-<id>} vom Server. Ein {@code rename}
     * gewinnt und bleibt gewinnen: ein Bot, der sich neu verbindet, kann die Beschriftung
     * nicht zuruecksetzen, mit der die Moderation ihn von einem zweiten unterscheidet.
     *
     * <p>Der Server bereinigt den Namen (Steuerzeichen raus, 24 Zeichen Obergrenze), er
     * ist also unbesehen darstellbar. Ungekuerzt darstellbar ist er deshalb nicht -
     * dafuer gibt es {@code Labels}.
     */
    public String displayName() {
        return name.isBlank() ? "bot-" + id : name;
    }
}
