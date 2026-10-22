package org.saltymc.config;

import java.util.Map;

/** Released destination 3. Only exact former stock text is intentionally replaced. */
final class MessageMigrationV3 {
    private MessageMigrationV3() { }
    static void apply(Map<String,Object> document) {
        stock(document,"commands","confirm-delete","Delete {type} {id}? Within {seconds} seconds: /{command} delete confirm",
                "Type /{command} delete confirm to delete this {type}.");
        stock(document,"commands","mode-child","Child {mode} selection inside {id}",
                "Child {mode} selection inside {id}. Same corner Y: full height; different Y: 3D.");
        stock(document,"errors","select-root","Select a root with /claim mode child <full|3d>.","Select a root with /claim mode child.");
        stock(document,"errors","child-mode","Child mode: full or 3d.","Use /claim mode child; corner Y levels determine height.");
    }
    private static void stock(Map<String,Object> document,String section,String key,String before,String after) {
        Object value=document.get(section);
        if(value instanceof Map<?,?>) {
            Map<String,Object> values=YamlData.map(value,"messages.yml",section);
            if(before.equals(values.get(key))) values.put(key,after);
        }
    }
}
