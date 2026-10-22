package org.saltymc.config;

import org.bukkit.Material;
import org.bukkit.Color;
import org.saltymc.claim.ClaimService;
import org.saltymc.flag.Flag;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Fully validated immutable settings; supplied values never use coercing/fallback getters. */
public record Settings(ClaimService.Limits limits,Material selectionTool,Material childSelectionTool,Material inspectionTool,
                       int sessionSeconds,int previewSeconds,Color parentBorderColor,Color childBorderColor,Map<Flag,ClaimFlag> claimFlags,Map<String,WorldSettings> worlds,
                       boolean serverManagesClaims) {
    public Settings {
        Objects.requireNonNull(limits); Objects.requireNonNull(selectionTool); Objects.requireNonNull(childSelectionTool); Objects.requireNonNull(inspectionTool);
        Objects.requireNonNull(parentBorderColor); Objects.requireNonNull(childBorderColor);
        claimFlags=Map.copyOf(claimFlags); worlds=Map.copyOf(worlds);
    }
    /** Read-only convenience; startup and reload both use ConfigFiles.pipeline. */
    public static Settings load(File file) throws Exception {
        return from(ConfigFiles.settingsFile().prepare(Files.readAllBytes(file.toPath())).document());
    }
    public record ClaimFlag(boolean defaultValue,boolean playerEditable) { }
    public ClaimFlag claimFlag(Flag flag) { return claimFlags.getOrDefault(flag,new ClaimFlag(flag.actor(),false)); }
    public WorldSettings world(String name) { return worlds.getOrDefault(name,WorldSettings.defaults()); }
    public static Settings from(Map<String,Object> data) { return from(data,Map.of()); }
    public static Settings from(Map<String,Object> data,Map<String,Object> worldDocument) {
        Map<String,Object> claims=section(data,"claims"),tools=section(data,"tools");
        long starting=integer(claims,"starting-blocks","claims.starting-blocks",0,Long.MAX_VALUE);
        int width=(int)integer(claims,"minimum-width","claims.minimum-width",1,1000000);
        int roots=(int)integer(claims,"maximum-roots","claims.maximum-roots",1,1000000);
        int children=(int)integer(claims,"maximum-children","claims.maximum-children",0,1000000);
        long area=integer(claims,"maximum-area","claims.maximum-area",1,Long.MAX_VALUE);
        Material select=material(tools,"selection"),childSelect=material(tools,"child-selection"),inspect=material(tools,"inspection");
        if(select==inspect||select==childSelect||childSelect==inspect) throw ConfigException.at("config.yml","tools","requires distinct selection, child-selection and inspection items");
        int timeout=(int)integer(data,"selection-timeout-seconds","selection-timeout-seconds",10,3600);
        int preview=(int)integer(data,"particle-duration-seconds","particle-duration-seconds",0,300);
        Map<String,Object> colors=section(data,"border-colors");
        Color parentColor=color(colors,"parent"),childColor=color(colors,"child");
        return new Settings(new ClaimService.Limits(starting,width,roots,children,area),select,childSelect,inspect,timeout,preview,parentColor,childColor,
                flags(section(data,"claim-flags")),WorldSettings.from(worldDocument),WorldSettings.bool(data.get("server-manages-claims"),"config.yml","server-manages-claims"));
    }
    public static void completeFlags(Map<String,Object> document) {
        WorldSettings.missing(document,"claim-flags",new LinkedHashMap<String,Object>());
        Map<String,Object> flags=section(document,"claim-flags");
        for(Flag flag:Flag.values()) {
            WorldSettings.missing(flags,flag.key(),new LinkedHashMap<String,Object>());
            Map<String,Object> settings=YamlData.map(flags.get(flag.key()),"config.yml","claim-flags."+flag.key());
            WorldSettings.missing(settings,"default",flag.actor()); WorldSettings.missing(settings,"player-editable",false);
        }
    }

    private static Map<String,Object> section(Map<String,Object> data,String key) { return YamlData.map(data.get(key),"config.yml",key); }
    private static Color color(Map<String,Object> colors,String key) {
        Object value=colors.get(key);
        if(!(value instanceof String text)||!text.matches("#[0-9a-fA-F]{6}"))
            throw ConfigException.at("config.yml","border-colors."+key,"must be a quoted hex color such as '#FFFF00'");
        return Color.fromRGB(Integer.parseInt(text.substring(1),16));
    }
    private static long integer(Map<String,Object> data,String key,String path,long min,long max) {
        Object value=data.get(key);
        if(!(value instanceof Integer)&&!(value instanceof Long)) throw ConfigException.at("config.yml",path,"must be an integer in range "+min+".."+max);
        long number=((Number)value).longValue();
        if(number<min||number>max) throw ConfigException.at("config.yml",path,"must be in range "+min+".."+max);
        return number;
    }
    private static Material material(Map<String,Object> tools,String key) {
        Object value=tools.get(key);
        if(!(value instanceof String text)) throw ConfigException.at("config.yml","tools."+key,"must be an item identifier string");
        Material material=Material.matchMaterial(text);
        if(material==null||material==Material.AIR||material==Material.CAVE_AIR||material==Material.VOID_AIR||material.name().startsWith("LEGACY_"))
            throw ConfigException.at("config.yml","tools."+key,"must reference a valid non-air item identifier");
        return material;
    }
    /** Item eligibility requires Paper registries; run as preflight before saving. */
    public void validateLiveTools() {
        if(!selectionTool.isItem()) throw ConfigException.at("config.yml","tools.selection","must reference an item");
        if(!childSelectionTool.isItem()) throw ConfigException.at("config.yml","tools.child-selection","must reference an item");
        if(!inspectionTool.isItem()) throw ConfigException.at("config.yml","tools.inspection","must reference an item");
    }
    private static Map<Flag,ClaimFlag> flags(Map<String,Object> data) {
        Map<Flag,ClaimFlag> values=new EnumMap<>(Flag.class);
        data.forEach((key,value)-> {
            String path="claim-flags."+key;
            Flag flag=WorldSettings.parseFlag(key,"config.yml","claim-flags");
            Map<String,Object> settings=YamlData.map(value,"config.yml",path);
            for(String field:settings.keySet()) if(!Set.of("default","player-editable").contains(field))
                throw ConfigException.at("config.yml",path+"."+field,"is unknown");
            ClaimFlag setting=new ClaimFlag(WorldSettings.bool(settings.get("default"),"config.yml",path+".default"),
                    WorldSettings.bool(settings.get("player-editable"),"config.yml",path+".player-editable"));
            if(values.put(flag,setting)!=null) throw ConfigException.at("config.yml",path,"contains duplicate flag identifiers");
        });
        return Map.copyOf(values);
    }
}
