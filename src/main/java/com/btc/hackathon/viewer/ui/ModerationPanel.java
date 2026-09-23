package com.btc.hackathon.viewer.ui;

import com.btc.hackathon.viewer.control.CommandType;
import com.btc.hackathon.viewer.control.ModerationChannel;
import com.btc.hackathon.viewer.control.ModerationCommand;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.net.ConnectionState;
import com.btc.hackathon.viewer.render.Palette;
import com.btc.hackathon.viewer.state.ViewState;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.net.URI;
import java.util.concurrent.CompletionException;

/**
 * Die Seitenleiste fuer die Moderation.
 *
 * <p>Aufbau von oben nach unten: Verbindungs- und Phasenanzeige, Teilnehmerliste mit
 * Gesundheitswerten, Knopfleiste, Befehlsprotokoll. Die Leiste laesst sich mit F2
 * ausblenden, damit die Spielansicht das ganze Fenster bekommt, wenn sie auf den Beamer
 * geht.
 *
 * <p>Diese Klasse ist die einzige Stelle, die Befehle absetzt. Die Antworten treffen auf
 * einem Netzthread ein und werden hier in den JavaFX-Thread uebergeben - anders als die
 * Spielzustaende, die im Bildtakt abgeholt werden, sind das einzelne, seltene Ereignisse,
 * fuer die {@code Platform.runLater} genau das richtige Mittel ist.
 */
public final class ModerationPanel extends VBox {

    private final ModerationChannel channel;

    private final Circle connectionDot = new Circle(6);
    private final Label connectionLabel = new Label("Server: getrennt");
    private final Label phaseLabel = new Label("-");
    private final Label participantsLabel = new Label("Teilnehmer");
    private final Label mapLabel = new Label("");

    private final LobbyTableView table;
    private final CommandBar commandBar;
    private final ConnectionBar connectionBar;
    private final CommandLogView log = new CommandLogView();

    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;

    public ModerationPanel(ModerationChannel channel, URI endpoint, ConnectionBar.Target target) {
        this.channel = channel;
        this.table = new LobbyTableView(this::kick, this::rename);
        this.commandBar = new CommandBar(type -> send(ModerationCommand.of(type)));
        this.connectionBar = new ConnectionBar(endpoint, target);

        setSpacing(10);
        setPadding(new Insets(12));
        setMinWidth(330);
        setPrefWidth(380);
        setStyle("-fx-background-color: " + Palette.toHex(Palette.BACKGROUND.brighter()) + ";");

        getChildren().addAll(
                title("Moderation"),
                statusRow(),
                connectionBar,
                phaseRow(),
                mapRow(),
                new Separator(),
                participantsLabel,
                table,
                new Separator(),
                commandBar,
                new Separator(),
                sectionLabel("Befehlsprotokoll"),
                log);

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(log, Priority.SOMETIMES);
        log.setPrefHeight(150);
    }

    // ------------------------------------------------------------------- Aufbau

    private Label title(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("SansSerif", FontWeight.BOLD, 18));
        label.setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT) + ";");
        return label;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT_DIM) + "; -fx-font-size: 11px;");
        return label;
    }

    private HBox statusRow() {
        connectionDot.setFill(Palette.WARNING);
        connectionLabel.setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT) + "; -fx-font-size: 12px;");
        HBox row = new HBox(8, connectionDot, connectionLabel);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    private HBox phaseRow() {
        Label caption = new Label("Phase:");
        caption.setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT_DIM) + "; -fx-font-size: 12px;");
        phaseLabel.setStyle("-fx-text-fill: " + Palette.toHex(Palette.ACCENT)
                + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        HBox row = new HBox(8, caption, phaseLabel);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    private HBox mapRow() {
        Label caption = new Label("Karte:");
        caption.setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT_DIM) + "; -fx-font-size: 12px;");
        mapLabel.setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT) + "; -fx-font-size: 12px;");
        HBox row = new HBox(8, caption, mapLabel);
        row.setStyle("-fx-alignment: center-left;");
        return row;
    }

    // ---------------------------------------------------------------- Auffrischen

    /** Meldet einen Wechsel des Verbindungszustands. Darf von jedem Thread gerufen werden. */
    public void onConnectionState(ConnectionState state) {
        Platform.runLater(() -> {
            connectionState = state;
            connectionDot.setFill(state.isConnected() ? Palette.OK
                    : state == ConnectionState.CONNECTING ? Palette.ACCENT : Palette.WARNING);
            connectionLabel.setText("Server: " + state.label());
        });
    }

    /** Traegt eine gewechselte Serveradresse in die Anzeige ein. */
    public void onEndpointChanged(URI endpoint) {
        Platform.runLater(() -> connectionBar.showCurrent(endpoint));
    }

    /**
     * Uebernimmt den aktuellen Zustand. Wird mit niedriger Frequenz gerufen - die Tabelle
     * bei 60 Zustaenden je Sekunde zu erneuern waere Verschwendung und liesse die Bedienung
     * flackern.
     */
    public void refresh(ViewState state) {
        LobbyState lobby = state.lobby();

        phaseLabel.setText(lobby.phase().label() + (lobby.paused() ? " - pausiert" : ""));
        participantsLabel.setText("Teilnehmer (" + lobby.connectedCount() + " von "
                + lobby.maxPlayers() + " verbunden)");
        participantsLabel.setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT) + ";");
        mapLabel.setText(lobby.map().width() + "x" + lobby.map().height()
                + ", " + lobby.map().symmetry()
                + (lobby.map().randomSeed() ? ", zufaellig" : ", Seed " + lobby.map().seed()));

        table.update(lobby.slots(), connectionState);
        commandBar.update(lobby, connectionState);
    }

    // -------------------------------------------------------------------- Befehle

    private void kick(int playerId) {
        send(ModerationCommand.kick(playerId));
    }

    private void rename(int playerId, String name) {
        send(ModerationCommand.rename(playerId, name));
    }

    /**
     * Setzt einen Befehl ab und protokolliert den Ausgang.
     *
     * <p>Es gibt genau drei moegliche Enden, und alle drei landen sichtbar im Protokoll:
     * der Server nimmt an, er lehnt mit Begruendung ab, oder es kommt gar keine Antwort.
     */
    private void send(ModerationCommand command) {
        CommandLogView.Entry pending =
                log.add(command.describe(), CommandLogView.Outcome.SENT, "");

        channel.send(command).whenComplete((reply, error) -> Platform.runLater(() -> {
            if (error != null) {
                log.resolve(pending, CommandLogView.Outcome.FAILED, rootMessage(error));
            } else if (reply.accepted()) {
                log.resolve(pending, CommandLogView.Outcome.ACCEPTED, "");
            } else {
                log.resolve(pending, CommandLogView.Outcome.REJECTED, reply.message());
            }
        }));
    }

    /** Erlaubt Tastenkuerzel auf denselben Weg wie die Knoepfe. */
    public void trigger(CommandType type) {
        send(ModerationCommand.of(type));
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
