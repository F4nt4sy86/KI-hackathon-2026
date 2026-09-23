package com.btc.hackathon.viewer.model;

/**
 * Karteneinstellungen fuer das naechste Match.
 *
 * @param seed als Zeichenkette, weil ein 64-Bit-Wert als JSON-Zahl an Genauigkeit
 *             verloere. "0" bedeutet: pro Match neu wuerfeln.
 */
public record MapSettings(int width, int height, double density, String symmetry, String seed) {

    public MapSettings {
        symmetry = symmetry == null ? "quad" : symmetry;
        seed = seed == null ? "0" : seed;
    }

    public static MapSettings unknown() {
        return new MapSettings(0, 0, 0, "quad", "0");
    }

    public boolean randomSeed() {
        return "0".equals(seed);
    }
}
