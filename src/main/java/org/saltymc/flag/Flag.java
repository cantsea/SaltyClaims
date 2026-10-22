package org.saltymc.flag;

import java.util.*;
import org.saltymc.message.MessageException;

/** Stable persisted keys and validated policy values; enum ordinals are never stored. */
public enum Flag {
    BLOCK_BREAK("build"), BLOCK_PLACE("build"), CONTAINER_USE("container"), DOOR_USE("access"),
    SWITCH_USE("access"), BED_USE("access"), UTILITY_USE("container"), CROP_HARVEST("container"),
    CROP_REPLANT("container"), BUCKET_USE("build"), ANIMAL_INTERACT("container"), ANIMAL_DAMAGE("build"),
    DECORATION_EDIT("build"), VEHICLE_EDIT("build"), VEHICLE_USE("access"), IGNITE("build"),
    PVP("deny", false), EXPLOSION_BLOCK_DAMAGE("deny", false), EXPLOSION_ENTITY_DAMAGE("deny", false),
    FIRE_SPREAD("deny", false), MOB_BLOCK_GRIEF("deny", false),
    FLUID_CROSSING("deny", false), PISTON_CROSSING("deny", false), HOPPER_CROSSING("deny", false);

    private final String defaultValue;
    private final boolean actor;
    Flag(String value) { this(value, true); }
    Flag(String value, boolean actor) { this.defaultValue = value; this.actor = actor; }
    public String key() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    public String defaultValue() { return defaultValue; }
    public boolean actor() { return actor; }
    public List<String> valuesAllowed() {
        return actor ? List.of("public", "access", "container", "build", "manage", "owner", "deny") : List.of("allow", "deny");
    }
    public String validate(String value) {
        value = value.toLowerCase(Locale.ROOT);
        if (!valuesAllowed().contains(value)) throw new MessageException("errors.flag-values","flag",key(),"values",String.join(", ",valuesAllowed()));
        return value;
    }
    public static Flag parse(String key) {
        return Arrays.stream(values()).filter(f -> f.key().equalsIgnoreCase(key)).findFirst()
                .orElseThrow(() -> new MessageException("errors.flag-unknown","flag",key));
    }
}
