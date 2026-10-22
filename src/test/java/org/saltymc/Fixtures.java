package org.saltymc;

import org.saltymc.claim.*;
import java.util.*;

public final class Fixtures {
    public static final UUID WORLD=UUID.randomUUID(), OWNER=UUID.randomUUID(), FRIEND=UUID.randomUUID(), STRANGER=UUID.randomUUID();
    public static Claim root(int min,int max) { return new Claim(UUID.randomUUID(),WORLD,OWNER,null,new Bounds(min,max,min,max,null,null),true,1,0,Map.of(),Map.of()); }
    public static Claim child(Claim parent,int minY,int maxY) { return new Claim(UUID.randomUUID(),WORLD,null,parent.id(),new Bounds(1,4,1,4,minY,maxY),true,1,0,Map.of(),Map.of()); }
    public static ClaimState state(Claim... claims) { Map<UUID,Claim> map=new HashMap<>(); for(Claim c:claims) map.put(c.id(),c); return new ClaimState(map,Map.of()); }
}
