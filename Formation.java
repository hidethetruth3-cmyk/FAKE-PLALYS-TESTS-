package com.fakeplayermod;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Slot layouts, placed in front of the player and aligned to the way the player faces. */
public final class Formation {
    public enum Shape { RANDOM, SQUARE, LINES_FORWARD, LINES_FACING }

    public record Slot(double x, double z, float yaw) {}

    private static final double SPACING = 1.5;
    private static final double LINE_GAP = 6.0;

    public static List<Slot> slots(Shape shape, int n, Vec3 origin, float playerYaw) {
        int q = Math.floorMod(Math.round(playerYaw / 90.0F), 4); // 0 south, 1 west, 2 north, 3 east
        double[][] fwd = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        double fx = fwd[q][0], fz = fwd[q][1];
        double rx = -fz, rz = fx;
        float yawF = q * 90.0F;
        List<Slot> out = new ArrayList<>();

        if (shape == Shape.SQUARE) {
            int cols = (int) Math.ceil(Math.sqrt(n));
            int rows = (int) Math.ceil(n / (double) cols);
            double ahead = 6.0 + rows * SPACING / 2.0;
            for (int i = 0; i < n; i++) {
                int r = i / cols, c = i % cols;
                int inRow = Math.min(cols, n - r * cols);
                double lat = (c - (inRow - 1) / 2.0) * SPACING;
                double fo = ahead + (r - (rows - 1) / 2.0) * SPACING;
                out.add(new Slot(origin.x + fx * fo + rx * lat, origin.z + fz * fo + rz * lat, yawF));
            }
        } else {
            boolean facing = shape == Shape.LINES_FACING;
            int rowA = (n + 1) / 2;
            double ahead = 6.0 + (facing ? LINE_GAP : SPACING) / 2.0;
            for (int i = 0; i < n; i++) {
                int k = i < rowA ? 0 : 1;
                int idx = k == 0 ? i : i - rowA;
                int inRow = k == 0 ? rowA : n - rowA;
                double lat = (idx - (inRow - 1) / 2.0) * SPACING;
                double off = facing ? (k == 0 ? -LINE_GAP / 2 : LINE_GAP / 2) : (k - 0.5) * SPACING;
                float yaw = facing && k == 1 ? yawF + 180.0F : yawF;
                double fo = ahead + off;
                out.add(new Slot(origin.x + fx * fo + rx * lat, origin.z + fz * fo + rz * lat, yaw));
            }
        }
        return out;
    }
    /**
     * Two sides facing each other with an aisle between them, running forward from the player.
     * left/right = number of bots on that side, spacing = blocks between bots (and between rows),
     * rows = parallel lines per side, gap = empty blocks between the two inner lines (0 = no gap).
     */
    public static List<Slot> aisle(int left, int right, double spacing, int rows, int gap, Vec3 origin, float playerYaw) {
        int q = Math.floorMod(Math.round(playerYaw / 90.0F), 4);
        double[][] fwd = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        double fx = fwd[q][0], fz = fwd[q][1];
        double rx = -fz, rz = fx; // your right-hand side
        double start = 3.0;
        double inner = (gap + 1) / 2.0;
        float yawLeftSide = yawOf(rx, rz);    // left-side bots look toward the aisle (to the right)
        float yawRightSide = yawOf(-rx, -rz); // right-side bots look back toward the left
        List<Slot> out = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            int count = side == 0 ? left : right;
            double sign = side == 0 ? -1.0 : 1.0;
            float yaw = side == 0 ? yawLeftSide : yawRightSide;
            for (int j = 0; j < count; j++) {
                int row = j % rows;
                int pos = j / rows;
                double lat = sign * (inner + row * spacing);
                double fo = start + pos * spacing;
                out.add(new Slot(origin.x + fx * fo + rx * lat, origin.z + fz * fo + rz * lat, yaw));
            }
        }
        return out;
    }

    private static float yawOf(double dx, double dz) {
        return (float) (Math.atan2(-dx, dz) * 57.29577951308232);
    }
    private Formation() {}
}
