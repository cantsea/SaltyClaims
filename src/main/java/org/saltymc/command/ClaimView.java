package org.saltymc.command;

import org.saltymc.claim.*;
import org.saltymc.message.Notice;
import java.util.*;
import java.util.function.Function;
import org.saltymc.trust.*;

/** Public dimensions/corners are normalized persisted bounds, never raw record debug text. */
public final class ClaimView {
    private ClaimView() { }
    public static Map<Trust,List<String>> trustGroups(Claim claim,Function<Subject,String> names) {
        Map<Trust,List<String>> result=new LinkedHashMap<>();
        for(Trust level:List.of(Trust.ACCESS,Trust.CONTAINER,Trust.BUILD,Trust.MANAGE))
            result.put(level,claim.trust().entrySet().stream().filter(entry->entry.getValue()==level).map(entry->names.apply(entry.getKey())).sorted(String.CASE_INSENSITIVE_ORDER).toList());
        return Collections.unmodifiableMap(result);
    }
    public static Notice details(String key,Claim claim,String world,String fullHeight,String type,int children) {
        Bounds bounds=claim.bounds();
        String first=bounds.minX()+", "+(bounds.fullHeight()?fullHeight:bounds.minY())+", "+bounds.minZ();
        String second=bounds.maxX()+", "+(bounds.fullHeight()?fullHeight:bounds.maxY())+", "+bounds.maxZ();
        return Notice.of(key,"id",claim.id(),"type",type,"world",world,"area",bounds.area(),"width",(long)bounds.maxX()-bounds.minX()+1,
                "length",(long)bounds.maxZ()-bounds.minZ()+1,"height",bounds.fullHeight()?fullHeight:(long)bounds.maxY()-bounds.minY()+1,
                "corner1",first,"corner2",second,"bounds",bounds,"children",children);
    }
    /** Parent followed by its children; all entries belong to this owner. */
    public static List<Claim> owned(ClaimState state,UUID owner) {
        Comparator<Claim> order=Comparator.comparing(Claim::world).thenComparingInt(c->c.bounds().minX())
                .thenComparingInt(c->c.bounds().minZ()).thenComparingInt(c->c.bounds().minY()==null?Integer.MIN_VALUE:c.bounds().minY()).thenComparing(Claim::id);
        List<Claim> result=new ArrayList<>();
        state.claims().values().stream().filter(c->c.root()&&owner.equals(c.owner())).sorted(order).forEach(root->{
            result.add(root); state.children(root.id()).stream().sorted(order).forEach(result::add);
        });
        return List.copyOf(result);
    }
}
