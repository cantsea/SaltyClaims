package org.saltymc.command;

import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import org.saltymc.message.MessageException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class DeleteConfirmationsTest {
    @Test void confirmationIsBoundToSenderRootAndCommandFamily() {
        DeleteConfirmations confirmations=new DeleteConfirmations(()->1); Claim root=root(0,9); ClaimState state=state(root);
        confirmations.request("Alex",state,root,"saltyclaims.command.claim.delete",false);
        assertEquals(root.id(),confirmations.require("Alex",state,false).claim());
        assertThrows(MessageException.class,()->confirmations.require("Other",state,false));
        assertThrows(MessageException.class,()->confirmations.require("Alex",state,true));
    }
    @Test void expiryAndDisconnectOrCancelInvalidatePendingDeletion() {
        AtomicLong clock=new AtomicLong(1); DeleteConfirmations confirmations=new DeleteConfirmations(clock::get); Claim root=root(0,9); ClaimState state=state(root);
        confirmations.request("Alex",state,root,"delete",false); clock.addAndGet(30000);
        assertThrows(MessageException.class,()->confirmations.require("Alex",state,false));
        confirmations.request("Alex",state,root,"delete",false); confirmations.clear("Alex");
        assertThrows(MessageException.class,()->confirmations.require("Alex",state,false));
    }
    @Test void newerRequestReplacesOlderTarget() {
        DeleteConfirmations confirmations=new DeleteConfirmations(()->1); Claim a=root(0,9),b=root(20,29); ClaimState state=state(a,b);
        confirmations.request("Alex",state,a,"delete",false); confirmations.request("Alex",state,b,"delete",false);
        assertEquals(b.id(),confirmations.require("Alex",state,false).claim());
    }
    @Test void changedChildOrDeletedRootRejectsConfirmation() {
        DeleteConfirmations confirmations=new DeleteConfirmations(()->1); Claim root=root(0,9),child=child(root,1,5); ClaimState before=state(root,child);
        confirmations.request("Alex",before,root,"delete",false);
        Claim changed=child.change(child.bounds(),false,Map.of(),Map.of());
        assertThrows(MessageException.class,()->confirmations.require("Alex",state(root,changed),false));
        assertThrows(MessageException.class,()->confirmations.require("Alex",ClaimState.empty(),false));
    }
    @Test void savedDeletionPermissionRemainsAvailableForRecheck() {
        DeleteConfirmations confirmations=new DeleteConfirmations(()->1); Claim root=root(0,9); ClaimState state=state(root);
        confirmations.request("Console",state,root,"saltyclaims.command.claima.delete",true);
        assertEquals("saltyclaims.command.claima.delete",confirmations.require("Console",state,true).permission());
    }
    @Test void resetIsBoundToSenderExpiryAndItsOwnAction() {
        AtomicLong clock=new AtomicLong(1); DeleteConfirmations confirmations=new DeleteConfirmations(clock::get);
        Claim root=root(0,9); ClaimState state=state(root);
        confirmations.requestReset("Console",state,"saltyclaims.command.claima.reset.all");
        assertEquals(state.claims(),confirmations.requireReset("Console",state).expected());
        assertEquals("saltyclaims.command.claima.reset.all",confirmations.requireReset("Console",state).permission());
        assertThrows(MessageException.class,()->confirmations.requireReset("Other",state));
        assertThrows(MessageException.class,()->confirmations.require("Console",state,true));
        clock.addAndGet(30000); assertThrows(MessageException.class,()->confirmations.requireReset("Console",state));
    }
    @Test void anyClaimChangeInvalidatesResetButBalanceChangesArePreserved() {
        DeleteConfirmations confirmations=new DeleteConfirmations(()->1);
        Claim root=root(0,9),child=child(root,1,5); ClaimState original=state(root,child);
        confirmations.requestReset("Console",original,"reset");
        Claim edited=child.change(child.bounds(),false,Map.of(),Map.of());
        assertThrows(MessageException.class,()->confirmations.requireReset("Console",state(root,edited)));
        assertThrows(MessageException.class,()->confirmations.requireReset("Console",state(root)));
        assertThrows(MessageException.class,()->confirmations.requireReset("Console",state(root,child,root(20,29))));
        ClaimState withBalance=new ClaimState(original.claims(),Map.of(OWNER,new ClaimState.Account(100,500)));
        confirmations.requireReset("Console",withBalance);
        ClaimState reset=ClaimService.resetAll(withBalance);
        assertTrue(reset.claims().isEmpty()); assertEquals(withBalance.accounts(),reset.accounts()); assertEquals(0,reset.used(OWNER));
    }
    @Test void resetAndSingleDeletionRequestsReplaceEachOtherAndCanBeCleared() {
        DeleteConfirmations confirmations=new DeleteConfirmations(()->1); Claim root=root(0,9); ClaimState state=state(root);
        confirmations.request("Console",state,root,"delete",true);
        confirmations.requestReset("Console",state,"reset");
        assertThrows(MessageException.class,()->confirmations.require("Console",state,true));
        confirmations.request("Console",state,root,"delete",true);
        assertThrows(MessageException.class,()->confirmations.requireReset("Console",state));
        confirmations.requestReset("Console",state,"reset"); confirmations.clear("Console");
        assertThrows(MessageException.class,()->confirmations.requireReset("Console",state));
    }
}
