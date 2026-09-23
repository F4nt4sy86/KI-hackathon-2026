package com.btc.hackathon.viewer.model;

/** Eine liegende Bombe. {@code fuse} ist die Restzeit in Ticks. */
public record Bomb(int id, int owner, int x, int y, int fuse) {
}
