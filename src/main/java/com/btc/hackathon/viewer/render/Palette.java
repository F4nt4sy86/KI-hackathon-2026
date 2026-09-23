package com.btc.hackathon.viewer.render;

import javafx.scene.paint.Color;

/**
 * Die Farben der Darstellung an einer Stelle.
 *
 * <p>Ausgelegt auf einen Beamer: dunkler Grund, kraeftige Spielerfarben, die sich auch
 * bei schwachem Kontrast noch auseinanderhalten lassen.
 */
public final class Palette {

    // Die Ersatzfarben sind aus der Palette des Sprite-Sets uebernommen
    // (src/pixelart.py, Dict P). Dadurch passt die Platzhalterdarstellung farblich zu
    // den Grafiken, falls einzelne Sprites fehlen.

    /** Grund hinter dem Spielfeld. */
    public static final Color BACKGROUND = Color.web("#14161F");
    /** Begehbarer Boden (fl_m). */
    public static final Color FLOOR = Color.web("#3A624E");
    /** Schachbrettartige Aufhellung (fl_l), damit das Raster ohne Grafiken lesbar bleibt. */
    public static final Color FLOOR_ALT = Color.web("#4A765E");
    /** Unzerstoerbare Wand (st_m). */
    public static final Color SOLID = Color.web("#666E88");
    public static final Color SOLID_EDGE = Color.web("#40465C");
    /** Zerstoerbarer Block (wd_m). */
    public static final Color BRICK = Color.web("#945C30");
    public static final Color BRICK_EDGE = Color.web("#643A20");

    public static final Color BOMB = Color.web("#363852");
    public static final Color BOMB_HIGHLIGHT = Color.web("#969CBE");
    public static final Color FUSE = Color.web("#FF8E2A");

    public static final Color EXPLOSION_CORE = Color.web("#FFD652");
    public static final Color EXPLOSION_EDGE = Color.web("#E43E2C");

    public static final Color POWERUP = Color.web("#3CAC68");

    public static final Color TEXT = Color.web("#E8EAF0");
    public static final Color TEXT_DIM = Color.web("#9AA0B0");
    public static final Color ACCENT = Color.web("#17BEBB");
    public static final Color WARNING = Color.web("#FF6B6B");
    public static final Color OK = Color.web("#3DDC97");

    /**
     * Spielerfarben, ueber {@code colorIndex} ausgewaehlt und im ganzen Spiel stabil.
     *
     * <p>Die vier Werte sind exakt die Anzugfarben der vier Sprite-Varianten
     * (blue, red, yellow, purple) und stehen in derselben Reihenfolge wie
     * {@link Sprites#PLAYER_VARIANTS}. Dadurch zeigen Teilnehmertabelle, Namensschild
     * und Spielfigur dieselbe Farbe - sonst waere die Zuordnung zwischen Liste und
     * Figur auf dem Beamer nicht eindeutig.
     *
     * <p>Gruen fehlt bewusst: der Boden ist gruen, eine gruene Figur wuerde darin
     * untergehen.
     */
    private static final Color[] PLAYERS = {
            Color.web("#3264BE"), // blue   - blu_m
            Color.web("#C6323E"), // red    - red_m
            Color.web("#E4A01E"), // yellow - yel_m
            Color.web("#844CC2"), // purple - pur_m
    };

    private Palette() {
    }

    /** Liefert die Spielerfarbe; der Index laeuft bei Bedarf um. */
    public static Color player(int colorIndex) {
        int i = Math.floorMod(colorIndex, PLAYERS.length);
        return PLAYERS[i];
    }

    public static int playerColorCount() {
        return PLAYERS.length;
    }

    /** Hex-Darstellung fuer CSS-Zuweisungen in der Oberflaeche. */
    public static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
