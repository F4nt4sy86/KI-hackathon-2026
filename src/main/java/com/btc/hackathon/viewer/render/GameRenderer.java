package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.Board;
import com.btc.hackathon.viewer.model.Bomb;
import com.btc.hackathon.viewer.model.Flame;
import com.btc.hackathon.viewer.model.MatchState;
import com.btc.hackathon.viewer.model.PlayerState;
import com.btc.hackathon.viewer.model.Powerup;
import com.btc.hackathon.viewer.model.Rules;
import com.btc.hackathon.viewer.model.Tile;
import com.btc.hackathon.viewer.state.ViewState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Zeichnet das Spielfeld.
 *
 * <p>Die Reihenfolge der Ebenen liegt fest: Boden, Kisten, feste Waende, Power-ups,
 * Bomben, Flammen, Spieler. Die Spieler werden dabei nach {@code y} sortiert, damit eine
 * Figur weiter unten die darueber verdeckt.
 *
 * <p>Die Klasse liest aus dem Modell und schreibt auf den {@link GraphicsContext}.
 * Gemerkt wird nur, wo gerade eine Kiste zerfaellt - das ist ein Ereignis, kein Zustand.
 */
public final class GameRenderer {

    private final AssetRegistry assets;
    private final CrateBreaks crateBreaks = new CrateBreaks();

    public GameRenderer(AssetRegistry assets) {
        this.assets = assets;
    }

    /** Beim Matchwechsel aufrufen, damit keine Animation ins neue Match hinueberlaeuft. */
    public void reset() {
        crateBreaks.clear();
    }

    /**
     * @param subTick Bruchteil eines Ticks seit dem letzten Zustand, 0 bis 1. Damit
     *                laufen die Figuren auch zwischen zwei Zustaenden weiter.
     */
    public void draw(GraphicsContext gc, Viewport vp, ViewState view, long nowNanos, double subTick) {
        Board board = view.board();
        MatchState state = view.state();
        if (board == null || state == null) {
            return;
        }
        Rules rules = view.matchInit() == null ? Rules.defaults() : view.matchInit().rules();

        crateBreaks.observe(state.events(), nowNanos);
        crateBreaks.prune(nowNanos);

        drawBoard(gc, vp, board, nowNanos);
        drawPowerups(gc, vp, state.powerups(), nowNanos);
        drawBombs(gc, vp, state.bombs(), rules);
        drawFlames(gc, vp, state.flames(), rules);
        drawPlayers(gc, vp, state.players(), view, subTick);
    }

    // ------------------------------------------------------------------- Ebenen

    private void drawBoard(GraphicsContext gc, Viewport vp, Board board, long nowNanos) {
        int tile = vp.tileSize();
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                double px = vp.screenX(x);
                double py = vp.screenY(y);

                // Boden liegt unter allem - die Sprites darueber haben Alphakanal.
                boolean even = (x + y) % 2 == 0;
                assets.draw(gc, even ? Sprites.FLOOR_A : Sprites.FLOOR_B, px, py, tile, tile,
                        even ? Palette.FLOOR : Palette.FLOOR_ALT);

                // Eine gerade zerfallende Kiste steht auf einer Zelle, die im Modell
                // schon leer ist - deshalb vor der Kachelabfrage.
                int breakFrame = crateBreaks.frameAt(x, y, nowNanos);
                if (breakFrame >= 0) {
                    assets.draw(gc, Sprites.crateBreak(breakFrame), px, py, tile, tile,
                            Palette.BRICK);
                    continue;
                }

                switch (board.tileAt(x, y)) {
                    case SOLID -> drawBlock(gc, Sprites.WALL_SOLID, px, py, tile,
                            Palette.SOLID, Palette.SOLID_EDGE);
                    case SOFT -> drawBlock(gc, Sprites.CRATE, px, py, tile,
                            Palette.BRICK, Palette.BRICK_EDGE);
                    case EMPTY, UNKNOWN -> { }
                }
            }
        }
    }

    private void drawBlock(GraphicsContext gc, String sprite, double px, double py, int tile,
                           Color fill, Color edge) {
        if (assets.has(sprite)) {
            assets.draw(gc, sprite, px, py, tile, tile, fill);
            return;
        }
        double inset = Math.max(1, tile * 0.06);
        gc.setFill(edge);
        gc.fillRect(px, py, tile, tile);
        gc.setFill(fill);
        gc.fillRect(px + inset, py + inset, tile - 2 * inset, tile - 2 * inset);
    }

    private void drawPowerups(GraphicsContext gc, Viewport vp, List<Powerup> powerups, long nowNanos) {
        int tile = vp.tileSize();
        int frame = Animations.itemFrame(nowNanos);

        for (Powerup p : powerups) {
            String sprite = Sprites.item(p.kind(), frame);
            double px = vp.screenX(p.x());
            double py = vp.screenY(p.y());

            if (assets.has(sprite)) {
                assets.draw(gc, sprite, px, py, tile, tile, null);
            } else {
                drawPowerupPlaceholder(gc, p, px, py, tile);
            }
        }
    }

    private void drawPowerupPlaceholder(GraphicsContext gc, Powerup p, double px, double py, int tile) {
        double inset = tile * 0.22;
        double size = tile - 2 * inset;
        gc.setFill(Palette.POWERUP);
        gc.fillOval(px + inset, py + inset, size, size);

        if (tile >= 20) {
            gc.setFill(Palette.BACKGROUND);
            gc.setFont(Font.font("SansSerif", size * 0.7));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(p.kind().name().substring(0, 1), px + tile / 2.0, py + inset + size * 0.78);
            gc.setTextAlign(TextAlignment.LEFT);
        }
    }

    private void drawBombs(GraphicsContext gc, Viewport vp, List<Bomb> bombs, Rules rules) {
        int tile = vp.tileSize();
        for (Bomb b : bombs) {
            double px = vp.screenX(b.x());
            double py = vp.screenY(b.y());
            String sprite = Sprites.bomb(Animations.fuseFrame(b.fuse(), rules.bombFuseTicks()));

            if (assets.has(sprite)) {
                assets.draw(gc, sprite, px, py, tile, tile, null);
            } else {
                drawBombPlaceholder(gc, b, px, py, tile, rules);
            }
        }
    }

    private void drawBombPlaceholder(GraphicsContext gc, Bomb b, double px, double py, int tile,
                                     Rules rules) {
        double urgency = 1.0 - Math.min(1.0,
                b.fuse() / (double) Math.max(1, rules.bombFuseTicks()));
        double pulse = 1 + 0.12 * urgency * Math.sin(b.fuse() * 0.5);
        double size = tile * 0.7 * pulse;
        double x = px + (tile - size) / 2;
        double y = py + (tile - size) / 2;

        gc.setFill(Palette.BOMB);
        gc.fillOval(x, y, size, size);
        gc.setFill(Palette.BOMB_HIGHLIGHT);
        gc.fillOval(x + size * 0.22, y + size * 0.18, size * 0.18, size * 0.18);
        gc.setStroke(Palette.FUSE);
        gc.setLineWidth(Math.max(1.5, tile * 0.06));
        gc.strokeLine(x + size * 0.62, y + size * 0.14, x + size * 0.86, y - size * 0.12);
    }

    private void drawFlames(GraphicsContext gc, Viewport vp, List<Flame> flames, Rules rules) {
        if (flames.isEmpty()) {
            return;
        }
        int tile = vp.tileSize();
        Set<Long> burning = ExplosionShape.occupancy(flames);

        for (Flame f : flames) {
            int frame = Animations.flameFrame(f.ticks(), rules.flameDurationTicks());
            Sprites.ExplosionPiece piece = ExplosionShape.pieceAt(f.x(), f.y(), burning);
            String sprite = Sprites.explosion(piece, frame);

            double px = vp.screenX(f.x());
            double py = vp.screenY(f.y());

            if (assets.has(sprite)) {
                assets.draw(gc, sprite, px, py, tile, tile, null);
            } else {
                drawFlamePlaceholder(gc, f, px, py, tile, rules);
            }
        }
    }

    private void drawFlamePlaceholder(GraphicsContext gc, Flame f, double px, double py, int tile,
                                      Rules rules) {
        gc.setGlobalAlpha(Animations.flameAlpha(f.ticks(), rules.flameDurationTicks()));
        gc.setFill(Palette.EXPLOSION_EDGE);
        gc.fillRect(px, py, tile, tile);
        double inset = tile * 0.18;
        gc.setFill(Palette.EXPLOSION_CORE);
        gc.fillRect(px + inset, py + inset, tile - 2 * inset, tile - 2 * inset);
        gc.setGlobalAlpha(1.0);
    }

    private void drawPlayers(GraphicsContext gc, Viewport vp, List<PlayerState> players,
                             ViewState view, double subTick) {
        int tile = vp.tileSize();

        // Nach y sortiert, damit eine Figur weiter unten die darueber verdeckt.
        List<PlayerState> ordered = players.stream()
                .filter(PlayerState::alive)
                .sorted(Comparator.comparingInt(PlayerState::y))
                .toList();

        for (PlayerState p : ordered) {
            // Die Figur steht mit den Fuessen am Zellenboden - das Sprite deckt die Zelle
            // ohne Versatz ab.
            double px = vp.screenX(p.cellX(subTick));
            double py = vp.screenY(p.cellY(subTick));

            String sprite = Sprites.player(p.id(), p.dir(), Animations.walkFrame(p));
            if (assets.has(sprite)) {
                assets.draw(gc, sprite, px, py, tile, tile, null);
            } else {
                drawPlayerPlaceholder(gc, p, px, py, tile);
            }

            drawNameTag(gc, vp, p, view, px + tile / 2.0, py);
        }
    }

    private void drawPlayerPlaceholder(GraphicsContext gc, PlayerState p, double px, double py,
                                       int tile) {
        Color color = Palette.player(p.id());
        double size = tile * 0.78;
        double x = px + (tile - size) / 2;
        double y = py + (tile - size) / 2;

        gc.setFill(color.darker());
        gc.fillOval(x, y, size, size);
        gc.setFill(color);
        gc.fillOval(x + size * 0.08, y + size * 0.08, size * 0.84, size * 0.84);

        // Kleiner Punkt in Blickrichtung - ohne Sprite sonst nicht erkennbar.
        double r = size * 0.12;
        double d = size * 0.26;
        gc.setFill(Palette.BACKGROUND);
        gc.fillOval(x + size / 2 + p.dir().dx() * d - r, y + size / 2 + p.dir().dy() * d - r,
                2 * r, 2 * r);
    }

    private void drawNameTag(GraphicsContext gc, Viewport vp, PlayerState p, ViewState view,
                             double centerX, double topY) {
        if (vp.tileSize() < 22) {
            return;
        }
        String name = view.nameOf(p.id());
        if (name == null || name.isBlank()) {
            return;
        }
        Font font = Font.font("SansSerif", Math.max(9, vp.tileSize() * 0.26));
        // Hoechstens drei Kacheln breit. Ein selbstgewaehlter Name darf 24 Zeichen haben -
        // ungekuerzt deckt das Schild die Nachbarn zu, und wer am Rand steht, schreibt
        // aus dem Feld heraus.
        name = Labels.fit(name, font, vp.tileSize() * 3.0);
        gc.setFont(font);
        gc.setTextAlign(TextAlignment.CENTER);
        // Dunkler Versatz darunter, damit die Schrift auf jedem Untergrund lesbar bleibt.
        gc.setFill(Palette.BACKGROUND);
        gc.fillText(name, centerX + 1, topY - 2);
        gc.setFill(Palette.player(p.id()));
        gc.fillText(name, centerX, topY - 3);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** Zeichnet ein Feld ohne Spielgeschehen - fuer die Kartenvorschau. */
    public void drawBoardOnly(GraphicsContext gc, Viewport vp, Board board) {
        int tile = vp.tileSize();
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                double px = vp.screenX(x);
                double py = vp.screenY(y);
                boolean even = (x + y) % 2 == 0;
                assets.draw(gc, even ? Sprites.FLOOR_A : Sprites.FLOOR_B, px, py, tile, tile,
                        even ? Palette.FLOOR : Palette.FLOOR_ALT);
                Tile type = board.tileAt(x, y);
                if (type == Tile.SOLID) {
                    drawBlock(gc, Sprites.WALL_SOLID, px, py, tile, Palette.SOLID, Palette.SOLID_EDGE);
                } else if (type == Tile.SOFT) {
                    drawBlock(gc, Sprites.CRATE, px, py, tile, Palette.BRICK, Palette.BRICK_EDGE);
                }
            }
        }
    }
}
