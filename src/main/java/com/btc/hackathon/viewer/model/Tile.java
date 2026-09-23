package com.btc.hackathon.viewer.model;

/**
 * Inhalt einer Zelle.
 *
 * <p>Die Zahlenwerte sind die des Servers ({@code bomber-domain::board::Tile}), die
 * Namen die des JSON in {@code tile_changes}.
 */
public enum Tile {
    /** Begehbar. */
    EMPTY,
    /** Unzerstoerbar: Rand und Saeulengitter. */
    SOLID,
    /** Zerstoerbare Kiste. Kann ein Power-up fallen lassen. */
    SOFT,
    /** Wert, den diese Version nicht kennt. */
    UNKNOWN;

    private static final Tile[] BY_CODE = {EMPTY, SOLID, SOFT};

    /** Aus dem flachen {@code tiles}-Array von {@code match_init}. */
    public static Tile fromCode(int code) {
        return code >= 0 && code < BY_CODE.length ? BY_CODE[code] : UNKNOWN;
    }

    /** Aus dem Namen in einem {@code tile_changes}-Eintrag. */
    public static Tile fromJson(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        return switch (name) {
            case "empty" -> EMPTY;
            case "solid" -> SOLID;
            case "soft" -> SOFT;
            default -> UNKNOWN;
        };
    }

    public byte code() {
        return (byte) ordinal();
    }
}
