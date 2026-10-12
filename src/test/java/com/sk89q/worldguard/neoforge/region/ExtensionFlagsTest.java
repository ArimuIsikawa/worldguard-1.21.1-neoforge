package com.sk89q.worldguard.neoforge.region;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionFlagsTest {
    @TempDir Path root;
    @Test void typedCustomFlagsParticipateInPriorityGroupsAndYamlRoundTrips() throws Exception {
        String name="extension-" + UUID.randomUUID().toString();
        FlagRegistry.register(name,FlagRegistry.Type.STATE,"allow","members");
        FlagRegistry.register(name,FlagRegistry.Type.STATE,"ALLOW","members");
        RegionRepository repository=new RegionRepository(root); Region region=Region.cuboid("home",0,0,0,10,10,10);
        region.flags().put(name,"deny"); region.members().groups().add("builders"); repository.regions("test:world").put(region.id(),region); repository.save("test:world"); repository.reload("test:world");
        RegionQuery member=new RegionQuery(repository.regions("test:world").values(),null,null).withGroups("builders"::equals);
        assertFalse(member.allows(1,1,1,name)); assertTrue(member.isMember(repository.regions("test:world").get("home")));
        assertTrue(new RegionQuery(repository.regions("test:world").values(),null,null).allows(1,1,1,name));
        assertThrows(IllegalArgumentException.class,()->FlagRegistry.register(name,FlagRegistry.Type.STRING,"allow","members"));
    }
    @Test void invalidCustomDefaultsDoNotLeavePartiallyRegisteredFlags() {
        String name="invalid-" + UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class,()->FlagRegistry.register(name,FlagRegistry.Type.INTEGER,"not-an-integer","all"));
        assertFalse(FlagRegistry.contains(name)); assertFalse(FlagRegistry.contains(name+"-group"));
    }
    @Test void ownerGroupsAreInheritedByChildrenAndGrantBuildingMembership() {
        Region parent=Region.cuboid("parent",0,0,0,1,1,1); parent.owners().groups().add("builders");
        Region child=Region.cuboid("child",10,0,10,20,10,20); child.parent("parent");
        RegionQuery query=new RegionQuery(Set.of(parent,child),null,null).withGroups("builders"::equals);
        assertTrue(query.isOwner(child)); assertTrue(query.canBuild(15,5,15));
    }
    @Test void namedGlobalTemplatesApplyOnlyThroughInheritanceWhileTheGlobalFallbackAlwaysApplies() throws Exception {
        Region template=Region.global("template"); template.flags().put("pvp","deny");
        Region child=Region.cuboid("child",10,0,10,20,10,20); child.parent("template");
        Region global=Region.global("__global__"); global.flags().put("fire-spread","deny");
        RegionRepository repository=new RegionRepository(root);
        repository.regions("test:world").putAll(java.util.Map.of("template",template,"child",child,"__global__",global));
        repository.save("test:world"); repository.reload("test:world");
        RegionQuery query=new RegionQuery(repository.regions("test:world").values(),null,null);
        assertFalse(query.allows(15,5,15,"pvp")); assertTrue(query.allows(100,5,100,"pvp"));
        assertFalse(query.allows(100,5,100,"fire-spread")); assertFalse(template.contains(0,0,0));
    }
}
