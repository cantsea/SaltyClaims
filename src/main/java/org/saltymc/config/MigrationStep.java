package org.saltymc.config;

import java.util.*;
import java.util.function.Consumer;

/** Destination numbers must be consecutive. Released actions/descriptions are append-only. */
public record MigrationStep(int destination,String description,Consumer<Map<String,Object>> action) {
    public MigrationStep { if(destination<1||description.isBlank()) throw new IllegalArgumentException("Invalid migration declaration"); Objects.requireNonNull(action); }
    public static void rename(Map<String,Object> document,String oldPath,String newPath,String filename) {
        List<String> oldParts=List.of(oldPath.split("\\.")),newParts=List.of(newPath.split("\\."));
        if(oldPath.equals(newPath)||newPath.startsWith(oldPath+".")||oldPath.startsWith(newPath+"."))
            throw new IllegalArgumentException("Migration rename paths cannot contain each other");
        Map<String,Object> source=parent(document,oldParts,false,filename);
        String oldKey=oldParts.getLast();
        if(source==null||!source.containsKey(oldKey)) return;
        Map<String,Object> target=parent(document,newParts,true,filename);
        String newKey=newParts.getLast(); Object value=source.get(oldKey);
        if(target.containsKey(newKey)&&!YamlData.equivalent(value,target.get(newKey)))
            throw ConfigException.at(filename,oldPath+" -> "+newPath,"has conflicting supplied values; resolve the conflict before retrying");
        target.put(newKey,YamlData.copy(value)); source.remove(oldKey);
    }
    private static Map<String,Object> parent(Map<String,Object> root,List<String> parts,boolean create,String file) {
        Map<String,Object> current=root;
        for(int i=0;i<parts.size()-1;i++) {
            String part=parts.get(i);
            if(!current.containsKey(part)) { if(!create) return null; current.put(part,new LinkedHashMap<String,Object>()); }
            current=YamlData.map(current.get(part),file,String.join(".",parts.subList(0,i+1)));
        }
        return current;
    }
}
