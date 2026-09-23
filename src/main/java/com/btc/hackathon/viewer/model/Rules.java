package com.btc.hackathon.viewer.model;

/**
 * Die Regelwerte des laufenden Matches, wie in {@code match_init} geliefert.
 *
 * <p>Der Viewer braucht davon vor allem {@code suddenDeathTick} - das Zusammenziehen
 * des Feldes ist ohne Ankuendigung verwirrend - und {@code bombFuseTicks} als Bezug
 * fuer die Zuendschnur-Animation.
 */
public record Rules(
        int bombFuseTicks,
        int flameDurationTicks,
        int ticksPerCell,
        int speedStepTicks,
        int startBombs,
        int startFlame,
        int maxFlame,
        int maxSpeed,
        int powerupChancePct,
        int roundTimeTicks,
        int suddenDeathTick) {

    /** Vorgaben des Servers, falls eine Nachricht sie nicht mitbringt. */
    public static Rules defaults() {
        return new Rules(120, 30, 8, 1, 1, 1, 6, 3, 30, 10800, 7200);
    }
}
