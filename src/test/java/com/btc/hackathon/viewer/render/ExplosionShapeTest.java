package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.Flame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.btc.hackathon.viewer.render.Sprites.ExplosionPiece.ARM_HORIZONTAL;
import static com.btc.hackathon.viewer.render.Sprites.ExplosionPiece.ARM_VERTICAL;
import static com.btc.hackathon.viewer.render.Sprites.ExplosionPiece.CENTER;
import static com.btc.hackathon.viewer.render.Sprites.ExplosionPiece.TIP_DOWN;
import static com.btc.hackathon.viewer.render.Sprites.ExplosionPiece.TIP_LEFT;
import static com.btc.hackathon.viewer.render.Sprites.ExplosionPiece.TIP_RIGHT;
import static com.btc.hackathon.viewer.render.Sprites.ExplosionPiece.TIP_UP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Prueft, dass aus einzelnen brennenden Kacheln ein zusammenhaengender Feuerbalken wird.
 *
 * <p>Das ist der Punkt, an dem Drahtformat und Sprite-Set auseinandergehen: der Server
 * schickt nur Kacheln, das Set liefert Mittelstueck, Balken und Spitzen. Geht die
 * Zuordnung schief, sieht die Explosion aus wie eine Reihe Einzelkugeln - ein Fehler, den
 * man am Bildschirm sieht, aber schwer festnagelt.
 */
class ExplosionShapeTest {

    private static Set<Long> burning(int[]... tiles) {
        List<Flame> flames = new ArrayList<>();
        for (int[] t : tiles) {
            flames.add(new Flame(t[0], t[1], 20));
        }
        return ExplosionShape.occupancy(flames);
    }

    @Test
    @DisplayName("Eine einzelne brennende Zelle ist das Mittelstueck")
    void loneTileIsCenter() {
        assertSame(CENTER, ExplosionShape.pieceAt(5, 5, burning(new int[] {5, 5})));
    }

    @Test
    @DisplayName("Die Kreuzung einer Bombe ist das Mittelstueck")
    void crossingIsCenter() {
        Set<Long> b = burning(
                new int[] {5, 5},
                new int[] {4, 5}, new int[] {6, 5},
                new int[] {5, 4}, new int[] {5, 6});

        assertSame(CENTER, ExplosionShape.pieceAt(5, 5, b));
    }

    @Test
    @DisplayName("Mitte eines waagerechten Balkens ist ein waagerechtes Balkenstueck")
    void middleOfHorizontalBeam() {
        Set<Long> b = burning(new int[] {4, 5}, new int[] {5, 5}, new int[] {6, 5});

        assertSame(ARM_HORIZONTAL, ExplosionShape.pieceAt(5, 5, b));
    }

    @Test
    @DisplayName("Mitte eines senkrechten Balkens ist ein senkrechtes Balkenstueck")
    void middleOfVerticalBeam() {
        Set<Long> b = burning(new int[] {5, 4}, new int[] {5, 5}, new int[] {5, 6});

        assertSame(ARM_VERTICAL, ExplosionShape.pieceAt(5, 5, b));
    }

    @Test
    @DisplayName("Die Spitze zeigt vom Balken weg")
    void tipsPointAwayFromTheBeam() {
        Set<Long> horizontal = burning(new int[] {4, 5}, new int[] {5, 5});
        // Kachel 5 hat ihren Nachbarn links, ist also das rechte Ende.
        assertSame(TIP_RIGHT, ExplosionShape.pieceAt(5, 5, horizontal));
        assertSame(TIP_LEFT, ExplosionShape.pieceAt(4, 5, horizontal));

        Set<Long> vertical = burning(new int[] {5, 4}, new int[] {5, 5});
        assertSame(TIP_DOWN, ExplosionShape.pieceAt(5, 5, vertical));
        assertSame(TIP_UP, ExplosionShape.pieceAt(5, 4, vertical));
    }

    @Test
    @DisplayName("Eine vollstaendige Bombenexplosion ergibt ein sauberes Kreuz")
    void fullCrossIsShapedCorrectly() {
        // Reichweite 2 in alle Richtungen um (5,5).
        Set<Long> b = burning(
                new int[] {5, 5},
                new int[] {3, 5}, new int[] {4, 5}, new int[] {6, 5}, new int[] {7, 5},
                new int[] {5, 3}, new int[] {5, 4}, new int[] {5, 6}, new int[] {5, 7});

        assertSame(CENTER, ExplosionShape.pieceAt(5, 5, b));

        assertSame(ARM_HORIZONTAL, ExplosionShape.pieceAt(4, 5, b));
        assertSame(ARM_HORIZONTAL, ExplosionShape.pieceAt(6, 5, b));
        assertSame(TIP_LEFT, ExplosionShape.pieceAt(3, 5, b));
        assertSame(TIP_RIGHT, ExplosionShape.pieceAt(7, 5, b));

        assertSame(ARM_VERTICAL, ExplosionShape.pieceAt(5, 4, b));
        assertSame(ARM_VERTICAL, ExplosionShape.pieceAt(5, 6, b));
        assertSame(TIP_UP, ExplosionShape.pieceAt(5, 3, b));
        assertSame(TIP_DOWN, ExplosionShape.pieceAt(5, 7, b));
    }

    @Test
    @DisplayName("Wo zwei Explosionen sich treffen, entsteht wieder ein Mittelstueck")
    void overlappingBeamsBecomeCenter() {
        // Ein waagerechter und ein senkrechter Balken kreuzen sich bei (5,5), ohne dass
        // dort eine Bombe lag.
        Set<Long> b = burning(
                new int[] {4, 5}, new int[] {5, 5}, new int[] {6, 5},
                new int[] {5, 4}, new int[] {5, 6});

        assertSame(CENTER, ExplosionShape.pieceAt(5, 5, b));
    }

    @Test
    @DisplayName("Eine L-Ecke wird als Mittelstueck gezeichnet")
    void perpendicularNeighboursBecomeCenter() {
        // Zwei Nachbarn, aber rechtwinklig zueinander - es gibt kein Eckstueck im Set,
        // das Mittelstueck deckt beide Richtungen ab.
        Set<Long> b = burning(new int[] {5, 5}, new int[] {4, 5}, new int[] {5, 4});

        assertSame(CENTER, ExplosionShape.pieceAt(5, 5, b));
    }

    @Test
    @DisplayName("Negative Koordinaten kollidieren nicht mit positiven")
    void keysStayUniqueAcrossSigns() {
        // Der Schluessel packt zwei ints in einen long; ein Vorzeichenfehler wuerde
        // Zellen miteinander verwechseln.
        assertEquals(ExplosionShape.key(1, -1), ExplosionShape.key(1, -1));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                ExplosionShape.key(1, -1), ExplosionShape.key(-1, 1));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                ExplosionShape.key(0, -1), ExplosionShape.key(-1, 0));
    }

    @Test
    @DisplayName("Ohne brennende Zellen entsteht eine leere Belegung")
    void emptyOccupancy() {
        assertEquals(0, ExplosionShape.occupancy(List.of()).size());
    }
}
