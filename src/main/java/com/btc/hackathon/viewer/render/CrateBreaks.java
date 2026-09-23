package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.GameEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Merkt sich, wo gerade eine Kiste zerfaellt.
 *
 * <p>Die Zerstoerung ist ein Ereignis, kein Zustand: im naechsten Tick ist die Zelle
 * schlicht leer. Ohne dieses kurze Gedaechtnis verschwaende die Kiste ohne Animation, und
 * die vier Bilder {@code crate_break_0…3} des Sprite-Sets blieben ungenutzt.
 *
 * <p>Wird nur vom JavaFX-Thread benutzt und braucht deshalb keine Synchronisierung.
 */
public final class CrateBreaks {

    private final Map<Long, Long> startedAtNanos = new HashMap<>();

    /** Nimmt die Ereignisse eines Ticks entgegen. */
    public void observe(List<GameEvent> events, long nowNanos) {
        for (GameEvent event : events) {
            if (event instanceof GameEvent.BlockDestroyed destroyed) {
                startedAtNanos.put(ExplosionShape.key(destroyed.x(), destroyed.y()), nowNanos);
            }
        }
    }

    /**
     * Bild der Zerfallsanimation dieser Zelle.
     *
     * @return das Bild, oder -1, wenn hier gerade keine Kiste zerfaellt
     */
    public int frameAt(int x, int y, long nowNanos) {
        Long started = startedAtNanos.get(ExplosionShape.key(x, y));
        if (started == null) {
            return -1;
        }
        return Animations.crateBreakFrame((nowNanos - started) / 1_000_000L);
    }

    /** Raeumt abgelaufene Animationen weg. In jedem Bild aufrufen. */
    public void prune(long nowNanos) {
        if (startedAtNanos.isEmpty()) {
            return;
        }
        long maxAgeNanos = Animations.CRATE_BREAK_MILLIS * 1_000_000L;
        for (Iterator<Map.Entry<Long, Long>> it = startedAtNanos.entrySet().iterator();
                it.hasNext(); ) {
            if (nowNanos - it.next().getValue() >= maxAgeNanos) {
                it.remove();
            }
        }
    }

    /** Beim Matchwechsel: nichts aus dem alten Match weiterzeichnen. */
    public void clear() {
        startedAtNanos.clear();
    }

    public int activeCount() {
        return startedAtNanos.size();
    }
}
