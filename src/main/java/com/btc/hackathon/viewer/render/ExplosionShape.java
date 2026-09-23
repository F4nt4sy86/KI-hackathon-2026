package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.Flame;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Leitet die Form eines Feuerbalkens aus der Nachbarschaft ab.
 *
 * <p>Der Server nennt in {@code flames} nur die brennenden Zellen; das Sprite-Set liefert
 * die Explosion bewusst zerlegt in Mittelstueck, Balkenstuecke und Spitzen, damit Balken
 * beliebiger Laenge aus denselben Kacheln entstehen. Beides zusammenzubringen ist genau
 * diese Rechnung. Ohne sie saehe ein Feuerbalken wie eine Reihe einzelner Kugeln aus.
 *
 * <p>Das Ereignis {@code explosion} liefert zwar die Armlaengen mit, gilt aber nur fuer
 * den Tick der Detonation, waehrend die Flamme mehrere Ticks brennt. Die Ableitung aus
 * der jeweils aktuellen Flammenliste ist deshalb die einfachere und zugleich robustere
 * Quelle: sie bleibt richtig, waehrend einzelne Zellen wieder erloeschen.
 */
public final class ExplosionShape {

    private ExplosionShape() {
    }

    /** Packt die brennenden Zellen in eine Menge, in der sich Nachbarn schnell finden. */
    public static Set<Long> occupancy(List<Flame> flames) {
        Set<Long> burning = new HashSet<>(Math.max(8, flames.size() * 2));
        for (Flame f : flames) {
            burning.add(key(f.x(), f.y()));
        }
        return burning;
    }

    /** Eindeutiger Schluessel einer Zelle; auch negative Koordinaten bleiben eindeutig. */
    public static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /**
     * Bestimmt das Teilbild fuer eine brennende Zelle.
     *
     * @param burning alle gleichzeitig brennenden Zellen, siehe {@link #occupancy}
     */
    public static Sprites.ExplosionPiece pieceAt(int x, int y, Set<Long> burning) {
        boolean up = burning.contains(key(x, y - 1));
        boolean down = burning.contains(key(x, y + 1));
        boolean left = burning.contains(key(x - 1, y));
        boolean right = burning.contains(key(x + 1, y));

        boolean horizontal = left || right;
        boolean vertical = up || down;

        if (horizontal && vertical) {
            // Kreuzung: das Mittelstueck bringt beide Balken in voller Staerke mit, damit
            // an der Kreuzung keine Einschnuerung entsteht.
            return Sprites.ExplosionPiece.CENTER;
        }
        if (horizontal) {
            if (left && right) {
                return Sprites.ExplosionPiece.ARM_HORIZONTAL;
            }
            // Die Spitze zeigt vom Nachbarn weg - sie ist das Ende des Balkens.
            return left ? Sprites.ExplosionPiece.TIP_RIGHT : Sprites.ExplosionPiece.TIP_LEFT;
        }
        if (vertical) {
            if (up && down) {
                return Sprites.ExplosionPiece.ARM_VERTICAL;
            }
            return up ? Sprites.ExplosionPiece.TIP_DOWN : Sprites.ExplosionPiece.TIP_UP;
        }
        // Einzelne brennende Zelle ohne Nachbarn - etwa eine Bombe mit Reichweite null
        // oder eine Zelle, deren Nachbarn schon erloschen sind.
        return Sprites.ExplosionPiece.CENTER;
    }
}
