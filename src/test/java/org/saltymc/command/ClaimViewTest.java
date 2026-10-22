package org.saltymc.command;

import org.junit.jupiter.api.Test;
import org.saltymc.claim.*;
import org.saltymc.message.Messages;
import org.saltymc.trust.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.saltymc.Fixtures.*;

class ClaimViewTest {
    @Test void claimBlocksIncludesStartingEarnedAndBonusAndChargesOnlyOwnedRoots() {
        Claim root=root(0,9),child=child(root,1,5);
        Claim other=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(20,29,20,29,null,null),true,1,0,Map.of(),Map.of());
        ClaimState state=new ClaimState(state(root,child,other).claims(),Map.of(OWNER,new ClaimState.Account(25,50)));
        assertEquals("Claim blocks: 975 available, 100 used, 1075 total.",Messages.defaults().text(ClaimView.claimBlocks(state,OWNER,1000)));
        assertEquals("Claim blocks: 1000 available, 0 used, 1000 total.",Messages.defaults().text(ClaimView.claimBlocks(ClaimState.empty(),OWNER,1000)));
        assertEquals("Claim blocks: 0 available, 100 used, 10 total.",Messages.defaults().text(ClaimView.claimBlocks(state(root),OWNER,10)));
    }
    @Test void rootAndThreeDimensionalChildShowInclusiveSizesAndBothCorners() {
        Claim root=root(-10,-1); var notice=ClaimView.details("commands.list-entry",root,"world","full height","Root",0);
        String line=Messages.defaults().text(notice);
        assertTrue(line.contains("10 x 10 (100 blocks)")); assertTrue(line.contains("X -10, Z -10"));
        assertTrue(line.contains("X -1, Z -1"));
        Claim child=child(root,2,6); notice=ClaimView.details("commands.list-entry",child,"world","full height","Child",0);
        line=Messages.defaults().text(notice);
        assertTrue(line.contains("4 x 4 x 5 (16 blocks)")); assertTrue(line.contains("X 1, Y 2, Z 1")); assertTrue(line.contains("X 4, Y 6, Z 4"));
        assertTrue(Messages.defaults().text(ClaimView.details("commands.corner-first",child,"world","full height","Child",0)).contains("X 1, Y 2, Z 1"));
    }
    @Test void ownedListIncludesEachParentsChildrenAndExcludesOtherOwners() {
        Claim root=root(0,9),child=child(root,1,5),another=root(20,29);
        Claim other=new Claim(UUID.randomUUID(),WORLD,STRANGER,null,new Bounds(40,49,40,49,null,null),true,1,0,Map.of(),Map.of());
        List<Claim> owned=ClaimView.owned(state(root,child,another,other),OWNER);
        assertEquals(List.of(root,child,another),owned);
    }
    @Test void everyAssignableLevelIsListedWithNamesAndEmptyGroups() {
        Claim root=root(0,9); root=root.change(root.bounds(),true,Map.of(Subject.PUBLIC,Trust.ACCESS,new Subject(FRIEND),Trust.BUILD),Map.of());
        var groups=ClaimView.trustGroups(root,subject->subject.isPublic()?"Public":"Alex");
        assertEquals(List.of(Trust.ACCESS,Trust.CONTAINER,Trust.BUILD,Trust.MANAGE),new ArrayList<>(groups.keySet()));
        assertEquals(List.of("Public"),groups.get(Trust.ACCESS)); assertEquals(List.of("Alex"),groups.get(Trust.BUILD));
        assertTrue(groups.get(Trust.CONTAINER).isEmpty()); assertTrue(groups.get(Trust.MANAGE).isEmpty());
        assertThrows(UnsupportedOperationException.class,()->groups.get(Trust.BUILD).add("Sam"));
    }
}
