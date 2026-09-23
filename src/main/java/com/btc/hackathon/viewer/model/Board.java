package com.btc.hackathon.viewer.model;

import java.util.Arrays;
import java.util.List;

/**
 * Das Kachelraster.
 *
 * <p>Der Server schickt es genau einmal, in {@code match_init}. Danach kommen nur noch
 * einzelne Aenderungen. Diese Klasse haelt deshalb den fortgeschriebenen Stand: sie
 * wird beim Matchstart angelegt und mit {@link #withChanges} weitergefuehrt.
 *
 * <p>Unveraenderlich, damit sie ohne Sperre vom Netz- zum Zeichenthread wandern kann.
 * {@link #withChanges} gibt bei leerer Aenderungsliste dieselbe Instanz zurueck - und
 * die Liste ist fast immer leer, sodass pro Tick nichts kopiert wird.
 */
public record Board(int width, int height, byte[] tiles) {

    public Board {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Feldgroesse muss positiv sein: " + width + "x" + height);
        }
        if (tiles.length != width * height) {
            throw new IllegalArgumentException(
                    "tiles hat " + tiles.length + " Eintraege, erwartet " + (width * height));
        }
        tiles = tiles.clone();
    }

    public static Board empty(int width, int height) {
        return new Board(width, height, new byte[width * height]);
    }

    /** Ausserhalb des Feldes gilt {@link Tile#SOLID}, damit Zeichencode nie pruefen muss. */
    public Tile tileAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return Tile.SOLID;
        }
        return Tile.fromCode(tiles[y * width + x]);
    }

    /**
     * Schreibt die Aenderungen eines Ticks fort.
     *
     * <p>Wer das auslaesst, sieht zerstoerte Kisten nach einiger Zeit wieder auftauchen -
     * eine der im Server-Leitfaden ausdruecklich genannten Fallen.
     */
    public Board withChanges(List<TileChange> changes) {
        if (changes.isEmpty()) {
            return this;
        }
        byte[] copy = tiles.clone();
        for (TileChange change : changes) {
            if (change.x() >= 0 && change.y() >= 0 && change.x() < width && change.y() < height) {
                copy[change.y() * width + change.x()] = change.tile().code();
            }
        }
        return new Board(width, height, copy);
    }

    /** Defensive Kopie - das interne Array verlaesst die Instanz nie. */
    @Override
    public byte[] tiles() {
        return tiles.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Board b
                && b.width == width
                && b.height == height
                && Arrays.equals(b.tiles, tiles);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * width + height) + Arrays.hashCode(tiles);
    }

    @Override
    public String toString() {
        return "Board[" + width + "x" + height + "]";
    }
}
