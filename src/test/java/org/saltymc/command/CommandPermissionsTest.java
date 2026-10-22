package org.saltymc.command;

import org.bukkit.command.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandPermissionsTest {
    @Test void oldFlagRoutesAreRemovedFromBothCommandFamilies() {
        ClaimCommands commands=new ClaimCommands(null);
        for(boolean admin:List.of(false,true)) for(String route:List.of("flag","flags")) {
            String name=admin?"claima":"claim"; List<String> messages=new ArrayList<>();
            var sender=sender(Set.of(ClaimCommands.permission(admin,route)),messages);
            commands.onCommand(sender,command(name),name,new String[]{route});
            assertEquals(List.of("[SaltyClaims] Unknown command. Use /"+name+" help."),messages);
            assertTrue(commands.onTabComplete(sender,command(name),name,new String[]{"fl"}).isEmpty());
        }
    }
    private Command command(String name) {
        return new Command(name) { @Override public boolean execute(CommandSender sender,String label,String[] arguments) { return false; } };
    }
    private CommandSender sender(Set<String> permissions,List<String> messages) {
        return (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CommandSender.class},(proxy,method,args)->switch(method.getName()) {
            case "hasPermission" -> permissions.contains(args[0].toString());
            case "sendMessage" -> { if(args!=null) for(Object arg:args) if(arg instanceof String s) messages.add(s); else if(arg instanceof String[] lines) messages.addAll(List.of(lines)); yield null; }
            case "getName" -> "test";
            case "toString" -> "test-sender";
            default -> method.getReturnType()==boolean.class?false:null;
        });
    }
    @Test void everyExecutablePathRejectsMissingOwnPermissionBeforeAnyPluginWork() {
        // Null plugin deliberately fails if denied execution reaches gameplay or storage.
        ClaimCommands commands=new ClaimCommands(null);
        for(boolean admin:List.of(false,true)) for(String path:admin?ClaimCommands.ADMIN_PATHS:ClaimCommands.PLAYER_PATHS) {
            List<String> messages=new ArrayList<>();
            commands.onCommand(sender(Set.of(),messages),command(admin?"claima":"claim"),admin?"claima":"claim",path.replace('.',' ').split(" "));
            assertEquals(List.of("[SaltyClaims] You do not have permission to do that."),messages,path);
        }
    }
    @Test void unrelatedAdminActionPermissionDoesNotAuthorizeDeletion() {
        List<String> messages=new ArrayList<>();
        new ClaimCommands(null).onCommand(sender(Set.of("saltyclaims.command.claima.resize","saltyclaims.command.claima.bypass"),messages),command("claima"),"claima",new String[]{"delete",UUID.randomUUID().toString()});
        assertEquals(List.of("[SaltyClaims] You do not have permission to do that."),messages);
    }
    @Test void bareRootUsesHelpPermissionWithoutACommonGate() {
        List<String> messages=new ArrayList<>();
        new ClaimCommands(null).onCommand(sender(Set.of("saltyclaims.command.claim.help","saltyclaims.command.claim.info"),messages),command("claim"),"claim",new String[]{});
        assertTrue(messages.contains("[SaltyClaims] /claim info [owner]")); assertFalse(messages.contains("[SaltyClaims] /claim delete"));
    }
    @Test void playerResizeCommandIsRemovedButToolPermissionDoesNotAdvertiseIt() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>();
        CommandSender sender=sender(Set.of("saltyclaims.command.claim.resize","saltyclaims.command.claim.help"),messages);
        commands.onCommand(sender,command("claim"),"claim",new String[]{"resize"});
        assertEquals(List.of("[SaltyClaims] Unknown command. Use /claim help."),messages);
        assertTrue(commands.onTabComplete(sender,command("claim"),"claim",new String[]{"res"}).isEmpty());
        messages.clear(); commands.onCommand(sender,command("claim"),"claim",new String[]{"help"});
        assertTrue(messages.stream().noneMatch(line->line.contains("/claim resize")));
        assertTrue(ClaimCommands.ADMIN_PATHS.contains("resize"));
    }
    @Test void childCreationAndRestrictionCommandsAreUnavailableEvenWithFormerPermissions() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>();
        CommandSender sender=sender(Set.of("saltyclaims.command.claim.child.create","saltyclaims.command.claim.child.restrict","saltyclaims.command.claim.help"),messages);
        assertTrue(commands.onTabComplete(sender,command("claim"),"claim",new String[]{"child",""}).isEmpty());
        for(String[] args:List.of(new String[]{"child","create"},new String[]{"child","restrict","off"}))
            commands.onCommand(sender,command("claim"),"claim",args);
        assertEquals(Collections.nCopies(2,"[SaltyClaims] Unknown command. Use /claim help."),messages);
        messages.clear(); commands.onCommand(sender,command("claim"),"claim",new String[]{"help"});
        assertTrue(messages.stream().noneMatch(line->line.contains("/claim child")));
    }
    @Test void playerDeleteRoutesAreRemoved() {
        List<String> messages=new ArrayList<>(); ClaimCommands commands=new ClaimCommands(null);
        commands.onCommand(sender(Set.of("saltyclaims.command.claim.delete"),messages),command("claim"),"claim",new String[]{"delete","confirm"});
        assertEquals(List.of("[SaltyClaims] Unknown command. Use /claim help."),messages);
        messages.clear(); commands.onCommand(sender(Set.of(),messages),command("claim"),"claim",new String[]{"confirm","old-token"});
        assertEquals(List.of("[SaltyClaims] Unknown command. Use /claim help."),messages);
    }
    @Test void confirmationKeywordCanBeUsedAsOwnerViaExplicitNameOption() {
        List<String> messages=new ArrayList<>();
        new ClaimCommands(null).onCommand(sender(Set.of(),messages),command("claima"),"claima",new String[]{"delete","--owner","confirm"});
        assertEquals(List.of("[SaltyClaims] You do not have permission to do that."),messages);
    }
    @Test void helpUsesPlayerNamesAndNeverAdvertisesUuidOrTokens() {
        List<String> messages=new ArrayList<>(); Set<String> permissions=new HashSet<>();
        for(String path:ClaimCommands.ADMIN_PATHS) permissions.add(ClaimCommands.permission(true,path));
        new ClaimCommands(null).onCommand(sender(permissions,messages),command("claima"),"claima",new String[]{"help"});
        assertTrue(messages.contains("[SaltyClaims] /claima delete confirm"));
        assertTrue(messages.stream().noneMatch(text->text.contains("<id>")||text.contains("<token>")||text.contains("UUID")));
    }
    @Test void resetConfirmationRequiresItsOwnPermissionAndHasSeparateCompletion() {
        List<String> messages=new ArrayList<>(); ClaimCommands commands=new ClaimCommands(null);
        CommandSender sender=sender(Set.of("saltyclaims.command.claima.reset.all"),messages);
        commands.onCommand(sender,command("claima"),"claima",new String[]{"reset","all","confirm"});
        assertEquals(List.of("[SaltyClaims] You do not have permission to do that."),messages);
        assertTrue(commands.onTabComplete(sender,command("claima"),"claima",new String[]{"reset","all",""}).isEmpty());
        CommandSender confirmer=sender(Set.of("saltyclaims.command.claima.reset.all.confirm"),new ArrayList<>());
        assertEquals(List.of("confirm"),commands.onTabComplete(confirmer,command("claima"),"claima",new String[]{"reset","all",""}));
    }
    @Test void modeCommandsAreRemovedFromExecutionAndCompletion() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>();
        CommandSender sender=sender(Set.of("saltyclaims.command.claim.mode.basic","saltyclaims.command.claim.mode.child"),messages);
        commands.onCommand(sender,command("claim"),"claim",new String[]{"mode","child"});
        assertEquals(List.of("[SaltyClaims] Unknown command. Use /claim help."),messages);
        assertFalse(ClaimCommands.PLAYER_PATHS.stream().anyMatch(path->path.startsWith("mode")));
        assertTrue(commands.onTabComplete(sender,command("claim"),"claim",new String[]{"mode",""}).isEmpty());
    }
    @Test void bulkTrustCommandsAreUnavailable() {
        ClaimCommands commands=new ClaimCommands(null);
        for(String route:List.of("trustall","untrustall")) {
            List<String> messages=new ArrayList<>();
            commands.onCommand(sender(Set.of(),messages),command("claim"),"claim",new String[]{route,"Alex"});
            assertEquals(List.of("[SaltyClaims] Unknown command. Use /claim help."),messages);
            assertFalse(ClaimCommands.PLAYER_PATHS.contains(route));
        }
    }
    @Test void removedSelectionAndExplainCommandsRejectAndAreNotAdvertised() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>(); Set<String> permissions=new HashSet<>();
        for(String route:List.of("pos1","pos2","create","explain")) {
            permissions.add(ClaimCommands.permission(false,route));
            commands.onCommand(sender(permissions,messages),command("claim"),"claim",new String[]{route});
            assertFalse(ClaimCommands.PLAYER_PATHS.contains(route));
        }
        assertEquals(Collections.nCopies(4,"[SaltyClaims] Unknown command. Use /claim help."),messages);
        assertTrue(commands.onTabComplete(sender(permissions,new ArrayList<>()),command("claim"),"claim",new String[]{""}).isEmpty());
        assertTrue(commands.onTabComplete(sender(permissions,new ArrayList<>()),command("claim"),"claim",new String[]{"explain",""}).isEmpty());
    }
    @Test void playerHelpExplainsClaimingAndToolRouteIsRemoved() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>();
        Set<String> permissions=new HashSet<>(); for(String path:ClaimCommands.PLAYER_PATHS) permissions.add(ClaimCommands.permission(false,path));
        permissions.add(ClaimCommands.permission(false,"tool"));
        commands.onCommand(sender(permissions,messages),command("claim"),"claim",new String[]{"help"});
        assertTrue(messages.getFirst().contains("stick for claims, bone for child claims, feather to inspect"));
        assertTrue(messages.stream().anyMatch(line->line.contains("two opposite corners")));
        assertTrue(messages.stream().anyMatch(line->line.contains("Same Y: full height; different Y: 3D")));
        assertTrue(messages.stream().anyMatch(line->line.contains("To resize")));
        assertTrue(messages.stream().anyMatch(line->line.contains("failed attempt clears")));
        assertFalse(messages.stream().anyMatch(line->line.contains("/claim tool")));
        messages.clear(); commands.onCommand(sender(permissions,messages),command("claim"),"claim",new String[]{"tool"});
        assertEquals(List.of("[SaltyClaims] Unknown command. Use /claim help."),messages);
        assertFalse(ClaimCommands.PLAYER_PATHS.contains("tool"));
    }
    @Test void helpUsesConfiguredToolItemsWithReadableNames() {
        var document=org.saltymc.config.YamlData.parse(org.saltymc.config.ConfigFiles.resource("config.yml"),"test");
        org.saltymc.config.YamlData.map(document.get("tools"),"test","tools").put("selection","DIAMOND_HOE");
        var notice=ClaimCommands.helpTools(org.saltymc.config.Settings.from(document));
        assertEquals("diamond hoe",notice.values().get("selection"));
        assertEquals("bone",notice.values().get("child-selection"));
    }
    @Test void unclaimRoutesHaveIndependentPermissionsAndAllCompletesWithoutBaseGrant() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>();
        commands.onCommand(sender(Set.of(),messages),command("unclaim"),"unclaim",new String[]{});
        assertEquals(List.of("[SaltyClaims] You do not have permission to do that."),messages);
        messages.clear(); commands.onCommand(sender(Set.of(ClaimCommands.unclaimPermission(false)),messages),command("unclaim"),"unclaim",new String[]{"all"});
        assertEquals(List.of("[SaltyClaims] You do not have permission to do that."),messages);
        assertEquals(List.of("all"),commands.onTabComplete(sender(Set.of(ClaimCommands.unclaimPermission(true)),new ArrayList<>()),command("unclaim"),"unclaim",new String[]{""}));
        assertTrue(commands.onTabComplete(sender(Set.of(),new ArrayList<>()),command("unclaim"),"unclaim",new String[]{""}).isEmpty());
    }
    @Test void unclaimRejectsConfirmationArgumentsAndIsIncludedInAuthorizedHelp() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>();
        var permissions=Set.of(ClaimCommands.unclaimPermission(false),ClaimCommands.unclaimPermission(true),ClaimCommands.permission(false,"help"));
        commands.onCommand(sender(permissions,messages),command("unclaim"),"unclaim",new String[]{"all","confirm"});
        assertEquals(List.of("[SaltyClaims] Usage: /unclaim [all]"),messages);
        messages.clear(); commands.onCommand(sender(permissions,messages),command("claim"),"claim",new String[]{"help"});
        assertTrue(messages.contains("[SaltyClaims] /unclaim")); assertTrue(messages.contains("[SaltyClaims] /unclaim all"));
        assertFalse(ClaimCommands.PLAYER_PATHS.contains("delete")); assertFalse(ClaimCommands.PLAYER_PATHS.contains("delete.confirm"));
    }
    @Test void benchmarkHasItsOwnPermissionAndIsOnlySuggestedToAuthorizedStaff() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>();
        commands.onCommand(sender(Set.of(ClaimCommands.permission(true,"backup")),messages),command("claima"),"claima",new String[]{"test"});
        assertEquals(List.of("[SaltyClaims] You do not have permission to do that."),messages);
        assertEquals(List.of("test"),commands.onTabComplete(sender(Set.of(ClaimCommands.permission(true,"test")),new ArrayList<>()),command("claima"),"claima",new String[]{"te"}));
        assertTrue(commands.onTabComplete(sender(Set.of(),new ArrayList<>()),command("claima"),"claima",new String[]{"te"}).isEmpty());
    }
}
