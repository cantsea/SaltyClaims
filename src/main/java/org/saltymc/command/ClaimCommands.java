package org.saltymc.command;

import org.saltymc.message.MessageException;
import org.saltymc.message.Messages;
import org.saltymc.message.Notice;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.permissions.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.saltymc.SaltyClaims;
import org.saltymc.claim.*;
import org.saltymc.flag.*;
import org.saltymc.selection.SelectionManager.Session;
import org.saltymc.trust.*;
import java.util.*;

/** All executable paths are independently permission-gated; roots have no blanket gate. */
public final class ClaimCommands implements CommandExecutor, TabCompleter, Listener {
    public static final List<String> PLAYER_PATHS = List.of("help", "tool", "pos1", "pos2", "create", "resize", "cancel", "info", "list", "delete", "confirm",
            "trust", "untrust", "trustlist", "flags", "flag", "explain", "mode.basic", "mode.child", "child.create", "child.restrict");
    public static final List<String> ADMIN_PATHS = List.of("help", "inspect", "bypass", "delete", "resize", "trust", "untrust", "trustlist", "flag", "flags",
            "balance.show", "balance.set", "balance.add", "balance.remove", "reload", "backup", "confirm");
    private record Confirmation(UUID claim, Map<UUID, Claim> expected, long expires, String permission, boolean admin) { }
    private final SaltyClaims plugin;
    private final Map<String, Map<String, Confirmation>> confirmations = new HashMap<>();
    public ClaimCommands(SaltyClaims plugin) { this.plugin = plugin; }
    private Messages messages() { return plugin==null?Messages.defaults():plugin.messages(); }
    public void expire() {
        long now=System.currentTimeMillis();
        confirmations.values().forEach(pending->pending.values().removeIf(c->c.expires()<now));
        confirmations.values().removeIf(Map::isEmpty);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { confirmations.remove(identity(event.getPlayer())); }
    public static String permission(boolean admin, String path) { return "saltyclaims.command." + (admin?"claima.":"claim.") + path; }
    public void registerPermissions() {
        for (boolean admin : List.of(false,true)) for (String path : admin?ADMIN_PATHS:PLAYER_PATHS)
            register(permission(admin,path), admin?PermissionDefault.OP:PermissionDefault.TRUE);
        for (Flag flag : Flag.values()) register("saltyclaims.flag." + flag.key(),PermissionDefault.TRUE);
        register("saltyclaims.admin.recovery",PermissionDefault.OP);
    }
    private static void register(String node, PermissionDefault value) {
        if (Bukkit.getPluginManager().getPermission(node) == null) Bukkit.getPluginManager().addPermission(new Permission(node,value));
    }
    private static void require(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) throw new MessageException("errors.missing-permission","permission",permission);
    }
    private static Player player(CommandSender sender) {
        if (!(sender instanceof Player p)) throw new MessageException("errors.player-only"); return p;
    }
    private static String arg(List<String> args, int i, String usage) {
        if (args.size() <= i) throw new MessageException("errors.usage","usage",usage); return args.get(i);
    }
    private static boolean toggle(String value) {
        if (!value.equalsIgnoreCase("on") && !value.equalsIgnoreCase("off")) throw new MessageException("errors.toggle");
        return value.equalsIgnoreCase("on");
    }
    private static UUID uuid(String value) {
        try { return UUID.fromString(value); } catch (IllegalArgumentException e) { throw new MessageException("errors.uuid","value",value); }
    }
    private UUID resolvePlayer(String value) {
        Player online = Bukkit.getPlayerExact(value);
        if (online != null) return online.getUniqueId();
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(value);
        if (cached == null) throw new MessageException("errors.unknown-player");
        return cached.getUniqueId();
    }
    private void owner(CommandSender sender, ClaimState state, Claim c, boolean admin) {
        if (!admin && !state.owner(c).equals(player(sender).getUniqueId())) throw new MessageException("errors.owner-only");
    }
    private void manager(CommandSender sender, Claim c, boolean admin) {
        if (!admin && !plugin.state().trust(c,player(sender).getUniqueId()).grants(Trust.MANAGE)) throw new MessageException("errors.manage-required");
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] raw) {
        boolean admin = command.getName().equalsIgnoreCase("claima");
        try {
            List<String> a = new ArrayList<>(Arrays.asList(raw));
            UUID explicit = null; boolean rootScope = false;
            for (int i=0;i<a.size();) {
                if (a.get(i).equalsIgnoreCase("--root")) { rootScope=true; a.remove(i); }
                else if (a.get(i).equalsIgnoreCase("--id")) { explicit=uuid(arg(a,i+1,"--id <uuid>")); a.remove(i+1); a.remove(i); }
                else i++;
            }
            if(admin&&(explicit!=null||rootScope)) throw new MessageException("errors.admin-target");
            if (a.isEmpty()) a.add("help");
            String path = a.getFirst().toLowerCase(Locale.ROOT);
            if (Set.of("mode","child","balance").contains(path)) path += "." + arg(a,1,"/"+label+" "+path+" <subcommand>").toLowerCase(Locale.ROOT);
            if (!(admin?ADMIN_PATHS:PLAYER_PATHS).contains(path)) throw new MessageException("errors.unknown-command","command",label);
            String node = permission(admin,path); require(sender,node);
            if (path.equals("help")) {
                for (String route : admin?ADMIN_PATHS:PLAYER_PATHS) if (sender.hasPermission(permission(admin,route))) messages().send(sender,"commands.help-line","usage",usage(admin,route));
                messages().send(sender,admin?"commands.help-admin":"commands.help-player"); return true;
            }
            if (path.equals("reload")) { plugin.reloadSettings(); messages().send(sender,"commands.reloaded"); return true; }
            if (!plugin.ready()) throw new MessageException("errors.maintenance");
            if (admin && path.equals("backup")) { plugin.backup(sender); return true; }
            if (admin && path.equals("bypass")) { Player p=player(sender); plugin.setBypass(p,toggle(arg(a,1,"/claima bypass <on|off>"))); messages().send(sender,"commands.bypass","enabled",plugin.bypass(p)); return true; }
            if (path.equals("confirm")) { confirm(sender,arg(a,1,"/"+label+" confirm <token>"),admin); return true; }
            if (path.equals("cancel")) { plugin.selections().cancel(player(sender)); confirmations.remove(identity(sender)); messages().send(sender,"commands.cancelled"); return true; }
            if (path.equals("tool")) { messages().send(sender,"commands.tool","selection",plugin.settings().selectionTool(),"inspection",plugin.settings().inspectionTool()); return true; }
            if (path.startsWith("balance.")) { balance(sender,a,path.substring(8)); return true; }
            if (path.equals("list")) {
                UUID who=player(sender).getUniqueId();
                plugin.state().claims().values().stream().filter(c->c.root()&&c.owner().equals(who)).forEach(c->messages().send(sender,"commands.list-entry","id",c.id(),"area",c.bounds().area(),"world",c.world()));
                long total=plugin.state().accounts().getOrDefault(who,new ClaimState.Account(0,0)).total(plugin.settings().limits().starting());
                messages().send(sender,"commands.allowance","used",plugin.state().used(who),"total",total); return true;
            }
            if (path.equals("pos1")||path.equals("pos2")) { Player p=player(sender); plugin.selections().set(p,path.equals("pos1"),p.getLocation().getBlock().getLocation()); return true; }
            if (path.equals("mode.basic")) { Player p=player(sender); plugin.selections().cancel(p); plugin.selections().session(p).mode="basic"; messages().send(sender,"commands.mode-basic"); return true; }
            if (path.equals("mode.child")) {
                Player p=player(sender); String mode=arg(a,2,"/claim mode child <full|3d>");
                if (!Set.of("full","3d").contains(mode)) throw new MessageException("errors.child-mode");
                Claim parent=target(sender,explicit,rootScope,false,a); owner(sender,plugin.state(),parent,false);
                if (!parent.root()) throw new MessageException("errors.root-required");
                plugin.selections().cancel(p); Session s=plugin.selections().session(p); s.mode=mode; s.parent=parent.id();
                messages().send(sender,"commands.mode-child","mode",mode,"id",parent.id()); return true;
            }
            if (path.equals("create")||path.equals("child.create")) { create(player(sender),path.equals("child.create")); return true; }
            Claim claim=target(sender,explicit,rootScope,admin,a);
            if (path.equals("info")||path.equals("inspect")) { info(sender,claim,admin); return true; }
            if (path.equals("resize")) { resize(sender,claim,admin,a); return true; }
            if (path.equals("delete")) { owner(sender,plugin.state(),claim,admin); requestDelete(sender,claim,node,admin); return true; }
            if (path.equals("trust")||path.equals("untrust")) { trust(sender,claim,a,admin,path.equals("untrust")); return true; }
            if (path.equals("trustlist")) {
                manager(sender,claim,admin); showTrust(sender,claim);
                if (!claim.root()) { messages().send(sender,"commands.inherit-trust","enabled",claim.inheritTrust()); if(claim.inheritTrust()) showTrust(sender,plugin.state().root(claim)); }
                return true;
            }
            if (path.equals("child.restrict")) {
                owner(sender,plugin.state(),claim,false); if(claim.root()) throw new MessageException("errors.child-required");
                boolean restricted=toggle(arg(a,2,"/claim child restrict <on|off>"));
                plugin.mutate(sender,s->ClaimService.replace(s,claim.change(claim.bounds(),!restricted,claim.trust(),claim.flags())),Notice.of("success.restricted","enabled",restricted,"id",claim.id())); return true;
            }
            if (path.equals("flags")||path.equals("explain")) {
                manager(sender,claim,admin); World world=world(claim);
                if(path.equals("explain")) {
                    Flag f=Flag.parse(arg(a,1,"/claim explain <flag> [player]")); UUID who=a.size()>2?resolvePlayer(a.get(2)):player(sender).getUniqueId();
                    Policy.Value value=plugin.value(claim,f,world);
                    messages().send(sender,"commands.explain","flag",f.key(),"value",value.value(),"source",value.source(),"allowed",Policy.check(plugin.state(),claim,who,f,value,false).allowed(),"trust",plugin.state().trust(claim,who));
                } else for(Flag f:Flag.values()) messages().send(sender,"commands.flag-entry","flag",f.key(),"value",plugin.value(claim,f,world).value(),"source",plugin.value(claim,f,world).source());
                return true;
            }
            if (path.equals("flag")) {
                owner(sender,plugin.state(),claim,admin); int offset=admin?2:1;
                Flag flag=Flag.parse(arg(a,offset,"/"+label+" flag "+(admin?"<id> ":"")+"<flag> <value|reset>"));
                if(!admin) require(sender,"saltyclaims.flag."+flag.key());
                if(plugin.settings().enforced().containsKey(flag)) throw new MessageException("errors.flag-enforced");
                String value=arg(a,offset+1,"Provide a value or reset.").toLowerCase(Locale.ROOT);
                Map<Flag,String> overrides=new EnumMap<>(Flag.class); overrides.putAll(claim.flags());
                if(value.equals("reset")) overrides.remove(flag); else overrides.put(flag,flag.validate(value));
                plugin.mutate(sender,s->ClaimService.replace(s,claim.change(claim.bounds(),claim.inheritTrust(),claim.trust(),overrides)),Notice.of("success.flag","flag",flag.key(),"value",value,"id",claim.id())); return true;
            }
        } catch (IllegalArgumentException|ArithmeticException e) { messages().sendError(sender,e); }
        catch (Exception e) { plugin.getLogger().log(java.util.logging.Level.SEVERE,"Command failure",e); messages().send(sender,"errors.action-failed"); }
        return true;
    }
    private static String usage(boolean admin,String path) {
        String base="/"+(admin?"claima":"claim")+" "+path.replace('.',' ');
        return base+switch(path) {
            case "trust" -> (admin?" <id>":"")+" <player|public> [access|container|build|manage]";
            case "untrust" -> (admin?" <id>":"")+" <player|public|all>";
            case "flag" -> (admin?" <id>":"")+" <key> <value|reset>";
            case "info" -> " [id]";
            case "inspect","delete","flags","trustlist" -> admin?" <id>":"";
            case "resize" -> admin?" <id> [minX minZ maxX maxZ [minY maxY]]":"";
            case "bypass","child.restrict" -> " <on|off>";
            case "mode.child" -> " <full|3d>";
            case "confirm" -> " <token>";
            case "explain" -> " <flag> [player]";
            case "balance.show" -> " <player>";
            case "balance.set","balance.add","balance.remove" -> " <player> <amount>";
            default -> "";
        };
    }
    private Claim target(CommandSender sender, UUID explicit, boolean root, boolean admin, List<String> args) {
        Claim c;
        if(explicit!=null) c=plugin.state().require(explicit);
        else if(admin) c=plugin.state().require(uuid(arg(args,1,"Provide a claim UUID.")));
        else if(args.getFirst().equals("info")&&args.size()>1) c=plugin.state().require(uuid(args.get(1)));
        else if(args.getFirst().equals("resize")&&plugin.selections().session(player(sender)).resize!=null)
            c=plugin.state().require(plugin.selections().session(player(sender)).resize);
        else c=plugin.at(player(sender).getLocation());
        if(c==null) throw new MessageException("errors.no-claim");
        return root?plugin.state().root(c):c;
    }
    private World world(Claim c) { World w=Bukkit.getWorld(c.world()); if(w==null) throw new MessageException("errors.world-unloaded"); return w; }
    private void validateWorld(World world, Bounds bounds) {
        if(plugin.settings().disabledWorlds().contains(world.getName())) throw new MessageException("errors.world-disabled");
        if(!bounds.fullHeight()&&(bounds.minY()<world.getMinHeight()||bounds.maxY()>=world.getMaxHeight())) throw new MessageException("errors.world-height");
        for(int x:new int[]{bounds.minX(),bounds.maxX()}) for(int z:new int[]{bounds.minZ(),bounds.maxZ()})
            if(!world.getWorldBorder().isInside(new Location(world,x+0.5,world.getMinHeight(),z+0.5))) throw new MessageException("errors.world-border");
    }
    private void create(Player p, boolean child) {
        Session selection=plugin.selections().session(p);
        if(child==selection.mode.equals("basic")) throw new MessageException("errors.selection-mode");
        Bounds bounds=plugin.selections().bounds(p); World world=selection.first.getWorld(); validateWorld(world,bounds);
        UUID parent=null;
        if(child) {
            if(selection.parent==null) throw new MessageException("errors.select-root");
            Claim root=plugin.state().require(selection.parent); owner(p,plugin.state(),root,false);
            if(!root.root()||!root.world().equals(world.getUID())) throw new MessageException("errors.child-world"); parent=root.id();
        }
        Claim claim=new Claim(UUID.randomUUID(),world.getUID(),child?null:p.getUniqueId(),parent,bounds,true,System.currentTimeMillis(),0,Map.of(),Map.of());
        plugin.mutate(p,s->ClaimService.put(s,claim,plugin.settings().limits(),false),Notice.of("success.created","type",messages().text(child?"labels.child":"labels.root"),"id",claim.id()));
        plugin.selections().cancel(p);
    }
    private void resize(CommandSender sender, Claim claim, boolean admin, List<String> args) {
        owner(sender,plugin.state(),claim,admin); Bounds b;
        if(admin&&args.size()>=6) {
            b=new Bounds(Integer.parseInt(args.get(2)),Integer.parseInt(args.get(4)),Integer.parseInt(args.get(3)),Integer.parseInt(args.get(5)),
                    args.size()>7?Integer.valueOf(args.get(6)):claim.bounds().minY(),args.size()>7?Integer.valueOf(args.get(7)):claim.bounds().maxY());
        } else {
            Player p=player(sender); Session session=plugin.selections().session(p);
            if(session.resize!=null&&(!session.resize.equals(claim.id())||session.revision!=claim.revision())) throw new MessageException("errors.resize-changed");
            b=plugin.selections().bounds(p);
            if(!session.first.getWorld().getUID().equals(claim.world())) throw new MessageException("errors.selection-world");
        }
        if(claim.root()) b=new Bounds(b.minX(),b.maxX(),b.minZ(),b.maxZ(),null,null);
        validateWorld(world(claim),b); Claim replacement=claim.change(b,claim.inheritTrust(),claim.trust(),claim.flags());
        plugin.mutate(sender,s->ClaimService.put(s,replacement,plugin.settings().limits(),admin),Notice.of("success.resized","id",claim.id()));
        if(sender instanceof Player p) plugin.selections().cancel(p);
    }
    private void trust(CommandSender sender, Claim claim, List<String> args, boolean admin, boolean remove) {
        manager(sender,claim,admin); int offset=admin?2:1; String target=arg(args,offset,"Provide a player, UUID, or public.");
        boolean all=remove&&target.equalsIgnoreCase("all");
        Subject subject=all||target.equalsIgnoreCase("public")?Subject.PUBLIC:new Subject(resolvePlayer(target));
        Trust level=remove?null:Trust.parse(args.size()>offset+1?args.get(offset+1):"build");
        Map<Subject,Trust> grants=TrustRules.edit(plugin.state(),claim,sender instanceof Player p?p.getUniqueId():null,admin,subject,level,remove,all);
        plugin.mutate(sender,s->ClaimService.replace(s,claim.change(claim.bounds(),claim.inheritTrust(),grants,claim.flags())),
                Notice.of("success.trust","id",claim.id()));
    }
    private void showTrust(CommandSender sender, Claim claim) { messages().send(sender,"commands.trust-list","id",claim.id(),"grants",claim.trust()); }
    private void info(CommandSender sender, Claim claim, boolean admin) {
        messages().send(sender,"commands.info","type",messages().text(claim.root()?"labels.root":"labels.child"),"id",claim.id(),"owner",plugin.state().owner(claim),"parent",claim.parent()==null?messages().text("labels.none"):claim.parent());
        messages().send(sender,"commands.bounds","bounds",claim.bounds(),"area",claim.bounds().area(),"children",plugin.state().children(claim.id()).size());
        if(sender instanceof Player p) {
            messages().send(sender,"commands.your-trust","trust",plugin.state().trust(claim,p.getUniqueId()));
            World world=Bukkit.getWorld(claim.world()); if(world!=null) plugin.selections().preview(p,claim.bounds(),world);
        }
        if(admin) {
            showTrust(sender,claim); messages().send(sender,"commands.local-flags","flags",claim.flags());
            if(!claim.root()) { messages().send(sender,"commands.inherit-trust","enabled",claim.inheritTrust()); showTrust(sender,plugin.state().root(claim)); messages().send(sender,"commands.root-flags","flags",plugin.state().root(claim).flags()); }
        }
    }
    private Map<UUID,Claim> tree(Claim c) {
        Map<UUID,Claim> expected=new HashMap<>(); expected.put(c.id(),c);
        if(c.root()) for(Claim child:plugin.state().children(c.id())) expected.put(child.id(),child);
        return Map.copyOf(expected);
    }
    private static String identity(CommandSender sender) { return sender instanceof Player p?p.getUniqueId().toString():"console:"+sender.getName(); }
    private void requestDelete(CommandSender sender, Claim claim, String permission, boolean admin) {
        String token=UUID.randomUUID().toString();
        Map<String,Confirmation> pending=confirmations.computeIfAbsent(identity(sender),ignored->new HashMap<>()); pending.clear();
        pending.put(token,new Confirmation(claim.id(),tree(claim),System.currentTimeMillis()+30000,permission,admin));
        messages().send(sender,"commands.confirm-delete","type",messages().text(claim.root()?"labels.root-tree":"labels.child"),"id",claim.id(),"seconds",30,"command",admin?"claima":"claim","token",token);
    }
    private void confirm(CommandSender sender,String token,boolean admin) {
        Map<String,Confirmation> pending=confirmations.getOrDefault(identity(sender),new HashMap<>()); Confirmation c=pending.get(token);
        if(c==null||c.expires()<System.currentTimeMillis()||c.admin()!=admin) throw new MessageException("errors.confirmation");
        require(sender,c.permission()); Claim current=plugin.state().require(c.claim()); owner(sender,plugin.state(),current,admin);
        if(!tree(current).equals(c.expected())) throw new MessageException("errors.tree-changed");
        plugin.mutate(sender,s->ClaimService.delete(s,current.id()),Notice.of("success.deleted","id",current.id())); pending.remove(token);
    }
    private void balance(CommandSender sender,List<String> a,String operation) {
        UUID target=resolvePlayer(arg(a,2,"/claima balance "+operation+" <player> [amount]"));
        ClaimState.Account before=plugin.state().accounts().getOrDefault(target,new ClaimState.Account(0,0));
        if(operation.equals("show")) { messages().send(sender,"commands.balance","player",target,"starting",plugin.settings().limits().starting(),"earned",before.earned(),"bonus",before.bonus(),"used",plugin.state().used(target)); return; }
        long amount=Long.parseLong(arg(a,3,"Provide a nonnegative amount.")); if(amount<0) throw new MessageException("errors.negative-amount");
        long bonus=switch(operation) { case "set"->amount; case "add"->Math.addExact(before.bonus(),amount); case "remove"->Math.subtractExact(before.bonus(),amount); default->throw new MessageException("errors.balance-operation"); };
        ClaimState.Account account=new ClaimState.Account(before.earned(),bonus);
        if(account.total(plugin.settings().limits().starting())<plugin.state().used(target)) throw new MessageException("errors.allowance-used");
        plugin.mutate(sender,s->{ Map<UUID,ClaimState.Account> accounts=new HashMap<>(s.accounts()); accounts.put(target,account); return new ClaimState(s.claims(),accounts); },Notice.of("success.balance","player",target,"bonus",bonus));
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args) {
        boolean admin=command.getName().equalsIgnoreCase("claima"); List<String> paths=admin?ADMIN_PATHS:PLAYER_PATHS;
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        List<String> options=new ArrayList<>();
        String head=args.length>1?String.join(".",Arrays.copyOf(args,args.length-1)).toLowerCase(Locale.ROOT)+".":"";
        for(String path:paths) if(sender.hasPermission(permission(admin,path))&&path.startsWith(head)) {
            String remainder=path.substring(head.length()); options.add(remainder.split("\\.")[0]);
        }
        if(args.length>1&&(args[0].equals("flag")||args[0].equals("explain"))&&sender.hasPermission(permission(admin,args[0]))) {
            int flagIndex=admin?2:1;
            if(args.length-1==flagIndex) for(Flag f:Flag.values()) options.add(f.key());
            if(args.length-1==flagIndex+1) try { options.addAll(Flag.parse(args[flagIndex]).valuesAllowed()); options.add("reset"); } catch(IllegalArgumentException ignored) { }
        }
        if(!admin&&args.length==3&&args[0].equals("trust")&&sender.hasPermission(permission(false,"trust"))) options.addAll(List.of("access","container","build","manage"));
        if(!admin&&args.length==3&&args[0].equals("mode")&&args[1].equals("child")&&sender.hasPermission(permission(false,"mode.child"))) options.addAll(List.of("full","3d"));
        return options.stream().filter(s->s.startsWith(prefix)).distinct().sorted().toList();
    }
}
