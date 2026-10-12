package com.sk89q.worldguard.neoforge.storage;

import com.sk89q.worldguard.neoforge.region.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against an isolated database explicitly supplied by the test environment. */
@EnabledIfEnvironmentVariable(named="WORLDGUARD_TEST_MYSQL_DSN",matches=".+")
class MySqlRegionStorageTest {
    private final String prefix="wgtest_" + UUID.randomUUID().toString().replace("-","").substring(0,12) + "_";
    private JdbcSettings settings() { return new JdbcSettings(System.getenv("WORLDGUARD_TEST_MYSQL_DSN"),System.getenv().getOrDefault("WORLDGUARD_TEST_MYSQL_USER","root"),System.getenv().getOrDefault("WORLDGUARD_TEST_MYSQL_PASSWORD",""),prefix); }
    private SqlRegionStorage storage() throws IOException { return new SqlRegionStorage(settings(),Map.of("world","minecraft:overworld")); }
    @AfterEach void removeOnlyTablesCreatedByThisTest() throws Exception {
        try(var c=settings().open();var s=c.createStatement()) {
            for(String suffix:List.of("region_poly2d_point","region_cuboid","region_poly2d","region_flag","region_players","region_groups","region","user","group","world","blacklist","migrations"))
                s.executeUpdate("DROP TABLE IF EXISTS " + settings().table(suffix));
        }
    }
    @Test void realMySqlUrlRoundTripAndTransactionRollback() throws Exception {
        SqlRegionStorage sql=storage(); Region parent=Region.cuboid("parent",0,-64,0,20,319,20); parent.priority(Integer.MAX_VALUE);
        parent.owners().uuids().add(UUID.randomUUID()); parent.owners().groups().add("both"); parent.members().groups().add("both"); parent.flags().put("pvp","deny");
        Region child=Region.polygon("child",List.of(new Region.Point(1,1),new Region.Point(5,1),new Region.Point(4,5)),0,50); child.parent("parent");
        sql.save("minecraft:overworld",Map.of("parent",parent,"child",child));
        assertEquals(Integer.MAX_VALUE,sql.load("minecraft:overworld").get("parent").priority());
        assertEquals(child.points(),sql.load("minecraft:overworld").get("child").points());
        assertEquals(Set.of("both"),sql.load("minecraft:overworld").get("parent").members().groups());
        try(var c=settings().open();var s=c.createStatement()) {
            s.executeUpdate("CREATE TRIGGER `" + prefix + "reject_flag` BEFORE INSERT ON " + settings().table("region_flag") + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test transaction failure'");
        }
        Region replacement=Region.cuboid("replacement",1,1,1,2,2,2); replacement.flags().put("pvp","allow");
        assertThrows(IOException.class,()->sql.save("minecraft:overworld",Map.of("replacement",replacement)));
        assertEquals(Set.of("parent","child"),sql.load("minecraft:overworld").keySet());
    }
    @Test void originalWorldGuardMysqlSchemaLoadsWithoutManualExport() throws Exception {
        try(var c=settings().open();var s=c.createStatement()) {
            for(String file:List.of("V1__Initial.sql","V2__Bug_fix_and_UUID.sql")) {
                String script=Files.readString(Path.of("need_migrate/migrations/region/mysql",file)).replace("${tablePrefix}",prefix);
                for(String statement:script.split(";")) if (!statement.isBlank()) s.execute(statement);
            }
            s.executeUpdate("INSERT INTO " + settings().table("world") + "(id,name) VALUES (41,'world')");
            s.executeUpdate("INSERT INTO " + settings().table("region") + "(id,world_id,type,priority) VALUES ('old',41,'cuboid',9)");
            s.executeUpdate("INSERT INTO " + settings().table("region_cuboid") + "(region_id,world_id,min_x,min_y,min_z,max_x,max_y,max_z) VALUES ('old',41,1,2,3,4,5,6)");
            s.executeUpdate("INSERT INTO " + settings().table("region_flag") + "(region_id,world_id,flag,value) VALUES ('old',41,'pvp','deny')");
        }
        SqlRegionStorage sql=storage(); var regions=sql.load("minecraft:overworld"); assertEquals("deny",regions.get("old").flags().get("pvp"));
        regions.get("old").priority(Integer.MAX_VALUE); sql.save("minecraft:overworld",regions);
        assertEquals(Integer.MAX_VALUE,sql.load("minecraft:overworld").get("old").priority());
        try(var c=settings().open();var s=c.createStatement();var r=s.executeQuery("SELECT name FROM " + settings().table("world"))) { assertTrue(r.next()); assertEquals("world",r.getString(1)); assertFalse(r.next()); }
    }
    @Test void blacklistLoggingWorksWithMySqlDriverAndOriginalColumns() throws Exception {
        new SqlBlacklistLogger(settings(),"blacklist").log("on-place","minecraft:overworld","Alice",1,2,3,"minecraft:tnt","quoted ' text");
        try(var c=settings().open();var s=c.createStatement();var r=s.executeQuery("SELECT event,comment FROM " + settings().table("blacklist"))) {
            assertTrue(r.next()); assertEquals("PLACE",r.getString(1)); assertEquals("quoted ' text",r.getString(2));
        }
    }
    @Test void preUuidOriginalSchemaUpgradesBothDomainKeysAndPreservesLegacyNames() throws Exception {
        try(var c=settings().open();var s=c.createStatement()) {
            String script=Files.readString(Path.of("need_migrate/migrations/region/mysql/V1__Initial.sql")).replace("${tablePrefix}",prefix);
            for(String statement:script.split(";")) if(!statement.isBlank()) s.execute(statement);
        }
        SqlRegionStorage sql=storage(); Region region=Region.cuboid("old",0,0,0,1,1,1); UUID uuid=UUID.randomUUID();
        region.owners().uuids().add(uuid); region.members().uuids().add(uuid); region.owners().players().add("oldname");
        region.owners().groups().add("both"); region.members().groups().add("both");
        sql.save("minecraft:overworld",Map.of("old",region)); Region loaded=sql.load("minecraft:overworld").get("old");
        assertEquals(Set.of(uuid),loaded.owners().uuids()); assertEquals(Set.of(uuid),loaded.members().uuids()); assertEquals(Set.of("oldname"),loaded.owners().players());
        try(var c=settings().open();var s=c.createStatement();var r=s.executeQuery("SELECT version,type,success FROM " + settings().table("migrations"))) {
            assertTrue(r.next()); assertEquals("2",r.getString(1)); assertEquals("INIT",r.getString(2)); assertTrue(r.getBoolean(3));
        }
    }
}
