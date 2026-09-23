package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.MatchInit;
import com.btc.hackathon.viewer.net.ServerConnection;
import com.btc.hackathon.viewer.state.ViewState;
import com.btc.hackathon.viewer.state.ViewStateStore;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Der Bildtakt.
 *
 * <p>Holt in jedem Bild den aktuellen Zustand aus dem {@link ViewStateStore} und waehlt
 * anhand der Phase, was gezeichnet wird. Der Takt laeuft unabhaengig von der
 * Nachrichtenrate: kommen gerade keine Zustaende, wird der letzte bekannte weitergezeichnet;
 * kommen mehr als Bilder, wird der neueste genommen.
 *
 * <p>Zwischen zwei Zustaenden bewegen sich die Figuren weiter - der Bruchteil des
 * laufenden Ticks stammt aus dem Zustandsspeicher. Ohne das ruckelt es auf jedem Schirm,
 * der schneller als die 60 Zustaende des Servers zeichnet.
 */
public final class RenderLoop extends AnimationTimer {

    private final Canvas canvas;
    private final ViewStateStore store;
    private final AssetRegistry assets;
    private final ServerConnection server;

    private final GameRenderer gameRenderer;
    private final LobbyRenderer lobbyRenderer;
    private final ResultOverlay resultOverlay = new ResultOverlay();
    private final DebugOverlay debugOverlay = new DebugOverlay();

    private boolean showDebug;
    private long lastMatchId = -1;

    public RenderLoop(Canvas canvas, ViewStateStore store, AssetRegistry assets,
                      ServerConnection server) {
        this.canvas = canvas;
        this.store = store;
        this.assets = assets;
        this.server = server;
        this.gameRenderer = new GameRenderer(assets);
        this.lobbyRenderer = new LobbyRenderer(assets);
    }

    public void toggleDebug() {
        showDebug = !showDebug;
    }

    public boolean isDebugVisible() {
        return showDebug;
    }

    @Override
    public void handle(long now) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        debugOverlay.tick(now);
        store.updateStatesPerSecond(now);

        ViewState state = store.current();
        forgetEffectsOnNewMatch(state);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        // Die Sprites sind Pixel-Art ohne Kantenglaettung. Mit interpolierter Skalierung
        // wuerden sie matschig - das Set schreibt NEAREST ausdruecklich vor.
        gc.setImageSmoothing(false);
        gc.setFill(Palette.BACKGROUND);
        gc.fillRect(0, 0, width, height);

        if (state.hasPreview()) {
            drawPreview(gc, width, height, state.preview());
        } else if (state.phase().showsBoard()) {
            drawGame(gc, width, height, state, now);
            if (state.phase() == LobbyPhase.MATCH_OVER && state.hasResult()) {
                resultOverlay.draw(gc, width, height, state.result(), state);
            } else if (state.paused()) {
                drawPaused(gc, width, height);
            }
        } else {
            lobbyRenderer.draw(gc, width, height, state.lobby(), now);
        }

        debugOverlay.drawConnectionWarning(gc, width, height, server.state(), server);
        if (showDebug) {
            debugOverlay.draw(gc, store, state, server, assets.loadedCount());
        }
    }

    /** Beim Matchwechsel duerfen keine Effekte des alten Matches weiterlaufen. */
    private void forgetEffectsOnNewMatch(ViewState state) {
        MatchInit init = state.matchInit();
        long matchId = init == null ? -1 : init.matchId();
        if (matchId != lastMatchId) {
            lastMatchId = matchId;
            gameRenderer.reset();
        }
    }

    private void drawGame(GraphicsContext gc, double width, double height, ViewState state, long now) {
        if (!state.hasBoard() || !state.hasState()) {
            drawWaitingForField(gc, width, height);
            return;
        }
        Viewport vp = Viewport.fit(width, height, state.board().width(), state.board().height());
        // Waehrend einer Pause steht die Simulation - dann auch nicht weiterinterpolieren.
        double subTick = state.paused() ? 0 : store.subTickProgress(now, state.tickRate());
        gameRenderer.draw(gc, vp, state, now, subTick);
    }

    private void drawPreview(GraphicsContext gc, double width, double height, MatchInit preview) {
        Viewport vp = Viewport.fit(width, height * 0.92,
                preview.board().width(), preview.board().height());
        gameRenderer.drawBoardOnly(gc, vp, preview.board());

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Palette.ACCENT);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.min(24, height * 0.038)));
        gc.fillText("KARTENVORSCHAU - Seed " + preview.seed(), width / 2, height * 0.97);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawWaitingForField(GraphicsContext gc, double width, double height) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Palette.TEXT_DIM);
        gc.setFont(Font.font("SansSerif", Math.min(24, height * 0.04)));
        gc.fillText("Match laeuft - warte auf das erste Spielfeld", width / 2, height / 2);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawPaused(GraphicsContext gc, double width, double height) {
        gc.setFill(Color.color(0, 0, 0, 0.55));
        gc.fillRect(0, 0, width, height);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Palette.TEXT);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.min(72, height * 0.11)));
        gc.fillText("PAUSIERT", width / 2, height / 2);
        gc.setTextAlign(TextAlignment.LEFT);
    }
}
