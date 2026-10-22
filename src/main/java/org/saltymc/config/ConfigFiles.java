package org.saltymc.config;

import org.saltymc.message.Messages;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Editable-file registry. plugin.yml is build metadata, not an editable configuration. */
public final class ConfigFiles {
    public record RuntimeConfiguration(Settings settings,Messages messages) { }
    private ConfigFiles() { }
    public static byte[] resource(String name) {
        try(InputStream input=ConfigFiles.class.getResourceAsStream("/"+name)) {
            if(input==null) throw new IOException("Missing resource"); return input.readAllBytes();
        } catch(IOException e) { throw new IllegalStateException("Missing bundled "+name); }
    }
    public static YamlFileSpec settingsFile() {
        return new YamlFileSpec("config.yml",resource("config.yml"),List.of(
                // Released v1: adoption only. Never add future renames/conversions to this step.
                new MigrationStep(1,"Adopt version metadata; preserve existing settings",document->{}),
                new MigrationStep(2,"Add configurable particle duration; preserve all supplied settings",document->{}),
                new MigrationStep(3,"Add distinct child selection tool; preserve supplied tool choices",document->{}),
                new MigrationStep(4,"Convert former stock tool set to stick, bone and feather; preserve customized sets",ToolMigrationV4::apply),
                new MigrationStep(5,"Add full flag and trust reference comments; preserve configured values",document->{})
        ),Set.of("flags.defaults","flags.enforced","worlds"),Settings::from);
    }
    public static YamlFileSpec messagesFile() {
        return new YamlFileSpec("messages.yml",resource("messages.yml"),List.of(
                new MigrationStep(1,"Adopt version metadata; preserve existing message templates",document->{}),
                new MigrationStep(2,"Update stock instructions for automatic selection, name targets and nested deletion confirmation",MessageMigrationV2::apply),
                new MigrationStep(3,"Simplify stock deletion prompt and explain automatic child height",MessageMigrationV3::apply),
                new MigrationStep(4,"Add claimed-land selection feedback; preserve existing messages",document->{}),
                new MigrationStep(5,"Add server-wide claim reset messages; preserve existing messages",document->{}),
                new MigrationStep(6,"Replace stock mode instructions with separate claim tools; remove obsolete mode messages",MessageMigrationV6::apply),
                new MigrationStep(7,"Expand stock claim and trust details; preserve customized templates",MessageMigrationV7::apply)
        ),Set.of(),Messages::from);
    }
    public static ConfigurationPipeline<RuntimeConfiguration> pipeline(Path directory,Consumer<String> logger) {
        return pipeline(directory,logger,UnaryOperator.identity());
    }
    public static ConfigurationPipeline<RuntimeConfiguration> pipeline(Path directory,Consumer<String> logger,UnaryOperator<String> display) {
        return new ConfigurationPipeline<>(directory,List.of(settingsFile(),messagesFile()),documents->
                new RuntimeConfiguration(Settings.from(documents.get("config.yml")),Messages.from(documents.get("messages.yml")).withDisplay(display)),new SafeConfigFiles(),logger);
    }
}
