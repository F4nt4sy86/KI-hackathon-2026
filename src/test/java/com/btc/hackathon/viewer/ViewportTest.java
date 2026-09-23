package com.btc.hackathon.viewer;

import com.btc.hackathon.viewer.render.Viewport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Einpassung des Spielfelds in das Fenster.
 *
 * <p>Der Renderer braucht JavaFX, diese Rechnung nicht - sie laesst sich deshalb ohne
 * laufende Oberflaechen-Laufzeit pruefen.
 */
class ViewportTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("Die Kachelgroesse ist immer ganzzahlig")
    void tileSizeIsWholePixels() {
        // Gebrochene Kachelgroessen setzen Grafiken auf halbe Pixel und lassen das
        // Raster beim Groesseaendern flimmern.
        Viewport vp = Viewport.fit(1001, 777, 15, 13);

        assertEquals(vp.tileSize(), Math.floor(vp.tileSize()), EPS);
        assertTrue(vp.tileSize() >= 1);
    }

    @Test
    @DisplayName("Das Feld bleibt im breiten Fenster waagerecht zentriert")
    void centeredInWideWindow() {
        Viewport vp = Viewport.fit(1000, 260, 15, 13);

        // Hoehe ist der begrenzende Faktor: 260/13 = 20
        assertEquals(20, vp.tileSize());
        assertEquals(0, vp.offsetY(), EPS);
        assertEquals(Math.floor((1000 - 15 * 20) / 2.0), vp.offsetX(), EPS);
    }

    @Test
    @DisplayName("Das Feld bleibt im hohen Fenster senkrecht zentriert")
    void centeredInTallWindow() {
        Viewport vp = Viewport.fit(300, 1000, 15, 13);

        // Breite ist der begrenzende Faktor: 300/15 = 20
        assertEquals(20, vp.tileSize());
        assertEquals(0, vp.offsetX(), EPS);
        assertEquals(Math.floor((1000 - 13 * 20) / 2.0), vp.offsetY(), EPS);
    }

    @Test
    @DisplayName("Das Feld passt immer vollstaendig ins Fenster")
    void alwaysFitsInside() {
        double[][] sizes = {{1280, 800}, {640, 480}, {1920, 1080}, {333, 999}, {801, 333}};
        for (double[] size : sizes) {
            Viewport vp = Viewport.fit(size[0], size[1], 15, 13);

            assertTrue(vp.pixelWidth() <= size[0], "zu breit bei " + size[0] + "x" + size[1]);
            assertTrue(vp.pixelHeight() <= size[1], "zu hoch bei " + size[0] + "x" + size[1]);
            assertTrue(vp.offsetX() >= 0);
            assertTrue(vp.offsetY() >= 0);
        }
    }

    @Test
    @DisplayName("Auch bei winzigem Fenster bleibt die Kachel mindestens ein Pixel gross")
    void neverCollapsesToZero() {
        Viewport vp = Viewport.fit(10, 10, 15, 13);

        assertEquals(1, vp.tileSize());
    }

    @Test
    @DisplayName("Ungueltige Feldgroessen fuehren nicht zu einer Division durch null")
    void handlesEmptyField() {
        Viewport vp = Viewport.fit(800, 600, 0, 0);

        assertEquals(1, vp.tileSize());
    }

    @Test
    @DisplayName("Nachkommaanteile der Spielerposition schlagen auf den Bildschirm durch")
    void subTilePositionsMapToPixels() {
        Viewport vp = Viewport.fit(600, 520, 15, 13);

        // Eine halbe Kachel weiter rechts als Kachel 3.
        double half = vp.screenX(3.5) - vp.screenX(3.0);

        assertEquals(vp.tileSize() / 2.0, half, EPS);
    }

    @Test
    @DisplayName("Kachel null liegt genau am Versatz")
    void originMatchesOffset() {
        Viewport vp = Viewport.fit(1280, 800, 15, 13);

        assertEquals(vp.offsetX(), vp.screenX(0), EPS);
        assertEquals(vp.offsetY(), vp.screenY(0), EPS);
    }
}
