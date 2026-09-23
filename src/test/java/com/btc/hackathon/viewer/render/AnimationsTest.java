package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.Direction;
import com.btc.hackathon.viewer.model.PlayerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueft die Bildauswahl der Animationen. */
class AnimationsTest {

    private static final int FUSE_TICKS = 120;
    private static final int FLAME_TICKS = 30;

    private static PlayerState moving(int progress, int total) {
        return new PlayerState(0, true, true, 5, 5, Direction.RIGHT, progress, total, 1, 1, 0, 0);
    }

    private static PlayerState standing() {
        return new PlayerState(0, true, false, 5, 5, Direction.RIGHT, 0, 8, 1, 1, 0, 0);
    }

    // -------------------------------------------------------------------- Laufen

    @Test
    @DisplayName("Eine stehende Figur bleibt auf einer Standpose")
    void standingUsesStandingPose() {
        // Der haeufigste Fehler bei zeitgesteuerten Laufzyklen ist die Figur, die im Stand
        // rudert. Weil das Bild aus dem Schrittfortschritt kommt, kann das nicht passieren.
        assertEquals(0, Animations.walkFrame(standing()));
    }

    @Test
    @DisplayName("Der Laufzyklus durchlaeuft alle vier Bilder der Reihe nach")
    void walkCycleRunsThroughAllFrames() {
        // move_total 8, vier Bilder: je zwei Ticks ein Bild.
        assertEquals(0, Animations.walkFrame(moving(0, 8)));
        assertEquals(0, Animations.walkFrame(moving(1, 8)));
        assertEquals(1, Animations.walkFrame(moving(2, 8)));
        assertEquals(2, Animations.walkFrame(moving(4, 8)));
        assertEquals(3, Animations.walkFrame(moving(6, 8)));
    }

    @Test
    @DisplayName("Das Bild bleibt auch bei ungewoehnlichen Werten im gueltigen Bereich")
    void walkFrameStaysInRange() {
        for (int total = 1; total <= 16; total++) {
            for (int progress = 0; progress <= total; progress++) {
                int frame = Animations.walkFrame(moving(progress, total));
                assertTrue(frame >= 0 && frame < Sprites.PLAYER_FRAMES,
                        "Bild " + frame + " bei " + progress + "/" + total);
            }
        }
    }

    @Test
    @DisplayName("move_total von null legt die Figur nicht lahm")
    void zeroMoveTotalIsHandled() {
        assertEquals(0, Animations.walkFrame(moving(3, 0)));
    }

    // ------------------------------------------------------------------- Bombe

    @Test
    @DisplayName("Die Zuendschnur brennt in die richtige Richtung ab")
    void fuseBurnsDown() {
        assertEquals(0, Animations.fuseFrame(FUSE_TICKS, FUSE_TICKS));
        assertEquals(Sprites.BOMB_FRAMES - 1, Animations.fuseFrame(0, FUSE_TICKS));
    }

    @Test
    @DisplayName("Die Bildfolge der Zuendschnur steigt monoton")
    void fuseFrameIsMonotonic() {
        int previous = -1;
        for (int fuse = FUSE_TICKS; fuse >= 0; fuse--) {
            int frame = Animations.fuseFrame(fuse, FUSE_TICKS);
            assertTrue(frame >= previous, "Bild sprang zurueck bei Restzeit " + fuse);
            previous = frame;
        }
    }

    @Test
    @DisplayName("Eine andere Zuendschnurlaenge des Servers wird mitgenommen")
    void fuseScalesWithTheRules() {
        // Der Wert kommt aus match_init; eine Regelaenderung darf die Anzeige nicht
        // aus dem Takt bringen.
        assertEquals(0, Animations.fuseFrame(300, 300));
        assertEquals(Sprites.BOMB_FRAMES - 1, Animations.fuseFrame(0, 300));
    }

    @Test
    @DisplayName("Ungueltige Regelwerte fuehren nicht zu einer Division durch null")
    void zeroTotalIsHandled() {
        assertEquals(0, Animations.fuseFrame(50, 0));
        assertEquals(0, Animations.flameFrame(10, 0));
    }

    // --------------------------------------------------------------- Explosion

    @Test
    @DisplayName("Die Flamme laeuft vom ersten zum letzten Bild")
    void flameRunsForward() {
        assertEquals(0, Animations.flameFrame(FLAME_TICKS, FLAME_TICKS));
        assertEquals(Sprites.EXPLOSION_FRAMES - 1, Animations.flameFrame(0, FLAME_TICKS));
    }

    @Test
    @DisplayName("Jede Restzeit ergibt ein gueltiges Flammenbild")
    void flameFrameStaysInRange() {
        for (int remaining = -5; remaining <= FLAME_TICKS + 5; remaining++) {
            int frame = Animations.flameFrame(remaining, FLAME_TICKS);
            assertTrue(frame >= 0 && frame < Sprites.EXPLOSION_FRAMES, "Bild war " + frame);
        }
    }

    @Test
    @DisplayName("Die Ersatzdarstellung blendet aus, verschwindet aber nie ganz")
    void flameAlphaStaysVisible() {
        assertTrue(Animations.flameAlpha(0, FLAME_TICKS) > 0);
        assertTrue(Animations.flameAlpha(FLAME_TICKS, FLAME_TICKS) <= 1.0);
        assertTrue(Animations.flameAlpha(FLAME_TICKS, FLAME_TICKS)
                > Animations.flameAlpha(2, FLAME_TICKS));
    }

    // ------------------------------------------------------- Zeitgesteuertes

    @Test
    @DisplayName("Power-ups wechseln zwischen genau zwei Bildern")
    void itemsPulseBetweenTwoFrames() {
        long ms = 1_000_000L;
        assertEquals(0, Animations.itemFrame(0));
        assertEquals(1, Animations.itemFrame(Animations.ITEM_FRAME_MILLIS * ms));
        assertEquals(0, Animations.itemFrame(2 * Animations.ITEM_FRAME_MILLIS * ms));
    }

    @Test
    @DisplayName("Die Leerlaufbewegung nutzt nur die beiden Standposen")
    void idleUsesStandingPosesOnly() {
        long ms = 1_000_000L;
        // Bild 1 und 3 sind Durchgangsposen mit angehobenem Bein - im Stand waeren sie falsch.
        assertEquals(0, Animations.idleFrame(0));
        assertEquals(2, Animations.idleFrame(Animations.IDLE_FRAME_MILLIS * ms));
        assertEquals(0, Animations.idleFrame(2 * Animations.IDLE_FRAME_MILLIS * ms));
    }

    @Test
    @DisplayName("Die Kistenanimation laeuft durch und endet dann")
    void crateBreakRunsOutAndStops() {
        assertEquals(0, Animations.crateBreakFrame(0));
        assertEquals(Sprites.CRATE_BREAK_FRAMES - 1,
                Animations.crateBreakFrame(Animations.CRATE_BREAK_MILLIS - 1));
        assertEquals(-1, Animations.crateBreakFrame(Animations.CRATE_BREAK_MILLIS));
        assertEquals(-1, Animations.crateBreakFrame(5000));
    }
}
