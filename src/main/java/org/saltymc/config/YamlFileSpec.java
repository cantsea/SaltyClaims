package org.saltymc.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** A file's independent schema, explicit open sections, immutable migration history and renderer. */
public final class YamlFileSpec {
    public record Candidate(Map<String,Object> document,int from,int to,Set<String> changedPaths,boolean changed,byte[] output) {
        public Candidate { document=YamlData.immutable(document); changedPaths=Set.copyOf(changedPaths); output=output.clone(); }
        @Override public byte[] output() { return output.clone(); }
    }
    private final String name;
    private final byte[] bundled;
    private final Map<String,Object> defaults;
    private final int version;
    private final List<MigrationStep> steps;
    private final Set<String> openSections;
    private final Consumer<Map<String,Object>> validator;
    private final Consumer<Map<String,Object>> complete;
    public YamlFileSpec(String name,byte[] bundled,List<MigrationStep> steps,Set<String> openSections,Consumer<Map<String,Object>> validator) {
        this(name,bundled,steps,openSections,validator,document->{});
    }
    public YamlFileSpec(String name,byte[] bundled,List<MigrationStep> steps,Set<String> openSections,Consumer<Map<String,Object>> validator,Consumer<Map<String,Object>> complete) {
        if(!name.matches("[A-Za-z0-9_-]+\\.yml")) throw new IllegalArgumentException("Expected an editable YAML filename");
        this.name=name; this.bundled=bundled.clone(); this.defaults=YamlData.immutable(YamlData.parse(bundled,name+" bundled defaults"));
        this.version=readVersion(defaults,name+" bundled defaults");
        if(version<1||!new ArrayList<>(defaults.keySet()).getLast().equals("config-version")) throw new IllegalArgumentException(name+": bundled config-version must be positive and last");
        this.steps=List.copyOf(steps); this.openSections=Set.copyOf(openSections); this.validator=validator; this.complete=complete;
        if(steps.size()!=version) throw new IllegalArgumentException(name+": history must cover every destination from 1 to "+version);
        for(int i=0;i<steps.size();i++) if(steps.get(i).destination()!=i+1) throw new IllegalArgumentException(name+": migration history must be ordered/consecutive");
        for(String open:openSections) {
            if(open.isEmpty()) continue;
            Object section=defaults; for(String part:open.split("\\.")) section=YamlData.map(section,name,open).get(part);
            YamlData.map(section,name,open);
        }
        validator.accept(defaults);
    }
    public String name() { return name; }
    public int version() { return version; }
    public Map<String,Object> defaults() { return defaults; }
    public Candidate prepare(byte[] original) {
        Map<String,Object> supplied=original==null?new LinkedHashMap<>():YamlData.parse(original,name);
        return prepare(original,supplied);
    }
    public Candidate prepare(byte[] original,Map<String,Object> input) {
        Map<String,Object> supplied=original==null?new LinkedHashMap<>():YamlData.parse(original,name);
        int from=original==null?version:readVersion(supplied,name);
        if(from>version) throw ConfigException.at(name,"config-version","is newer than supported version "+version+"; file was not overwritten");
        Map<String,Object> migrated=original==null&&input.isEmpty()?YamlData.copyMap(defaults):YamlData.copyMap(input);
        for(int destination=from+1;destination<=version;destination++) {
            try { steps.get(destination-1).action().accept(migrated); }
            catch(ConfigException e) { throw new ConfigException(name+": migration to version "+destination+" rejected: "+e.getMessage()); }
            catch(Exception e) { throw new ConfigException(name+": migration to version "+destination+" failed (configured values omitted)"); }
            migrated.put("config-version",destination);
        }
        complete.accept(migrated);
        Map<String,Object> reconciled=reconcile(migrated,defaults,"");
        validator.accept(YamlData.immutable(reconciled));
        Set<String> changed=new TreeSet<>(YamlData.differences(supplied,reconciled));
        // A supplied version at the top also needs a rewrite to keep metadata at the bottom.
        if(!supplied.isEmpty()&&!new ArrayList<>(supplied.keySet()).getLast().equals("config-version")) changed.add("config-version");
        boolean rewrite=original==null||!changed.isEmpty();
        byte[] output=original==null&&YamlData.equivalent(defaults,reconciled)?bundled:rewrite?render(reconciled):original;
        // Validate the serialized artifact before any disk write, too.
        Map<String,Object> roundtrip=YamlData.parse(output,name);
        if(!YamlData.equivalent(roundtrip,reconciled)) throw new ConfigException(name+": YAML serialization changed the prepared document");
        return new Candidate(reconciled,from,version,changed,rewrite,output);
    }
    private Map<String,Object> reconcile(Map<String,Object> supplied,Map<String,Object> schema,String prefix) {
        Map<String,Object> result=new LinkedHashMap<>();
        if(prefix.isEmpty()&&openSections.contains("")) supplied.forEach((key,value)-> {
            if(!schema.containsKey(key)) result.put(key,YamlData.copy(value));
        });
        for(var entry:schema.entrySet()) {
            String key=entry.getKey(),path=prefix.isEmpty()?key:prefix+"."+key;
            Object fallback=entry.getValue();
            if(!supplied.containsKey(key)) { result.put(key,YamlData.copy(fallback)); continue; }
            Object actual=supplied.get(key);
            if(fallback instanceof Map<?,?>) {
                Map<String,Object> map=YamlData.map(actual,name,path);
                result.put(key,openSections.contains(path)?YamlData.copyMap(map):reconcile(map,YamlData.map(fallback,name,path),path));
            } else {
                if(actual instanceof Map<?,?>) throw ConfigException.at(name,path,"must not be a mapping");
                result.put(key,YamlData.copy(actual));
            }
        }
        return result;
    }
    public static int readVersion(Map<String,Object> document,String filename) {
        if(!document.containsKey("config-version")) return 0;
        Object value=document.get("config-version");
        if(!(value instanceof Integer)&&!(value instanceof Long)) throw ConfigException.at(filename,"config-version","must be a positive integer");
        long number=((Number)value).longValue();
        if(number<1||number>Integer.MAX_VALUE) throw ConfigException.at(filename,"config-version","must be a positive 32-bit integer");
        return (int)number;
    }
    private byte[] render(Map<String,Object> document) {
        try {
            YamlConfiguration source=new YamlConfiguration(),output=new YamlConfiguration();
            // Treat dots in administrator-defined world names as literal key characters.
            source.options().pathSeparator('\u001f').parseComments(true);
            output.options().pathSeparator('\u001f').parseComments(true);
            source.loadFromString(new String(bundled,StandardCharsets.UTF_8));
            output.options().setHeader(source.options().getHeader()).setFooter(source.options().getFooter());
            fill(output,document,"",source,output);
            return output.saveToString().getBytes(StandardCharsets.UTF_8);
        } catch(Exception e) { throw new ConfigException(name+": failed to render bundled comments and key order"); }
    }
    private static void fill(ConfigurationSection target,Map<String,Object> data,String prefix,YamlConfiguration source,YamlConfiguration output) {
        for(var entry:data.entrySet()) {
            String key=entry.getKey(),path=prefix.isEmpty()?key:prefix+'\u001f'+key;
            if(entry.getValue() instanceof Map<?,?>) fill(target.createSection(key),YamlData.map(entry.getValue(),"",path),path,source,output);
            else target.set(key,entry.getValue());
            output.setComments(path,source.getComments(path)); output.setInlineComments(path,source.getInlineComments(path));
        }
    }
}
