package com.sk89q.worldguard.neoforge.region;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Inclusive integer-block geometry independent of the Minecraft runtime. */
public final class Region {
    private static final Pattern VALID_ID = Pattern.compile("[a-zA-Z0-9_.,'\\-+/]{1,128}");
    private final String id;
    private final String type;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final List<Point> points;
    private final Map<String, Object> flags = new LinkedHashMap<>();
    private final Domain owners = new Domain();
    private final Domain members = new Domain();
    private int priority;
    private String parent;

    private Region(String id, String type, int x1, int y1, int z1, int x2, int y2, int z2, List<Point> points) {
        this.id = normalizeId(id);
        this.type = type;
        this.minX = Math.min(x1, x2); this.maxX = Math.max(x1, x2);
        this.minY = Math.min(y1, y2); this.maxY = Math.max(y1, y2);
        this.minZ = Math.min(z1, z2); this.maxZ = Math.max(z1, z2);
        this.points = List.copyOf(points);
        if ("__global__".equals(this.id) && !"global".equals(type)) {
            throw new IllegalArgumentException("Only the global region may use the ID __global__");
        }
    }

    public static String normalizeId(String id) {
        Objects.requireNonNull(id, "id");
        if (!VALID_ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid region ID: " + id);
        return id.toLowerCase(Locale.ROOT);
    }

    public static Region cuboid(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Region(id, "cuboid", x1, y1, z1, x2, y2, z2, List.of());
    }

    public static Region polygon(String id, List<Point> points, int minY, int maxY) {
        Objects.requireNonNull(points, "points");
        // Original files may contain degenerate or self-crossing outlines.
        // Preserve their even/odd containment when importing existing protection.
        if (points.isEmpty()) throw new IllegalArgumentException("A polygon requires at least one point");
        int x1 = points.stream().mapToInt(Point::x).min().orElseThrow();
        int x2 = points.stream().mapToInt(Point::x).max().orElseThrow();
        int z1 = points.stream().mapToInt(Point::z).min().orElseThrow();
        int z2 = points.stream().mapToInt(Point::z).max().orElseThrow();
        return new Region(id, "poly2d", x1, minY, z1, x2, maxY, z2, points);
    }

    public static Region global(String id) {
        return new Region(id, "global", 0, 0, 0, 0, 0, 0, List.of());
    }

    public String id() { return id; }
    public String type() { return type; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
    public List<Point> points() { return points; }
    public int priority() { return priority; }
    public void priority(int priority) { this.priority = priority; }
    public String parent() { return parent; }
    public void parent(String parent) {
        String next = parent == null || parent.isBlank() ? null : normalizeId(parent);
        if (id.equals(next)) throw new IllegalArgumentException("A region cannot inherit itself");
        this.parent = next;
    }
    public Map<String, Object> flags() { return flags; }
    public Domain owners() { return owners; }
    public Domain members() { return members; }

    public boolean contains(int x, int y, int z) {
        if (type.equals("global")) return false;
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return false;
        return !type.equals("poly2d") || contains2d(x, z);
    }

    private boolean contains2d(int x, int z) {
        Point p = new Point(x, z);
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Point a = points.get(j), b = points.get(i);
            if (onSegment(a, b, p)) return true;
            if ((a.z > z) != (b.z > z)
                    && x < ((double) b.x - a.x) * ((double) z - a.z) / ((double) b.z - a.z) + a.x) {
                inside = !inside;
            }
        }
        return inside;
    }

    public boolean intersects(Region other) {
        Objects.requireNonNull(other, "other");
        if (maxX < other.minX || minX > other.maxX || maxY < other.minY || minY > other.maxY
                || maxZ < other.minZ || minZ > other.maxZ) return false;
        if (type.equals("global") || other.type.equals("global")) return false;
        List<Point> a = footprint(), b = other.footprint();
        for (Point point : a) if (other.contains(point.x, Math.max(minY, other.minY), point.z)) return true;
        for (Point point : b) if (contains(point.x, Math.max(minY, other.minY), point.z)) return true;
        for (int i = 0; i < a.size(); i++) {
            for (int j = 0; j < b.size(); j++) {
                if (segmentsIntersect(a.get(i), a.get((i + 1) % a.size()), b.get(j), b.get((j + 1) % b.size()))) return true;
            }
        }
        return false;
    }

    private List<Point> footprint() {
        return type.equals("poly2d") ? points : List.of(new Point(minX, minZ), new Point(maxX, minZ),
                new Point(maxX, maxZ), new Point(minX, maxZ));
    }

    /** Match WorldGuard's cuboid volume and zero-volume polygon/global convention. */
    public long volume() {
        if (type.equals("global") || type.equals("poly2d")) return 0;
        BigInteger columns = BigInteger.valueOf((long) maxX - minX + 1).multiply(BigInteger.valueOf((long) maxZ - minZ + 1));
        BigInteger volume = columns.multiply(BigInteger.valueOf((long) maxY - minY + 1));
        return volume.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    public Region copy() {
        Region result = new Region(id, type, minX, minY, minZ, maxX, maxY, maxZ, points);
        result.priority = priority;
        result.parent = parent;
        flags.forEach((key, value) -> result.flags.put(key, copyValue(value)));
        result.owners.uuids().addAll(owners.uuids()); result.owners.players().addAll(owners.players()); result.owners.groups().addAll(owners.groups());
        result.members.uuids().addAll(members.uuids()); result.members.players().addAll(members.players()); result.members.groups().addAll(members.groups());
        return result;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, child) -> copy.put(String.valueOf(key), copyValue(child)));
            return copy;
        }
        if (value instanceof java.util.Set<?> set) {
            java.util.Set<Object> copy = new java.util.LinkedHashSet<>();
            set.forEach(child -> copy.add(copyValue(child))); return copy;
        }
        if (value instanceof List<?> list) return new ArrayList<>(list.stream().map(Region::copyValue).toList());
        return value;
    }

    private static int orientation(Point a, Point b, Point c) {
        return BigInteger.valueOf((long)b.x - a.x).multiply(BigInteger.valueOf((long)c.z - a.z))
                .subtract(BigInteger.valueOf((long)b.z - a.z).multiply(BigInteger.valueOf((long)c.x - a.x))).signum();
    }

    private static boolean onSegment(Point a, Point b, Point c) {
        return orientation(a, b, c) == 0 && c.x >= Math.min(a.x, b.x) && c.x <= Math.max(a.x, b.x)
                && c.z >= Math.min(a.z, b.z) && c.z <= Math.max(a.z, b.z);
    }

    private static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        int o1 = orientation(a, b, c), o2 = orientation(a, b, d), o3 = orientation(c, d, a), o4 = orientation(c, d, b);
        return o1 != o2 && o3 != o4 || o1 == 0 && onSegment(a, b, c) || o2 == 0 && onSegment(a, b, d)
                || o3 == 0 && onSegment(c, d, a) || o4 == 0 && onSegment(c, d, b);
    }

    public record Point(int x, int z) { }
}
