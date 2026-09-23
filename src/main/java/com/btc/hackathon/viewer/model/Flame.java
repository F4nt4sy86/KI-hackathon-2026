package com.btc.hackathon.viewer.model;

/**
 * Eine brennende Zelle. {@code ticks} ist die Restdauer.
 *
 * <p>Diese Liste sagt, welche Zellen gerade toedlich sind. Die <em>Form</em> des
 * Feuerbalkens liefert dagegen das Ereignis {@link GameEvent.Explosion} mit den
 * Armlaengen.
 */
public record Flame(int x, int y, int ticks) {
}
