package org.saltymc.command;

import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.saltymc.claim.*;
import org.saltymc.config.Settings;
import org.saltymc.flag.*;
import org.saltymc.message.*;
import java.util.*;
import java.util.function.Function;

/** One authorization path for listing, completion and mutation; only owners edit claim flags. */
public final class ClaimFlagCommands implements CommandExecutor,TabCompleter {
    public interface Port {
        boolean ready(); ClaimState state(); Settings settings(); Messages messages(); Claim at(Location location);
        void mutate(CommandSender sender,Function<ClaimState,ClaimState> operation,Notice success);
    }
    public static final String TOGGLE="saltyclaims.command.claimflag.toggle",LIST="saltyclaims.command.claimflags";
    private final Port plugin;
    public ClaimFlagCommands(Port plugin) { this.plugin=plugin; }
    private Messages messages() { return plugin==null?Messages.defaults():plugin.messages(); }
    private static void require(CommandSender sender,String permission) {
        if(!sender.hasPermission(permission)) throw new MessageException("errors.missing-permission","permission",permission);
    }
    private Claim target(Player player) {
        if(!plugin.ready()) throw new MessageException("errors.maintenance");
        Claim claim=plugin.at(player.getLocation());
        if(claim==null) throw new MessageException("errors.no-claim");
        if(!plugin.state().owner(claim).equals(player.getUniqueId())) throw new MessageException("errors.owner-only");
        return claim;
    }
    private List<Flag> editable(Player player) {
        if(!player.hasPermission(TOGGLE)) return List.of();
        return Arrays.stream(Flag.values()).filter(f->plugin.settings().claimFlag(f).playerEditable()
                &&player.hasPermission("saltyclaims.flag."+f.key())).toList();
    }
    private Policy.Value value(Claim claim,Flag flag,Player player) {
        return Policy.resolve(plugin.state(),claim,flag,player.getWorld().getName(),plugin.settings().claimFlags(),plugin.settings().worlds());
    }
    private void list(Player player,Claim claim) {
        List<Flag> flags=editable(player);
        if(flags.isEmpty()) { messages().send(player,"commands.no-editable-flags"); return; }
        for(Flag flag:flags) {
            Policy.Value effective=value(claim,flag,player);
            messages().send(player,"commands.flag-entry","flag",flag.key(),"value",effective.enabled(),"source",effective.source());
        }
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        try {
            boolean listing=command.getName().equalsIgnoreCase("claimflags");
            require(sender,listing?LIST:TOGGLE);
            if(!(sender instanceof Player player)) throw new MessageException("errors.player-only");
            if(listing?args.length!=0:args.length<1||args.length>2||!args[0].equalsIgnoreCase("toggle"))
                throw new MessageException("errors.usage","usage",listing?"/claimflags":"/claimflag toggle [flag]");
            Claim claim=target(player);
            if(listing||args.length==1) { list(player,claim); return true; }
            Flag flag=Flag.parse(args[1]);
            // Use the identical editable list for execution and suggestions, including typed hidden flags.
            if(!editable(player).contains(flag)) throw new MessageException("errors.flag-locked");
            boolean enabled=!value(claim,flag,player).enabled();
            plugin.mutate(sender,state->{
                Claim current=state.require(claim.id());
                if(!state.owner(current).equals(player.getUniqueId())) throw new MessageException("errors.owner-only");
                Map<Flag,String> overrides=new EnumMap<>(Flag.class); overrides.putAll(current.flags());
                overrides.put(flag,Boolean.toString(enabled));
                return ClaimService.replace(state,current.change(current.bounds(),current.inheritTrust(),current.trust(),overrides));
            },Notice.of("success.flag","flag",flag.key(),"value",enabled,"id",claim.id()));
        } catch(IllegalArgumentException|ArithmeticException e) { messages().sendError(sender,e); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args) {
        if(!command.getName().equalsIgnoreCase("claimflag")||!(sender instanceof Player player)||!sender.hasPermission(TOGGLE)) return List.of();
        try {
            target(player);
            if(editable(player).isEmpty()) return List.of();
            if(args.length==1) return "toggle".startsWith(args[0].toLowerCase(Locale.ROOT))?List.of("toggle"):List.of();
            if(args.length==2&&args[0].equalsIgnoreCase("toggle")) return editable(player).stream().map(Flag::key)
                    .filter(key->key.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        } catch(IllegalArgumentException ignored) { }
        return List.of();
    }
}
