package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.Direction;
import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.model.Slot;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Die Lobby-Grossansicht fuer den Beamer.
 *
 * <p>Zeigt jeden Sitzplatz, belegt oder frei - der Server schickt leere Plaetze
 * ausdruecklich mit, damit ein festes Raster gezeichnet werden kann statt einer
 * wachsenden Liste.
 *
 * <p>Ein Bot, der verstummt ist, wird deutlich als solcher gezeichnet. Das ist kein
 * Schmuck: er sieht sonst bis zum Matchbeginn aus wie ein Teilnehmer, und die Partie
 * startet ohne ihn.
 */
public final class LobbyRenderer {

    /** So breit wird ein Teilnehmerplatz hoechstens. */
    private static final double MAX_SLOT_WIDTH = 340;

    private final AssetRegistry assets;

    public LobbyRenderer(AssetRegistry assets) {
        this.assets = assets;
    }

    public void draw(GraphicsContext gc, double width, double height, LobbyState lobby, long nowNanos) {
        double titleY = height * 0.16;

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFill(Palette.TEXT);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.min(72, height * 0.11)));
        gc.fillText(title(lobby), width / 2, titleY);

        gc.setFill(Palette.TEXT_DIM);
        gc.setFont(Font.font("SansSerif", Math.min(20, height * 0.032)));
        gc.fillText(subtitle(lobby), width / 2, titleY + Math.min(36, height * 0.055));

        drawSlots(gc, width, height, lobby, nowNanos);
        drawHint(gc, width, height, lobby);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private static String title(LobbyState lobby) {
        return switch (lobby.phase()) {
            case COUNTDOWN -> "GLEICH GEHT ES LOS";
            case LOCKED -> "LOBBY GESPERRT";
            case UNKNOWN -> "VERBINDE";
            default -> "LOBBY";
        };
    }

    private static String subtitle(LobbyState lobby) {
        if (lobby.phase() == LobbyPhase.UNKNOWN) {
            return "Warte auf den Server";
        }
        String map = lobby.map().width() + "x" + lobby.map().height()
                + ", " + lobby.map().symmetry()
                + (lobby.map().randomSeed() ? ", zufaelliges Feld" : ", Seed " + lobby.map().seed());
        return map;
    }

    private void drawSlots(GraphicsContext gc, double width, double height, LobbyState lobby,
                           long nowNanos) {
        List<Slot> slots = lobby.slots();
        int count = slots.isEmpty() ? lobby.maxPlayers() : slots.size();
        if (count <= 0) {
            count = 4;
        }
        int columns = count <= 4 ? count : (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil(count / (double) columns);

        double areaTop = height * 0.30;
        double areaHeight = height * 0.46;
        double gap = Math.min(24, width * 0.02);
        double slotWidth = Math.min(MAX_SLOT_WIDTH, (width * 0.86 - gap * (columns - 1)) / columns);
        double slotHeight = Math.min(150, (areaHeight - gap * (rows - 1)) / rows);
        double totalWidth = slotWidth * columns + gap * (columns - 1);
        double startX = (width - totalWidth) / 2;

        for (int i = 0; i < count; i++) {
            int col = i % columns;
            int row = i / columns;
            double x = startX + col * (slotWidth + gap);
            double y = areaTop + row * (slotHeight + gap);

            if (i < slots.size() && slots.get(i).connected()) {
                drawOccupiedSlot(gc, x, y, slotWidth, slotHeight, slots.get(i), nowNanos);
            } else {
                int number = i < slots.size() ? slots.get(i).id() : i;
                drawEmptySlot(gc, x, y, slotWidth, slotHeight, number);
            }
        }
    }

    private void drawOccupiedSlot(GraphicsContext gc, double x, double y, double w, double h,
                                  Slot slot, long nowNanos) {
        Color color = Palette.player(slot.id());

        gc.setFill(color.deriveColor(0, 1, 0.32, 1));
        gc.fillRoundRect(x, y, w, h, 14, 14);
        gc.setStroke(slot.stale() ? Palette.WARNING : color);
        gc.setLineWidth(3);
        gc.strokeRoundRect(x, y, w, h, 14, 14);

        drawAvatar(gc, x + w * 0.05, y + h * 0.12, h * 0.76, slot, nowNanos, color);

        double textX = x + w * 0.05 + h * 0.82;
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Palette.TEXT);
        gc.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.min(24, h * 0.22)));
        gc.fillText(shorten(slot.displayName(), 16), textX, y + h * 0.42);

        gc.setFont(Font.font("SansSerif", Math.min(15, h * 0.15)));
        if (slot.stale()) {
            gc.setFill(Palette.WARNING);
            gc.fillText(staleText(slot), textX, y + h * 0.66);
        } else {
            gc.setFill(Palette.OK);
            gc.fillText(slot.packetsPerSec() + " Pakete/s", textX, y + h * 0.66);
        }

        gc.setFill(Palette.TEXT_DIM);
        gc.setFont(Font.font("SansSerif", Math.min(13, h * 0.13)));
        gc.fillText(slot.addr() == null ? "" : slot.addr(), textX, y + h * 0.86);

        gc.setTextAlign(TextAlignment.CENTER);
    }

    private static String staleText(Slot slot) {
        Integer ms = slot.staleMs();
        if (ms == null) {
            return "noch kein Paket";
        }
        return String.format("seit %.1f s still", ms / 1000.0);
    }

    /**
     * Zeichnet die Spielfigur des Teilnehmers.
     *
     * <p>Dieselbe Grafik und dieselbe Farbe wie im Match - so ist schon in der Lobby klar,
     * welche Figur zu welchem Platz gehoert.
     */
    private void drawAvatar(GraphicsContext gc, double x, double y, double size, Slot slot,
                            long nowNanos, Color color) {
        String sprite = Sprites.player(slot.id(), Direction.DOWN, Animations.idleFrame(nowNanos));
        if (assets.has(sprite)) {
            assets.draw(gc, sprite, x, y, size, size, null);
        } else {
            gc.setFill(color);
            gc.fillOval(x + size * 0.15, y + size * 0.15, size * 0.7, size * 0.7);
        }
    }

    private void drawEmptySlot(GraphicsContext gc, double x, double y, double w, double h, int number) {
        gc.setStroke(Palette.TEXT_DIM);
        gc.setLineWidth(2);
        gc.setLineDashes(8, 8);
        gc.strokeRoundRect(x, y, w, h, 14, 14);
        gc.setLineDashes();

        gc.setFill(Palette.TEXT_DIM);
        gc.setFont(Font.font("SansSerif", Math.min(18, h * 0.18)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Platz " + number + " frei", x + w / 2, y + h / 2 + 6);
    }

    private void drawHint(GraphicsContext gc, double width, double height, LobbyState lobby) {
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font("SansSerif", Math.min(20, height * 0.032)));

        String hint;
        Color color = Palette.TEXT_DIM;
        if (lobby.phase() == LobbyPhase.COUNTDOWN) {
            hint = "Countdown laeuft";
            color = Palette.ACCENT;
        } else if (lobby.paused()) {
            hint = "Pausiert - erst fortsetzen, dann starten";
            color = Palette.WARNING;
        } else if (lobby.canStart()) {
            hint = lobby.connectedCount() + " Teilnehmer verbunden - die Moderation kann starten";
            color = Palette.OK;
        } else {
            hint = "Warte auf Teilnehmer (" + lobby.connectedCount() + " von mindestens "
                    + lobby.minPlayers() + ")";
        }
        gc.setFill(color);
        gc.fillText(hint, width / 2, height * 0.88);
    }

    private static String shorten(String s, int max) {
        if (s == null || s.isBlank()) {
            return "(ohne Namen)";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
