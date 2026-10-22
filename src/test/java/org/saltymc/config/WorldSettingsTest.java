package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saltymc.flag.Flag;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class WorldSettingsTest {
    @TempDir Path directory;
    private byte[] bytes(String yaml) { return yaml.getBytes(StandardCharsets.UTF_8); }
    @Test void newWorldsHaveAllTrueFlagsAndClaimingDisabled() {
        var candidate=ConfigFiles.worldsFile(()->List.of("world","world.custom")).prepare(null);
        var worlds=WorldSettings.from(candidate.document());
        for(var world:worlds.values()) {
            assertFalse(world.claiming()); assertEquals(Flag.values().length,world.wildernessFlags().size());
            for(Flag flag:Flag.values()) assertTrue(world.allows(flag));
        }
        assertTrue(new String(candidate.output(),StandardCharsets.UTF_8).contains("world.custom:"));
    }
    @Test void reloadAddsNewWorldsAndMissingFlagsWithoutResettingSuppliedValues() {
        var spec=ConfigFiles.worldsFile(()->List.of("world","new_world"));
        var candidate=spec.prepare(bytes("world:\n  claiming: true\n  wilderness-flags:\n    block-break: false\n"));
        var worlds=WorldSettings.from(candidate.document());
        assertTrue(worlds.get("world").claiming()); assertFalse(worlds.get("world").allows(Flag.BLOCK_BREAK));
        assertTrue(worlds.get("world").allows(Flag.PVP)); assertFalse(worlds.get("new_world").claiming());
        assertFalse(spec.prepare(candidate.output()).changed());
        assertThrows(UnsupportedOperationException.class,()->worlds.get("world").wildernessFlags().put(Flag.PVP,true));
    }
    @Test void invalidFlagsAndNonBooleanValuesAreRejected() {
        for(String yaml:List.of("world: {claiming: 'false'}", "world: {wilderness-flags: {pvp: deny}}",
                "world: {wilderness-flags: {unknown: false}}", "world: {enforced: {pvp: false}}", "world: false",
                "world: null", "world: {claiming: null}", "world: {wilderness-flags: null}", "world: {wilderness-flags: {pvp: null}}"))
            assertThrows(ConfigException.class,()->ConfigFiles.worldsFile(List::of).prepare(bytes(yaml)));
    }
    @Test void freshPipelineDiscoversLoadedWorldsAndReloadPreservesAdminEdits() throws Exception {
        var names=new ArrayList<>(List.of("world"));
        var pipeline=ConfigFiles.pipeline(directory,ignored->{},UnaryOperator.identity(),()->names);
        var initial=pipeline.update(c->{},c->{});
        assertFalse(initial.settings().world("world").claiming());
        String yaml=Files.readString(directory.resolve("worlds.yml")).replace("block-break: true","block-break: false");
        Files.writeString(directory.resolve("worlds.yml"),yaml); names.add("world_nether");
        var next=pipeline.update(c->{},c->{});
        assertFalse(next.settings().world("world").allows(Flag.BLOCK_BREAK));
        assertFalse(next.settings().world("world_nether").claiming());
    }
    @Test void legacyWorldRulesMoveWithExactConfigBackupAndNewRulesWin() throws Exception {
        String legacy="flags:\n  defaults:\n    block-break: build\n  enforced:\n    pvp: deny\nworlds:\n  world.custom:\n    pvp: allow\ndisabled-worlds: [world_nether]\nconfig-version: 6\n";
        Files.writeString(directory.resolve("config.yml"),legacy);
        var pipeline=ConfigFiles.pipeline(directory,ignored->{},UnaryOperator.identity(),()->List.of("world.custom","world_nether"));
        var config=pipeline.update(c->{},c->{});
        assertTrue(config.settings().world("world.custom").claiming());
        assertTrue(config.settings().world("world.custom").allows(Flag.BLOCK_BREAK));
        assertFalse(config.settings().world("world.custom").allows(Flag.PVP));
        assertFalse(config.settings().world("world_nether").claiming());
        assertFalse(Files.readString(directory.resolve("config.yml")).contains("enforced:"));
        try(var files=Files.list(directory.resolve("config-backups"))) {
            assertTrue(files.anyMatch(p->{try { return Files.readString(p).equals(legacy); }catch(Exception e) { return false; }}));
        }
        pipeline.update(c->{},c->{});
        assertTrue(pipeline.current().settings().world("world.custom").allows(Flag.BLOCK_BREAK));
    }
    @Test void invalidWorldsRejectWholeBundleAndKeepPriorRuntime() throws Exception {
        var pipeline=ConfigFiles.pipeline(directory,ignored->{}); var prior=pipeline.update(c->{},c->{});
        byte[] before=Files.readAllBytes(directory.resolve("config.yml"));
        Files.writeString(directory.resolve("worlds.yml"),"world: {wilderness-flags: {pvp: 'false'}}");
        assertThrows(ConfigException.class,()->pipeline.update(c->{},c->{}));
        assertSame(prior,pipeline.current()); assertArrayEquals(before,Files.readAllBytes(directory.resolve("config.yml")));
    }
    @Test void retryAfterPartialMigrationKeepsMovedWorldRules() throws Exception {
        Files.writeString(directory.resolve("config.yml"),"worlds:\n  world:\n    pvp: allow\nconfig-version: 6\n");
        SafeConfigFiles files=new SafeConfigFiles() {
            boolean fail=true;
            @Override public void replace(Path temporary,Path destination,boolean existed) throws java.io.IOException {
                if(destination.getFileName().toString().equals("config.yml")&&fail) {
                    fail=false; throw new java.io.IOException("simulated failure");
                }
                super.replace(temporary,destination,existed);
            }
        };
        var pipeline=new ConfigurationPipeline<>(directory,List.of(ConfigFiles.worldsFile(()->List.of("world")),ConfigFiles.settingsFile(),ConfigFiles.messagesFile()),
                docs->Settings.from(docs.get("config.yml"),docs.get("worlds.yml")),files,ignored->{},docs->FlagMigrationV7.moveWorlds(docs,List.of("world")));
        assertThrows(ConfigException.class,()->pipeline.update(c->{},c->{}));
        assertTrue(Files.readString(directory.resolve("config.yml")).contains("config-version: 6"));
        assertTrue(Files.readString(directory.resolve("worlds.yml")).contains("pvp: true"));
        assertTrue(pipeline.update(c->{},c->{}).world("world").allows(Flag.PVP));
    }
}
