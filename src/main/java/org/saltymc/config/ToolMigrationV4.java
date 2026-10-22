package org.saltymc.config;

import java.util.Map;

/** Released destination 4. Replace the complete former stock set, never a customized set. */
final class ToolMigrationV4 {
    private ToolMigrationV4() { }
    static void apply(Map<String,Object> document) {
        Object value=document.get("tools");
        if(value==null) return; // Reconciliation supplies current defaults when no tools were configured.
        Map<String,Object> tools=YamlData.map(value,"config.yml","tools");
        if(!former(tools,"selection","GOLDEN_SHOVEL")||!former(tools,"child-selection","IRON_SHOVEL")||!former(tools,"inspection","STICK")) return;
        tools.put("selection","STICK"); tools.put("child-selection","BONE"); tools.put("inspection","FEATHER");
    }
    private static boolean former(Map<String,Object> tools,String key,String expected) {
        return !tools.containsKey(key)||expected.equals(tools.get(key));
    }
}
