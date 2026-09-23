package com.btc.hackathon.viewer.model;

/**
 * Der Zustand einer Spielfigur in einem Tick.
 *
 * <p><b>Die Falle dieses Protokolls:</b> waehrend eine Figur laeuft, sind {@code x} und
 * {@code y} bereits die <em>Zielzelle</em> - der Schritt gilt ab dem ersten Tick als
 * festgelegt und laesst sich nicht abbrechen. Wer diese Werte direkt zeichnet, sieht
 * Figuren um eine Zelle vorausspringen. Gezeichnet wird deshalb rueckwaerts vom Ziel,
 * siehe {@link #cellX(double)}.
 */
public record PlayerState(
        int id,
        boolean alive,
        boolean moving,
        int x,
        int y,
        Direction dir,
        int moveProgress,
        int moveTotal,
        int bombsMax,
        int flame,
        int speed,
        int score) {

    /**
     * Fortschritt des laufenden Schritts als Anteil von 0 bis 1.
     *
     * @param subTick Bruchteil eines Ticks seit dem Eintreffen dieses Zustands. Damit
     *                laeuft die Figur auch auf einem 144-Hz-Schirm rund, obwohl der
     *                Server nur 60 Zustaende je Sekunde schickt.
     */
    public double progress(double subTick) {
        if (!moving || moveTotal <= 0) {
            return 1.0;
        }
        double advanced = Math.min(moveTotal, moveProgress + Math.max(0, subTick));
        return advanced / moveTotal;
    }

    /**
     * Zeichenposition in Zellen, mit Nachkommaanteil.
     *
     * <p>Laeuft rueckwaerts vom Ziel: bei Fortschritt 0 steht die Figur noch ganz auf der
     * Ausgangszelle, bei Fortschritt 1 auf der Zielzelle. Beide Zweige treffen sich
     * genau, sodass beim Einrasten kein Sprung sichtbar wird.
     */
    public double cellX(double subTick) {
        if (!moving) {
            return x;
        }
        return x - dir.dx() * (1.0 - progress(subTick));
    }

    public double cellY(double subTick) {
        if (!moving) {
            return y;
        }
        return y - dir.dy() * (1.0 - progress(subTick));
    }
}
