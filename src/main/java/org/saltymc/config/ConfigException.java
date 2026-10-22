package org.saltymc.config;

/** Safe to show/log: messages identify files/paths and expectations, never supplied values. */
public class ConfigException extends IllegalArgumentException {
    public ConfigException(String message) { super(message); }
    public static ConfigException at(String file,String path,String expectation) {
        return new ConfigException(file+": "+path+" "+expectation);
    }
}
