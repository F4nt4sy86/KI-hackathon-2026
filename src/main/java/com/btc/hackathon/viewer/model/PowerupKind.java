package com.btc.hackathon.viewer.model;

/**
 * Art eines Power-ups.
 *
 * <p>Die Simulation kennt genau diese drei. Das Sprite-Set bringt zusaetzlich
 * {@code item_kick} und {@code item_remote} mit, die der Server nicht vergibt.
 */
public enum PowerupKind {
    /** Eine gleichzeitige Bombe mehr. */
    EXTRA_BOMB,
    /** Eine Zelle mehr Sprengreichweite. */
    FLAME,
    /** Eine Geschwindigkeitsstufe mehr. */
    SPEED,
    UNKNOWN;

    public static PowerupKind fromJson(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        return switch (name) {
            case "extra_bomb" -> EXTRA_BOMB;
            case "flame" -> FLAME;
            case "speed" -> SPEED;
            default -> UNKNOWN;
        };
    }
}
