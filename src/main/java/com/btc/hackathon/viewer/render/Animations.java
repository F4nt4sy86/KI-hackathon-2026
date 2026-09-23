package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.PlayerState;

/**
 * Die Bildauswahl der Animationen - reine Rechnung, ohne Oberflaechenbezug.
 *
 * <p>Absichtlich vom Renderer getrennt: so laesst sich pruefen, dass die Zuendschnur in
 * der richtigen Richtung abbrennt und eine stehende Figur nicht auf der Stelle rudert,
 * ohne die JavaFX-Laufzeit zu starten.
 *
 * <p>Die Zeitfenster kommen aus {@code match_init} - {@code bomb_fuse_ticks} und
 * {@code flame_duration_ticks} - und werden hier nur noch auf die vorhandenen Bilder
 * verteilt. Damit stimmt die Darstellung auch, wenn jemand die Regeln des Servers
 * umstellt.
 */
public final class Animations {

    /** So lange steht ein Bild des Power-up-Pulsierens. */
    public static final long ITEM_FRAME_MILLIS = 320;

    /** So lange steht eine Standpose in der Lobby. */
    public static final long IDLE_FRAME_MILLIS = 700;

    /** Dauer der Zerfallsanimation einer Kiste. */
    public static final long CRATE_BREAK_MILLIS = 260;

    private Animations() {
    }

    /**
     * Waehlt das Bild des Laufzyklus aus dem Schrittfortschritt.
     *
     * <p>Nicht aus der Wanduhr: so bleibt die Schrittfrequenz an die Simulation gekoppelt,
     * passt von selbst zur Geschwindigkeitsstufe und eine stehende Figur bleibt stehen.
     */
    public static int walkFrame(PlayerState player) {
        if (!player.moving() || player.moveTotal() <= 0) {
            // Bild 0 ist eine Standpose.
            return 0;
        }
        int frame = (int) Math.floor(
                player.moveProgress() / (double) player.moveTotal() * Sprites.PLAYER_FRAMES);
        return Math.floorMod(frame, Sprites.PLAYER_FRAMES);
    }

    /**
     * Je weniger Restzeit, desto weiter ist die Zuendschnur abgebrannt.
     *
     * @param fuseTicks  Restzeit der Bombe in Ticks
     * @param totalTicks volle Zuendschnurlaenge laut Regelwerk
     */
    public static int fuseFrame(int fuseTicks, int totalTicks) {
        if (totalTicks <= 0) {
            return 0;
        }
        double burned = 1.0 - clamp01(fuseTicks / (double) totalTicks);
        return frameFor(burned, Sprites.BOMB_FRAMES);
    }

    /**
     * Je weniger Restzeit, desto weiter ist die Flamme fortgeschritten.
     *
     * @param remainingTicks Restdauer dieser brennenden Zelle
     * @param totalTicks     volle Flammendauer laut Regelwerk
     */
    public static int flameFrame(int remainingTicks, int totalTicks) {
        if (totalTicks <= 0) {
            return 0;
        }
        double elapsed = 1.0 - clamp01(remainingTicks / (double) totalTicks);
        return frameFor(elapsed, Sprites.EXPLOSION_FRAMES);
    }

    /** Zweibildriges Pulsieren der Power-ups, an der Uhr haengend. */
    public static int itemFrame(long nowNanos) {
        return (int) Math.floorMod(millis(nowNanos) / ITEM_FRAME_MILLIS, Sprites.ITEM_FRAMES);
    }

    /**
     * Leerlaufbewegung in der Lobby.
     *
     * @return {@code 0} oder {@code 2} - die beiden Standposen des Laufzyklus
     */
    public static int idleFrame(long nowNanos) {
        return Math.floorMod(millis(nowNanos) / IDLE_FRAME_MILLIS, 2) == 0 ? 0 : 2;
    }

    /**
     * Bild der Zerfallsanimation einer Kiste.
     *
     * @param ageMillis Zeit seit dem Ereignis {@code block_destroyed}
     * @return das Bild, oder -1, wenn die Animation abgelaufen ist
     */
    public static int crateBreakFrame(long ageMillis) {
        if (ageMillis < 0 || ageMillis >= CRATE_BREAK_MILLIS) {
            return -1;
        }
        double progress = ageMillis / (double) CRATE_BREAK_MILLIS;
        return frameFor(progress, Sprites.CRATE_BREAK_FRAMES);
    }

    /** Restliche Sichtbarkeit einer Flamme, fuer die Ersatzdarstellung ohne Sprites. */
    public static double flameAlpha(int remainingTicks, int totalTicks) {
        if (totalTicks <= 0) {
            return 1.0;
        }
        return Math.max(0.15, clamp01(remainingTicks / (double) totalTicks));
    }

    private static long millis(long nowNanos) {
        return nowNanos / 1_000_000L;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Verteilt einen Fortschritt von 0 bis 1 auf {@code count} Bilder.
     *
     * <p>Fortschritt 1.0 muss auf das letzte Bild fallen, nicht ueber den Rand hinaus.
     */
    private static int frameFor(double progress, int count) {
        int frame = (int) (progress * count);
        return Math.max(0, Math.min(count - 1, frame));
    }
}
