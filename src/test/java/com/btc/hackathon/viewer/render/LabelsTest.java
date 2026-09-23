package com.btc.hackathon.viewer.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft den Textzuschnitt, soweit er ohne laufende Oberflaeche pruefbar ist.
 *
 * <p>Das Messen von Breiten braucht die JavaFX-Laufzeit und bleibt hier aussen vor. Die
 * Zeichenzaehlung nicht: seit Bots sich selbst benennen duerfen, kommen Namen aus fremder
 * Hand, und ein mitten durch ein Emoji gelegter Schnitt ergibt ein Ersatzkaestchen im
 * Ergebnisbild - genau dort, wo es am meisten auffaellt.
 */
class LabelsTest {

    private static final String EMOJI = "💣"; // Bombe, zwei chars, ein Zeichen

    @Test
    @DisplayName("Was passt, bleibt unveraendert")
    void shortEnoughStaysAsItIs() {
        assertEquals("bot-0", Labels.shorten("bot-0", 24));
        assertEquals("bot-0", Labels.limit("bot-0", 24));
        assertEquals("abcde", Labels.shorten("abcde", 5));
    }

    @Test
    @DisplayName("Zu langer Text bekommt Auslassungspunkte")
    void longTextIsMarkedAsCut() {
        assertEquals("abcd…", Labels.shorten("abcdefgh", 5));
        assertEquals("abcde", Labels.limit("abcdefgh", 5));
    }

    @Test
    @DisplayName("Ein Emoji wird nicht in der Mitte durchgeschnitten")
    void surrogatePairsSurviveIntactOrNotAtAll() {
        String name = EMOJI.repeat(4);

        String cut = Labels.limit(name, 2);
        assertEquals(EMOJI.repeat(2), cut);
        // Der harte Beweis: kein halbes Paar am Ende.
        assertTrue(cut.codePointCount(0, cut.length()) == 2);
        assertTrue(Character.isHighSurrogate(cut.charAt(cut.length() - 2)));

        String marked = Labels.shorten(name, 3);
        assertEquals(EMOJI.repeat(2) + "…", marked);
    }

    @Test
    @DisplayName("Leeres und Unsinniges liefert nichts, statt zu werfen")
    void degenerateInputIsHarmless() {
        assertEquals("", Labels.shorten(null, 10));
        assertEquals("", Labels.limit(null, 10));
        assertEquals("", Labels.shorten("abc", 0));
        assertEquals("", Labels.limit("abc", -1));
        // Bei Platz fuer genau ein Zeichen bleiben die Auslassungspunkte uebrig - der
        // Hinweis "hier fehlt etwas" ist mehr wert als ein einzelner Buchstabe.
        assertEquals("a…", Labels.shorten("abc", 1));
    }
}
