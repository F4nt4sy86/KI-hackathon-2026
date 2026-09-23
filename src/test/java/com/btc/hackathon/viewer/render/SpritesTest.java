package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.Direction;
import com.btc.hackathon.viewer.model.PowerupKind;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft die Sprite-Namen gegen die tatsaechlich vorliegenden Dateien.
 *
 * <p>Das ist hier der wichtigste Test: ein Tippfehler in einem Namen faellt nicht auf,
 * sondern laesst den Renderer stillschweigend auf die Platzhalterdarstellung
 * zurueckfallen. Der Fehler zeigt sich dann erst auf dem Beamer - und auch dort nur als
 * "sieht irgendwie falsch aus".
 *
 * <p>Fehlt der Grafikordner, wird der Test uebersprungen statt rot - er prueft die
 * Grafiken, nicht ihre Anwesenheit.
 */
class SpritesTest {

    private static Path spriteDir;

    @BeforeAll
    static void locateSprites() throws IOException {
        Path assets = Path.of("assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(assets, 5)) {
            Optional<Path> found = walk
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve(Sprites.MARKER + ".png")))
                    .findFirst();
            spriteDir = found.orElse(null);
        }
    }

    private static void requireSprites() {
        Assumptions.assumeTrue(spriteDir != null,
                "Kein Sprite-Ordner unter assets/ gefunden - Test wird uebersprungen");
    }

    private static boolean exists(String name) {
        return Files.isRegularFile(spriteDir.resolve(name + ".png"));
    }

    /** Sammelt jeden Namen, den der Zeichencode erzeugen kann. */
    private static List<String> allGeneratedNames() {
        List<String> names = new ArrayList<>();
        names.add(Sprites.FLOOR_A);
        names.add(Sprites.FLOOR_B);
        names.add(Sprites.WALL_SOLID);
        names.add(Sprites.CRATE);

        for (int colorIndex = 0; colorIndex < Sprites.PLAYER_VARIANTS.length; colorIndex++) {
            for (Direction direction : Direction.values()) {
                if (direction == Direction.UNKNOWN) {
                    continue; // faellt auf "down" zurueck, erzeugt keinen eigenen Namen
                }
                for (int frame = 0; frame < Sprites.PLAYER_FRAMES; frame++) {
                    names.add(Sprites.player(colorIndex, direction, frame));
                }
            }
        }
        for (int frame = 0; frame < Sprites.BOMB_FRAMES; frame++) {
            names.add(Sprites.bomb(frame));
        }
        for (int frame = 0; frame < Sprites.CRATE_BREAK_FRAMES; frame++) {
            names.add(Sprites.crateBreak(frame));
        }
        for (Sprites.ExplosionPiece piece : Sprites.ExplosionPiece.values()) {
            for (int frame = 0; frame < Sprites.EXPLOSION_FRAMES; frame++) {
                names.add(Sprites.explosion(piece, frame));
            }
        }
        for (PowerupKind type : PowerupKind.values()) {
            for (int frame = 0; frame < Sprites.ITEM_FRAMES; frame++) {
                String name = Sprites.item(type, frame);
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    @Test
    @DisplayName("Zu jedem erzeugten Namen liegt eine Datei vor")
    void everyGeneratedNameHasAFile() {
        requireSprites();

        List<String> missing = allGeneratedNames().stream()
                .filter(name -> !exists(name))
                .toList();

        assertTrue(missing.isEmpty(),
                "Diese Sprites erwartet der Renderer, aber sie fehlen in " + spriteDir + ": " + missing);
    }

    @Test
    @DisplayName("Die Kacheln des Spielfelds liegen vor")
    void tileSpritesExist() {
        requireSprites();

        assertTrue(exists(Sprites.FLOOR_A));
        assertTrue(exists(Sprites.FLOOR_B));
        assertTrue(exists(Sprites.WALL_SOLID));
        assertTrue(exists(Sprites.CRATE));
    }

    @Test
    @DisplayName("Alle vier Spielervarianten sind vollstaendig")
    void everyPlayerVariantIsComplete() {
        requireSprites();

        // 4 Varianten x 4 Richtungen x 4 Bilder
        long playerSprites = allGeneratedNames().stream()
                .filter(n -> n.startsWith("player_"))
                .filter(SpritesTest::exists)
                .count();

        assertEquals(4 * 4 * Sprites.PLAYER_FRAMES, playerSprites);
    }

    // ------------------------------------------------------- Namensbildung pur

    @Test
    @DisplayName("Die Farbindizes laufen ueber die vier Varianten um")
    void colorIndexWrapsAroundVariants() {
        requireSprites();

        assertEquals("blue", Sprites.playerVariant(0));
        assertEquals("purple", Sprites.playerVariant(3));
        // Mehr Teilnehmer als Varianten: der Index laeuft um, statt zu werfen.
        assertEquals("blue", Sprites.playerVariant(4));
        assertEquals("red", Sprites.playerVariant(-3));
    }

    @Test
    @DisplayName("Eine unbekannte Richtung wird zur Vorderansicht")
    void unknownDirectionFallsBackToDown() {
        assertEquals(Sprites.player(0, Direction.DOWN, 0),
                Sprites.player(0, Direction.UNKNOWN, 0));
    }

    @Test
    @DisplayName("Bildnummern ausserhalb des Vorrats werden gekappt")
    void frameIndicesAreClamped() {
        assertEquals("bomb_3", Sprites.bomb(99));
        assertEquals("bomb_0", Sprites.bomb(-1));
        assertEquals("expl_center_4", Sprites.explosion(Sprites.ExplosionPiece.CENTER, 99));
    }

    @Test
    @DisplayName("Eine unbekannte Powerup-Art hat kein Sprite")
    void unknownPowerupHasNoSprite() {
        // Der Renderer zeichnet dann den Platzhalter, statt ein falsches Bild zu waehlen.
        assertNull(Sprites.item(PowerupKind.UNKNOWN, 0));
        assertNotNull(Sprites.item(PowerupKind.SPEED, 0));
    }

    @Test
    @DisplayName("Palette und Sprite-Varianten haben dieselbe Reihenfolge")
    void paletteMatchesVariantCount() {
        // Sonst zeigte die Teilnehmertabelle eine andere Farbe als die Figur im Spiel.
        assertEquals(Sprites.PLAYER_VARIANTS.length, Palette.playerColorCount());
    }
}
