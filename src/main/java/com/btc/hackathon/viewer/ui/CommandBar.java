package com.btc.hackathon.viewer.ui;

import com.btc.hackathon.viewer.control.CommandAvailability;
import com.btc.hackathon.viewer.control.CommandType;
import com.btc.hackathon.viewer.model.LobbyPhase;
import com.btc.hackathon.viewer.model.LobbyState;
import com.btc.hackathon.viewer.net.ConnectionState;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Die Knopfleiste der Moderation.
 *
 * <p>Welcher Knopf bedienbar ist, entscheidet ausschliesslich {@link CommandAvailability}
 * - und fuer "Start" letztlich der Server selbst ueber sein {@code can_start}. Ein Knopf,
 * der nichts bewirken wuerde, ist gesperrt und erklaert per Hinweis, warum. Das verhindert
 * Fehlbedienung vor Publikum.
 *
 * <p>Gegensaetzliche Befehlspaare teilen sich einen Knopf: Pausieren/Fortsetzen und
 * Sperren/Oeffnen. Zwei Knoepfe nebeneinander waeren im Ernstfall nur eine
 * Verwechslungsgelegenheit.
 */
public final class CommandBar extends VBox {

    private final Consumer<CommandType> onCommand;

    private final Button startButton = new Button(CommandType.START.label());
    private final Button pauseButton = new Button(CommandType.PAUSE.label());
    private final Button endButton = new Button(CommandType.END.label());
    private final Button resetButton = new Button(CommandType.RESET.label());
    private final Button lockButton = new Button(CommandType.LOCK.label());
    private final Button previewButton = new Button(CommandType.PREVIEW_MAP.label());

    private final Map<CommandType, Button> buttons = new EnumMap<>(CommandType.class);

    /** Welcher Befehl aktuell hinter dem geteilten Knopf steckt. */
    private CommandType pauseAction = CommandType.PAUSE;
    private CommandType lockAction = CommandType.LOCK;

    /**
     * Ob "Zurueck zur Lobby" gerade ein laufendes Match wegraeumen wuerde.
     *
     * <p>Derselbe Knopf ist je nach Phase harmlos oder endgueltig: nach dem Match raeumt
     * er ein Ergebnis weg, das jeder schon gesehen hat; waehrend des Matches beendet er
     * das Spiel und laesst nicht einmal ein Ergebnis stehen. Nur im zweiten Fall gibt es
     * eine Rueckfrage - eine Rueckfrage, die immer kommt, klickt man irgendwann weg,
     * ohne sie zu lesen.
     */
    private boolean resetEndsMatch;

    public CommandBar(Consumer<CommandType> onCommand) {
        this.onCommand = onCommand;

        setSpacing(8);
        setPadding(new Insets(4, 0, 4, 0));

        buttons.put(CommandType.START, startButton);
        buttons.put(CommandType.PAUSE, pauseButton);
        buttons.put(CommandType.END, endButton);
        buttons.put(CommandType.RESET, resetButton);
        buttons.put(CommandType.LOCK, lockButton);
        buttons.put(CommandType.PREVIEW_MAP, previewButton);

        startButton.setOnAction(e -> onCommand.accept(CommandType.START));
        pauseButton.setOnAction(e -> onCommand.accept(pauseAction));
        endButton.setOnAction(e -> confirmEnd());
        resetButton.setOnAction(e -> reset());
        lockButton.setOnAction(e -> onCommand.accept(lockAction));
        previewButton.setOnAction(e -> onCommand.accept(CommandType.PREVIEW_MAP));

        startButton.getStyleClass().add("primary-command");
        endButton.getStyleClass().add("danger-command");

        for (Button b : new Button[] {startButton, pauseButton, endButton, resetButton,
                lockButton, previewButton}) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinHeight(34);
            getChildren().add(b);
        }
    }

    /**
     * Ein Abbruch beendet eine laufende Vorfuehrung und laesst sich nicht rueckgaengig
     * machen - deshalb die Rueckfrage.
     */
    private void confirmEnd() {
        confirm("Match beenden",
                "Laufendes Match wirklich beenden?",
                "Das Match endet ohne Sieger. Das Ergebnis bleibt stehen, bis zurueck zur "
                        + "Lobby gewechselt wird.",
                CommandType.END);
    }

    /**
     * Zurueck zur Lobby - mit Rueckfrage, solange ein Match laeuft.
     *
     * <p>Nach dem Match ist das der gewohnte naechste Schritt; dort waere eine Rueckfrage
     * nur im Weg. Waehrend des Matches ist derselbe Knopf schaerfer als "Beenden": er
     * stoppt das Spiel <em>und</em> raeumt das Ergebnis weg, das sonst stehen bliebe.
     */
    private void reset() {
        if (!resetEndsMatch) {
            onCommand.accept(CommandType.RESET);
            return;
        }
        confirm("Zurueck zur Lobby",
                "Laufendes Match abbrechen und zur Lobby zurueck?",
                "Das Match endet sofort. Anders als bei \"" + CommandType.END.label()
                        + "\" bleibt kein Ergebnis stehen.",
                CommandType.RESET);
    }

    private void confirm(String title, String header, String detail, CommandType command) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(getScene() == null ? null : getScene().getWindow());
        alert.showAndWait()
                .filter(response -> response == ButtonType.OK)
                .ifPresent(response -> onCommand.accept(command));
    }

    /** Schaltet die Knoepfe passend zum aktuellen Zustand frei. */
    public void update(LobbyState lobby, ConnectionState connection) {
        Set<CommandType> available = CommandAvailability.available(lobby, connection);

        // Die geteilten Knoepfe zeigen, was jetzt moeglich ist.
        pauseAction = available.contains(CommandType.RESUME) ? CommandType.RESUME : CommandType.PAUSE;
        pauseButton.setText(pauseAction.label());

        lockAction = lobby != null && lobby.phase() == LobbyPhase.LOCKED
                ? CommandType.UNLOCK
                : CommandType.LOCK;
        lockButton.setText(lockAction.label());

        LobbyPhase phase = lobby == null ? null : lobby.phase();
        resetEndsMatch = phase == LobbyPhase.RUNNING || phase == LobbyPhase.COUNTDOWN;
        // Solange der Knopf ein Match beendet, sieht er auch danach aus.
        resetButton.getStyleClass().remove("danger-command");
        if (resetEndsMatch) {
            resetButton.getStyleClass().add("danger-command");
        }

        apply(startButton, CommandType.START, available, lobby, connection);
        apply(pauseButton, pauseAction, available, lobby, connection);
        apply(endButton, CommandType.END, available, lobby, connection);
        apply(resetButton, CommandType.RESET, available, lobby, connection);
        apply(lockButton, lockAction, available, lobby, connection);
        apply(previewButton, CommandType.PREVIEW_MAP, available, lobby, connection);
    }

    private void apply(Button button, CommandType type, Set<CommandType> available,
                       LobbyState lobby, ConnectionState connection) {
        button.setDisable(!available.contains(type));
        String reason = CommandAvailability.blockedReason(type, lobby, connection);
        button.setTooltip(reason == null ? null : new Tooltip(reason));
    }

    /** Zugriff fuer Tastenkuerzel und Tests. */
    public Button buttonFor(CommandType type) {
        return buttons.get(type);
    }
}
