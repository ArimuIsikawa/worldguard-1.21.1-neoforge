package com.sk89q.worldguard.neoforge.storage;

import com.sk89q.worldguard.neoforge.region.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SqlRegionStorageTest {
    @TempDir Path root;
    private JdbcSettings settings() { return new JdbcSettings("jdbc:sqlite:" + root.resolve("regions.sqlite"),"","","wg_"); }
    private SqlRegionStorage storage() throws IOException { return new SqlRegionStorage(settings(),Map.of("world","minecraft:overworld")); }
    private Map<String,Region> sample() {
        Region parent = Region.cuboid("home",1,-64,3,9,319,8); parent.priority(Integer.MAX_VALUE);
        parent.owners().uuids().add(UUID.fromString("79fc4913-a44c-47c8-8cae-d08cfdd60bb5")); parent.members().players().add("Alice");
        parent.owners().groups().add("builders"); parent.members().groups().add("builders");
        parent.flags().put("pvp","deny"); parent.flags().put("entry-group","nonmembers"); parent.flags().put("deny-spawn",Set.of("minecraft:zombie"));
        parent.flags().put("teleport",Map.of("world","world","x",2d,"y",70d,"z",5d,"yaw",90d,"pitch",10d));
        parent.flags().put("extension:flag", Map.of("answer",List.of("hello",3)));
        Region child = Region.polygon("child",List.of(new Region.Point(2,3),new Region.Point(5,3),new Region.Point(4,7)),10,20); child.parent("home");
        return new LinkedHashMap<>(Map.of("home",parent,"child",child,"__global__",Region.global("__global__")));
    }
    @Test void realSqliteRoundTripPreservesGeometryDomainsGroupsParentsAndTypedFlags() throws Exception {
        SqlRegionStorage sql=storage(); sql.save("minecraft:overworld",sample());
        Map<String,Region> loaded=storage().load("minecraft:overworld");
        assertEquals(3,loaded.size()); assertEquals(Integer.MAX_VALUE,loaded.get("home").priority());
        assertEquals(sample().get("child").points(),loaded.get("child").points()); assertEquals("home",loaded.get("child").parent());
        assertEquals(Set.of("builders"),loaded.get("home").owners().groups()); assertEquals(Set.of("builders"),loaded.get("home").members().groups());
        assertEquals(sample().get("home").owners().uuids(),loaded.get("home").owners().uuids());
        assertEquals(Set.of("Alice"),loaded.get("home").members().players()); assertEquals("deny",loaded.get("home").flags().get("pvp"));
        assertEquals(sample().get("home").flags().get("teleport"),loaded.get("home").flags().get("teleport"));
        assertEquals(sample().get("home").flags().get("extension:flag"),loaded.get("home").flags().get("extension:flag"));
    }
    @Test void readsOriginalSchemaAndRetainsTheLegacyWorldIdOnSave() throws Exception {
        SqlRegionStorage sql=storage();
        try(Connection c=settings().open();var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO wg_world(id,name) VALUES (41,'world')");
            s.executeUpdate("INSERT INTO wg_region(id,world_id,type,priority) VALUES ('legacy',41,'cuboid',7)");
            s.executeUpdate("INSERT INTO wg_region_cuboid VALUES ('legacy',41,1,2,3,4,5,6)");
            s.executeUpdate("INSERT INTO wg_region_flag(region_id,world_id,flag,value) VALUES ('legacy',41,'pvp','deny')");
        }
        Map<String,Region> loaded=sql.load("minecraft:overworld"); assertEquals("deny",loaded.get("legacy").flags().get("pvp"));
        sql.save("minecraft:overworld",loaded); assertEquals(Set.of("minecraft:overworld"),sql.dimensions());
        try(Connection c=settings().open();var s=c.createStatement();var r=s.executeQuery("SELECT id,name FROM wg_world")) {
            assertTrue(r.next()); assertEquals(41,r.getInt(1)); assertEquals("world",r.getString(2)); assertFalse(r.next());
        }
    }
    @Test void sqliteConstraintFailureRollsBackTheEntireWriteAndOtherDimensionsSurvive() throws Exception {
        SqlRegionStorage sql=storage(); sql.save("minecraft:overworld",sample()); sql.save("example:moon",Map.of("moon",Region.cuboid("moon",0,0,0,1,1,1)));
        try(Connection c=settings().open();var s=c.createStatement()) { s.executeUpdate("CREATE TRIGGER block_test_write BEFORE INSERT ON wg_region_flag BEGIN SELECT RAISE(ABORT,'test write failure'); END"); }
        Region replacement=Region.cuboid("broken",1,1,1,2,2,2); replacement.flags().put("pvp","deny");
        assertThrows(IOException.class,()->sql.save("minecraft:overworld",Map.of("broken",replacement)));
        assertEquals(3,sql.load("minecraft:overworld").size()); assertTrue(sql.load("example:moon").containsKey("moon"));
    }
    @Test void repositorySwitchAndYamlExportRetainCachedMapAndRecoverOnFailedSqlReload() throws Exception {
        RegionRepository repo=new RegionRepository(root.resolve("worlds")); Map<String,Region> live=repo.regions("minecraft:overworld");
        live.putAll(sample()); repo.save("minecraft:overworld"); SqlRegionStorage sql=storage(); sql.save("minecraft:overworld",sample());
        repo.reloadAll(Set.of("minecraft:overworld"),sql); assertSame(live,repo.regions("minecraft:overworld")); assertTrue(repo.isSql());
        Path exported=root.resolve("export.yml"); repo.exportYaml("minecraft:overworld",exported); assertTrue(Files.readString(exported).contains("poly2d"));
        try(Connection c=settings().open();var s=c.createStatement()) { s.executeUpdate("UPDATE wg_region SET parent='missing' WHERE id='child'"); }
        assertThrows(IOException.class,()->repo.reloadAll(Set.of("minecraft:overworld"),sql)); assertEquals(3,live.size());
        assertThrows(IOException.class,()->repo.save("minecraft:overworld"));
    }
    @Test void legacySqliteGroupKeyUpgradePreservesExistingOwnersAndPermitsMembershipAsWell() throws Exception {
        SqlRegionStorage sql=storage(); sql.save("minecraft:overworld",Map.of("a",Region.cuboid("a",0,0,0,1,1,1)));
        try(Connection c=settings().open();var s=c.createStatement()) {
            s.executeUpdate("DROP TABLE wg_region_groups"); s.executeUpdate("CREATE TABLE wg_region_groups(region_id TEXT,world_id INTEGER,group_id INTEGER,owner BOOLEAN,PRIMARY KEY(region_id,world_id,group_id))");
            s.executeUpdate("INSERT INTO wg_group(id,name) VALUES (99,'old')"); s.executeUpdate("INSERT INTO wg_region_groups VALUES ('a',1,99,1)");
        }
        SqlRegionStorage upgraded=storage(); Map<String,Region> loaded=upgraded.load("minecraft:overworld"); assertEquals(Set.of("old"),loaded.get("a").owners().groups());
        loaded.get("a").members().groups().add("old"); upgraded.save("minecraft:overworld",loaded);
        assertEquals(Set.of("old"),upgraded.load("minecraft:overworld").get("a").members().groups());
    }
    @Test void blacklistSqlLogUsesOriginalEventNamesAndEscapesPlayerControlledText() throws Exception {
        SqlBlacklistLogger logger=new SqlBlacklistLogger(settings(),"blacklist");
        logger.log("on-destroy-with","minecraft:overworld","Alice",1,2,3,"minecraft:diamond_pickaxe","quote ' and ; DROP TABLE wg_world");
        try(Connection c=settings().open();var s=c.createStatement();var r=s.executeQuery("SELECT event,player,item,comment FROM wg_blacklist")) {
            assertTrue(r.next()); assertEquals("DESTROY_WITH",r.getString(1)); assertEquals("Alice",r.getString(2)); assertEquals("minecraft:diamond_pickaxe",r.getString(3)); assertTrue(r.getString(4).contains("DROP TABLE"));
        }
    }
    @Test void invalidIdentifiersAreRejectedAndPasswordsNeverAppearInSettingsDiagnostics() {
        assertThrows(IllegalArgumentException.class,()->new JdbcSettings("jdbc:sqlite:test","","","wg_;drop"));
        assertThrows(IllegalArgumentException.class,()->new JdbcSettings("jdbc:sqlite:","","",""));
        assertThrows(IllegalArgumentException.class,()->new JdbcSettings("jdbc:sqlite::memory:","","",""));
        assertFalse(new JdbcSettings("jdbc:mysql://localhost/db","user","private-pass","").toString().contains("private-pass"));
    }
    @Test void actualOriginalSqliteSchemaRetainsForeignKeyDataDuringRoundTrip() throws Exception {
        try(var c=settings().open();var s=c.createStatement()) {
            String script=Files.readString(Path.of("need_migrate/migrations/region/sqlite/V1__Initial.sql")).replace("${tablePrefix}","wg_");
            for(String statement:script.split(";")) if(!statement.isBlank()) s.execute(statement);
        }
        SqlRegionStorage sql=storage(); var expected=sample(); UUID same=UUID.randomUUID();
        expected.get("home").owners().uuids().add(same); expected.get("home").members().uuids().add(same);
        sql.save("minecraft:overworld",expected); var loaded=sql.load("minecraft:overworld");
        assertEquals(3,loaded.size()); assertEquals(expected.get("home").owners().uuids(),loaded.get("home").owners().uuids());
        assertTrue(loaded.get("home").members().uuids().contains(same)); assertEquals(expected.get("child").points(),loaded.get("child").points());
        sql.save("minecraft:overworld",Map.of("new",Region.cuboid("new",0,0,0,1,1,1)));
        assertEquals(Set.of("new"),sql.load("minecraft:overworld").keySet());
    }
}
