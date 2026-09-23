package com.btc.hackathon.viewer.render;

import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.function.DoubleFunction;

/**
 * Zuschnitt von Beschriftungen auf den vorhandenen Platz.
 *
 * <p>Seit Bots sich im Anmeldepaket selbst benennen duerfen, ist ein Name nicht mehr
 * {@code bot-0}, sondern bis zu 24 frei gewaehlte Zeichen. Jede Stelle, die einen Namen
 * zeichnet, muss damit rechnen: ungekuerzt laeuft er ueber den Nachbarn, ueber den
 * Punktestand oder aus dem Bild heraus. Der Server begrenzt die Laenge, aber nicht die
 * Breite - "WWWWWWWWWWWWWWWWWWWWWWWW" ist dreimal so breit wie dieselbe Zahl schmaler
 * Zeichen, und wie breit etwas wird, weiss nur die Schrift.
 *
 * <p>Gemessen wird deshalb wirklich, nicht ueber eine geschaetzte Zeichenbreite. Die
 * Leinwand selbst kann Text nicht messen; dafuer gibt es einen {@link Text}-Knoten, der
 * hier wiederverwendet wird. Er wird nur aus dem Zeichenvorgang heraus angefasst, also
 * ausschliesslich vom JavaFX-Thread.
 */
public final class Labels {

    private static Text measure;

    private Labels() {
    }

    /**
     * Kuerzt auf hoechstens {@code maxChars} Zeichen, mit Auslassungspunkten.
     *
     * <p>Gezaehlt werden Codepoints, nicht {@code char}s. Ein Emoji besteht aus zweien,
     * und mitten hindurch geschnitten wird daraus ein Ersatzkaestchen. Der Server
     * streicht nur Steuerzeichen - Emoji laesst er durch, und in einem Wettbewerb unter
     * Kollegen heisst der erste Bot erfahrungsgemaess nicht "bot-0".
     */
    public static String shorten(String text, int maxChars) {
        if (text == null || maxChars <= 0) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= maxChars) {
            return text;
        }
        return limit(text, Math.max(1, maxChars - 1)) + "…";
    }

    /**
     * Die ersten {@code maxChars} Zeichen, ohne Auslassungspunkte.
     *
     * <p>Fuer Eingaben, die der Server ohnehin auf dieselbe Laenge stutzt: dort waere ein
     * angehaengtes Zeichen kein Hinweis, sondern Teil des Namens.
     */
    public static String limit(String text, int maxChars) {
        if (text == null || maxChars <= 0) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= maxChars) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxChars));
    }

    /** Kuerzt so weit, bis der Text in {@code maxWidth} passt. */
    public static String fit(String text, Font font, double maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (width(text, font) <= maxWidth) {
            return text;
        }
        // Rueckwaerts kuerzen. Namen sind auf 24 Zeichen begrenzt, das sind hoechstens
        // zwei Dutzend Messungen fuer eine Beschriftung - nicht je Kachel, je Name.
        for (int chars = text.codePointCount(0, text.length()) - 1; chars > 0; chars--) {
            String candidate = shorten(text, chars);
            if (width(candidate, font) <= maxWidth) {
                return candidate;
            }
        }
        return "…";
    }

    /**
     * Die groesste Schrift zwischen {@code minSize} und {@code maxSize}, in der der Text
     * noch in {@code maxWidth} passt.
     *
     * <p>Fuer Ueberschriften, die gross sein sollen, aber nicht auf Kosten der
     * Lesbarkeit: der Siegername darf schrumpfen, abgeschnitten gehoert er nicht.
     *
     * @param bySize baut die Schrift zu einer Groesse - als Funktion, damit Schnitt und
     *               Familie erhalten bleiben und nicht bei jeder Groesse neu genannt
     *               werden muessen
     */
    public static Font fitFont(String text, DoubleFunction<Font> bySize, double maxSize,
                               double minSize, double maxWidth) {
        double size = Math.max(minSize, maxSize);
        Font font = bySize.apply(size);
        while (size > minSize && width(text, font) > maxWidth) {
            size = Math.max(minSize, size - 2);
            font = bySize.apply(size);
        }
        return font;
    }

    /** Breite des Textes in dieser Schrift, in Bildpunkten. */
    public static double width(String text, Font font) {
        Text node = measure;
        if (node == null) {
            node = new Text();
            measure = node;
        }
        node.setFont(font);
        node.setText(text == null ? "" : text);
        return node.getLayoutBounds().getWidth();
    }
}
