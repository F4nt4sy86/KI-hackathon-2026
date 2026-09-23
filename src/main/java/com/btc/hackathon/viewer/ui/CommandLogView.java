package com.btc.hackathon.viewer.ui;

import com.btc.hackathon.viewer.render.Palette;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Das Protokoll der abgesetzten Befehle.
 *
 * <p>Wenn im Ernstfall etwas nicht reagiert, ist hier sofort ablesbar, ob der Befehl
 * ueberhaupt rausging und was der Server geantwortet hat. Der Server begruendet jede
 * Ablehnung; ohne diese Anzeige bliebe die Begruendung ungesehen.
 */
public final class CommandLogView extends ListView<CommandLogView.Entry> {

    /** So viele Zeilen bleiben stehen, aeltere fallen hinten raus. */
    private static final int MAX_ENTRIES = 60;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Ausgang eines Befehls. */
    public enum Outcome {
        /** Abgesetzt, Antwort steht noch aus. */
        SENT,
        ACCEPTED,
        /** Der Server hat fachlich abgelehnt und begruendet. */
        REJECTED,
        /** Gar keine Antwort, oder die Verbindung stand nicht. */
        FAILED
    }

    /** Eine Zeile des Protokolls. */
    public record Entry(LocalTime time, String label, Outcome outcome, String detail) {
    }

    private final ObservableList<Entry> entries = FXCollections.observableArrayList();

    public CommandLogView() {
        setItems(entries);
        setFocusTraversable(false);
        setPlaceholder(new Label("Noch kein Befehl abgesetzt"));
        setCellFactory(list -> new EntryCell());
    }

    public Entry add(String label, Outcome outcome, String detail) {
        Entry entry = new Entry(LocalTime.now(), label, outcome, detail == null ? "" : detail);
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        scrollTo(0);
        return entry;
    }

    /**
     * Ersetzt eine noch offene Zeile durch ihr Ergebnis.
     *
     * <p>So bleibt es bei einer Zeile je Befehl, statt "abgesetzt" und "beantwortet"
     * getrennt zu protokollieren. Ist die Zeile bereits herausgefallen, wird nichts getan.
     */
    public void resolve(Entry pending, Outcome outcome, String detail) {
        int index = entries.indexOf(pending);
        if (index < 0) {
            return;
        }
        entries.set(index, new Entry(pending.time(), pending.label(), outcome,
                detail == null ? "" : detail));
    }

    private static final class EntryCell extends ListCell<Entry> {

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setStyle("");
                return;
            }
            String marker = switch (entry.outcome()) {
                case SENT -> "…";
                case ACCEPTED -> "OK";
                case REJECTED -> "abgelehnt";
                case FAILED -> "Fehler";
            };
            String text = entry.time().format(TIME) + "  " + entry.label() + "  " + marker;
            if (!entry.detail().isBlank()) {
                text += " - " + entry.detail();
            }
            setText(text);

            String color = switch (entry.outcome()) {
                case SENT -> Palette.toHex(Palette.TEXT_DIM);
                case ACCEPTED -> Palette.toHex(Palette.OK);
                case REJECTED, FAILED -> Palette.toHex(Palette.WARNING);
            };
            setStyle("-fx-text-fill: " + color + "; -fx-font-family: 'Monospaced'; -fx-font-size: 11px;");
        }
    }
}
