package org.saltymc.config;

import org.saltymc.flag.Flag;
import java.util.*;

/** Per-world claiming switch and independent wilderness behavior. Wilderness defaults to allowed. */
public record WorldSettings(boolean claiming, Map<Flag,Boolean> wildernessFlags) {
    public WorldSettings { wildernessFlags=Map.copyOf(wildernessFlags); }
    public static WorldSettings defaults() { return new WorldSettings(false,Map.of()); }
    public boolean allows(Flag flag) { return wildernessFlags.getOrDefault(flag,true); }
    static boolean bool(Object value,String file,String path) {
        if(!(value instanceof Boolean result)) throw ConfigException.at(file,path,"must be true or false");
        return result;
    }
    public static Map<String,WorldSettings> from(Map<String,Object> document) {
        Map<String,WorldSettings> result=new LinkedHashMap<>();
        document.forEach((name,value)-> {
            if(name.equals("config-version")) return;
            if(name.isBlank()) throw ConfigException.at("worlds.yml",name,"requires a nonempty world name");
            Map<String,Object> world=YamlData.map(value,"worlds.yml",name);
            for(String key:world.keySet()) if(!Set.of("claiming","wilderness-flags").contains(key))
                throw ConfigException.at("worlds.yml",name+"."+key,"is unknown");
            Map<Flag,Boolean> flags=new EnumMap<>(Flag.class);
            YamlData.map(world.get("wilderness-flags"),"worlds.yml",name+".wilderness-flags").forEach((key,enabled)-> {
                Flag flag=parseFlag(key,"worlds.yml",name+".wilderness-flags");
                if(flags.put(flag,bool(enabled,"worlds.yml",name+".wilderness-flags."+key))!=null)
                    throw ConfigException.at("worlds.yml",name+".wilderness-flags","contains duplicate flag identifiers");
            });
            result.put(name,new WorldSettings(bool(world.get("claiming"),"worlds.yml",name+".claiming"),flags));
        });
        return Map.copyOf(result);
    }
    static Flag parseFlag(String key,String file,String path) {
        try { return Flag.parse(key); }
        catch(IllegalArgumentException e) { throw ConfigException.at(file,path+"."+key,"references an unknown flag"); }
    }
    public static void complete(Map<String,Object> document,Collection<String> loadedWorlds) {
        for(String name:loadedWorlds) missing(document,name,new LinkedHashMap<String,Object>());
        for(var entry:document.entrySet()) {
            if(entry.getKey().equals("config-version")) continue;
            Map<String,Object> world=YamlData.map(entry.getValue(),"worlds.yml",entry.getKey());
            missing(world,"claiming",false);
            missing(world,"wilderness-flags",new LinkedHashMap<String,Object>());
            Map<String,Object> flags=YamlData.map(world.get("wilderness-flags"),"worlds.yml",entry.getKey()+".wilderness-flags");
            for(Flag flag:Flag.values()) missing(flags,flag.key(),true);
        }
    }
    static void missing(Map<String,Object> document,String key,Object fallback) {
        if(!document.containsKey(key)) document.put(key,fallback);
    }
}
