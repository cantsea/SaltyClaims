package org.saltymc.diagnostics;

import org.saltymc.claim.*;
import org.saltymc.flag.*;
import org.saltymc.storage.SqliteStore;
import org.saltymc.trust.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.*;

/** Isolated synthetic benchmarks. No Bukkit calls, live state, live database or world access. */
public final class ClaimBenchmark {
    public static final long SEED=0x53414c5459L;
    private static final UUID WORLD=new UUID(0,1),OWNER=new UUID(0,2),FRIEND=new UUID(0,3),VISITOR=new UUID(0,4);
    private static volatile long sink;
    public record Options(List<Integer> roots,int warmups,int samples,int batches,int queries,int timeoutSeconds) {
        public Options {
            roots=List.copyOf(roots);
            if(roots.isEmpty()||roots.size()>3||roots.stream().anyMatch(n->n<1||n>10000)||warmups<1||warmups>5
                    ||samples<1||samples>15||batches<1||batches>50||queries<1||queries>2048||timeoutSeconds<1||timeoutSeconds>120)
                throw new IllegalArgumentException("Invalid benchmark limits");
        }
        public static Options standard() { return new Options(List.of(100,1000,10000),2,7,25,1024,120); }
    }
    public record Stats(double medianUs,double p95Us,double maxUs) {
        public static Stats of(long[] durations,int operations) {
            if(durations.length==0||operations<1) throw new IllegalArgumentException("Empty measurements");
            long[] sorted=durations.clone(); Arrays.sort(sorted);
            double median=sorted.length%2==0?sorted[sorted.length/2-1]/2.0+sorted[sorted.length/2]/2.0:sorted[sorted.length/2];
            return new Stats(median/1000.0/operations,
                    sorted[(int)Math.ceil(sorted.length*.95)-1]/1000.0/operations,sorted[sorted.length-1]/1000.0/operations);
        }
    }
    private record Query(int x,int y,int z,UUID expected) { }
    private record Scenario(String name,ClaimState state,Claim first,List<Query> queries) { }
    private final Options options;
    private final Consumer<String> log;
    private final long deadline;
    private ClaimBenchmark(Options options,Consumer<String> log) {
        this.options=options; this.log=log; deadline=System.nanoTime()+options.timeoutSeconds()*1000000000L;
    }
    public static void run(Path reportDirectory,Options options,Consumer<String> log) throws Exception {
        ClaimBenchmark test=new ClaimBenchmark(options,log);
        log.accept("Synthetic benchmark seed="+SEED+"; roots="+options.roots()+"; warmups="+options.warmups()+"; samples="+options.samples());
        log.accept("Lookup/policy numbers are microseconds per operation, summarized over batch averages; not individual-event tail latency.");
        log.accept("Edit preparation includes domain validation where applicable plus full index rebuild; measured on a background thread, NOT server-thread MSPT.");
        log.accept("No real claims/worlds/players are modified. No chunks, entities or particle borders are created.");
        log.accept("PASS means correctness checks completed; no performance thresholds are enforced.");
        Path work=Files.createTempDirectory(reportDirectory,"benchmark-work-");
        try {
            for(int roots:options.roots()) test.scenario(test.grid(roots,16),work.resolve("grid-"+roots));
            int largeCount=Math.min(1000,Collections.max(options.roots()));
            test.scenario(test.grid(largeCount,128),work.resolve("large-footprints"));
            test.scenario(test.dense(),work.resolve("dense"));
            test.check(); log.accept("CHECKS: PASS (lookups, policy, rejected overlap, ownership, SQLite round-trip and restart)");
        } finally {
            // This directory is uniquely created above and contains only synthetic SQLite test files.
            try(var paths=Files.walk(work)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private void check() {
        if(Thread.currentThread().isInterrupted()) throw new CancellationException("Test interrupted");
        if(System.nanoTime()>=deadline) throw new CancellationException("Test time limit reached");
    }
    private static Claim root(int i,Bounds bounds) {
        return new Claim(new UUID(1,i+1),WORLD,OWNER,null,bounds,true,1,0,Map.of(new Subject(FRIEND),Trust.BUILD,Subject.PUBLIC,Trust.ACCESS),Map.of(Flag.PVP,"deny"));
    }
    private Scenario grid(int count,int width) {
        check(); Map<UUID,Claim> claims=new LinkedHashMap<>(); List<Claim> roots=new ArrayList<>();
        for(int i=0;i<count;i++) {
            int x=(i%100-50)*(width+32),z=(i/100-50)*(width+32);
            Claim root=root(i,new Bounds(x,x+width-1,z,z+width-1,null,null)); roots.add(root); claims.put(root.id(),root);
            if(i%4==0) {
                Claim child=new Claim(new UUID(2,i+1),WORLD,null,root.id(),new Bounds(x+2,x+5,z+2,z+5,0,7),i%8!=0,1,0,
                        Map.of(new Subject(FRIEND),Trust.ACCESS),Map.of()); claims.put(child.id(),child);
            }
        }
        Random random=new Random(SEED); List<Query> queries=new ArrayList<>();
        for(int i=0;i<options.queries();i++) {
            int index=random.nextInt(count); if(i%4==2) index-=index%4;
            Claim root=roots.get(index); int x=root.bounds().minX(),z=root.bounds().minZ();
            queries.add(switch(i%4) {
                case 0->new Query(x+width+4,64,z+width+4,null);
                case 1->new Query(x+12,64,z+12,root.id());
                case 2->new Query(x+3,3,z+3,new UUID(2,index+1));
                default->new Query(x+width-1,64,z+width-1,root.id());
            });
        }
        return new Scenario("grid roots="+count+"; width="+width,new ClaimState(claims,Map.of()),roots.getFirst(),queries);
    }
    private Scenario dense() {
        Claim root=root(0,new Bounds(0,15,0,15,null,null)); Map<UUID,Claim> claims=new LinkedHashMap<>(); claims.put(root.id(),root);
        for(int i=0;i<100;i++) {
            Claim child=new Claim(new UUID(3,i+1),WORLD,null,root.id(),new Bounds(2,5,2,5,i*2,i*2+1),true,1,0,Map.of(),Map.of()); claims.put(child.id(),child);
        }
        List<Query> queries=new ArrayList<>();
        for(int i=0;i<options.queries();i++) queries.add(i%3==0?new Query(20,64,20,null):
                i%3==1?new Query(3,(i%100)*2,3,new UUID(3,i%100+1)):new Query(12,64,12,root.id()));
        return new Scenario("dense chunk: 1 parent + 100 stacked children",new ClaimState(claims,Map.of()),root,queries);
    }
    private void scenario(Scenario scenario,Path databaseFolder) throws Exception {
        check(); ClaimState state=scenario.state(); state.validate(); ClaimIndex index=new ClaimIndex(state); Claim first=scenario.first();
        log.accept(""); log.accept("SCENARIO "+scenario.name()+"; total claims="+state.claims().size());
        for(Query query:scenario.queries()) {
            Claim found=index.at(WORLD,query.x(),query.y(),query.z());
            require(Objects.equals(query.expected(),found==null?null:found.id()),"Lookup mismatch");
            Policy.Value value=Policy.resolve(found,Flag.BLOCK_BREAK,"synthetic",Map.of(),Map.of());
            boolean expected=found==null||found.root();
            require(Policy.check(state,found,FRIEND,Flag.BLOCK_BREAK,value,false).allowed()==expected,"Trust/policy mismatch");
        }
        require(!Policy.check(state,first,VISITOR,Flag.BLOCK_BREAK,Policy.resolve(first,Flag.BLOCK_BREAK,"synthetic",Map.of(),Map.of()),false).allowed(),"Visitor unexpectedly allowed");
        measure("index rebuild",options.samples(),1,()->{ sink^=new ClaimIndex(state).at(WORLD,first.bounds().minX()+12,64,first.bounds().minZ()+12).revision(); });
        measure("mixed lookup (batch mean)",options.batches(),options.queries(),()->{
            long checksum=0; for(Query query:scenario.queries()) { Claim found=index.at(WORLD,query.x(),query.y(),query.z()); if(found!=null) checksum^=found.id().getLeastSignificantBits(); } sink=checksum;
        });
        measure("lookup + policy (batch mean)",options.batches(),options.queries(),()->{
            long allowed=0; for(Query query:scenario.queries()) {
                Claim found=index.at(WORLD,query.x(),query.y(),query.z());
                if(Policy.check(state,found,FRIEND,Flag.BLOCK_BREAK,Policy.resolve(found,Flag.BLOCK_BREAK,"synthetic",Map.of(),Map.of()),false).allowed()) allowed++;
            } sink=allowed;
        });
        ClaimService.Limits limits=new ClaimService.Limits(Long.MAX_VALUE/2,3,20000,100,1000000);
        Claim addition=new Claim(new UUID(4,1),WORLD,OWNER,null,new Bounds(1000000,1000015,1000000,1000015,null,null),true,1,0,Map.of(),Map.of());
        Claim resized=first.change(new Bounds(first.bounds().minX(),first.bounds().maxX()-1,first.bounds().minZ(),first.bounds().maxZ(),null,null),true,first.trust(),first.flags());
        measureEdit("create preparation",()->ClaimService.put(state,addition,limits,false));
        measureEdit("resize preparation",()->ClaimService.put(state,resized,limits,false));
        measureEdit("trust edit preparation",()->ClaimService.replace(state,first.change(first.bounds(),true,Map.of(new Subject(FRIEND),Trust.ACCESS),first.flags())));
        measureEdit("delete preparation",()->ClaimService.delete(state,first.id()));
        Claim overlap=new Claim(new UUID(5,1),WORLD,OWNER,null,first.bounds(),true,1,0,Map.of(),Map.of());
        try { ClaimService.put(state,overlap,limits,false); throw new IllegalStateException("Overlap was accepted"); }
        catch(org.saltymc.message.MessageException expected) { require(expected.notice().key().equals("errors.overlap"),"Unexpected overlap rejection"); }
        try { ClaimService.unclaim(state,VISITOR,first.id()); throw new IllegalStateException("Non-owner deletion was accepted"); }
        catch(org.saltymc.message.MessageException expected) { require(expected.notice().key().equals("errors.owner-only"),"Unexpected ownership rejection"); }
        Path database=databaseFolder.resolve("claims.db");
        try(SqliteStore store=new SqliteStore(database)) {
            timed("SQLite initial save",()->store.save(ClaimState.empty(),state));
            require(state.equals(store.load()),"SQLite initial round-trip mismatch");
            ClaimState changed=ClaimService.replace(state,first.change(first.bounds(),true,Map.of(),first.flags()));
            timed("SQLite single-claim delta save",()->store.save(state,changed));
            require(changed.equals(store.load()),"SQLite delta mismatch");
            timed("SQLite delete all owned claims",()->store.save(changed,ClaimService.unclaimAll(changed,OWNER)));
            require(store.load().claims().isEmpty(),"SQLite unclaim all mismatch");
        }
        try(SqliteStore reopened=new SqliteStore(database)) { require(reopened.load().claims().isEmpty(),"SQLite restart mismatch"); }
        log.accept("Scenario checks: PASS");
    }
    private void measureEdit(String name,Supplier<ClaimState> operation) {
        measure(name,options.samples(),1,()->{ ClaimState after=operation.get(); ClaimIndex index=new ClaimIndex(after); sink=after.claims().size()^System.identityHashCode(index); });
    }
    private void measure(String name,int samples,int operations,Runnable operation) {
        for(int i=0;i<options.warmups();i++) { check(); operation.run(); }
        long[] durations=new long[samples];
        for(int i=0;i<samples;i++) { check(); long start=System.nanoTime(); operation.run(); durations[i]=System.nanoTime()-start; }
        Stats stats=Stats.of(durations,operations);
        log.accept(String.format(Locale.ROOT,"%s: median=%.3f us/op; p95=%.3f us/op; max=%.3f us/op; samples=%d; operations/sample=%d",
                name,stats.medianUs(),stats.p95Us(),stats.maxUs(),samples,operations));
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private void timed(String name,Checked operation) throws Exception {
        check(); long start=System.nanoTime(); operation.run(); log.accept(String.format(Locale.ROOT,"%s: %.3f ms (single sample)",name,(System.nanoTime()-start)/1000000.0));
    }
    private static void require(boolean condition,String reason) { if(!condition) throw new IllegalStateException(reason); }
}
