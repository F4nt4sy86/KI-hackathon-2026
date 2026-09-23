package com.btc.hackathon.viewer.model;

/**
 * Etwas Punktuelles, das in diesem Tick geschehen ist.
 *
 * <p>Ereignisse gibt es, weil manches im Zustandsvergleich unsichtbar bleibt - eine
 * Bombe, die innerhalb eines Ticks explodiert und verschwindet - und weil Animationen
 * einen Ausloeser brauchen, keinen Pegel.
 *
 * <p>Jedes Kennungsfeld nennt seinen Namensraum ({@code player}, {@code bomb},
 * {@code powerup}) statt eines blossen {@code id}: Bombe 3 und Power-up 3 haben nichts
 * miteinander zu tun, und so laesst sich die Verwechslung gar nicht erst hinschreiben.
 */
public sealed interface GameEvent {

    /** Eine Bombe wurde gelegt. */
    record BombPlaced(int bomb, int player, int x, int y) implements GameEvent {
    }

    /**
     * Eine Bombe ist detoniert.
     *
     * <p>Das Ereignis liefert die <em>Form</em> des Feuerbalkens: die Armlaengen in
     * Zellen je Richtung, ausgehend von der Mitte. Damit laesst sich die Explosion aus
     * den Einzelteilen des Sprite-Sets zusammensetzen, ohne sie aus der Nachbarschaft
     * raten zu muessen.
     */
    record Explosion(int bomb, int x, int y, int up, int down, int left, int right)
            implements GameEvent {
    }

    /** Eine Kiste wurde zerstoert - Ausloeser fuer die Zerfallsanimation. */
    record BlockDestroyed(int x, int y) implements GameEvent {
    }

    record PowerupSpawned(int powerup, int x, int y, PowerupKind kind) implements GameEvent {
    }

    record PowerupTaken(int powerup, int player, PowerupKind kind) implements GameEvent {
    }

    /** Ein Power-up ist im Feuer verbrannt - anderer Ausgang als eingesammelt. */
    record PowerupBurned(int powerup, int x, int y) implements GameEvent {
    }

    record Death(int player) implements GameEvent {
    }

    /** Der Sudden-Death-Rand hat eine Zelle zugemauert. */
    record WallClosed(int x, int y) implements GameEvent {
    }

    /**
     * Ein Ereignistyp, den diese Version nicht kennt.
     *
     * <p>Der Server darf neue hinzufuegen; sie werden mitgefuehrt statt verworfen, damit
     * das Ereignisprotokoll in der Oberflaeche vollstaendig bleibt.
     */
    record Unknown(String type) implements GameEvent {
    }
}
