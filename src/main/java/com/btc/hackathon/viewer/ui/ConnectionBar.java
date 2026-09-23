package com.btc.hackathon.viewer.ui;

import com.btc.hackathon.viewer.render.Palette;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URI;

/**
 * Adresse des Servers, zur Laufzeit umstellbar.
 *
 * <p>Ohne das muesste man den Viewer neu starten, sobald der Server auf einem anderen
 * Rechner laeuft als gedacht - genau die Situation, die am Hackathon-Tag eintritt, wenn
 * das Fenster schon auf dem Beamer liegt. Der Wechsel bricht die bestehende Verbindung ab
 * und baut sofort die neue auf; der Zustandsspeicher fuellt sich mit der ersten Nachricht
 * der neuen Gegenstelle von selbst wieder.
 *
 * <p>Geprueft wird hier nur, was ohne Netzzugriff feststeht: ein nicht leerer Name und ein
 * Port im gueltigen Bereich. Ob unter der Adresse wirklich ein Server steht, beantwortet
 * die Verbindungsanzeige - eine Vorabpruefung waere nur eine zweite Wahrheit, die der
 * ersten widersprechen kann.
 */
public final class ConnectionBar extends VBox {

    /** Was beim Druck auf "Verbinden" passieren soll. */
    @FunctionalInterface
    public interface Target {
        void connect(String host, int port);
    }

    private final Target target;

    private final TextField hostField = new TextField();
    private final TextField portField = new TextField();
    private final Button connectButton = new Button("Verbinden");
    private final Label currentLabel = new Label();

    public ConnectionBar(URI initial, Target target) {
        this.target = target;

        setSpacing(4);
        setPadding(new Insets(2, 0, 2, 0));

        hostField.setPromptText("Server, z.B. 10.0.0.5");
        hostField.setTooltip(new Tooltip("Adresse oder Rechnername des Arena-Servers"));
        HBox.setHgrow(hostField, Priority.ALWAYS);

        portField.setPromptText("Port");
        portField.setPrefWidth(70);
        portField.setMaxWidth(70);
        portField.setTooltip(new Tooltip(
                "Webport des Servers (web_bind), nicht der UDP-Port 47800 der Bots"));

        connectButton.setMinHeight(28);
        connectButton.setOnAction(e -> apply());
        // Enter in einem der Felder tut dasselbe wie der Knopf.
        hostField.setOnAction(e -> apply());
        portField.setOnAction(e -> apply());

        currentLabel.setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT_DIM)
                + "; -fx-font-size: 10px;");

        HBox row = new HBox(6, hostField, portField, connectButton);
        row.setStyle("-fx-alignment: center-left;");

        getChildren().addAll(row, currentLabel);

        showCurrent(initial);
    }

    /**
     * Uebernimmt die Eingabe, sofern sie Hand und Fuss hat.
     *
     * <p>Ein ungueltiger Wert wird markiert und nicht abgeschickt. Die Alternative -
     * stillschweigend auf einen Vorgabewert zurueckzufallen - waere schlimmer: man
     * bekaeme eine Verbindung, nur nicht die gewuenschte, und suchte den Fehler
     * anderswo.
     */
    private void apply() {
        String host = hostField.getText() == null ? "" : hostField.getText().trim();
        String rawPort = portField.getText() == null ? "" : portField.getText().trim();

        boolean hostOk = !host.isEmpty() && host.indexOf(' ') < 0;
        mark(hostField, hostOk);

        int port = -1;
        try {
            port = Integer.parseInt(rawPort);
        } catch (NumberFormatException ignored) {
            // bleibt ungueltig
        }
        boolean portOk = port >= 1 && port <= 65535;
        mark(portField, portOk);

        if (hostOk && portOk) {
            target.connect(host, port);
        }
    }

    private static void mark(TextField field, boolean valid) {
        field.setStyle(valid ? "" : "-fx-border-color: " + Palette.toHex(Palette.WARNING)
                + "; -fx-border-width: 1.5;");
    }

    /** Traegt die tatsaechlich benutzte Adresse ein - auch die aus den Startparametern. */
    public void showCurrent(URI uri) {
        hostField.setText(uri.getHost());
        portField.setText(String.valueOf(uri.getPort()));
        mark(hostField, true);
        mark(portField, true);
        currentLabel.setText("verbindet mit " + uri);
    }
}
