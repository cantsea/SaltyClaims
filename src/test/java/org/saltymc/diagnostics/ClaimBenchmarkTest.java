package org.saltymc.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class ClaimBenchmarkTest {
    @TempDir Path directory;
    @Test void isolatedSuiteExercisesScenariosAndCleansTemporaryDatabasesWithoutTouchingExistingFiles() throws Exception {
        Path live=directory.resolve("claims.db"); byte[] original="existing-server-database-sentinel".getBytes(java.nio.charset.StandardCharsets.UTF_8); Files.write(live,original);
        List<String> report=new ArrayList<>();
        ClaimBenchmark.run(directory,new ClaimBenchmark.Options(List.of(8,16),1,2,3,32,120),report::add);
        assertArrayEquals(original,Files.readAllBytes(live));
        try(var files=Files.list(directory)) { assertEquals(List.of(live),files.toList()); }
        assertEquals(4,report.stream().filter(line->line.startsWith("SCENARIO ")).count());
        for(String metric:List.of("mixed lookup","lookup + policy","create preparation","resize preparation","trust edit preparation","delete preparation","SQLite initial save","SQLite single-claim delta save","SQLite delete all owned claims"))
            assertTrue(report.stream().anyMatch(line->line.startsWith(metric)),metric);
        assertTrue(report.getLast().startsWith("CHECKS: PASS"));
        assertTrue(report.stream().anyMatch(line->line.contains("NOT server-thread MSPT")));
    }
    @Test void interruptionAbortsAndStillCleansWorkspace() throws Exception {
        Thread.currentThread().interrupt();
        try { assertThrows(CancellationException.class,()->ClaimBenchmark.run(directory,new ClaimBenchmark.Options(List.of(1),1,1,1,1,1),ignored->{})); }
        finally { Thread.interrupted(); }
        try(var files=Files.list(directory)) { assertEquals(0,files.count()); }
    }
    @Test void statisticsAreDeterministicAndDoNotMutateSamples() {
        long[] durations={9000,1000,5000,3000,7000};
        var stats=ClaimBenchmark.Stats.of(durations,2);
        assertEquals(2.5,stats.medianUs()); assertEquals(4.5,stats.p95Us()); assertEquals(4.5,stats.maxUs());
        assertArrayEquals(new long[]{9000,1000,5000,3000,7000},durations);
        assertEquals(2.0,ClaimBenchmark.Stats.of(new long[]{1000,3000},1).medianUs());
        assertThrows(IllegalArgumentException.class,()->ClaimBenchmark.Stats.of(new long[0],1));
    }
    @Test void workloadLimitsPreventUnboundedCommandWork() {
        assertThrows(IllegalArgumentException.class,()->new ClaimBenchmark.Options(List.of(10001),1,1,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new ClaimBenchmark.Options(List.of(1),1,1,1,1,121));
        assertThrows(IllegalArgumentException.class,()->new ClaimBenchmark.Options(List.of(),1,1,1,1,1));
    }
}
