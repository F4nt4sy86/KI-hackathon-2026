package com.btc.hackathon.viewer.model;

/**
 * Vorschau eines Spielfelds, ohne dass etwas gestartet wurde.
 *
 * <p>Antwort auf {@code preview_map} und inhaltlich genau ein {@link MatchInit} -
 * dadurch laesst sie sich mit demselben Zeichencode darstellen.
 */
public record MapPreview(MatchInit board) implements ServerMessage {
}
