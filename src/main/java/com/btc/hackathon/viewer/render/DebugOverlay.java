package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.net.ConnectionState;
import com.btc.hackathon.viewer.net.ServerConnection;
import com.btc.hackathon.viewer.state.ViewState;
import com.btc.hackathon.viewer.state.ViewStateStore;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Technische Anzeige fuer die Fehlersuche, umschaltbar mit F3.
 *
 * <p>Der wichtigste Teil ist der grosse Hinweis "KEINE VERBINDUNG": steht die Verbindung
 * nicht, ist das im Ernstfall die Information, die man sofort braucht - und sie wird
 * eingeblendet, auch wenn die technische Anzeige ausgeschaltet ist.
 */
public final class DebugOverlay {

    private long frames;
    private long fpsWindowStartNanos;
    private double fps;

    /** Der grosse Hinweis - wird immer gezeichnet, unabhaengig von F3. */
    public void drawConnectionWarning(GraphicsContext gc, double width, double height,
                                      ConnectionState connection, ServerConnection server) {
        if (connection.isConnected()) {
            return;
        }
        gc.setFill(Color.color(0, 0, 0, 0.6));
        gc.fillRect(0, height / 2 - height * 0.10, width, height * 0.20);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Palette.WARNING);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.min(48, height * 0.075)));
        gc.fillText("KEINE VERBINDUNG ZUM SERVER", width / 2, height / 2);

        gc.setFill(Palette.TEXT_DIM);
        gc.setFont(Font.font("SansSerif", Math.min(18, height * 0.028)));
        gc.fillText(server.uri().toString(), width / 2, height / 2 + Math.min(32, height * 0.048));
        gc.fillText(connection == ConnectionState.CONNECTING
                        ? "Verbindungsversuch laeuft"
                        : "Naechster Versuch in Kuerze",
                width / 2, height / 2 + Math.min(58, height * 0.082));
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** Zaehlt die Bildrate mit. In jedem Bild aufrufen, auch wenn die Anzeige aus ist. */
    public void tick(long nowNanos) {
        frames++;
        if (fpsWindowStartNanos == 0) {
            fpsWindowStartNanos = nowNanos;
            return;
        }
        long elapsed = nowNanos - fpsWindowStartNanos;
        if (elapsed >= 500_000_000L) {
            fps = frames * 1_000_000_000.0 / elapsed;
            frames = 0;
            fpsWindowStartNanos = nowNanos;
        }
    }

    public void draw(GraphicsContext gc, ViewStateStore store, ViewState state,
                     ServerConnection server, int loadedAssets) {
        String[] lines = {
                String.format("%.0f FPS", fps),
                String.format("%.1f Zustaende/s", store.statesPerSecond()),
                "Phase: " + state.phase() + (state.paused() ? " (pausiert)" : ""),
                "Tick: " + (state.hasState() ? state.state().tick() : 0),
                "Verbindung: " + server.state().label(),
                "Nachrichten: " + server.messagesReceived(),
                "Lesefehler: " + server.parseErrors(),
                "Feld: " + (state.hasBoard()
                        ? state.board().width() + "x" + state.board().height()
                        : "-"),
                "Sprites: " + loadedAssets,
        };

        double lineHeight = 16;
        double boxWidth = 230;
        double boxHeight = lines.length * lineHeight + 14;

        gc.setFill(Color.color(0, 0, 0, 0.62));
        gc.fillRoundRect(8, 8, boxWidth, boxHeight, 8, 8);

        gc.setFont(Font.font("Monospaced", 12));
        for (int i = 0; i < lines.length; i++) {
            // Die Fehlerzeile rot faerben, sobald sie nicht mehr null ist.
            gc.setFill(i == 6 && server.parseErrors() > 0 ? Palette.WARNING : Palette.TEXT);
            gc.fillText(lines[i], 18, 26 + i * lineHeight);
        }
    }
}
