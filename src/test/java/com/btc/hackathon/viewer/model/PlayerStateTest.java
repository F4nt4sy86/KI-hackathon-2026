package com.btc.hackathon.viewer.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Rueckwaerts-Interpolation der Spielfiguren.
 *
 * <p>Das ist die Falle dieses Protokolls, und der Server-Leitfaden fuehrt sie als ersten
 * Punkt seiner Stolperfallenliste: waehrend eine Figur laeuft, sind {@code x}/{@code y}
 * schon die <em>Zielzelle</em>. Wer sie direkt zeichnet, sieht Figuren eine Zelle
 * vorausspringen. Der Fehler sieht auf dem Schirm nach einem Ruckeln aus und ist ohne
 * diesen Test schwer festzunageln.
 */
class PlayerStateTest {

    private static final double EPS = 1e-9;

    private static PlayerState moving(int x, int y, Direction dir, int progress, int total) {
        return new PlayerState(0, true, true, x, y, dir, progress, total, 1, 1, 0, 0);
    }

    private static PlayerState standing(int x, int y) {
        return new PlayerState(0, true, false, x, y, Direction.DOWN, 0, 8, 1, 1, 0, 0);
    }

    @Test
    @DisplayName("Eine stehende Figur steht genau auf ihrer Zelle")
    void standingIsExact() {
        PlayerState p = standing(3, 5);

        assertEquals(3, p.cellX(0), EPS);
        assertEquals(5, p.cellY(0), EPS);
        assertEquals(3, p.cellX(0.9), EPS);
    }

    @Test
    @DisplayName("Zu Schrittbeginn steht die Figur noch auf der Ausgangszelle")
    void stepStartsBehindTheDestination() {
        // Ziel ist (3,5), gelaufen wird nach rechts - also kam sie von (2,5).
        PlayerState p = moving(3, 5, Direction.RIGHT, 0, 8);

        assertEquals(2, p.cellX(0), EPS);
        assertEquals(5, p.cellY(0), EPS);
    }

    @Test
    @DisplayName("Auf halbem Weg steht die Figur zwischen den Zellen")
    void midStepIsHalfway() {
        PlayerState p = moving(3, 5, Direction.RIGHT, 4, 8);

        assertEquals(2.5, p.cellX(0), EPS);
    }

    @Test
    @DisplayName("Am Schrittende trifft die Figur genau auf der Zielzelle ein")
    void stepEndsOnTheDestination() {
        PlayerState p = moving(3, 5, Direction.RIGHT, 8, 8);

        assertEquals(3, p.cellX(0), EPS);
    }

    @Test
    @DisplayName("Der Uebergang vom Laufen zum Stehen erzeugt keinen Sprung")
    void noSnapWhenTheStepSettles() {
        // Genau das ist der Grund fuer die Rueckwaertsrechnung: beide Zweige muessen sich
        // im letzten Tick treffen.
        PlayerState last = moving(3, 5, Direction.RIGHT, 8, 8);
        PlayerState settled = standing(3, 5);

        assertEquals(settled.cellX(0), last.cellX(0), EPS);
        assertEquals(settled.cellY(0), last.cellY(0), EPS);
    }

    @Test
    @DisplayName("Alle vier Richtungen laufen rueckwaerts vom Ziel")
    void everyDirectionInterpolatesBackwards() {
        assertEquals(6, moving(5, 5, Direction.LEFT, 0, 8).cellX(0), EPS);
        assertEquals(4, moving(5, 5, Direction.RIGHT, 0, 8).cellX(0), EPS);
        assertEquals(6, moving(5, 5, Direction.UP, 0, 8).cellY(0), EPS);
        assertEquals(4, moving(5, 5, Direction.DOWN, 0, 8).cellY(0), EPS);
    }

    @Test
    @DisplayName("Die Bewegung laeuft monoton auf das Ziel zu")
    void movementIsMonotonic() {
        double previous = -1;
        for (int progress = 0; progress <= 8; progress++) {
            double x = moving(3, 5, Direction.RIGHT, progress, 8).cellX(0);
            assertTrue(x >= previous, "Position sprang zurueck bei Fortschritt " + progress);
            previous = x;
        }
    }

    @Test
    @DisplayName("Der Bruchteil eines Ticks bewegt die Figur weiter")
    void subTickAdvancesTheFigure() {
        // Der Server schickt 60 Zustaende je Sekunde; ohne Zwischenschritt ruckelte es auf
        // jedem schnelleren Schirm.
        PlayerState p = moving(3, 5, Direction.RIGHT, 4, 8);

        double atTick = p.cellX(0);
        double halfTickLater = p.cellX(0.5);

        assertTrue(halfTickLater > atTick);
        assertEquals(atTick + 0.5 / 8.0, halfTickLater, EPS);
    }

    @Test
    @DisplayName("Der Bruchteil laeuft nie ueber das Ziel hinaus")
    void subTickNeverOvershoots() {
        // Bei stockender Verbindung soll die Figur stehen bleiben, nicht davonwandern.
        PlayerState p = moving(3, 5, Direction.RIGHT, 8, 8);

        assertEquals(3, p.cellX(1.0), EPS);
        assertEquals(3, p.cellX(5.0), EPS);
    }

    @Test
    @DisplayName("move_total von null fuehrt nicht zu einer Division durch null")
    void zeroMoveTotalIsHandled() {
        PlayerState p = moving(3, 5, Direction.RIGHT, 0, 0);

        assertEquals(1.0, p.progress(0), EPS);
        assertEquals(3, p.cellX(0), EPS);
    }

    @Test
    @DisplayName("Eine unbekannte Richtung laesst die Figur auf der Zielzelle stehen")
    void unknownDirectionStaysPut() {
        PlayerState p = moving(3, 5, Direction.UNKNOWN, 0, 8);

        assertEquals(3, p.cellX(0), EPS);
        assertEquals(5, p.cellY(0), EPS);
    }
}
