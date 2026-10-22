package org.saltymc.message;

import java.util.*;

/** A message key and immutable data, safe to carry across asynchronous saves. */
public record Notice(String key, Map<String,String> values) {
    public Notice { Objects.requireNonNull(key); values=Map.copyOf(values); }
    public static Notice of(String key,Object... pairs) {
        if(pairs.length%2!=0) throw new IllegalArgumentException("Message placeholders require key/value pairs.");
        Map<String,String> values=new HashMap<>();
        for(int i=0;i<pairs.length;i+=2) values.put(pairs[i].toString(),String.valueOf(pairs[i+1]));
        return new Notice(key,values);
    }
}
