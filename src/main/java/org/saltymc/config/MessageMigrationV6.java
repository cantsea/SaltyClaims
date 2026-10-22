package org.saltymc.config;

import java.util.Map;

/** Released destination 6. Only exact stock instructions are intentionally replaced. */
final class MessageMigrationV6 {
    private MessageMigrationV6() { }
    static void apply(Map<String,Object> document) {
        stock(document,"commands","tool",
                "Select with {selection}, inspect with {inspection}. Right-click two corners to claim immediately. Click an owned corner, then its new position, to resize.",
                "Claim with {selection}, subdivide with {child-selection}, inspect with {inspection}. Right-click two corners to create immediately. Click an owned corner with the matching tool, then its new position, to resize.");
        stock(document,"errors","selection-mode","Choose the appropriate /claim mode first.","Select with the appropriate regular or child claim tool first.");
        stock(document,"errors","select-root","Select a root with /claim mode child.","Use the child claim tool inside your parent claim.");
    }
    private static void stock(Map<String,Object> document,String section,String key,String before,String after) {
        if(document.get(section) instanceof Map<?,?>) {
            Map<String,Object> values=YamlData.map(document.get(section),"messages.yml",section);
            if(before.equals(values.get(key))) values.put(key,after);
        }
    }
}
