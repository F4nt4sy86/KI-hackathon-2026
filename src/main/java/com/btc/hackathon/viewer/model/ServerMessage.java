package com.btc.hackathon.viewer.model;

/**
 * Alles, was der Server ueber den WebSocket schickt.
 *
 * <p>Sealed, damit ein {@code switch} darueber vom Compiler auf Vollstaendigkeit
 * geprueft wird. Unbekannte {@code type}-Werte werden beim Einlesen verworfen und
 * erreichen diese Hierarchie nicht - der Guide sagt ausdruecklich, dass neue Typen
 * hinzukommen koennen.
 */
public sealed interface ServerMessage
        permits LobbyState, MatchInit, MatchState, MatchEnd, MapPreview, CommandReply {
}
