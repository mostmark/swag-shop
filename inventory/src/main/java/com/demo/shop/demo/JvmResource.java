package com.demo.shop.demo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Heap, CPU and threads of this JVM, read from its own management beans, for the JVM line on the demo control
 * page.
 */
@Path("/api/demo/jvm")
public class JvmResource {

    public record JvmStats(String service, long heapUsedMb, long heapCommittedMb, long heapMaxMb,
            double processCpuLoad, int availableProcessors, int liveThreads, long gcCount, long gcTimeMs,
            long uptimeSeconds) {
    }

    @ConfigProperty(name = "quarkus.application.name")
    String service;

    @GET
    public JvmStats jvm() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double cpu = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                ? Math.max(0, os.getProcessCpuLoad())
                : 0;
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcTime += Math.max(0, gc.getCollectionTime());
        }
        long mb = 1024 * 1024;
        return new JvmStats(service, heap.getUsed() / mb, heap.getCommitted() / mb, heap.getMax() / mb, cpu,
                Runtime.getRuntime().availableProcessors(), ManagementFactory.getThreadMXBean().getThreadCount(),
                gcCount, gcTime, ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
    }
}
