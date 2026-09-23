package com.btc.hackathon.viewer.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Laedt die Sprites und haelt sie vor.
 *
 * <p>Bewusst aus dem Dateisystem statt aus dem Classpath: so lassen sich Grafiken
 * austauschen, ohne neu zu uebersetzen - am Hackathon-Tag der entscheidende Unterschied.
 *
 * <p>Das Verzeichnis wird gesucht statt fest verdrahtet. Entpackt jemand eine neue
 * Fassung des Sprite-Sets nach {@code assets/}, landet sie erfahrungsgemaess in einem
 * weiteren Unterordner; die Suche nach dem Ordner, der {@code floor_0.png} enthaelt,
 * spart das manuelle Verschieben und ueberlebt auch eine geaenderte Verzeichnistiefe.
 *
 * <p>Fehlt ein Bild, liefern die Zeichenhilfen ein eingefaerbtes Rechteck. Der Viewer
 * laeuft damit auch bei leerem Grafikordner vollstaendig.
 */
public final class AssetRegistry {

    private static final Logger LOG = Logger.getLogger(AssetRegistry.class.getName());

    /** So tief wird unterhalb des Grafikordners nach den Sprites gesucht. */
    private static final int MAX_SEARCH_DEPTH = 5;

    private final Path configuredDir;
    private final Map<String, Image> images = new HashMap<>();
    private Path spriteDir;

    public AssetRegistry(Path configuredDir) {
        this.configuredDir = configuredDir;
    }

    /** Sucht das Sprite-Verzeichnis und liest alle PNG darin ein. */
    public void load() {
        images.clear();
        spriteDir = null;

        if (!Files.isDirectory(configuredDir)) {
            LOG.info("Grafikordner " + configuredDir.toAbsolutePath()
                    + " existiert nicht - es wird mit Platzhaltern gezeichnet");
            return;
        }

        Optional<Path> found = findSpriteDir(configuredDir);
        if (found.isEmpty()) {
            LOG.warning("Unterhalb von " + configuredDir.toAbsolutePath() + " wurde kein Ordner mit "
                    + Sprites.MARKER + ".png gefunden - es wird mit Platzhaltern gezeichnet");
            return;
        }

        spriteDir = found.get();
        loadAllFrom(spriteDir);
        LOG.info(images.size() + " Sprite(s) aus " + spriteDir.toAbsolutePath() + " geladen");
    }

    /**
     * Sucht den Ordner, der die Sprites enthaelt.
     *
     * <p>Erkennungsmerkmal ist {@link Sprites#MARKER} - ein Ordner mit dieser Datei ist
     * der gesuchte. Der Grafikordner selbst wird zuerst geprueft.
     */
    private static Optional<Path> findSpriteDir(Path root) {
        if (Files.isRegularFile(root.resolve(Sprites.MARKER + ".png"))) {
            return Optional.of(root);
        }
        try (Stream<Path> walk = Files.walk(root, MAX_SEARCH_DEPTH)) {
            return walk.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve(Sprites.MARKER + ".png")))
                    .findFirst();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Grafikordner " + root + " liess sich nicht durchsuchen", e);
            return Optional.empty();
        }
    }

    private void loadAllFrom(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> pngs = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .toList();
            for (Path png : pngs) {
                loadOne(png);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Sprites aus " + dir + " liessen sich nicht lesen", e);
        }
    }

    private void loadOne(Path file) {
        String fileName = file.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - ".png".length());
        try (InputStream in = Files.newInputStream(file)) {
            Image image = new Image(in);
            if (image.isError()) {
                LOG.log(Level.WARNING, "Sprite " + file + " liess sich nicht lesen", image.getException());
                return;
            }
            images.put(name, image);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Sprite " + file + " liess sich nicht lesen", e);
        }
    }

    // --------------------------------------------------------------------- Zugriff

    public boolean has(String name) {
        return name != null && images.containsKey(name);
    }

    /** @return das Bild, oder {@code null}, wenn es nicht vorliegt */
    public Image image(String name) {
        return name == null ? null : images.get(name);
    }

    public int loadedCount() {
        return images.size();
    }

    /** {@code true}, solange keine einzige Grafik geladen werden konnte. */
    public boolean isEmpty() {
        return images.isEmpty();
    }

    /** Das tatsaechlich benutzte Verzeichnis, oder {@code null}, wenn keines gefunden wurde. */
    public Path spriteDir() {
        return spriteDir;
    }

    // ---------------------------------------------------------------- Zeichenhilfen

    /**
     * Zeichnet das Sprite, oder ersatzweise ein Rechteck in {@code fallback}.
     *
     * <p>Alle Renderer gehen durch diese Methode, damit die Ersatzdarstellung an genau
     * einer Stelle festgelegt ist.
     */
    public void draw(GraphicsContext gc, String name, double x, double y, double w, double h,
                     Color fallback) {
        Image image = image(name);
        if (image != null) {
            gc.drawImage(image, x, y, w, h);
        } else if (fallback != null) {
            gc.setFill(fallback);
            gc.fillRect(x, y, w, h);
        }
    }

    /** Wie {@link #draw}, aber als Kreis, wenn kein Sprite vorliegt. */
    public void drawRound(GraphicsContext gc, String name, double x, double y, double w, double h,
                          Color fallback) {
        Image image = image(name);
        if (image != null) {
            gc.drawImage(image, x, y, w, h);
        } else if (fallback != null) {
            gc.setFill(fallback);
            gc.fillOval(x, y, w, h);
        }
    }
}
