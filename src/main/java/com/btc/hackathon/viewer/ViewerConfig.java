package com.btc.hackathon.viewer;

import com.btc.hackathon.viewer.net.ServerConnection;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Startparameter des Viewers.
 *
 * <p>Wird aus Kommandozeilenargumenten und System-Properties zusammengesetzt, damit sich
 * am Hackathon-Tag Adresse und Grafikordner umstellen lassen, ohne neu zu uebersetzen.
 *
 * <pre>
 * mvn javafx:run -Djavafx.args="--host=10.0.0.5 --port=8080"
 * </pre>
 *
 * <p><b>Beim Start ueber {@code mvn javafx:run} zaehlen nur die Argumente.</b> Das Plugin
 * startet eine eigene JVM und reicht Mavens System-Properties nicht weiter - ein
 * {@code -Dviewer.host=...} auf der Maven-Kommandozeile bliebe wirkungslos, und der Viewer
 * faende stumm auf {@code 127.0.0.1} zurueck. Die Properties greifen nur, wenn die
 * Anwendung in derselben JVM laeuft - aus der Entwicklungsumgebung heraus oder aus dem
 * eigenstaendigen JAR ({@code java -jar ...-viewer.jar}).
 *
 * <p>Damit ein Tippfehler nicht unbemerkt bleibt, wird die tatsaechlich benutzte Adresse
 * protokolliert und steht zusaetzlich in der Titelzeile des Fensters. Umstellen laesst sie
 * sich waehrend des Betriebs ueber das Adressfeld der Moderationsleiste
 * ({@link #withServer(String, int)}); diese Werte hier sind nur der Startpunkt.
 */
public record ViewerConfig(
        String host,
        int port,
        boolean spectateOnly,
        Path assetDir,
        boolean showPanel) {

    private static final Logger LOG = Logger.getLogger(ViewerConfig.class.getName());

    public static final String DEFAULT_HOST = "127.0.0.1";
    /** {@code web_bind} des Servers. Der UDP-Port 47800 gehoert den Bots, nicht uns. */
    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_ASSET_DIR = "assets";

    /** Baut die Konfiguration aus Argumenten und System-Properties. */
    public static ViewerConfig from(List<String> args) {
        String host = stringValue(args, "host", "viewer.host", DEFAULT_HOST);
        int port = intValue(args, "port", "viewer.port", DEFAULT_PORT);
        boolean spectateOnly = flag(args, "spectate-only", "viewer.spectateOnly");
        String assets = stringValue(args, "assets", "viewer.assets", DEFAULT_ASSET_DIR);
        boolean showPanel = !flag(args, "no-panel", "viewer.noPanel") && !spectateOnly;

        ViewerConfig config = new ViewerConfig(host, port, spectateOnly, Path.of(assets), showPanel);
        LOG.info("Konfiguration: " + config);
        return config;
    }

    /**
     * Der zu verwendende Endpunkt.
     *
     * <p>Standardmaessig {@code /ws/admin}: er liefert alles, was {@code /ws/spectate}
     * liefert, und nimmt zusaetzlich Befehle an - eine Verbindung genuegt also fuer beide
     * Aufgaben. Nur wer ausdruecklich einen reinen Zuschauerschirm will, waehlt den
     * lesenden Endpunkt.
     */
    public URI endpoint() {
        return spectateOnly
                ? ServerConnection.spectateEndpoint(host, port)
                : ServerConnection.adminEndpoint(host, port);
    }

    /**
     * Dieselbe Konfiguration mit anderer Serveradresse.
     *
     * <p>Wird gebraucht, wenn die Adresse zur Laufzeit ueber die Moderationsleiste
     * umgestellt wird: {@code spectateOnly} bleibt erhalten, damit ein reiner
     * Zuschauerschirm auch nach dem Wechsel keine Befehle absetzen kann.
     */
    public ViewerConfig withServer(String newHost, int newPort) {
        return new ViewerConfig(newHost, newPort, spectateOnly, assetDir, showPanel);
    }

    private static String stringValue(List<String> args, String argName, String property,
                                      String fallback) {
        for (String arg : args) {
            String prefix = "--" + argName + "=";
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return System.getProperty(property, fallback);
    }

    private static int intValue(List<String> args, String argName, String property, int fallback) {
        String raw = stringValue(args, argName, property, null);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOG.warning("Ungueltiger Wert fuer " + argName + ": " + raw + " - nutze " + fallback);
            return fallback;
        }
    }

    private static boolean flag(List<String> args, String argName, String property) {
        return args.contains("--" + argName) || Boolean.getBoolean(property);
    }

    @Override
    public String toString() {
        return "Server=" + endpoint()
                + ", Grafiken=" + assetDir.toAbsolutePath()
                + ", Panel=" + (showPanel ? "an" : "aus");
    }
}
