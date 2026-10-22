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
                       int sessionSeconds,int previewSeconds,Color parentBorderColor,Color childBorderColor,Set<String> disabledWorlds,Map<Flag,String> defaults,
                       Map<Flag,String> enforced,Map<String,Map<Flag,String>> worlds) {
    public Settings {
        Objects.requireNonNull(limits); Objects.requireNonNull(selectionTool); Objects.requireNonNull(childSelectionTool); Objects.requireNonNull(inspectionTool);
        Objects.requireNonNull(parentBorderColor); Objects.requireNonNull(childBorderColor);
        disabledWorlds=Set.copyOf(disabledWorlds); defaults=Map.copyOf(defaults); enforced=Map.copyOf(enforced);
        Map<String,Map<Flag,String>> copy=new LinkedHashMap<>(); worlds.forEach((key,value)->copy.put(key,Map.copyOf(value))); worlds=Map.copyOf(copy);
    }
    /** Read-only convenience; startup and reload both use ConfigFiles.pipeline. */
    public static Settings load(File file) throws Exception {
        return from(ConfigFiles.settingsFile().prepare(Files.readAllBytes(file.toPath())).document());
    }
    public static Settings from(Map<String,Object> data) {
        Map<String,Object> claims=section(data,"claims"),tools=section(data,"tools"),flagSection=section(data,"flags");
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
        Object disabled=data.get("disabled-worlds");
        if(!(disabled instanceof List<?> list)) throw ConfigException.at("config.yml","disabled-worlds","must be a list of nonempty strings");
        Set<String> disabledWorlds=new LinkedHashSet<>();
        for(int i=0;i<list.size();i++) {
            Object value=list.get(i);
            if(!(value instanceof String text)||text.isBlank()) throw ConfigException.at("config.yml","disabled-worlds["+i+"]","must be a nonempty string");
            disabledWorlds.add(text);
        }
        Map<String,Map<Flag,String>> worlds=new LinkedHashMap<>();
        for(var entry:section(data,"worlds").entrySet()) {
            if(entry.getKey().isBlank()) throw ConfigException.at("config.yml","worlds","requires nonempty world names");
            worlds.put(entry.getKey(),flags(YamlData.map(entry.getValue(),"config.yml","worlds."+entry.getKey()),"worlds."+entry.getKey()));
        }
        return new Settings(new ClaimService.Limits(starting,width,roots,children,area),select,childSelect,inspect,timeout,preview,parentColor,childColor,disabledWorlds,
                flags(YamlData.map(flagSection.get("defaults"),"config.yml","flags.defaults"),"flags.defaults"),
                flags(YamlData.map(flagSection.get("enforced"),"config.yml","flags.enforced"),"flags.enforced"),worlds);
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
    private static Map<Flag,String> flags(Map<String,Object> data,String path) {
        Map<Flag,String> values=new EnumMap<>(Flag.class);
        for(var entry:data.entrySet()) {
            Flag flag;
            try { flag=Flag.parse(entry.getKey()); }
            catch(IllegalArgumentException e) { throw ConfigException.at("config.yml",path+"."+entry.getKey(),"references an unknown flag"); }
            if(!(entry.getValue() instanceof String text)||!flag.valuesAllowed().contains(text.toLowerCase(Locale.ROOT)))
                throw ConfigException.at("config.yml",path+"."+entry.getKey(),"must be a string from: "+String.join(", ",flag.valuesAllowed()));
            if(values.put(flag,text.toLowerCase(Locale.ROOT))!=null) throw ConfigException.at("config.yml",path,"contains duplicate flag identifiers ignoring case");
        }
        return Map.copyOf(values);
    }
}
