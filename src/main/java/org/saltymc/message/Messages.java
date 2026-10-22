package org.saltymc.message;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.saltymc.config.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Immutable messages snapshot. Templates are colored before inserting literal placeholder data. */
public final class Messages {
    private static final Pattern PLACEHOLDER=Pattern.compile("\\{([a-zA-Z0-9_-]+)}");
    private static final Messages DEFAULTS=readDefaults();
    private final Map<String,String> templates;
    private Messages(Map<String,String> templates) { this.templates=Map.copyOf(templates); }
    public static Messages defaults() { return DEFAULTS; }
    public static Messages load(Path path) throws Exception {
        return from(ConfigFiles.messagesFile().prepare(Files.readAllBytes(path)).document());
    }
    private static Messages readDefaults() {
        return new Messages(flatten(YamlData.parse(ConfigFiles.resource("messages.yml"),"messages.yml bundled defaults")));
    }
    public static Messages from(Map<String,Object> document) {
        Map<String,String> templates=flatten(document);
        for(var entry:DEFAULTS.templates.entrySet()) {
            String value=templates.get(entry.getKey());
            if(value==null) throw ConfigException.at("messages.yml",entry.getKey(),"is missing");
            Set<String> allowed=placeholders(entry.getValue());
            if(!allowed.containsAll(placeholders(value))) throw ConfigException.at("messages.yml",entry.getKey(),"references an unsupported placeholder; use placeholders from the bundled template");
        }
        return new Messages(templates);
    }
    private static Set<String> placeholders(String template) {
        Set<String> result=new HashSet<>(); Matcher matcher=PLACEHOLDER.matcher(template);
        while(matcher.find()) result.add(matcher.group(1)); return result;
    }
    private static Map<String,String> flatten(Map<String,Object> document) {
        Map<String,String> result=new LinkedHashMap<>(); flatten(document,"",result); return result;
    }
    private static void flatten(Map<String,Object> data,String prefix,Map<String,String> result) {
        data.forEach((key,value)-> {
            String path=prefix.isEmpty()?key:prefix+"."+key;
            if(path.equals("config-version")) return;
            if(value instanceof Map<?,?>) flatten(YamlData.map(value,"messages.yml",path),path,result);
            else if(value instanceof String text) result.put(path,text);
            else throw ConfigException.at("messages.yml",path,"must be a string (quote booleans and numbers)");
        });
    }
    public String text(String key,Object... pairs) { return text(Notice.of(key,pairs)); }
    public String text(Notice notice) {
        String template=templates.get(notice.key());
        if(template==null) throw new IllegalArgumentException("Unknown message key: "+notice.key());
        return substitute(color(template),notice.values());
    }
    private static String substitute(String template,Map<String,String> values) {
        Matcher matcher=PLACEHOLDER.matcher(template); StringBuilder result=new StringBuilder();
        while(matcher.find()) matcher.appendReplacement(result,Matcher.quoteReplacement(values.getOrDefault(matcher.group(1),matcher.group())));
        matcher.appendTail(result); return result.toString();
    }
    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&',text); }
    private Notice errorNotice(Throwable error) {
        if(error instanceof MessageException localized) return localized.notice();
        if(error instanceof NumberFormatException) return Notice.of("errors.invalid-number");
        if(error instanceof ArithmeticException) return Notice.of("errors.number-range");
        return Notice.of("errors.invalid-input","reason",String.valueOf(error.getMessage()));
    }
    public String error(Throwable error) { return text(errorNotice(error)); }
    public void sendError(CommandSender sender,Throwable error) { send(sender,errorNotice(error)); }
    /** Prefix each nonempty line exactly once, including help/list output and console replies. */
    public List<String> lines(Notice notice) {
        String body=text(notice), prefix=color(templates.get("prefix"));
        if(body.isEmpty()) return List.of();
        return Arrays.stream(body.split("\\R",-1)).map(line->prefix+line).toList();
    }
    public void send(CommandSender sender,String key,Object... pairs) { send(sender,Notice.of(key,pairs)); }
    public void send(CommandSender sender,Notice notice) { lines(notice).forEach(sender::sendMessage); }
}
