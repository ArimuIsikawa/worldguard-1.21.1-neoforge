package com.sk89q.worldguard.neoforge.region;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegionGeometryTest {
    @Test void cuboidNormalizesCornersAndIncludesBoundaryBlocks() {
        Region region = Region.cuboid("TEST", 10, 10, 10, -10, -10, -10);
        assertEquals("test", region.id());
        assertTrue(region.contains(-10, -10, -10)); assertTrue(region.contains(10, 10, 10));
        assertFalse(region.contains(11, 0, 0));
        assertEquals(21L * 21 * 21, region.volume());
    }

    @Test void polygonIncludesEdgesVerticesAndHeightBoundaries() {
        Region triangle = Region.polygon("triangle", List.of(new Region.Point(0, 0), new Region.Point(4, 0), new Region.Point(0, 4)), 2, -2);
        assertTrue(triangle.contains(0, -2, 0)); assertTrue(triangle.contains(2, 2, 2));
        assertFalse(triangle.contains(3, 0, 3)); assertFalse(triangle.contains(1, 3, 1));
        assertEquals(0, triangle.volume());
    }

    @Test void concavePolygonDoesNotProtectItsBoundingBoxCutout() {
        Region polygon = Region.polygon("l", List.of(new Region.Point(0, 0), new Region.Point(5, 0), new Region.Point(5, 2),
                new Region.Point(2, 2), new Region.Point(2, 5), new Region.Point(0, 5)), 0, 10);
        assertTrue(polygon.contains(1, 5, 4)); assertFalse(polygon.contains(4, 5, 4));
        assertFalse(polygon.intersects(Region.cuboid("box", 3, 0, 3, 4, 10, 4)));
        assertTrue(polygon.intersects(Region.cuboid("edge", 2, 0, 3, 4, 10, 4)));
    }

    @Test void intersectingEdgesAreDetectedEvenWhenNeitherPolygonContainsVertices() {
        Region horizontal = Region.cuboid("horizontal", -5, 0, -1, 5, 10, 1);
        Region vertical = Region.polygon("vertical", List.of(new Region.Point(-1, -5), new Region.Point(1, -5), new Region.Point(1, 5), new Region.Point(-1, 5)), 0, 10);
        assertTrue(horizontal.intersects(vertical)); assertTrue(vertical.intersects(horizontal));
        assertFalse(horizontal.intersects(Region.cuboid("above", -5, 11, -1, 5, 12, 1)));
    }

    @Test void globalAndHugeVolumesDoNotOverflow() {
        Region global = Region.global("__global__");
        assertFalse(global.contains(Integer.MIN_VALUE, 0, Integer.MAX_VALUE));
        assertEquals(0, global.volume());
        assertEquals(Long.MAX_VALUE, Region.cuboid("huge", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE).volume());
    }

    @Test void invalidPolygonAndRegionIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Region.cuboid("bad space", 0, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Region.polygon("empty", List.of(), 0, 1));
    }

    @Test void originalDegenerateAndCrossingPolygonsKeepTheirProtectedOutline() {
        Region line=Region.polygon("line", List.of(new Region.Point(0,0),new Region.Point(1,1),new Region.Point(2,2)),0,1);
        assertTrue(line.contains(1,1,1)); assertFalse(line.contains(1,1,2));
        Region crossed=Region.polygon("crossed", List.of(new Region.Point(0,0),new Region.Point(4,4),new Region.Point(4,0),new Region.Point(0,4)),0,1);
        assertTrue(crossed.contains(2,0,2)); assertFalse(crossed.contains(2,0,1));
        assertEquals(0,crossed.volume());
    }
}
