package org.saltymc.config;

import java.util.Map;
import java.util.Objects;

/** Released destination 8. Historical snapshots are immutable inputs, not current defaults. */
final class MessageMigrationV8 {
    private MessageMigrationV8() { }
    static void apply(Map<String,Object> document) {
        Map<String,Object> before=YamlData.parse(ConfigFiles.resource("config-migrations/messages-v7.yml"),"bundled messages v7");
        Map<String,Object> after=YamlData.parse(ConfigFiles.resource("config-migrations/messages-v8.yml"),"bundled messages v8");
        replaceStock(document,before,after);
    }
    private static void replaceStock(Map<String,Object> document,Map<String,Object> before,Map<String,Object> after) {
        for(var entry:before.entrySet()) {
            String key=entry.getKey(); Object old=entry.getValue(),next=after.get(key),configured=document.get(key);
            if(old instanceof Map<?,?>&&next instanceof Map<?,?>&&configured instanceof Map<?,?>)
                replaceStock(YamlData.map(configured,"messages.yml",key),YamlData.map(old,"bundled messages v7",key),YamlData.map(next,"bundled messages v8",key));
            else if(old instanceof String&&next instanceof String&&!Objects.equals(old,next)&&Objects.equals(old,configured)) document.put(key,next);
        }
    }
}
