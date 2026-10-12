package com.sk89q.worldguard.neoforge.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceProfilerTest {
    @TempDir Path directory;
    @Test void actualThreadSamplingWritesAReportAndStopsItsWorker() throws Exception {
        try(var profiler=new PerformanceProfiler(directory)) {
            profiler.start(1,5,"*",path->fail("Profile should be stopped before automatic completion"),error->fail(error));
            long deadline=System.nanoTime()+5_000_000_000L;
            while(profiler.samples()==0 && System.nanoTime()<deadline) Thread.sleep(5);
            assertTrue(profiler.samples()>0); assertTrue(profiler.running());
            Path report=profiler.stop(); assertFalse(profiler.running());
            assertTrue(Files.readString(report).contains("samples\n"));
            assertThrows(IllegalStateException.class,profiler::stop);
        }
    }
    @Test void invalidSchedulesAndConcurrentProfilesAreRejected() {
        try(var profiler=new PerformanceProfiler(directory)) {
            assertThrows(IllegalArgumentException.class,()->profiler.start(0,20,"*",p->{},e->{}));
            assertThrows(IllegalArgumentException.class,()->profiler.start(1,0,"*",p->{},e->{}));
            profiler.start(1,20,"Server thread",p->{},e->{});
            assertThrows(IllegalStateException.class,()->profiler.start(1,20,"*",p->{},e->{}));
        }
    }
}
