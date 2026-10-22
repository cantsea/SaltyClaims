package org.saltymc.config;

import org.saltymc.flag.Flag;
import java.util.*;

/** Convert legacy typed values to behavior switches; exact originals are backed up by the pipeline. */
final class FlagMigrationV7 {
    private FlagMigrationV7() { }
    static boolean enabled(Flag flag,Object value,String path) {
        if(!(value instanceof String text)) throw ConfigException.at("config.yml",path,"must be a legacy flag value");
        try { return flag.enabled(flag.validate(text)); }
        catch(IllegalArgumentException e) { throw ConfigException.at("config.yml",path,"has an invalid legacy flag value"); }
    }
    static Map<String,Object> section(Map<String,Object> document,String key) {
        return document.containsKey(key)?YamlData.map(document.get(key),"config.yml",key):Map.of();
    }
    static void apply(Map<String,Object> document) {
        validateLegacy(document);
        Map<String,Object> flags=section(document,"flags");
        Map<String,Object> defaults=section(flags,"defaults"),enforced=section(flags,"enforced");
        Map<String,Object> claims=document.containsKey("claim-flags")?YamlData.map(document.get("claim-flags"),"config.yml","claim-flags"):new LinkedHashMap<>();
        for(var values:List.of(defaults,enforced)) values.forEach((key,value)-> {
            Flag flag=WorldSettings.parseFlag(key,"config.yml","flags");
            claims.putIfAbsent(flag.key(),new LinkedHashMap<>(Map.of("default",enabled(flag,value,"flags."+key),"player-editable",false)));
        });
        // Former enforced values must take precedence over former defaults.
        enforced.forEach((key,value)-> {
            Flag flag=WorldSettings.parseFlag(key,"config.yml","flags.enforced");
            claims.put(flag.key(),new LinkedHashMap<>(Map.of("default",enabled(flag,value,"flags.enforced."+key),"player-editable",false)));
        });
        document.put("claim-flags",claims);
        document.remove("flags"); document.remove("worlds"); document.remove("disabled-worlds");
    }
    static void moveWorlds(Map<String,Map<String,Object>> documents,Collection<String> loadedWorlds) {
        Map<String,Object> config=documents.get("config.yml");
        if(YamlFileSpec.readVersion(config,"config.yml")>=7) return;
        validateLegacy(config);
        Map<String,Object> oldWorlds=section(config,"worlds"),oldFlags=section(config,"flags");
        Map<String,Object> defaults=section(oldFlags,"defaults"),enforced=section(oldFlags,"enforced");
        Object disabled=config.getOrDefault("disabled-worlds",List.of());
        if(!(disabled instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof String s)||s.isBlank()))
            throw ConfigException.at("config.yml","disabled-worlds","must be a list of nonempty strings");
        Set<String> names=new LinkedHashSet<>(loadedWorlds); names.addAll(oldWorlds.keySet());
        for(Object name:(List<?>)disabled) names.add((String)name);
        Map<String,Object> worlds=documents.get("worlds.yml");
        for(String name:names) {
            if(worlds.containsKey(name)) continue; // An explicit new-format world always wins.
            Map<String,Object> flags=new LinkedHashMap<>();
            Map<String,Object> local=oldWorlds.containsKey(name)?YamlData.map(oldWorlds.get(name),"config.yml","worlds."+name):Map.of();
            for(var source:List.of(defaults,local,enforced)) source.forEach((key,value)-> {
                Flag flag=WorldSettings.parseFlag(key,"config.yml","worlds."+name);
                flags.put(flag.key(),enabled(flag,value,"worlds."+name+"."+key));
            });
            // Only preserve claiming for legacy installations, not fresh installations.
            boolean legacy=!config.isEmpty();
            Map<String,Object> world=new LinkedHashMap<>();
            world.put("claiming",legacy&&!((List<?>)disabled).contains(name)); world.put("wilderness-flags",flags);
            worlds.put(name,world);
        }
    }
    private static void validateLegacy(Map<String,Object> document) {
        Object disabled=document.getOrDefault("disabled-worlds",List.of());
        if(!(disabled instanceof List<?> list)||list.stream().anyMatch(v->!(v instanceof String s)||s.isBlank()))
            throw ConfigException.at("config.yml","disabled-worlds","must be a list of nonempty strings");
        section(document,"worlds").forEach((world,values)->YamlData.map(values,"config.yml","worlds."+world).forEach((key,value)-> {
            Flag flag=WorldSettings.parseFlag(key,"config.yml","worlds."+world);
            enabled(flag,value,"worlds."+world+"."+key);
        }));
    }
}
