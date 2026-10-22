package org.saltymc.command;

import org.bukkit.command.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandPermissionsTest {
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
            assertEquals(List.of("[SaltyClaims] Missing permission: "+ClaimCommands.permission(admin,path)),messages,path);
        }
    }
    @Test void unrelatedAdminActionPermissionDoesNotAuthorizeDeletion() {
        List<String> messages=new ArrayList<>();
        new ClaimCommands(null).onCommand(sender(Set.of("saltyclaims.command.claima.resize","saltyclaims.command.claima.bypass"),messages),command("claima"),"claima",new String[]{"delete",UUID.randomUUID().toString()});
        assertEquals(List.of("[SaltyClaims] Missing permission: saltyclaims.command.claima.delete"),messages);
    }
    @Test void bareRootUsesHelpPermissionWithoutACommonGate() {
        List<String> messages=new ArrayList<>();
        new ClaimCommands(null).onCommand(sender(Set.of("saltyclaims.command.claim.help","saltyclaims.command.claim.create"),messages),command("claim"),"claim",new String[]{});
        assertTrue(messages.contains("[SaltyClaims] /claim create")); assertFalse(messages.contains("[SaltyClaims] /claim delete"));
    }
    @Test void nestedCommandsAndSuggestionsAreFilteredIndividually() {
        ClaimCommands commands=new ClaimCommands(null); List<String> messages=new ArrayList<>();
        CommandSender sender=sender(Set.of("saltyclaims.command.claim.child.create"),messages);
        assertEquals(List.of("create"),commands.onTabComplete(sender,command("claim"),"claim",new String[]{"child",""}));
        commands.onCommand(sender,command("claim"),"claim",new String[]{"child","restrict","on"});
        assertEquals(List.of("[SaltyClaims] Missing permission: saltyclaims.command.claim.child.restrict"),messages);
    }
    @Test void deleteConfirmationHasItsOwnPermissionAndOldTopLevelRouteIsGone() {
        List<String> messages=new ArrayList<>(); ClaimCommands commands=new ClaimCommands(null);
        commands.onCommand(sender(Set.of("saltyclaims.command.claim.delete"),messages),command("claim"),"claim",new String[]{"delete","confirm"});
        assertEquals(List.of("[SaltyClaims] Missing permission: saltyclaims.command.claim.delete.confirm"),messages);
        messages.clear(); commands.onCommand(sender(Set.of(),messages),command("claim"),"claim",new String[]{"confirm","old-token"});
        assertEquals(List.of("[SaltyClaims] Unknown subcommand. Use /claim help."),messages);
    }
    @Test void confirmationKeywordCanBeUsedAsOwnerViaExplicitNameOption() {
        List<String> messages=new ArrayList<>();
        new ClaimCommands(null).onCommand(sender(Set.of(),messages),command("claima"),"claima",new String[]{"delete","--owner","confirm"});
        assertEquals(List.of("[SaltyClaims] Missing permission: saltyclaims.command.claima.delete"),messages);
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
        assertEquals(List.of("[SaltyClaims] Missing permission: saltyclaims.command.claima.reset.all.confirm"),messages);
        assertTrue(commands.onTabComplete(sender,command("claima"),"claima",new String[]{"reset","all",""}).isEmpty());
        CommandSender confirmer=sender(Set.of("saltyclaims.command.claima.reset.all.confirm"),new ArrayList<>());
        assertEquals(List.of("confirm"),commands.onTabComplete(confirmer,command("claima"),"claima",new String[]{"reset","all",""}));
    }
}
