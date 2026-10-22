package org.saltymc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.saltymc.flag.Flag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationPipelineTest {
    @TempDir Path directory;
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static List<MigrationStep> adoption() { return List.of(new MigrationStep(1,"Adopt metadata without changing values",data->{})); }
    private YamlFileSpec spec(String name,String defaults,List<MigrationStep> steps,Set<String> open) {
        return new YamlFileSpec(name,bytes(defaults),steps,open,data->{});
    }
    private ConfigurationPipeline<Map<String,Map<String,Object>>> pipeline(List<YamlFileSpec> specs,SafeConfigFiles files,List<String> logs) {
        return new ConfigurationPipeline<>(directory,specs,documents->documents,files,logs::add);
    }
    private Map<String,Map<String,Object>> run(ConfigurationPipeline<Map<String,Map<String,Object>>> pipeline) { return pipeline.update(candidate->{},candidate->{}); }
    private Path write(String name,String content) throws IOException { Path file=directory.resolve(name); Files.writeString(file,content); return file; }
    private List<Path> backups() throws IOException {
        Path path=directory.resolve("config-backups");
        if(!Files.exists(path)) return List.of();
        try(var entries=Files.list(path)) { return entries.toList(); }
    }
    @Test void freshInstallCopiesCurrentDefaultsExactlyWithIndependentVersionsAtBottom() throws Exception {
        ConfigurationPipeline<ConfigFiles.RuntimeConfiguration> pipeline=ConfigFiles.pipeline(directory,ignored->{});
        ConfigFiles.RuntimeConfiguration snapshot=pipeline.update(candidate->{},candidate->{});
        assertEquals(10000,snapshot.settings().limits().starting());
        for(String name:List.of("config.yml","messages.yml")) {
            assertArrayEquals(ConfigFiles.resource(name),Files.readAllBytes(directory.resolve(name)));
            int version=name.equals("config.yml")?ConfigFiles.settingsFile().version():ConfigFiles.messagesFile().version();
            assertTrue(Files.readString(directory.resolve(name)).stripTrailing().endsWith("config-version: "+version));
        }
        assertTrue(backups().isEmpty());
    }
    @Test void unversionedInstallPreservesCustomValuesAndBacksUpExactOriginalCommentsAndBytes() throws Exception {
        String original="# Private custom comment\r\nclaims:\r\n  starting-blocks: 54321\r\ndisabled-worlds: [world_nether]\r\n";
        write("config.yml",original);
        ConfigFiles.RuntimeConfiguration result=ConfigFiles.pipeline(directory,ignored->{}).update(candidate->{},candidate->{});
        assertEquals(54321,result.settings().limits().starting()); assertFalse(result.settings().world("world_nether").claiming());
        assertEquals(1,backups().size()); assertArrayEquals(bytes(original),Files.readAllBytes(backups().getFirst()));
        String rewritten=Files.readString(directory.resolve("config.yml"));
        assertTrue(rewritten.contains("# Root claims cover full world height")); assertFalse(rewritten.contains("Private custom comment"));
        assertTrue(rewritten.indexOf("claims:")<rewritten.indexOf("tools:")); assertTrue(rewritten.stripTrailing().endsWith("config-version: "+ConfigFiles.settingsFile().version()));
    }
    @Test void migrationsRunAllSkippedDestinationsInOrderBeforePruning() throws Exception {
        List<Integer> visited=new ArrayList<>();
        List<MigrationStep> history=List.of(
                new MigrationStep(1,"Adopt",data->visited.add(1)),
                new MigrationStep(2,"Rename old field",data->{ visited.add(2); MigrationStep.rename(data,"old-name","middle-name","sample.yml"); }),
                new MigrationStep(3,"Rename again",data->{ visited.add(3); MigrationStep.rename(data,"middle-name","current-name","sample.yml"); }));
        YamlFileSpec spec=spec("sample.yml","current-name: default\nconfig-version: 3\n",history,Set.of());
        write("sample.yml","old-name: customized\n");
        var result=run(pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>()));
        assertEquals(List.of(1,2,3),visited); assertEquals("customized",result.get("sample.yml").get("current-name"));
        assertFalse(Files.readString(directory.resolve("sample.yml")).contains("old-name"));
    }
    @Test void alreadyVersionedFileRunsOnlyMissingSteps() throws Exception {
        List<Integer> visited=new ArrayList<>();
        YamlFileSpec spec=spec("sample.yml","value: kept\nconfig-version: 3\n",List.of(
                new MigrationStep(1,"First",data->visited.add(1)),new MigrationStep(2,"Second",data->visited.add(2)),new MigrationStep(3,"Third",data->visited.add(3))),Set.of());
        write("sample.yml","value: administrator\nconfig-version: 1\n");
        run(pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>())); assertEquals(List.of(2,3),visited);
    }
    @Test void historyRejectsGapsReorderingAndMissingVersions() {
        String defaults="value: x\nconfig-version: 2\n";
        assertThrows(IllegalArgumentException.class,()->spec("sample.yml",defaults,List.of(new MigrationStep(2,"Wrong",data->{})),Set.of()));
        assertThrows(IllegalArgumentException.class,()->spec("sample.yml",defaults,List.of(new MigrationStep(2,"Wrong",data->{}),new MigrationStep(1,"Wrong",data->{})),Set.of()));
    }
    @Test void eachFileVersionAdvancesIndependently() throws Exception {
        YamlFileSpec a=spec("a.yml","value: a\nconfig-version: 2\n",List.of(adoption().getFirst(),new MigrationStep(2,"Second",data->{})),Set.of());
        YamlFileSpec b=spec("b.yml","value: b\nconfig-version: 1\n",adoption(),Set.of());
        write("a.yml","value: custom\nconfig-version: 1\n"); Path bPath=write("b.yml","# retained comment\nvalue: b\nconfig-version: 1\n");
        byte[] before=Files.readAllBytes(bPath); FileTime timestamp=Files.getLastModifiedTime(bPath);
        run(pipeline(List.of(a,b),new SafeConfigFiles(),new ArrayList<>()));
        assertTrue(Files.readString(directory.resolve("a.yml")).endsWith("config-version: 2\n"));
        assertArrayEquals(before,Files.readAllBytes(bPath)); assertEquals(timestamp,Files.getLastModifiedTime(bPath)); assertEquals(1,backups().size());
    }
    @Test void changedBundledDefaultNeverResetsConfiguredValueAndObsoletePathsAreRemoved() throws Exception {
        YamlFileSpec spec=spec("sample.yml","value: new-default\nadded: enabled\nconfig-version: 1\n",adoption(),Set.of());
        write("sample.yml","value: old-default\nobsolete:\n  credentials: SECRET\nconfig-version: 1\n"); List<String> logs=new ArrayList<>();
        var result=run(pipeline(List.of(spec),new SafeConfigFiles(),logs)).get("sample.yml");
        assertEquals("old-default",result.get("value")); assertEquals("enabled",result.get("added")); assertFalse(result.containsKey("obsolete"));
        assertTrue(logs.stream().anyMatch(line->line.contains("obsolete"))); assertTrue(logs.stream().noneMatch(line->line.contains("SECRET")||line.contains("old-default")));
    }
    @Test void renamesPreserveScalarsListsAndEntireNestedSections() {
        for(String value:List.of("custom","[one, two]","{options: {nested: [one, two]}, enabled: true}")) {
            Map<String,Object> document=YamlData.parse(bytes("old: "+value+"\n"),"sample.yml"); Object old=YamlData.copy(document.get("old"));
            MigrationStep.rename(document,"old","new.path","sample.yml");
            assertEquals(old,YamlData.map(document.get("new"),"sample.yml","new").get("path")); assertFalse(document.containsKey("old"));
        }
    }
    @Test void equalRenameDestinationIsIdempotentButConflictingDestinationsFailWithoutWrites() throws Exception {
        Map<String,Object> equal=YamlData.parse(bytes("old: [a, b]\nnew: [a, b]\n"),"sample.yml");
        MigrationStep.rename(equal,"old","new","sample.yml"); assertFalse(equal.containsKey("old"));
        YamlFileSpec spec=spec("sample.yml","new: default\nconfig-version: 1\n",List.of(new MigrationStep(1,"Rename",data->MigrationStep.rename(data,"old","new","sample.yml"))),Set.of());
        Path path=write("sample.yml","old: PRIVATE_A\nnew: PRIVATE_B\n"); byte[] original=Files.readAllBytes(path);
        ConfigException error=assertThrows(ConfigException.class,()->run(pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>())));
        assertTrue(error.getMessage().contains("old -> new")); assertFalse(error.toString().contains("PRIVATE")); assertNull(error.getCause());
        assertArrayEquals(original,Files.readAllBytes(path)); assertTrue(backups().isEmpty());
    }
    @Test void renameRejectsScalarDestinationParentRatherThanOverwritingIt() {
        Map<String,Object> data=YamlData.parse(bytes("old: [a]\nnew: KEEP\n"),"sample.yml");
        assertThrows(ConfigException.class,()->MigrationStep.rename(data,"old","new.child","sample.yml")); assertEquals("KEEP",data.get("new"));
    }
    @Test void declaredOpenSectionsRetainWholeAdministratorTreesAndAreValidatedSeparately() throws Exception {
        String defaults="fixed:\n  enabled: true\nkits: {}\nconfig-version: 1\n";
        Consumer<Map<String,Object>> validate=data->{
            Map<String,Object> kits=YamlData.map(data.get("kits"),"sample.yml","kits");
            for(var kit:kits.entrySet()) YamlData.map(kit.getValue(),"sample.yml","kits."+kit.getKey());
        };
        YamlFileSpec spec=new YamlFileSpec("sample.yml",bytes(defaults),adoption(),Set.of("kits"),validate);
        write("sample.yml","fixed:\n  enabled: false\n  obsolete: remove\nkits:\n  admin-kit:\n    rewards: [gold, iron]\n    requirements:\n      custom-option: 123\n");
        var result=run(pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>())).get("sample.yml");
        Map<String,Object> kit=YamlData.map(YamlData.map(result.get("kits"),"","").get("admin-kit"),"","");
        assertEquals(List.of("gold","iron"),kit.get("rewards")); assertTrue(kit.containsKey("requirements"));
        assertFalse(YamlData.map(result.get("fixed"),"","").containsKey("obsolete"));
        write("sample.yml","kits:\n  broken: false\n"); assertThrows(ConfigException.class,()->run(pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>())));
    }
    @Test void realOpenWorldsAndFlagsPreserveLiteralDottedNamesAndRejectUnknownReferences() throws Exception {
        Path path=write("config.yml","worlds:\n  world.custom:\n    pvp: allow\nflags:\n  defaults:\n    block-break: owner\n");
        var pipeline=ConfigFiles.pipeline(directory,ignored->{}); var snapshot=pipeline.update(candidate->{},candidate->{});
        assertTrue(snapshot.settings().world("world.custom").allows(Flag.PVP));
        assertTrue(snapshot.settings().claimFlag(Flag.BLOCK_BREAK).defaultValue());
        assertFalse(Files.readString(path).contains("worlds:"));
        assertTrue(Files.readString(directory.resolve("worlds.yml")).contains("world.custom:"));
        write("config.yml","worlds:\n  world.custom:\n    missing-flag: allow\n");
        ConfigException e=assertThrows(ConfigException.class,()->pipeline.update(candidate->{},candidate->{}));
        assertTrue(e.getMessage().contains("worlds.world.custom.missing-flag")); assertSame(snapshot,pipeline.current());
    }
    @Test void malformedYamlDuplicateKeysAndUnsafeTagsAreRejectedWithoutExposingContents() throws Exception {
        for(String malformed:List.of("prefix: [SECRET_UNTERMINATED", "prefix: SECRET_ONE\nprefix: SECRET_TWO\n", "prefix: !!java/object:SECRET_TYPE {}\n", "? [SECRET_KEY]\n: x\n")) {
            Path file=write("messages.yml",malformed); byte[] before=Files.readAllBytes(file);
            ConfigException e=assertThrows(ConfigException.class,()->ConfigFiles.pipeline(directory,ignored->{}).update(candidate->{},candidate->{}));
            assertFalse(e.toString().contains("SECRET")); assertNull(e.getCause()); assertArrayEquals(before,Files.readAllBytes(file));
            assertFalse(Files.exists(directory.resolve("config.yml"))); assertTrue(backups().isEmpty());
        }
    }
    @Test void invalidSuppliedTypesNeverFallBackIncludingNullListsAndStrings() throws Exception {
        for(String invalid:List.of("claims:\n  starting-blocks: null\n", "tools:\n  selection: 123\n", "disabled-worlds: [123]\n", "flags:\n  defaults:\n    pvp: true\n", "selection-timeout-seconds: 9\n")) {
            Path path=write("config.yml",invalid); byte[] before=Files.readAllBytes(path);
            assertThrows(ConfigException.class,()->ConfigFiles.pipeline(directory,ignored->{}).update(candidate->{},candidate->{}));
            assertArrayEquals(before,Files.readAllBytes(path)); assertTrue(backups().isEmpty());
        }
    }
    @Test void invalidMessagePlaceholderPreventsSavingEitherFile() throws Exception {
        write("config.yml","claims:\n  starting-blocks: 1234\n"); String invalid="commands:\n  allowance: '{unknown-reference}'\n"; write("messages.yml",invalid);
        ConfigException e=assertThrows(ConfigException.class,()->ConfigFiles.pipeline(directory,ignored->{}).update(candidate->{},candidate->{}));
        assertTrue(e.getMessage().contains("commands.allowance")); assertFalse(Files.readString(directory.resolve("config.yml")).contains("config-version")); assertTrue(backups().isEmpty());
    }
    @Test void bundleReferenceValidationRunsBeforeAnyFileWrites() throws Exception {
        YamlFileSpec a=spec("a.yml","message: welcome\nconfig-version: 1\n",adoption(),Set.of());
        YamlFileSpec b=spec("b.yml","welcome: hello\nconfig-version: 1\n",adoption(),Set.of());
        write("a.yml","message: absent\n");
        var pipeline=new ConfigurationPipeline<>(directory,List.of(a,b),documents->{
            if(!documents.get("b.yml").containsKey(documents.get("a.yml").get("message"))) throw ConfigException.at("a.yml","message","references a missing message key in b.yml");
            return documents;
        },new SafeConfigFiles(),ignored->{});
        assertThrows(ConfigException.class,()->pipeline.update(candidate->{},candidate->{}));
        assertFalse(Files.exists(directory.resolve("b.yml"))); assertTrue(backups().isEmpty());
    }
    @Test void invalidAndFutureVersionsAreNeverOverwritten() throws Exception {
        for(String version:List.of("0","-1","1.5","'1'","true","null","2147483648",Integer.toString(ConfigFiles.messagesFile().version()+1))) {
            String original="prefix: PRIVATE_PREFIX\nconfig-version: "+version+"\n"; write("messages.yml",original);
            assertThrows(ConfigException.class,()->ConfigFiles.pipeline(directory,ignored->{}).update(candidate->{},candidate->{}));
            assertEquals(original,Files.readString(directory.resolve("messages.yml"))); assertFalse(Files.exists(directory.resolve("config.yml"))); assertTrue(backups().isEmpty());
        }
    }
    @Test void unchangedFilesKeepBytesCommentsTimestampsAndDoNotCreateBackups() throws Exception {
        var pipeline=ConfigFiles.pipeline(directory,ignored->{}); pipeline.update(candidate->{},candidate->{});
        Path config=directory.resolve("config.yml"); Files.writeString(config,"# Custom retained header\n"+Files.readString(config));
        byte[] before=Files.readAllBytes(config); FileTime timestamp=Files.getLastModifiedTime(config);
        pipeline.update(candidate->{},candidate->{});
        assertArrayEquals(before,Files.readAllBytes(config)); assertEquals(timestamp,Files.getLastModifiedTime(config)); assertTrue(backups().isEmpty());
    }
    @Test void metadataIsMovedToBottomWhenNecessary() throws Exception {
        var pipeline=ConfigFiles.pipeline(directory,ignored->{}); pipeline.update(candidate->{},candidate->{});
        Path config=directory.resolve("config.yml"); String version="config-version: "+ConfigFiles.settingsFile().version(); String text=Files.readString(config).replace(version,""); Files.writeString(config,version+"\n"+text);
        pipeline.update(candidate->{},candidate->{});
        assertTrue(Files.readString(config).stripTrailing().endsWith("config-version: "+ConfigFiles.settingsFile().version())); assertEquals(1,backups().size());
    }
    @Test void concurrentEditsAreDetectedEvenWhenUnchangedFileIsEdited() throws Exception {
        var pipeline=ConfigFiles.pipeline(directory,ignored->{}); var before=pipeline.update(candidate->{},candidate->{});
        String changed=Files.readString(directory.resolve("messages.yml"))+"# edited concurrently\n";
        ConfigException e=assertThrows(ConfigException.class,()->pipeline.update(candidate->Files.writeString(directory.resolve("messages.yml"),changed),candidate->fail("must not apply")));
        assertTrue(e.getMessage().contains("concurrent-edit")); assertSame(before,pipeline.current()); assertEquals(changed,Files.readString(directory.resolve("messages.yml"))); assertTrue(backups().isEmpty());
    }
    @Test void editDuringBackupIsDetectedBeforeReplacement() throws Exception {
        YamlFileSpec spec=spec("sample.yml","value: default\nconfig-version: 1\n",adoption(),Set.of()); Path path=write("sample.yml","value: original\n");
        SafeConfigFiles io=new SafeConfigFiles() {
            @Override public Path backup(Path original,Snapshot snapshot,int from,int to) throws IOException {
                Path backup=super.backup(original,snapshot,from,to); Files.writeString(original,"value: concurrent-edit\n"); return backup;
            }
        };
        assertThrows(ConfigException.class,()->run(pipeline(List.of(spec),io,new ArrayList<>())));
        assertEquals("value: concurrent-edit\n",Files.readString(path)); assertEquals("value: original\n",Files.readString(backups().getFirst()));
    }
    @Test void overlappingReloadsAreRejectedAcrossPreparationAndApplication() throws Exception {
        var pipeline=ConfigFiles.pipeline(directory,ignored->{}); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<?> first=executor.submit(()->pipeline.update(candidate->{entered.countDown(); if(!release.await(5,TimeUnit.SECONDS)) throw new IOException("timeout");},candidate->{}));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            ConfigException e=assertThrows(ConfigException.class,()->pipeline.update(candidate->{},candidate->{})); assertTrue(e.getMessage().contains("already running"));
            release.countDown(); first.get(5,TimeUnit.SECONDS); assertFalse(pipeline.updating());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void backupFailureStopsAllReplacementsAndPreservesRuntime() throws Exception {
        YamlFileSpec a=spec("a.yml","value: a\nconfig-version: 1\n",adoption(),Set.of()),b=spec("b.yml","value: b\nconfig-version: 1\n",adoption(),Set.of());
        write("a.yml","value: customized-a\n"); write("b.yml","value: customized-b\n"); AtomicInteger count=new AtomicInteger();
        SafeConfigFiles io=new SafeConfigFiles() { @Override public Path backup(Path original,Snapshot snapshot,int from,int to) throws IOException {
            if(count.incrementAndGet()==2) throw new IOException("SECRET_ERROR"); return super.backup(original,snapshot,from,to);
        } };
        var pipeline=pipeline(List.of(a,b),io,new ArrayList<>()); ConfigException e=assertThrows(ConfigException.class,()->run(pipeline));
        assertTrue(e.getMessage().contains("backup failed")); assertFalse(e.toString().contains("SECRET")); assertNull(pipeline.current());
        assertFalse(Files.readString(directory.resolve("a.yml")).contains("config-version")); assertFalse(Files.readString(directory.resolve("b.yml")).contains("config-version"));
    }
    @Test void stageWriteFailureDoesNotReplaceFilesOrLeaveTemporaryData() throws Exception {
        YamlFileSpec spec=spec("sample.yml","value: x\nconfig-version: 1\n",adoption(),Set.of()); write("sample.yml","value: original\n");
        SafeConfigFiles io=new SafeConfigFiles() { @Override public Path stage(Path destination,byte[] bytes) throws IOException { throw new IOException("SECRET_IO"); } };
        ConfigException e=assertThrows(ConfigException.class,()->run(pipeline(List.of(spec),io,new ArrayList<>())));
        assertTrue(e.getMessage().contains("temporary-file write failed")); assertEquals("value: original\n",Files.readString(directory.resolve("sample.yml")));
        try(var paths=Files.list(directory)) { assertTrue(paths.noneMatch(path->path.toString().endsWith(".tmp"))); }
    }
    @Test void interruptedMultiFileUpgradeResumesWithoutRepeatingCommittedMigration() throws Exception {
        AtomicInteger migratedA=new AtomicInteger(),migratedB=new AtomicInteger();
        YamlFileSpec a=spec("a.yml","value: a\nconfig-version: 1\n",List.of(new MigrationStep(1,"A",data->migratedA.incrementAndGet())),Set.of());
        YamlFileSpec b=spec("b.yml","value: b\nconfig-version: 1\n",List.of(new MigrationStep(1,"B",data->migratedB.incrementAndGet())),Set.of());
        write("a.yml","value: custom-a\n"); write("b.yml","value: custom-b\n");
        SafeConfigFiles io=new SafeConfigFiles() { @Override public void replace(Path temp,Path target,boolean existed) throws IOException {
            if(target.getFileName().toString().equals("b.yml")) throw new IOException("simulated interrupted upgrade"); super.replace(temp,target,existed);
        } };
        var failed=pipeline(List.of(a,b),io,new ArrayList<>()); ConfigException e=assertThrows(ConfigException.class,()->run(failed));
        assertTrue(e.getMessage().contains("file upgrades remain saved: a.yml")); assertNull(failed.current());
        assertTrue(Files.readString(directory.resolve("a.yml")).contains("config-version: 1")); assertFalse(Files.readString(directory.resolve("b.yml")).contains("config-version"));
        run(pipeline(List.of(a,b),new SafeConfigFiles(),new ArrayList<>()));
        assertEquals(1,migratedA.get()); assertEquals(2,migratedB.get()); assertEquals("custom-b",YamlData.parse(Files.readAllBytes(directory.resolve("b.yml")),"b.yml").get("value"));
    }
    @Test void runtimeApplicationFailureKeepsOldSnapshotAndExplicitlyReportsSavedFiles() throws Exception {
        YamlFileSpec spec=spec("sample.yml","value: default\nconfig-version: 1\n",adoption(),Set.of()); var pipeline=pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>());
        var previous=run(pipeline); write("sample.yml","value: custom\nobsolete: remove\nconfig-version: 1\n");
        ConfigException e=assertThrows(ConfigException.class,()->pipeline.update(candidate->{},candidate->{throw new IOException("PRIVATE_APPLICATION_VALUE");}));
        assertSame(previous,pipeline.current()); assertTrue(e.getMessage().contains("runtime application failed")); assertTrue(e.getMessage().contains("file upgrades remain saved: sample.yml"));
        assertFalse(e.toString().contains("PRIVATE")); assertFalse(Files.readString(directory.resolve("sample.yml")).contains("obsolete"));
        assertEquals("custom",run(pipeline).get("sample.yml").get("value"));
    }
    @Test void validationFailureDoesNotStartDependentApplicationAndRuntimeSnapshotsAreDeeplyImmutable() throws Exception {
        var pipeline=ConfigFiles.pipeline(directory,ignored->{}); AtomicInteger starts=new AtomicInteger();
        assertThrows(ConfigException.class,()->pipeline.update(candidate->{throw new ConfigException("preflight rejected");},candidate->starts.incrementAndGet()));
        assertEquals(0,starts.get()); assertNull(pipeline.current()); assertFalse(Files.exists(directory.resolve("config.yml")));
        var current=pipeline.update(candidate->{},candidate->starts.incrementAndGet()); assertEquals(1,starts.get());
        assertThrows(UnsupportedOperationException.class,()->current.settings().worlds().put("x",WorldSettings.defaults()));
    }
    @Test void backupAccessIsRestrictedWhenFilesystemSupportsIt() throws Exception {
        YamlFileSpec spec=spec("sample.yml","value: default\nconfig-version: 1\n",adoption(),Set.of()); write("sample.yml","value: PRIVATE\n");
        run(pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>())); Path backup=backups().getFirst();
        if(Files.getFileAttributeView(backup,PosixFileAttributeView.class)!=null) assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(backup));
        else if(Files.getFileAttributeView(backup,AclFileAttributeView.class)!=null) {
            var acl=Files.getFileAttributeView(backup,AclFileAttributeView.class).getAcl(); assertEquals(1,acl.size()); assertEquals(Files.getOwner(backup),acl.getFirst().principal());
        }
    }
    @Test void explicitDocumentedConversionRunsOnceAndDoesNotReapplyOnRetry() throws Exception {
        YamlFileSpec spec=spec("sample.yml","amount: 10\nconfig-version: 2\n",List.of(adoption().getFirst(),
                new MigrationStep(2,"Convert legacy allowance units by doubling; preserve other fields",data->data.put("amount",((Number)data.get("amount")).longValue()*2))),Set.of());
        write("sample.yml","amount: 7\nconfig-version: 1\n"); var pipeline=pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>());
        assertEquals(14,((Number)run(pipeline).get("sample.yml").get("amount")).intValue());
        assertEquals(14,((Number)run(pipeline).get("sample.yml").get("amount")).intValue()); assertEquals(1,backups().size());
    }
    @Test void atomicMoveFallbackStillUsesPreparedBytesAndExactBackups() throws Exception {
        YamlFileSpec spec=spec("sample.yml","value: default\nconfig-version: 1\n",adoption(),Set.of()); write("sample.yml","value: customized\n");
        SafeConfigFiles io=new SafeConfigFiles() {
            @Override protected void moveAtomically(Path temporary,Path destination) throws IOException {
                throw new AtomicMoveNotSupportedException(temporary.toString(),destination.toString(),"test filesystem");
            }
        };
        run(pipeline(List.of(spec),io,new ArrayList<>()));
        assertTrue(Files.readString(directory.resolve("sample.yml")).contains("config-version: 1"));
        assertEquals("value: customized\n",Files.readString(backups().getFirst()));
    }
    @Test void fileCreatedSincePreparationIsNeverOverwritten() throws Exception {
        var pipeline=ConfigFiles.pipeline(directory,ignored->{}); String edited="prefix: preserved-new-file\n";
        assertThrows(ConfigException.class,()->pipeline.update(candidate->write("messages.yml",edited),candidate->fail("must not apply")));
        assertEquals(edited,Files.readString(directory.resolve("messages.yml"))); assertFalse(Files.exists(directory.resolve("config.yml")));
    }
    @Test void realBackupDirectoryFailureDoesNotTouchOriginal() throws Exception {
        YamlFileSpec spec=spec("sample.yml","value: default\nconfig-version: 1\n",adoption(),Set.of());
        write("sample.yml","value: kept\n"); write("config-backups","not a directory");
        assertThrows(ConfigException.class,()->run(pipeline(List.of(spec),new SafeConfigFiles(),new ArrayList<>())));
        assertEquals("value: kept\n",Files.readString(directory.resolve("sample.yml")));
    }
    @Test void explicitNullRootIsInvalidButBlankLegacyFilesCanBeUpgraded() throws Exception {
        write("config.yml","null\n");
        assertThrows(ConfigException.class,()->ConfigFiles.pipeline(directory,ignored->{}).update(candidate->{},candidate->{}));
        assertEquals("null\n",Files.readString(directory.resolve("config.yml"))); assertFalse(Files.exists(directory.resolve("messages.yml")));
        write("config.yml","# comment-only legacy file\n");
        ConfigFiles.pipeline(directory,ignored->{}).update(candidate->{},candidate->{});
        assertTrue(Files.readString(directory.resolve("config.yml")).contains("config-version: "+ConfigFiles.settingsFile().version()));
    }
    @Test void nonRegularEditableFileFailsBeforeCreatingOtherFiles() throws Exception {
        Files.createDirectory(directory.resolve("config.yml"));
        assertThrows(ConfigException.class,()->ConfigFiles.pipeline(directory,ignored->{}).update(candidate->{},candidate->{}));
        assertFalse(Files.exists(directory.resolve("messages.yml")));
    }
    @Test void unchangedFileEditedDuringReplacementPreventsRuntimePublication() throws Exception {
        YamlFileSpec a=spec("a.yml","value: a\nconfig-version: 1\n",adoption(),Set.of()),b=spec("b.yml","value: b\nconfig-version: 1\n",adoption(),Set.of());
        write("a.yml","value: custom-a\n"); write("b.yml","value: b\nconfig-version: 1\n");
        SafeConfigFiles io=new SafeConfigFiles() {
            @Override public void replace(Path temporary,Path destination,boolean existed) throws IOException {
                super.replace(temporary,destination,existed); Files.writeString(directory.resolve("b.yml"),"value: concurrent\nconfig-version: 1\n");
            }
        };
        var pipeline=pipeline(List.of(a,b),io,new ArrayList<>());
        ConfigException e=assertThrows(ConfigException.class,()->run(pipeline));
        assertTrue(e.getMessage().contains("final concurrent-edit check")); assertTrue(e.getMessage().contains("file upgrades remain saved: a.yml"));
        assertNull(pipeline.current()); assertTrue(Files.readString(directory.resolve("b.yml")).contains("concurrent"));
    }
    @Test void renameDoesNotGuessBetweenDifferentYamlNumericTypes() {
        Map<String,Object> data=YamlData.parse(bytes("old: 1\nnew: 1.0\n"),"sample.yml");
        assertThrows(ConfigException.class,()->MigrationStep.rename(data,"old","new","sample.yml"));
        assertTrue(data.containsKey("old")); assertInstanceOf(Double.class,data.get("new"));
    }
}
