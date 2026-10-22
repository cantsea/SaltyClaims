package org.saltymc.command;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import org.saltymc.config.*;
import org.saltymc.flag.Flag;
import org.saltymc.message.*;
import org.saltymc.trust.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class ClaimFlagCommandsTest {
    @Test void serverManagedModeKeepsEditableClaimFlagsAvailable() {
        Harness h=new Harness();
        var data=YamlData.parse(ConfigFiles.resource("config.yml"),"test"); data.put("server-manages-claims",true);
        var flags=YamlData.map(data.get("claim-flags"),"test","");
        YamlData.map(flags.get("pvp"),"test","").put("player-editable",true); h.settings=Settings.from(data);
        h.run("claimflags"); assertTrue(h.messages.getFirst().contains("pvp: false"));
        h.run("claimflag","toggle","pvp"); assertEquals("true",h.state.require(h.root.id()).flags().get(Flag.PVP));
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type,BiFunction<String,Object[],Object> call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,args)->switch(m.getName()) {
            case "equals"->p==args[0]; case "hashCode"->System.identityHashCode(p); default->call.apply(m.getName(),args);
        });
    }
    private static Command command(String name) { return new Command(name) {
        @Override public boolean execute(CommandSender s,String label,String[] args) { return false; }
    }; }
    private static class Harness implements ClaimFlagCommands.Port {
        Claim root=root(0,9);
        ClaimState state=org.saltymc.Fixtures.state(root);
        UUID actor=OWNER;
        boolean ready=true,wilderness;
        int mutations;
        Set<String> permissions=new HashSet<>(Set.of(ClaimFlagCommands.LIST,ClaimFlagCommands.TOGGLE,"saltyclaims.flag.pvp","saltyclaims.flag.fire-spread"));
        List<String> messages=new ArrayList<>();
        Settings settings;
        World world=proxy(World.class,(name,args)->name.equals("getName")?"world":null);
        Player player=proxy(Player.class,(name,args)->switch(name) {
            case "getUniqueId"->actor; case "getWorld"->world; case "getLocation"->new Location(world,2,12,2);
            case "hasPermission"->permissions.contains(args[0]);
            case "sendMessage"->{ if(args[0] instanceof String text) messages.add(text); yield null; }
            default->null;
        });
        ClaimFlagCommands commands=new ClaimFlagCommands(this);
        Harness() {
            var data=YamlData.parse(ConfigFiles.resource("config.yml"),"test");
            var flags=YamlData.map(data.get("claim-flags"),"test","");
            YamlData.map(flags.get("pvp"),"test","").put("player-editable",true);
            settings=Settings.from(data);
        }
        void run(String name,String... args) { commands.onCommand(player,command(name),name,args); }
        List<String> complete(String... args) { return commands.onTabComplete(player,command("claimflag"),"claimflag",args); }
        public boolean ready() { return ready; }
        public ClaimState state() { return state; }
        public Settings settings() { return settings; }
        public Messages messages() { return Messages.defaults(); }
        public Claim at(Location l) { return wilderness?null:new ClaimIndex(state).at(WORLD,2,12,2); }
        public void mutate(CommandSender sender,Function<ClaimState,ClaimState> operation,Notice success) {
            state=operation.apply(state); mutations++; messages().send(sender,success);
        }
    }
    @Test void listAndCompletionExposeOnlyEditablePermittedFlags() {
        Harness h=new Harness(); h.run("claimflags");
        assertEquals(List.of("[SaltyClaims] pvp: false"),h.messages);
        assertEquals(List.of("toggle"),h.complete(""));
        assertEquals(List.of("pvp"),h.complete("toggle",""));
        h.permissions.remove("saltyclaims.flag.pvp"); h.messages.clear(); h.run("claimflags");
        assertTrue(h.messages.getFirst().contains("no editable")); assertTrue(h.complete("toggle","").isEmpty());
    }
    @Test void toggleFlipsCurrentValueAndPersistsBooleanOverrides() {
        Harness h=new Harness(); h.run("claimflag","toggle","pvp");
        assertEquals("true",h.state.require(h.root.id()).flags().get(Flag.PVP));
        h.run("claimflag","toggle","pvp");
        assertEquals("false",h.state.require(h.root.id()).flags().get(Flag.PVP)); assertEquals(2,h.mutations);
    }
    @Test void hiddenTypedFlagsCannotBypassConfigOrPermission() {
        Harness h=new Harness(); h.run("claimflag","toggle","fire-spread"); assertEquals(0,h.mutations);
        h.permissions.remove("saltyclaims.flag.pvp"); h.run("claimflag","toggle","pvp"); assertEquals(0,h.mutations);
        assertTrue(h.messages.stream().allMatch(m->m.contains("permission")));
    }
    @Test void wildernessAndNonOwnersIncludingManagersCannotListCompleteOrToggle() {
        for(int mode=0;mode<3;mode++) {
            Harness h=new Harness();
            if(mode==0) h.wilderness=true;
            else { h.actor=FRIEND; if(mode==2) h.state=state(h.root.change(h.root.bounds(),true,Map.of(new Subject(FRIEND),Trust.MANAGE),Map.of())); }
            h.run("claimflags"); h.run("claimflag","toggle","pvp");
            assertTrue(h.complete("toggle","").isEmpty()); assertEquals(0,h.mutations);
            assertTrue(h.messages.stream().noneMatch(m->m.contains("pvp:")));
        }
    }
    @Test void childToggleUsesConfiguredDefaultAndOnlyChangesChild() {
        Harness h=new Harness();
        Claim parent=h.root.change(h.root.bounds(),true,Map.of(),Map.of(Flag.PVP,"true"));
        Claim child=child(parent,10,20); h.state=state(parent,child);
        h.run("claimflag","toggle","pvp");
        assertEquals("true",h.state.require(parent.id()).flags().get(Flag.PVP));
        assertEquals("true",h.state.require(child.id()).flags().get(Flag.PVP));
    }
    @Test void independentCommandPermissionsAreEnforced() {
        Harness h=new Harness(); h.permissions.remove(ClaimFlagCommands.TOGGLE);
        h.run("claimflag","toggle","pvp"); assertEquals(0,h.mutations); assertTrue(h.complete("toggle","").isEmpty());
        h.messages.clear(); h.permissions.remove(ClaimFlagCommands.LIST); h.run("claimflags");
        assertEquals(List.of("[SaltyClaims] You do not have permission to do that."),h.messages);
    }
    @Test void missingFlagListsAvailableFlagsAndExtraArgumentsDoNotMutate() {
        Harness h=new Harness(); h.run("claimflag","toggle"); assertTrue(h.messages.getFirst().contains("pvp: false"));
        h.run("claimflag","toggle","pvp","true"); h.run("claimflags","pvp"); assertEquals(0,h.mutations);
    }
    @Test void maintenanceHidesAllSuggestions() {
        Harness h=new Harness(); h.ready=false; h.run("claimflags");
        assertTrue(h.complete("toggle","").isEmpty()); assertTrue(h.messages.getFirst().contains("temporarily unavailable"));
    }
}
