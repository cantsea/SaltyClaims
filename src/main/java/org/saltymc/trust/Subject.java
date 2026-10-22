package org.saltymc.trust;

import java.util.UUID;

public record Subject(UUID playerId) {
    public static final Subject PUBLIC = new Subject(null);
    public boolean isPublic() { return playerId == null; }
    public String key() { return isPublic() ? "public" : playerId.toString(); }
    public static Subject parse(String key) { return key.equalsIgnoreCase("public") ? PUBLIC : new Subject(UUID.fromString(key)); }
}
