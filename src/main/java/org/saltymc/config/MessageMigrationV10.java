package org.saltymc.config;

import java.util.Map;

/** Released destination 10. Rename tool guidance before fixed-schema pruning. */
final class MessageMigrationV10 {
    private MessageMigrationV10() { }
    static void apply(Map<String,Object> document) {
        MigrationStep.rename(document,"commands.tool","commands.help-tools","messages.yml");
        if(document.get("commands") instanceof Map<?,?>) {
            Map<String,Object> commands=YamlData.map(document.get("commands"),"messages.yml","commands");
            if("Claims: {selection}. Child claims: {child-selection}. Inspect: {inspection}. Right-click two corners to claim, or an owned corner to resize.".equals(commands.get("help-tools")))
                commands.put("help-tools","Tools: {selection} for claims, {child-selection} for child claims, {inspection} to inspect.");
            if("Commands apply to the claim you are standing in.".equals(commands.get("help-player")))
                commands.put("help-player","Right-click two opposite corners to create a claim.\nUse the child claim tool inside your own claim. Same Y: full height; different Y: 3D.\nTo resize, click an owned corner with its tool, then the new corner.\nSwitching away or a failed attempt clears your selection.\nStand in a claim to use trust and management commands.");
        }
    }
}
