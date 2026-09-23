package com.btc.hackathon.viewer.model;

/**
 * Blickrichtung einer Spielfigur.
 *
 * <p>Geht als Zeichenkette ueber die Leitung, nicht als Zahl - die Reihenfolge der
 * Konstanten hier ist deshalb frei waehlbar.
 */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    UNKNOWN(0, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /** Schrittweite in Zellen - gebraucht fuer die Rueckwaerts-Interpolation. */
    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public static Direction fromJson(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        return switch (name) {
            case "up" -> UP;
            case "down" -> DOWN;
            case "left" -> LEFT;
            case "right" -> RIGHT;
            default -> UNKNOWN;
        };
    }
}
