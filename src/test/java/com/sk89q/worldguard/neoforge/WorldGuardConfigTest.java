package com.sk89q.worldguard.neoforge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WorldGuardConfigTest {
    @TempDir Path root;
    @Test void perDimensionOverridesInheritedGlobalRules() throws Exception {
        Files.writeString(root.resolve("config.yml"), "operator-level: 3\nfire: {disable-all-fire-spread: true}\nmigration: {worlds: {legacy: 'example:moon'}}\n");
        Path directory = WorldGuardConfig.worldDirectory(root.resolve("worlds"), "example:moon");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("config.yml"), "fire: {disable-all-fire-spread: false}\nregions: {disable-bypass-by-default: true}\n");
        WorldGuardConfig config = new WorldGuardConfig(root);
        config.loadWorld("example:moon");
        assertEquals(3, config.operatorLevel());
        assertTrue(config.denied("minecraft:overworld", "fire-spread"));
        assertFalse(config.denied("example:moon", "fire-spread"));
        assertEquals("example:moon", config.migrationWorlds().get("legacy"));
    }
    @Test void malformedOrDuplicateYamlFailsInsteadOfDisablingProtection() throws Exception {
        Files.writeString(root.resolve("config.yml"), "regions: true\nregions: false\n");
        assertThrows(IOException.class, () -> new WorldGuardConfig(root));
        Files.writeString(root.resolve("config.yml"), "operator-level: 5\n");
        assertThrows(IOException.class, () -> new WorldGuardConfig(root));
    }
    @Test void worldPathsCannotEscapeDirectory() {
        assertThrows(IllegalArgumentException.class, () -> WorldGuardConfig.worldDirectory(root, "test:../../other"));
        assertThrows(IllegalArgumentException.class, () -> WorldGuardConfig.worldDirectory(root, "test:/other"));
    }
    @Test void diagnosticReportsRedactCredentialsWithoutChangingTheLiveConfiguration() throws Exception {
        Files.writeString(root.resolve("config.yml"),"regions: {sql: {use: false, dsn: 'jdbc:mysql://localhost/db?password=hidden-url', password: hidden-password}}\nblacklist: {logging: {database: {pass: hidden-log-password}}}\nhost-keys: {server: hidden-host-key}\n");
        WorldGuardConfig config=new WorldGuardConfig(root);
        String report=new org.yaml.snakeyaml.Yaml().dump(config.diagnosticSnapshot());
        assertFalse(report.contains("hidden-url")); assertFalse(report.contains("hidden-password")); assertFalse(report.contains("hidden-log-password")); assertFalse(report.contains("hidden-host-key"));
        assertTrue(report.contains("[redacted]")); assertEquals("hidden-password",config.string(null,"regions.sql.password",""));
    }
}
