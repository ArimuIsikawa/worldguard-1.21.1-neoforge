package com.sk89q.worldguard.neoforge.region;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegionRepositoryTest {
    @TempDir Path temporary;
    private static final String DIMENSION = "minecraft:overworld";
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Path source(String yaml) throws IOException {
        Path path = temporary.resolve("spigot-regions.yml"); Files.writeString(path, yaml); return path;
    }

    @Test void importsAllShapesFlagsDomainsAndInheritanceFromWorldGuardYaml() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        Path source = source("""
                regions:
                    __global__:
                        type: global
                        priority: 0
                        flags: {pvp: deny}
                        owners: {}
                        members: {}
                    spawn:
                        type: cuboid
                        min: {x: -10, y: -64, z: -10}
                        max: {x: 10, y: 319, z: 10}
                        priority: 5
                        flags:
                            greeting: Welcome!
                            entry: deny
                            entry-group: non-members
                            heal-delay: 5
                            heal-min-health: 2.5
                            notify-enter: true
                            deny-spawn: [zombie, minecraft:skeleton]
                            teleport: {world: world, x: 0.5, y: 65, z: 0.5, yaw: 90, pitch: 0}
                            extension: {nested: [one, two], state: retained}
                        owners:
                            unique-ids: [00000000-0000-0000-0000-000000000001]
                            players: [LegacyAlice]
                            groups: [admins]
                        members: {players: [Bob], groups: [builders]}
                    polygon:
                        type: poly2d
                        min-y: 0
                        max-y: 100
                        points: [{x: 0, z: 0}, {x: 10, z: 0}, {x: 0, z: 10}]
                        priority: 10
                        parent: spawn
                        flags: {pvp: allow}
                        owners: {}
                        members: {}
                """);
        String original = Files.readString(source);
        assertEquals(3, repository.importYaml(source, DIMENSION, false));
        Map<String, Region> regions = repository.regions(DIMENSION);
        Region spawn = regions.get("spawn");
        assertEquals(ALICE, spawn.owners().uuids().iterator().next());
        assertEquals(Set.of("admins"), spawn.owners().groups());
        assertEquals(Set.of("builders"), spawn.members().groups());
        assertEquals("nonmembers", spawn.flags().get("entry-group"));
        assertEquals(5, spawn.flags().get("heal-delay"));
        assertEquals(2.5, spawn.flags().get("heal-min-health"));
        assertEquals("spawn", regions.get("polygon").parent());
        assertEquals(original, Files.readString(source));
        repository.save(DIMENSION);
        RegionRepository reloaded = new RegionRepository(temporary.resolve("native"));
        Region again = reloaded.regions(DIMENSION).get("spawn");
        assertEquals(spawn.flags(), again.flags());
        assertEquals(spawn.owners().players(), again.owners().players());
        assertEquals(spawn.owners().groups(), again.owners().groups());
    }

    @Test void nonReplacingImportPreservesExistingRegionsAndConflictingIds() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        Region existing = Region.cuboid("existing", 0, 0, 0, 1, 1, 1);
        Region conflict = Region.cuboid("plot", 100, 0, 100, 101, 1, 101);
        repository.regions(DIMENSION).put(existing.id(), existing);
        repository.regions(DIMENSION).put(conflict.id(), conflict);
        Path source = source("""
                regions:
                    plot:
                        type: cuboid
                        min: {x: 0, y: 0, z: 0}
                        max: {x: 10, y: 10, z: 10}
                    fresh:
                        type: cuboid
                        min: {x: 0, y: 0, z: 0}
                        max: {x: 10, y: 10, z: 10}
                """);
        assertEquals(1, repository.importYaml(source, DIMENSION, false));
        assertSame(conflict, repository.regions(DIMENSION).get("plot"));
        assertEquals(3, repository.regions(DIMENSION).size());
        assertEquals(2, repository.importYaml(source, DIMENSION, true));
        assertFalse(repository.regions(DIMENSION).containsKey("existing"));
        assertEquals(0, repository.regions(DIMENSION).get("plot").minX());
    }

    @Test void invalidReloadKeepsLoadedRegionsAndNeverRewritesBadFile() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        Region plot = Region.cuboid("plot", 0, 0, 0, 1, 1, 1);
        repository.regions(DIMENSION).put("plot", plot); repository.save(DIMENSION);
        long revision = repository.revision(DIMENSION);
        String malformed = "regions: [broken";
        Files.writeString(repository.file(DIMENSION), malformed);
        assertThrows(IOException.class, () -> repository.reload(DIMENSION));
        assertSame(plot, repository.regions(DIMENSION).get("plot"));
        assertEquals(revision, repository.revision(DIMENSION));
        assertEquals(malformed, Files.readString(repository.file(DIMENSION)));
        assertThrows(IOException.class, () -> repository.save(DIMENSION));
        RegionRepository firstLoad = new RegionRepository(temporary.resolve("native"));
        assertThrows(IOException.class, () -> firstLoad.regions(DIMENSION));
    }

    @Test void malformedImportDoesNotAlterNativeFileOrCache() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        repository.regions(DIMENSION).put("plot", Region.cuboid("plot", 0, 0, 0, 1, 1, 1));
        repository.save(DIMENSION);
        String before = Files.readString(repository.file(DIMENSION));
        Path invalid = source("""
                regions:
                    broken:
                        type: cuboid
                        min: {x: 0, y: 0, z: 0}
                        max: {x: 1, y: 1, z: 1}
                        flags: {pvp: typo}
                """);
        assertThrows(IOException.class, () -> repository.importYaml(invalid, DIMENSION, true));
        assertEquals(before, Files.readString(repository.file(DIMENSION)));
        assertEquals(Set.of("plot"), repository.regions(DIMENSION).keySet());
    }

    @Test void duplicateKeysBadUuidAndMissingGeometryFailClosed() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        for (String malformed : List.of(
                "regions: {plot: {type: cuboid, min: {x: 0, y: 0, z: 0}}}",
                "regions: {plot: {type: unknown}}",
                "regions: {__global__: {type: global, flags: {pvp: allow, pvp: deny}}}",
                "regions: {__global__: {type: global, owners: {unique-ids: [invalid]}}}",
                "regions: {plot: {type: cuboid, min: {x: 0.5, y: 0, z: 0}, max: {x: 1, y: 1, z: 1}}}",
                "{}")) {
            Path source = source(malformed);
            assertThrows(IOException.class, () -> repository.importYaml(source, DIMENSION, false), malformed);
        }
    }

    @Test void parentCyclesAndMissingParentsAreRejectedBeforeSave() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        Region a = Region.cuboid("a", 0, 0, 0, 1, 1, 1), b = Region.cuboid("b", 0, 0, 0, 1, 1, 1);
        repository.regions(DIMENSION).put("a", a); repository.regions(DIMENSION).put("b", b);
        a.parent("b"); b.parent("a");
        assertThrows(IOException.class, () -> repository.save(DIMENSION));
        b.parent("missing");
        assertThrows(IOException.class, () -> repository.save(DIMENSION));
    }

    @Test void atomicWritesLeaveNoTemporaryFilesAndRevisionTracksSuccessfulChanges() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        repository.regions(DIMENSION);
        long initial = repository.revision(DIMENSION);
        repository.save(DIMENSION); assertTrue(repository.revision(DIMENSION) > initial);
        long saved = repository.revision(DIMENSION);
        repository.reload(DIMENSION); assertTrue(repository.revision(DIMENSION) > saved);
        try (var entries = Files.list(repository.file(DIMENSION).getParent())) {
            assertEquals(List.of("regions.yml"), entries.map(path -> path.getFileName().toString()).toList());
        }
        assertEquals(Set.of(DIMENSION), repository.allDimensions());
    }

    @Test void dimensionPathsCannotEscapeRepository() {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        assertThrows(IllegalArgumentException.class, () -> repository.file("minecraft:../../outside"));
        assertThrows(IllegalArgumentException.class, () -> repository.file("minecraft:/outside"));
        assertThrows(IllegalArgumentException.class, () -> repository.file("minecraft:folder/../outside"));
        assertTrue(repository.file("mod:moon/orbit").startsWith(repository.root()));
    }

    @Test void partialImportsCanInheritExistingParentButReplacingImportsMustBeComplete() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        repository.regions(DIMENSION).put("parent", Region.cuboid("parent", 0, 0, 0, 100, 100, 100));
        Path partial = source("""
                regions:
                    child:
                        type: cuboid
                        min: {x: 0, y: 0, z: 0}
                        max: {x: 10, y: 10, z: 10}
                        parent: parent
                """);
        assertEquals(1, repository.importYaml(partial, DIMENSION, false));
        assertEquals("parent", repository.regions(DIMENSION).get("child").parent());
        String saved = Files.readString(repository.file(DIMENSION));
        assertThrows(IOException.class, () -> repository.importYaml(partial, DIMENSION, true));
        assertEquals(saved, Files.readString(repository.file(DIMENSION)));
        assertEquals(Set.of("parent", "child"), repository.regions(DIMENSION).keySet());
    }

    @Test void replacingImportRepairsCorruptionAndClearsFailedLoadGuard() throws IOException {
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        repository.regions(DIMENSION); repository.save(DIMENSION);
        Files.writeString(repository.file(DIMENSION), "invalid: [");
        assertThrows(IOException.class, () -> repository.reload(DIMENSION));
        Path valid = source("regions: {__global__: {type: global, flags: {pvp: deny}}}");
        assertEquals(1, repository.importYaml(valid, DIMENSION, true));
        assertDoesNotThrow(() -> repository.save(DIMENSION));
        assertEquals("deny", repository.regions(DIMENSION).get("__global__").flags().get("pvp"));
    }

    @Test void multiWorldReloadIsTransactional() throws IOException {
        String otherDimension = "minecraft:the_nether";
        RegionRepository repository = new RegionRepository(temporary.resolve("native"));
        Region original = Region.cuboid("original", 0, 0, 0, 1, 1, 1);
        repository.regions(DIMENSION).put("original", original);
        repository.regions(otherDimension); repository.saveAll();
        Files.writeString(repository.file(DIMENSION), "regions: {__global__: {type: global}}");
        Files.writeString(repository.file(otherDimension), "regions: [invalid");
        long revision = repository.revision(DIMENSION);
        assertThrows(IOException.class, () -> repository.reloadAll(List.of(DIMENSION, otherDimension)));
        assertSame(original, repository.regions(DIMENSION).get("original"));
        assertEquals(revision, repository.revision(DIMENSION));
        Files.writeString(repository.file(otherDimension), "regions: {}");
        repository.reloadAll(List.of(DIMENSION, otherDimension));
        assertEquals(Set.of("__global__"), repository.regions(DIMENSION).keySet());
    }
}
