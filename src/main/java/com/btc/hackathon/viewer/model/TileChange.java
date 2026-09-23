package com.btc.hackathon.viewer.model;

/** Eine einzelne Kachel hat sich geaendert. Wird auf das Board fortgeschrieben. */
public record TileChange(int x, int y, Tile tile) {
}
