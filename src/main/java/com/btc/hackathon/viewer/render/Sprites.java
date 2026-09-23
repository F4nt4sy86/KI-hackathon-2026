package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.Direction;
import com.btc.hackathon.viewer.model.PowerupKind;

/**
 * Die Namen der Sprites an einer Stelle.
 *
 * <p>Das Sprite-Set liefert 121 Einzelbilder zu 64x64 mit Alphakanal. Die Namen folgen
 * festen Mustern, die hier zusammengesetzt werden - so steht die Kenntnis des
 * Dateischemas nicht verstreut im Zeichencode.
 *
 * <p>Die Zuordnung stammt aus {@code atlas.json} und der Beschreibung des Sets.
 */
public final class Sprites {

    /** Kantenlaenge eines Sprites in Pixeln. */
    public static final int SOURCE_SIZE = 64;

    public static final String FLOOR_A = "floor_0";
    public static final String FLOOR_B = "floor_1";
    public static final String WALL_SOLID = "wall_solid";
    public static final String CRATE = "crate";
    public static final String CRATE_BREAK_PREFIX = "crate_break";

    /** Bild, an dem sich das Sprite-Verzeichnis erkennen laesst. */
    public static final String MARKER = FLOOR_A;

    /** Die vier Anzugvarianten des Sets, in der Reihenfolge des Farbindex. */
    public static final String[] PLAYER_VARIANTS = {"blue", "red", "yellow", "purple"};

    /** Bilder je Laufzyklus: 0 und 2 sind Standposen, 1 und 3 die Durchgangsposen. */
    public static final int PLAYER_FRAMES = 4;
    public static final int BOMB_FRAMES = 4;
    public static final int EXPLOSION_FRAMES = 5;
    public static final int ITEM_FRAMES = 2;
    public static final int CRATE_BREAK_FRAMES = 4;

    /** Teile, aus denen sich ein Feuerbalken zusammensetzt. */
    public enum ExplosionPiece {
        CENTER("expl_center"),
        ARM_HORIZONTAL("expl_arm_h"),
        ARM_VERTICAL("expl_arm_v"),
        TIP_UP("expl_tip_up"),
        TIP_DOWN("expl_tip_down"),
        TIP_LEFT("expl_tip_left"),
        TIP_RIGHT("expl_tip_right");

        private final String prefix;

        ExplosionPiece(String prefix) {
            this.prefix = prefix;
        }

        String frame(int index) {
            return prefix + "_" + index;
        }
    }

    private Sprites() {
    }

    /** Die Anzugvariante zu einem Farbindex; der Index laeuft bei Bedarf um. */
    public static String playerVariant(int colorIndex) {
        return PLAYER_VARIANTS[Math.floorMod(colorIndex, PLAYER_VARIANTS.length)];
    }

    /**
     * @param frame Nummer im Laufzyklus, 0 bis 3
     */
    public static String player(int colorIndex, Direction direction, int frame) {
        return "player_" + playerVariant(colorIndex) + "_" + directionName(direction)
                + "_" + Math.floorMod(frame, PLAYER_FRAMES);
    }

    private static String directionName(Direction direction) {
        return switch (direction) {
            case UP -> "up";
            case LEFT -> "left";
            case RIGHT -> "right";
            // Nach vorne ist die sinnvollste Ansicht, wenn die Richtung unbekannt ist.
            case DOWN, UNKNOWN -> "down";
        };
    }

    /** @param frame 0 bis 3; die Zuendschnur brennt ueber die Bilder sichtbar ab */
    public static String bomb(int frame) {
        return "bomb_" + clamp(frame, BOMB_FRAMES);
    }

    /** @param frame 0 bis 3 der Zerfallsanimation einer Kiste */
    public static String crateBreak(int frame) {
        return CRATE_BREAK_PREFIX + "_" + clamp(frame, CRATE_BREAK_FRAMES);
    }

    public static String explosion(ExplosionPiece piece, int frame) {
        return piece.frame(clamp(frame, EXPLOSION_FRAMES));
    }

    /**
     * Sprite eines Power-ups.
     *
     * <p>Der Server vergibt genau drei Arten. Das Set bringt zusaetzlich
     * {@code item_kick} und {@code item_remote} mit; die bleiben ungenutzt, bis die
     * Simulation sie kennt.
     *
     * @return der Name, oder {@code null} fuer eine Art, die das Set nicht kennt -
     *         dann zeichnet der Renderer den Platzhalter
     */
    public static String item(PowerupKind kind, int frame) {
        String name = switch (kind) {
            case EXTRA_BOMB -> "item_bomb_up";
            case FLAME -> "item_fire_up";
            case SPEED -> "item_speed_up";
            case UNKNOWN -> null;
        };
        return name == null ? null : name + "_" + clamp(frame, ITEM_FRAMES);
    }

    private static int clamp(int frame, int count) {
        if (frame < 0) {
            return 0;
        }
        return Math.min(frame, count - 1);
    }
}
