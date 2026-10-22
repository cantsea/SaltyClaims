package org.saltymc.trust;

import org.saltymc.message.MessageException;

import java.util.Locale;

public enum Trust {
    VISITOR(0), ACCESS(1), CONTAINER(2), BUILD(3), MANAGE(4), OWNER(5);
    private final int rank;
    Trust(int rank) { this.rank = rank; }
    public boolean grants(Trust required) { return rank >= required.rank; }
    public static Trust highest(Trust a, Trust b) { return a.grants(b) ? a : b; }
    public static Trust parse(String value) {
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new MessageException("errors.trust-level"); }
    }
    public boolean assignable() { return this != OWNER && this != VISITOR; }
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
