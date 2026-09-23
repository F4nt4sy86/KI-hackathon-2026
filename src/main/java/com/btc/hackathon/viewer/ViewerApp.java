package com.btc.hackathon.viewer;

import com.btc.hackathon.viewer.control.ModerationChannel;
import com.btc.hackathon.viewer.model.CommandReply;
import com.btc.hackathon.viewer.model.MapPreview;
import com.btc.hackathon.viewer.model.ServerMessage;
import com.btc.hackathon.viewer.net.ConnectionState;
import com.btc.hackathon.viewer.net.ServerConnection;
import com.btc.hackathon.viewer.render.AssetRegistry;
import com.btc.hackathon.viewer.render.Palette;
import com.btc.hackathon.viewer.render.RenderLoop;
import com.btc.hackathon.viewer.state.ViewStateStore;
import com.btc.hackathon.viewer.ui.ModerationPanel;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URI;
import java.net.URL;
import java.util.logging.Logger;

/**
 * Verdrahtet Verbindung, Zustand und Oberflaeche.
 *
 * <p>Es gibt genau eine Verbindung zum Server - einen WebSocket auf {@code /ws/admin}.
 * Ueber ihn kommen Lobby, Spielzustaende und Ergebnisse herein und gehen die
 * Moderationsbefehle hinaus. Der UDP-Port des Servers gehoert den Bots; diese Anwendung
 * fasst ihn nicht an.
 *
 * <p>Hier wird auch entschieden, wohin eine eingehende Nachricht geht: Antworten auf
 * Befehle an den Steuerkanal, alles andere in den Zustandsspeicher.
 */
public final class ViewerApp extends Application {

    private static final Logger LOG = Logger.getLogger(ViewerApp.class.getName());

    /** Die Seitenleiste braucht keine 60 Aktualisierungen pro Sekunde. */
    private static final Duration PANEL_REFRESH = Duration.millis(200);

    private ViewerConfig config;
    private ViewStateStore store;
    private ServerConnection connection;
    private ModerationChannel moderation;
    private RenderLoop renderLoop;
    private Timeline panelRefresh;

    private SplitPane splitPane;
    private ModerationPanel panel;
    private Stage stage;

    @Override
    public void init() {
        config = ViewerConfig.from(getParameters().getRaw());
        store = new ViewStateStore();
    }

    @Override
    public void start(Stage stage) {
        AssetRegistry assets = new AssetRegistry(config.assetDir());
        assets.load();

        connection = new ServerConnection(config.endpoint(), this::onMessage, this::onConnectionState);
        moderation = new ModerationChannel(connection);

        Canvas canvas = new Canvas();
        StackPane canvasHolder = new StackPane(canvas);
        canvasHolder.setStyle("-fx-background-color: " + Palette.toHex(Palette.BACKGROUND) + ";");
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());

        panel = new ModerationPanel(moderation, config.endpoint(), this::switchServer);

        splitPane = new SplitPane(canvasHolder);
        SplitPane.setResizableWithParent(panel, false);
        if (config.showPanel()) {
            showPanel(true);
        }

        Scene scene = new Scene(splitPane, 1400, 860);
        applyStylesheet(scene);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F2) {
                showPanel(!splitPane.getItems().contains(panel));
            } else if (event.getCode() == KeyCode.F3) {
                renderLoop.toggleDebug();
            } else if (event.getCode() == KeyCode.F5) {
                // Eine angezeigte Kartenvorschau wieder wegraeumen.
                store.clearPreview();
            } else if (event.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
            }
        });

        this.stage = stage;
        stage.setTitle("Bomberman Viewer - " + config.endpoint());
        stage.setScene(scene);
        stage.show();

        renderLoop = new RenderLoop(canvas, store, assets, connection);
        renderLoop.start();

        panelRefresh = new Timeline(new KeyFrame(PANEL_REFRESH, e -> panel.refresh(store.current())));
        panelRefresh.setCycleCount(Animation.INDEFINITE);
        panelRefresh.play();

        connection.start();
    }

    /**
     * Verteilt eine eingegangene Nachricht.
     *
     * <p>Laeuft auf dem Thread des WebSocket-Clients, nicht auf dem JavaFX-Thread. Beide
     * Ziele sind darauf ausgelegt: der Zustandsspeicher ist eine atomare Referenz, der
     * Steuerkanal wechselt selbst in den FX-Thread, bevor er die Oberflaeche anfasst.
     */
    private void onMessage(ServerMessage message) {
        if (message instanceof CommandReply reply) {
            moderation.onReply(reply);
            return;
        }
        store.accept(message);
        if (message instanceof MapPreview) {
            // Die Kartenvorschau ist zugleich die Antwort auf preview_map - der Server
            // schickt dafuer kein ack.
            moderation.onPreview();
        }
    }

    /**
     * Stellt den Viewer im laufenden Betrieb auf einen anderen Server um.
     *
     * <p>Der Zustandsspeicher wird dabei geleert. Sonst stuende die Lobby des alten
     * Servers noch am Schirm, bis die erste Nachricht des neuen eintrifft - und falls der
     * neue gar nicht antwortet, bliebe sie fuer immer stehen und taeuschte eine
     * Verbindung vor, die es nicht gibt.
     */
    private void switchServer(String host, int port) {
        config = config.withServer(host, port);
        URI endpoint = config.endpoint();

        moderation.failAllPending(new IllegalStateException("Serverwechsel"));
        store.reset();
        connection.connectTo(endpoint);

        panel.onEndpointChanged(endpoint);
        stage.setTitle("Bomberman Viewer - " + endpoint);
        LOG.info("Serverwechsel auf " + endpoint);
    }

    private void onConnectionState(ConnectionState state) {
        if (!state.isConnected()) {
            // Offene Befehle koennen nicht mehr beantwortet werden.
            moderation.failAllPending(new IllegalStateException("Verbindung zum Server verloren"));
        }
        if (panel != null) {
            panel.onConnectionState(state);
        }
    }

    /** Blendet die Seitenleiste ein oder aus (F2). */
    private void showPanel(boolean visible) {
        if (visible) {
            if (!splitPane.getItems().contains(panel)) {
                splitPane.getItems().add(panel);
                splitPane.setDividerPositions(0.73);
            }
        } else {
            splitPane.getItems().remove(panel);
        }
    }

    private void applyStylesheet(Scene scene) {
        URL css = ViewerApp.class.getResource("/viewer.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    @Override
    public void stop() {
        if (panelRefresh != null) {
            panelRefresh.stop();
        }
        if (renderLoop != null) {
            renderLoop.stop();
        }
        if (connection != null) {
            connection.close();
        }
        LOG.info("Viewer beendet");
    }

    /** Einstieg fuer den Start ueber {@link ViewerLauncher}. */
    public static void run(String[] args) {
        launch(args);
    }
}
