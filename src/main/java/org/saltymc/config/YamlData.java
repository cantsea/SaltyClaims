package org.saltymc.config;

import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import java.io.StringReader;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.math.BigInteger;

/** Strict, safe YAML parsing with bounded expansion and value-free diagnostics. */
public final class YamlData {
    private YamlData() { }
    public static Map<String,Object> parse(byte[] bytes,String filename) {
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            LoaderOptions options=new LoaderOptions();
            options.setAllowDuplicateKeys(false); options.setAllowRecursiveKeys(false);
            options.setNestingDepthLimit(50); options.setMaxAliasesForCollections(50); options.setCodePointLimit(2_000_000);
            Yaml yaml=new Yaml(new SafeConstructor(options));
            var root=yaml.compose(new StringReader(text));
            if(root==null) return new LinkedHashMap<>(); // Blank/comment-only legacy files are version zero.
            if(!(root instanceof MappingNode)) throw ConfigException.at(filename,"<root>","must be a mapping");
            Object value=yaml.load(text);
            if(!(value instanceof Map<?,?>)) throw ConfigException.at(filename,"<root>","must be a mapping");
            return map(normalize(value,filename,"<root>",0,new int[]{0}),filename,"<root>");
        } catch(ConfigException e) { throw e; }
        catch(MarkedYAMLException e) {
            String position=e.getProblemMark()==null?"":" at line "+(e.getProblemMark().getLine()+1)+", column "+(e.getProblemMark().getColumn()+1);
            throw new ConfigException(filename+": malformed YAML"+position+" (duplicate keys, unsafe tags, and invalid structures are rejected)");
        } catch(Exception e) { throw new ConfigException(filename+": invalid UTF-8 or malformed/unsupported YAML"); }
    }
    private static Object normalize(Object value,String file,String path,int depth,int[] count) {
        if(depth>50||++count[0]>100000) throw ConfigException.at(file,path,"exceeds YAML complexity limits");
        if(value instanceof Map<?,?> map) {
            Map<String,Object> result=new LinkedHashMap<>();
            for(var entry:map.entrySet()) {
                if(!(entry.getKey() instanceof String key)||key.isEmpty()||key.chars().anyMatch(Character::isISOControl))
                    throw ConfigException.at(file,path,"requires nonempty string keys without control characters");
                result.put(key,normalize(entry.getValue(),file,path+"."+key,depth+1,count));
            }
            return result;
        }
        if(value instanceof List<?> list) {
            List<Object> result=new ArrayList<>();
            for(int i=0;i<list.size();i++) result.add(normalize(list.get(i),file,path+"["+i+"]",depth+1,count));
            return result;
        }
        if(value==null||value instanceof String||value instanceof Number||value instanceof Boolean) return value;
        throw ConfigException.at(file,path,"has an unsupported YAML scalar type (quote text such as dates)");
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object value,String file,String path) {
        if(!(value instanceof Map<?,?>)) throw ConfigException.at(file,path,"must be a mapping");
        return (Map<String,Object>)value;
    }
    public static Object copy(Object value) {
        if(value instanceof Map<?,?> map) { Map<String,Object> result=new LinkedHashMap<>(); map.forEach((key,v)->result.put((String)key,copy(v))); return result; }
        if(value instanceof List<?> list) return new ArrayList<>(list.stream().map(YamlData::copy).toList());
        return value;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> copyMap(Map<String,Object> map) { return (Map<String,Object>)copy(map); }
    public static Object freeze(Object value) {
        if(value instanceof Map<?,?> map) { Map<String,Object> result=new LinkedHashMap<>(); map.forEach((key,v)->result.put((String)key,freeze(v))); return Collections.unmodifiableMap(result); }
        if(value instanceof List<?> list) return Collections.unmodifiableList(list.stream().map(YamlData::freeze).toList());
        return value;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> immutable(Map<String,Object> map) { return (Map<String,Object>)freeze(map); }
    /** YAML integers can round-trip between Integer and Long without changing their value. */
    public static boolean equivalent(Object a,Object b) {
        if(Objects.equals(a,b)) return true;
        if(integral(a)&&integral(b)) return new BigInteger(a.toString()).equals(new BigInteger(b.toString()));
        if(a instanceof Map<?,?> x && b instanceof Map<?,?> y) {
            if(!x.keySet().equals(y.keySet())) return false;
            return x.entrySet().stream().allMatch(entry->equivalent(entry.getValue(),y.get(entry.getKey())));
        }
        if(a instanceof List<?> x && b instanceof List<?> y) {
            if(x.size()!=y.size()) return false;
            for(int i=0;i<x.size();i++) if(!equivalent(x.get(i),y.get(i))) return false;
            return true;
        }
        return false;
    }
    private static boolean integral(Object value) {
        return value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long||value instanceof BigInteger;
    }
    public static Set<String> differences(Map<String,Object> before,Map<String,Object> after) {
        Set<String> changed=new TreeSet<>(); diff(before,after,"",changed); return Collections.unmodifiableSet(changed);
    }
    private static void diff(Map<String,Object> before,Map<String,Object> after,String prefix,Set<String> changed) {
        Set<String> keys=new LinkedHashSet<>(before.keySet()); keys.addAll(after.keySet());
        for(String key:keys) {
            String path=prefix.isEmpty()?key:prefix+"."+key;
            Object a=before.get(key),b=after.get(key);
            if(before.containsKey(key)&&after.containsKey(key)&&a instanceof Map<?,?>&&b instanceof Map<?,?>)
                diff(map(a,"",path),map(b,"",path),path,changed);
            else if(before.containsKey(key)!=after.containsKey(key)||!Objects.equals(a,b)) changed.add(path);
        }
    }
}
