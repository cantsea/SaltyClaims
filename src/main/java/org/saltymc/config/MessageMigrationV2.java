package org.saltymc.config;

import java.util.*;

/** Released destination 2. Replace obsolete stock instructions; preserve customized wording. */
final class MessageMigrationV2 {
    private MessageMigrationV2() { }
    static void apply(Map<String,Object> document) {
        stock(document,"commands.help-admin","Claim commands take an explicit claim UUID.","Claim commands take an owner's player name. Use --at <world> <x> <y> <z> when they have multiple claims.");
        stock(document,"commands.help-player","Target the claim here, or use --id <uuid> / --root.","Target the claim here, or use --owner <player>, --root or --at <world> <x> <y> <z>.");
        stock(document,"commands.tool","Select with {selection}, inspect with {inspection}. Right-click two corners, then /claim create. Click an owned corner to resize.","Select with {selection}, inspect with {inspection}. Right-click two corners to claim immediately. Click an owned corner, then its new position, to resize.");
        stock(document,"commands.confirm-delete","Delete {type} {id}? Within {seconds} seconds: /{command} confirm {token}","Delete {type} {id}? Within {seconds} seconds: /{command} delete confirm");
        stock(document,"selection.preview","Area {area}; use {command} to save.","Selected area: {area} blocks.");
        stock(document,"selection.resize","Resize selected. Select the new opposite corner, then /claim resize.","Resize selected. Right-click the new corner to resize immediately.");
        stock(document,"errors.unknown-player","Unknown cached player. Use their UUID or ask them to join first.","Unknown player. Ask them to join first.");
        stock(document,"errors.admin-target","Admin commands take the claim UUID directly after the subcommand.","Admin commands take the claim owner's player name after the subcommand.");
        stock(document,"errors.child-required","Stand inside a child claim or use --id.","Stand inside a child claim or select its location with --at <world> <x> <y> <z>.");
        stock(document,"errors.no-claim","No claim here. Use --id <uuid> to target one.","No claim here. Stand inside one or use --owner <player>.");
        stock(document,"errors.overlap","Overlapping claims: {id} / {other}","Selection overlaps an existing claim.");
        // Exact old command syntax in custom templates must not keep directing players to removed routes.
        rewriteCommands(document);
    }
    private static void stock(Map<String,Object> document,String path,String oldValue,String newValue) {
        String[] parts=path.split("\\."); Object section=document.get(parts[0]);
        if(section instanceof Map<?,?>) {
            Map<String,Object> map=YamlData.map(section,"messages.yml",parts[0]);
            if(oldValue.equals(map.get(parts[1]))) map.put(parts[1],newValue);
        }
    }
    private static void rewriteCommands(Map<String,Object> section) {
        for(var entry:section.entrySet()) {
            if(entry.getValue() instanceof Map<?,?>) rewriteCommands(YamlData.map(entry.getValue(),"messages.yml",entry.getKey()));
            else if(entry.getValue() instanceof String text) entry.setValue(text
                    .replace("/{command} confirm {token}","/{command} delete confirm")
                    .replace("/claim confirm {token}","/claim delete confirm")
                    .replace("/claima confirm {token}","/claima delete confirm")
                    .replace("--id <uuid>","--owner <player>"));
        }
    }
}
