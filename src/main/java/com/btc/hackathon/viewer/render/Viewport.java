package com.btc.hackathon.viewer.render;

/**
 * Rechnet Feldkoordinaten in Bildschirmkoordinaten um.
 *
 * <p>Die Kantenlaenge einer Kachel ist bewusst ganzzahlig: mit gebrochenen Groessen
 * landen Kachelgrafiken auf halben Pixeln und das Raster flimmert beim Groesseaendern.
 * Der verbleibende Rand wird als Rahmen verteilt, das Feld bleibt also immer zentriert
 * und formattreu.
 */
public record Viewport(int tileSize, double offsetX, double offsetY, int fieldWidth, int fieldHeight) {

    /** Passt ein Feld in die gegebene Zeichenflaeche ein. */
    public static Viewport fit(double canvasWidth, double canvasHeight, int fieldWidth, int fieldHeight) {
        if (fieldWidth <= 0 || fieldHeight <= 0) {
            return new Viewport(1, 0, 0, Math.max(fieldWidth, 1), Math.max(fieldHeight, 1));
        }
        int tile = (int) Math.floor(Math.min(canvasWidth / fieldWidth, canvasHeight / fieldHeight));
        if (tile < 1) {
            tile = 1;
        }
        double usedWidth = (double) tile * fieldWidth;
        double usedHeight = (double) tile * fieldHeight;
        return new Viewport(
                tile,
                Math.floor((canvasWidth - usedWidth) / 2),
                Math.floor((canvasHeight - usedHeight) / 2),
                fieldWidth,
                fieldHeight);
    }

    /** @param fieldX Position in Kacheleinheiten, Nachkommastellen erlaubt */
    public double screenX(double fieldX) {
        return offsetX + fieldX * tileSize;
    }

    public double screenY(double fieldY) {
        return offsetY + fieldY * tileSize;
    }

    public double pixelWidth() {
        return (double) tileSize * fieldWidth;
    }

    public double pixelHeight() {
        return (double) tileSize * fieldHeight;
    }
}
