package com.btc.hackathon.viewer.render;

import com.btc.hackathon.viewer.model.MatchEnd;
import com.btc.hackathon.viewer.model.MatchResult;
import com.btc.hackathon.viewer.state.ViewState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.Comparator;
import java.util.List;

/**
 * Legt das Endergebnis ueber das eingefrorene Spielfeld.
 *
 * <p>Das Feld bleibt darunter sichtbar - so sieht man noch, wie die Partie ausging,
 * waehrend der Sieger eingeblendet wird. Der Server bleibt auf {@code match_over} stehen,
 * bis die Moderation zuruecksetzt; die Anzeige darf also beliebig lange stehen.
 */
public final class ResultOverlay {

    public void draw(GraphicsContext gc, double width, double height, MatchEnd result,
                     ViewState view) {
        // Abdunkeln, damit die Schrift auch auf hellen Kacheln lesbar bleibt.
        gc.setFill(Color.color(0, 0, 0, 0.68));
        gc.fillRect(0, 0, width, height);

        gc.setTextAlign(TextAlignment.CENTER);

        double titleY = height * 0.28;
        if (result.hasWinner()) {
            int winner = result.winner();
            gc.setFill(Palette.TEXT_DIM);
            gc.setFont(Font.font("SansSerif", Math.min(26, height * 0.042)));
            gc.fillText("SIEGER", width / 2, titleY - Math.min(52, height * 0.08));

            String name = view.nameOf(winner);
            gc.setFill(Palette.player(winner));
            // Der Siegername darf schrumpfen, abgeschnitten gehoert er nicht - er ist
            // das Einzige, worauf in diesem Augenblick jeder im Raum schaut.
            gc.setFont(Labels.fitFont(name,
                    size -> Font.font("SansSerif", FontWeight.BOLD, size),
                    Math.min(84, height * 0.13), Math.min(30, height * 0.05), width * 0.86));
            gc.fillText(name, width / 2, titleY);
        } else {
            gc.setFill(Palette.TEXT);
            gc.setFont(Font.font("SansSerif", FontWeight.BOLD, Math.min(64, height * 0.1)));
            gc.fillText("KEIN SIEGER", width / 2, titleY);
        }

        gc.setFill(Palette.TEXT_DIM);
        gc.setFont(Font.font("SansSerif", Math.min(20, height * 0.032)));
        gc.fillText(result.reason().label(), width / 2, titleY + height * 0.055);

        drawScores(gc, width, height, result, view);

        gc.setFill(Palette.TEXT_DIM);
        gc.setFont(Font.font("SansSerif", Math.min(18, height * 0.028)));
        gc.fillText("Die Moderation kann zurueck in die Lobby wechseln", width / 2, height * 0.92);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private void drawScores(GraphicsContext gc, double width, double height, MatchEnd result,
                            ViewState view) {
        List<MatchResult> scores = result.results().stream()
                .sorted(Comparator.comparingInt(MatchResult::placement))
                .toList();
        if (scores.isEmpty()) {
            return;
        }

        double rowHeight = Math.min(38, height * 0.06);
        double startY = height * 0.47;
        double fontSize = Math.min(22, rowHeight * 0.62);

        Font rowFont = Font.font("SansSerif", FontWeight.BOLD, fontSize);
        // Die Spalte reicht vom Namen bis kurz vor den Punktestand. Ohne diese Grenze
        // schiebt sich ein langer Name unter die Zahl, und beides wird unlesbar.
        double nameWidth = 230;

        for (int i = 0; i < scores.size(); i++) {
            MatchResult s = scores.get(i);
            double y = startY + i * rowHeight;

            gc.setFont(rowFont);
            gc.setFill(Palette.TEXT_DIM);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(s.placement() + ".", width / 2 - 150, y);

            gc.setFill(Palette.player(s.id()));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(Labels.fit(view.nameOf(s.id()), rowFont, nameWidth), width / 2 - 130, y);

            gc.setFill(Palette.TEXT);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setFont(Font.font("SansSerif", fontSize));
            gc.fillText(String.valueOf(s.score()), width / 2 + 170, y);
        }
        gc.setTextAlign(TextAlignment.CENTER);
    }
}
