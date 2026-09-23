package com.btc.hackathon.viewer.ui;

import com.btc.hackathon.viewer.control.CommandAvailability;
import com.btc.hackathon.viewer.model.Slot;
import com.btc.hackathon.viewer.net.ConnectionState;
import com.btc.hackathon.viewer.render.Labels;
import com.btc.hackathon.viewer.render.Palette;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.converter.DefaultStringConverter;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * Die Teilnehmerliste der Lobby.
 *
 * <p>Zeigt dieselbe Farbe wie das Spielfeld, damit sich Zeile und Figur eindeutig
 * zuordnen lassen. Die Gesundheitswerte stehen bewusst mit in der Tabelle: ein Bot, der
 * verstummt ist, muss auffallen, <em>bevor</em> jemand Start drueckt - sonst startet die
 * Partie ohne ihn.
 *
 * <p>Die Liste wird an Ort und Stelle abgeglichen statt ersetzt - sonst spraenge bei
 * zwei Aktualisierungen je Sekunde staendig die Auswahl weg und eine begonnene
 * Namensaenderung braeche ab.
 */
public final class LobbyTableView extends TableView<Slot> {

    /** Obergrenze des Servers fuer einen Namen, in Zeichen. */
    private static final int NAME_LIMIT = 24;

    private final ObservableList<Slot> rows = FXCollections.observableArrayList();

    private volatile ConnectionState connection = ConnectionState.DISCONNECTED;

    /**
     * @param onKick   bekommt die Platznummer, die freigegeben werden soll
     * @param onRename bekommt Platznummer und neuen Namen
     */
    public LobbyTableView(IntConsumer onKick, BiConsumer<Integer, String> onRename) {
        setItems(rows);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setPlaceholder(new Label("Noch kein Zustand vom Server"));
        setEditable(true);
        setFocusTraversable(false);

        getColumns().setAll(List.of(
                colorColumn(),
                nameColumn(onRename),
                healthColumn(),
                kickColumn(onKick)));
    }

    private TableColumn<Slot, Slot> colorColumn() {
        TableColumn<Slot, Slot> column = new TableColumn<>("");
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(c -> new ColorDotCell());
        column.setPrefWidth(30);
        column.setMinWidth(30);
        column.setMaxWidth(30);
        column.setSortable(false);
        column.setReorderable(false);
        column.setEditable(false);
        return column;
    }

    /**
     * Der Name ist bearbeitbar.
     *
     * <p>Bots benennen sich inzwischen selbst; ein {@code rename} von hier ueberschreibt
     * das dauerhaft. Genau dafuer ist die Spalte da: um zwei Bots auseinanderzuhalten,
     * die sich denselben Namen gegeben haben.
     */
    private TableColumn<Slot, String> nameColumn(BiConsumer<Integer, String> onRename) {
        TableColumn<Slot, String> column = new TableColumn<>("Teilnehmer");
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().displayName()));
        // Die Spalte ist schmaler als 24 Zeichen; was abgeschnitten wird, muss wenigstens
        // beim Daraufzeigen lesbar sein.
        column.setCellFactory(c -> new TextFieldTableCell<Slot, String>(new DefaultStringConverter()) {
            @Override
            public void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setTooltip(empty || name == null || name.isBlank() ? null : new Tooltip(name));
            }
        });
        column.setOnEditCommit(event -> {
            Slot slot = event.getRowValue();
            String name = event.getNewValue() == null ? "" : event.getNewValue().trim();
            // Auf dieselben 24 Zeichen kuerzen wie der Server. Sonst steht im
            // Befehlsprotokoll etwas anderes, als danach in der Tabelle erscheint.
            name = Labels.limit(name, NAME_LIMIT);
            if (!name.isEmpty() && !name.equals(slot.displayName())) {
                onRename.accept(slot.id(), name);
            }
        });
        column.setPrefWidth(120);
        column.setSortable(false);
        column.setReorderable(false);
        return column;
    }

    private TableColumn<Slot, Slot> healthColumn() {
        TableColumn<Slot, Slot> column = new TableColumn<>("Zustand");
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(c -> new HealthCell());
        column.setPrefWidth(96);
        column.setSortable(false);
        column.setReorderable(false);
        column.setEditable(false);
        return column;
    }

    private TableColumn<Slot, Slot> kickColumn(IntConsumer onKick) {
        TableColumn<Slot, Slot> column = new TableColumn<>("");
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(c -> new KickCell(onKick, () -> connection));
        column.setPrefWidth(46);
        column.setMinWidth(46);
        column.setMaxWidth(46);
        column.setSortable(false);
        column.setReorderable(false);
        column.setEditable(false);
        return column;
    }

    /**
     * Uebernimmt die aktuelle Aufstellung.
     *
     * <p>Ist sie unveraendert, passiert nichts - das haelt die Tabelle bei zwei
     * Aktualisierungen je Sekunde ruhig.
     */
    public void update(List<Slot> slots, ConnectionState connectionState) {
        this.connection = connectionState;
        if (rows.equals(slots)) {
            return;
        }
        rows.setAll(slots);
    }

    // -------------------------------------------------------------------- Zellen

    private static final class ColorDotCell extends TableCell<Slot, Slot> {

        private final Circle dot = new Circle(7);

        @Override
        protected void updateItem(Slot slot, boolean empty) {
            super.updateItem(slot, empty);
            if (empty || slot == null) {
                setGraphic(null);
                return;
            }
            Color color = Palette.player(slot.id());
            dot.setFill(slot.connected() ? color : color.deriveColor(0, 0.25, 0.5, 1));
            setGraphic(dot);
        }
    }

    private static final class HealthCell extends TableCell<Slot, Slot> {

        @Override
        protected void updateItem(Slot slot, boolean empty) {
            super.updateItem(slot, empty);
            if (empty || slot == null) {
                setText(null);
                setStyle("");
                setTooltip(null);
                return;
            }
            if (!slot.connected()) {
                setText("frei");
                setStyle("-fx-text-fill: " + Palette.toHex(Palette.TEXT_DIM) + ";");
            } else if (slot.stale()) {
                setText(slot.staleMs() == null ? "still" : (slot.staleMs() / 1000) + " s still");
                setStyle("-fx-text-fill: " + Palette.toHex(Palette.WARNING) + "; -fx-font-weight: bold;");
            } else {
                setText(slot.packetsPerSec() + "/s");
                setStyle("-fx-text-fill: " + Palette.toHex(Palette.OK) + ";");
            }
            setTooltip(new Tooltip(details(slot)));
        }

        private static String details(Slot slot) {
            if (!slot.connected()) {
                return "Platz " + slot.id() + " ist frei";
            }
            return "Adresse: " + (slot.addr() == null ? "unbekannt" : slot.addr())
                    + "\nPakete/s: " + slot.packetsPerSec()
                    + String.format("%nVerlust: %.1f %%", slot.lossPct())
                    + "\nStille: " + (slot.staleMs() == null ? "-" : slot.staleMs() + " ms")
                    + "\nZuletzt gesehen: " + (slot.lastSeenTick() == null ? "-" : "Tick " + slot.lastSeenTick());
        }
    }

    private static final class KickCell extends TableCell<Slot, Slot> {

        private final Button button = new Button("✕");
        private final IntConsumer onKick;
        private final java.util.function.Supplier<ConnectionState> connection;

        KickCell(IntConsumer onKick, java.util.function.Supplier<ConnectionState> connection) {
            this.onKick = onKick;
            this.connection = connection;
            button.getStyleClass().add("kick-button");
            button.setTooltip(new Tooltip("Platz freigeben"));
        }

        @Override
        protected void updateItem(Slot slot, boolean empty) {
            super.updateItem(slot, empty);
            if (empty || slot == null || !slot.connected()) {
                setGraphic(null);
                return;
            }
            button.setDisable(!CommandAvailability.canKick(slot, connection.get()));
            button.setOnAction(e -> onKick.accept(slot.id()));
            setGraphic(button);
        }
    }
}
