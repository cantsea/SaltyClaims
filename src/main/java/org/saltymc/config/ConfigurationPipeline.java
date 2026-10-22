package org.saltymc.config;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Shared startup/reload pipeline. No runtime publication occurs until save and application succeed. */
public final class ConfigurationPipeline<T> {
    @FunctionalInterface public interface Factory<T> { T build(Map<String,Map<String,Object>> documents) throws Exception; }
    @FunctionalInterface public interface Action<T> { void run(T candidate) throws Exception; }
    private record Prepared(YamlFileSpec spec,Path path,SafeConfigFiles.Snapshot original,YamlFileSpec.Candidate candidate) { }
    private final Path directory;
    private final List<YamlFileSpec> specifications;
    private final Factory<T> factory;
    private final SafeConfigFiles files;
    private final Consumer<String> log;
    private final AtomicBoolean updating=new AtomicBoolean();
    private volatile T current;
    public ConfigurationPipeline(Path directory,List<YamlFileSpec> specifications,Factory<T> factory,SafeConfigFiles files,Consumer<String> log) {
        this.directory=directory.toAbsolutePath().normalize(); this.specifications=List.copyOf(specifications);
        if(specifications.stream().map(YamlFileSpec::name).distinct().count()!=specifications.size()) throw new IllegalArgumentException("Duplicate configuration filenames");
        this.factory=factory; this.files=files; this.log=log;
    }
    public T current() { return current; }
    public boolean updating() { return updating.get(); }
    public T update(Action<T> preflight,Action<T> application) {
        if(!updating.compareAndSet(false,true)) throw new ConfigException("A configuration startup/reload operation is already running");
        List<String> saved=new ArrayList<>(); Map<Path,Path> staged=new LinkedHashMap<>();
        String phase="preparation",filename="configuration bundle";
        try {
            List<Prepared> prepared=new ArrayList<>(); Map<String,Map<String,Object>> documents=new LinkedHashMap<>();
            for(YamlFileSpec spec:specifications) {
                filename=spec.name(); Path path=directory.resolve(filename);
                SafeConfigFiles.Snapshot original=files.read(path);
                YamlFileSpec.Candidate candidate=spec.prepare(original.bytes());
                prepared.add(new Prepared(spec,path,original,candidate)); documents.put(filename,candidate.document());
            }
            filename="configuration bundle"; phase="validation";
            T candidate=Objects.requireNonNull(factory.build(Collections.unmodifiableMap(documents))); preflight.run(candidate);
            // Validate every supplied file, including unchanged files, before backing up or saving any.
            phase="concurrent-edit check";
            for(Prepared p:prepared) { filename=p.spec().name(); files.check(p.path(),p.original()); }
            Files.createDirectories(directory);
            // Back up ALL changed originals before replacing ANY file. A backup failure stops all saves.
            phase="backup";
            for(Prepared p:prepared) if(p.candidate().changed()&&p.original().exists()) {
                filename=p.spec().name(); Path backup=files.backup(p.path(),p.original(),p.candidate().from(),p.candidate().to());
                log.accept(filename+": exact sensitive backup created: "+backup.getFileName());
            }
            phase="temporary-file write";
            for(Prepared p:prepared) if(p.candidate().changed()) {
                filename=p.spec().name(); staged.put(p.path(),files.stage(p.path(),p.candidate().output()));
            }
            phase="concurrent-edit check";
            for(Prepared p:prepared) { filename=p.spec().name(); files.check(p.path(),p.original()); }
            Map<Path,SafeConfigFiles.Snapshot> committed=new HashMap<>();
            for(Prepared p:prepared) if(p.candidate().changed()) {
                filename=p.spec().name(); phase="replacement/concurrent-edit check";
                files.check(p.path(),p.original());
                files.replace(staged.get(p.path()),p.path(),p.original().exists()); saved.add(filename);
                SafeConfigFiles.Snapshot written=files.read(p.path());
                if(!Arrays.equals(written.bytes(),p.candidate().output())) throw new ConfigException(filename+": edited immediately after replacement");
                committed.put(p.path(),written);
                log.accept(filename+": "+(p.original().exists()?"version "+p.candidate().from()+" -> "+p.candidate().to():"created at version "+p.candidate().to())
                        +"; changed paths: "+String.join(", ",new TreeSet<>(p.candidate().changedPaths())));
            }
            phase="final concurrent-edit check";
            for(Prepared p:prepared) { filename=p.spec().name(); files.check(p.path(),committed.getOrDefault(p.path(),p.original())); }
            phase="runtime application"; filename="configuration bundle";
            application.run(candidate);
            current=candidate; // One immutable snapshot, published only after every previous phase succeeds.
            return candidate;
        } catch(Exception e) {
            String detail=e instanceof ConfigException?e.getMessage():filename+": "+phase+" failed (configured values omitted)";
            String recovery=saved.isEmpty()?"; no file replacements completed; previous runtime retained"
                    :"; file upgrades remain saved: "+String.join(", ",saved)+"; previous runtime retained; fix the cause and retry; no automatic downgrade";
            // Do not chain parser/IO/hook causes: they may include YAML excerpts, values or credentials.
            throw new ConfigException(detail+recovery);
        } finally {
            for(Path temporary:staged.values()) try { Files.deleteIfExists(temporary); }
            catch(Exception ignored) { log.accept("Configuration temporary-file cleanup failed; treat leftover .tmp files as sensitive"); }
            updating.set(false);
        }
    }
}
