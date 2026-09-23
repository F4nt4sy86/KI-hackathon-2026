package com.btc.hackathon.viewer.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ein abzusetzender Moderationsbefehl.
 *
 * <p>Auf der Leitung ist das ein JSON-Objekt mit einem {@code cmd}-Feld und den
 * Zusatzangaben direkt daneben, nicht verschachtelt:
 * {@code {"cmd":"kick","id":2}}.
 */
public record ModerationCommand(CommandType type, Map<String, Object> params) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ModerationCommand {
        params = Map.copyOf(params);
    }

    // -------------------------------------------------------------- Erzeugung

    public static ModerationCommand of(CommandType type) {
        return new ModerationCommand(type, Map.of());
    }

    public static ModerationCommand kick(int playerId) {
        return new ModerationCommand(CommandType.KICK, Map.of("id", playerId));
    }

    public static ModerationCommand rename(int playerId, String name) {
        return new ModerationCommand(CommandType.RENAME,
                Map.of("id", playerId, "name", name == null ? "" : name));
    }

    /**
     * Einstellungen fuer das naechste Match. Alle Angaben sind freiwillig; was
     * {@code null} ist, laesst der Server unveraendert.
     *
     * <p>Breite und Hoehe erzwingt der Server auf ungerade und begrenzt sie auf 7 bis 63 -
     * anzuzeigen ist deshalb, was der Server zurueckmeldet, nicht was eingegeben wurde.
     */
    public static ModerationCommand configureMap(Integer width, Integer height, Double density,
                                                 String symmetry, Long seed) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (width != null) {
            params.put("width", width);
        }
        if (height != null) {
            params.put("height", height);
        }
        if (density != null) {
            params.put("density", density);
        }
        if (symmetry != null) {
            params.put("symmetry", symmetry);
        }
        if (seed != null) {
            params.put("seed", seed);
        }
        return new ModerationCommand(CommandType.CONFIGURE_MAP, params);
    }

    // ---------------------------------------------------------------- Ausgabe

    /** Das JSON, das ueber den WebSocket geht. */
    public String toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("cmd", type.wire());
        params.forEach((key, value) -> {
            switch (value) {
                case Integer i -> node.put(key, i);
                case Long l -> node.put(key, l);
                case Double d -> node.put(key, d);
                case Boolean b -> node.put(key, b);
                case null -> node.putNull(key);
                default -> node.put(key, value.toString());
            }
        });
        return node.toString();
    }

    /** Kurzbeschreibung fuer das Befehlsprotokoll der Oberflaeche. */
    public String describe() {
        if (params.isEmpty()) {
            return type.label();
        }
        StringBuilder sb = new StringBuilder(type.label()).append(" (");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.append(')').toString();
    }
}
