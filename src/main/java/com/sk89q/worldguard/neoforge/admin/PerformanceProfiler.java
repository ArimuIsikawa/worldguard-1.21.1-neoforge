package com.sk89q.worldguard.neoforge.admin;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Server-owned stack sampler, independent of Bukkit's scheduler and profiling API. */
public final class PerformanceProfiler implements AutoCloseable {
    private final Path directory;
    private ScheduledExecutorService executor;
    private final Map<String,Long> stacks=new HashMap<>();
    private long samples,started;
    private String thread;
    public PerformanceProfiler(Path directory) { this.directory=directory; }
    public synchronized boolean running() { return executor!=null; }
    public synchronized long samples() { return samples; }
    public synchronized void start(int minutes,int interval,String thread,Consumer<Path> completed,Consumer<IOException> failed) {
        if(minutes<1 || minutes>10 || interval<1 || interval>100) throw new IllegalArgumentException("Profile duration must be 1..10 minutes and interval 1..100 milliseconds");
        if(running()) throw new IllegalStateException("A profile is already running; use /wg stopprofile first");
        this.thread=thread; samples=0; stacks.clear(); started=System.nanoTime();
        var pool=new java.util.concurrent.ScheduledThreadPoolExecutor(1,task->{ Thread worker=new Thread(task,"WorldGuard profiler"); worker.setDaemon(true); return worker; });
        pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false); pool.setRemoveOnCancelPolicy(true); executor=pool;
        executor.scheduleWithFixedDelay(this::sample,0,interval,TimeUnit.MILLISECONDS);
        executor.schedule(()->{ try { completed.accept(stop()); } catch(IOException error) { failed.accept(error); } },minutes,TimeUnit.MINUTES);
    }
    private synchronized void sample() {
        if(!running()) return;
        var bean=ManagementFactory.getThreadMXBean();
        for(var info : bean.getThreadInfo(bean.getAllThreadIds(),64)) if(info!=null && (thread.equals("*") || thread.equalsIgnoreCase(info.getThreadName())) && info.getStackTrace().length>0) {
            StringBuilder key=new StringBuilder(info.getThreadName()).append(" [").append(info.getThreadState()).append("]\n");
            for(var frame : info.getStackTrace()) key.append("  ").append(frame).append('\n');
            stacks.merge(key.toString(),1L,Long::sum); samples++;
        }
    }
    public synchronized Path stop() throws IOException {
        if(!running()) throw new IllegalStateException("No WorldGuard profile is running");
        executor.shutdown(); executor=null;
        StringBuilder report=new StringBuilder("WorldGuard NeoForge stack sampling profile\n").append("Duration: ").append((System.nanoTime()-started)/1_000_000L).append(" ms\nThread filter: ").append(thread).append("\nSamples: ").append(samples).append("\n\n");
        stacks.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).forEach(entry->report.append(entry.getValue()).append(" samples\n").append(entry.getKey()).append('\n'));
        Files.createDirectories(directory); Path file=directory.resolve("profile-" + Instant.now().toEpochMilli() + ".txt");
        Files.writeString(file,report); stacks.clear(); return file;
    }
    @Override public synchronized void close() { if(executor!=null) executor.shutdownNow(); executor=null; stacks.clear(); }
}
