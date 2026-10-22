package org.saltymc.api;

import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import org.saltymc.config.*;
import org.saltymc.message.MessageException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class SaltyClaimsApiTest {
    private static final class Harness implements DefaultSaltyClaimsApi.Port {
        ClaimState state=ClaimState.empty(),next;
        boolean primary=true,validWorld=true,busy;
        Runnable committed,failed;
        Settings settings;
        DefaultSaltyClaimsApi api=new DefaultSaltyClaimsApi(this);
        Harness(boolean managed) {
            var data=YamlData.parse(ConfigFiles.resource("config.yml"),"test");
            data.put("server-manages-claims",managed);
            var limits=YamlData.map(data.get("claims"),"test","");
            limits.put("starting-blocks",0); limits.put("maximum-roots",1); limits.put("maximum-area",100);
            settings=Settings.from(data);
        }
        public boolean primaryThread() { return primary; }
        public Settings settings() { return settings; }
        public void validateWorld(UUID world,Bounds bounds) {
            if(!validWorld) throw new MessageException("errors.world-unloaded");
            if(bounds.minX()<0) throw new MessageException("errors.world-border");
        }
        public void mutate(Function<ClaimState,ClaimState> operation,Runnable success,Runnable failure) {
            if(busy) throw new MessageException("errors.storage-busy");
            next=operation.apply(state); busy=true; committed=success; failed=failure;
        }
        void commit() { state=next; busy=false; committed.run(); }
        void fail() { busy=false; failed.run(); }
        CompletionStage<Claim> create() { return api.createClaim(OWNER,WORLD,new Bounds(20,23,20,23,null,null)); }
    }
    @Test void successWaitsForPersistenceAndBypassesPlayerBudgetsCountsAndWorldSwitchInEitherMode() {
        for(boolean managed:List.of(false,true)) {
            Harness h=new Harness(managed); Claim existing=root(0,9); h.state=state(existing);
            assertFalse(h.settings.world("world").claiming());
            var result=h.create();
            assertFalse(result.toCompletableFuture().isDone()); assertEquals(1,h.state.claims().size());
            h.commit(); Claim created=result.toCompletableFuture().join();
            assertEquals(created,h.state.require(created.id())); assertEquals(OWNER,created.owner());
            assertTrue(created.root()); assertTrue(created.flags().isEmpty()); assertTrue(created.trust().isEmpty());
            assertEquals(2,h.state.claims().size()); assertEquals(116,h.state.used(OWNER));
        }
    }
    @Test void overlapSizeMinimumWidthAndRootHeightValidationAreNotBypassed() {
        for(Bounds bounds:List.of(new Bounds(0,3,0,3,null,null),new Bounds(20,30,20,30,null,null),
                new Bounds(20,21,20,21,null,null),new Bounds(20,23,20,23,1,5))) {
            Harness h=new Harness(true); h.state=state(root(0,9));
            var result=h.api.createClaim(OWNER,WORLD,bounds);
            assertThrows(CompletionException.class,()->result.toCompletableFuture().join());
            assertEquals(1,h.state.claims().size()); assertFalse(h.busy);
        }
    }
    @Test void invalidWorldOrBorderFailsWithoutMutation() {
        Harness h=new Harness(true); h.validWorld=false;
        assertThrows(CompletionException.class,()->h.create().toCompletableFuture().join()); assertFalse(h.busy);
        h.validWorld=true;
        assertThrows(CompletionException.class,()->h.api.createClaim(OWNER,WORLD,new Bounds(-5,-2,0,3,null,null)).toCompletableFuture().join());
        assertTrue(h.state.claims().isEmpty());
    }
    @Test void busyOrFailedStorageNeverReportsSuccessOrPublishesClaim() {
        Harness h=new Harness(true); var pending=h.create();
        assertThrows(CompletionException.class,()->h.create().toCompletableFuture().join());
        assertFalse(pending.toCompletableFuture().isDone()); h.fail();
        assertThrows(CompletionException.class,()->pending.toCompletableFuture().join()); assertTrue(h.state.claims().isEmpty());
    }
    @Test void disableCompletesPendingRequestsAndRetainedServiceRejectsNewOnes() {
        Harness h=new Harness(true); var pending=h.create(); h.api.close();
        assertThrows(CompletionException.class,()->pending.toCompletableFuture().join());
        assertThrows(CompletionException.class,()->h.create().toCompletableFuture().join());
    }
    @Test void callsOffTheServerThreadAreRejectedBeforeWorldOrStateAccess() {
        Harness h=new Harness(true); h.primary=false;
        assertThrows(CompletionException.class,()->h.create().toCompletableFuture().join()); assertFalse(h.busy);
    }
}
