package org.saltymc.config;

import java.util.Map;

/** Released destination 7. Expand exact stock views while preserving customized templates. */
final class MessageMigrationV7 {
    private MessageMigrationV7() { }
    static void apply(Map<String,Object> document) {
        if(!(document.get("commands") instanceof Map<?,?>)) return;
        Map<String,Object> commands=YamlData.map(document.get("commands"),"messages.yml","commands");
        replace(commands,"list-entry","{id} area={area} world={world}","{type} in {world}: {width} x {length} ({area} blocks), height: {height}; first corner: {corner1}; second corner: {corner2}");
        replace(commands,"bounds","{bounds} area={area} children={children}","Size: {width} x {length} ({area} blocks); height: {height}; children: {children}");
        replace(commands,"trust-list","Grants on {id}: {grants}","Trust on {id}:");
    }
    private static void replace(Map<String,Object> commands,String key,String before,String after) {
        if(before.equals(commands.get(key))) commands.put(key,after);
    }
}
