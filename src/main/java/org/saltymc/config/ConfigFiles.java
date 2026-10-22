package org.saltymc.config;

import org.saltymc.message.Messages;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

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
                new MigrationStep(1,"Adopt version metadata; preserve existing settings",document->{})
        ),Set.of("flags.defaults","flags.enforced","worlds"),Settings::from);
    }
    public static YamlFileSpec messagesFile() {
        return new YamlFileSpec("messages.yml",resource("messages.yml"),List.of(
                new MigrationStep(1,"Adopt version metadata; preserve existing message templates",document->{})
        ),Set.of(),Messages::from);
    }
    public static ConfigurationPipeline<RuntimeConfiguration> pipeline(Path directory,Consumer<String> logger) {
        return new ConfigurationPipeline<>(directory,List.of(settingsFile(),messagesFile()),documents->
                new RuntimeConfiguration(Settings.from(documents.get("config.yml")),Messages.from(documents.get("messages.yml"))),new SafeConfigFiles(),logger);
    }
}
