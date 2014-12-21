package com.attensity.test;

import com.google.common.util.concurrent.RateLimiter;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.attensity.es.concurrent.*;

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

/*
 * This Java source file was auto generated by running 'gradle init --type java-library'
 * by 'Kyle' at '12/6/14 4:57 PM' with Gradle 2.2
 *
 * @author Kyle, @date 12/6/14 4:57 PM
 */
@RunWith(Parameterized.class)
public class ElasticsearchLoadTest {

    private int numUsers;
    private int durationInSeconds;
    private int messageRatePerSecond;
    private int bulkSize;
    private int concurrentBulkRequestsPerUser;
    private int flushSizeInMB;
    private int flushTimeInSeconds;

    public ElasticsearchLoadTest(int numUsers, int durationInSeconds, int messageRatePerSecond, int bulkSize,
                                 int concurrentBulkRequestsPerUser, int flushSizeInMB, int flushTimeInSeconds) {

        this.numUsers = numUsers;
        this.durationInSeconds = durationInSeconds;
        this.messageRatePerSecond = messageRatePerSecond;
        this.bulkSize = bulkSize;
        this.concurrentBulkRequestsPerUser = concurrentBulkRequestsPerUser;
        this.flushSizeInMB = flushSizeInMB;
        this.flushTimeInSeconds = flushTimeInSeconds;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> returnParameters() {
        return Arrays.asList(new Object[][]{
                // increase bulk size
                {1, 60, 5000, 1000, 5, 15, 5}

//                {1, 60, 5000, 2000, 1, 15, 5},
//                {1, 60, 5000, 3000, 1, 15, 5},
//
//                // increase concurrent requests per user
//                {1, 60, 5000, 1000, 1, 15, 5},
//                {1, 60, 5000, 1000, 2, 15, 5},
//                {1, 60, 5000, 1000, 4, 15, 5},
//                {1, 60, 5000, 1000, 8, 15, 5},
//
//                // increase bulk size in mb
//                {1, 60, 5000, 1000, 1, 5, 5},
//                {1, 60, 5000, 1000, 1, 10, 5},
//                {1, 60, 5000, 1000, 1, 15, 5}
        });
    }

    @Test
    public void loadTestElasticsearch() throws InterruptedException, ExecutionException {
        System.out.println(this.getClass().getCanonicalName());
        System.out.printf("Started elasticsearch load test with the following parameters...\n%s users\n%s seconds long\n%s message per second\n%s messages per bulk request\n\n",
                numUsers,
                durationInSeconds,
                messageRatePerSecond,
                bulkSize
        );
        ExecutorService executorService = Executors.newFixedThreadPool(numUsers);
        List<UserThread> threadList = Collections.synchronizedList(new ArrayList<>(numUsers));
        System.out.println("Creating threads...");
        ExecutorService threadSetupService = Executors.newFixedThreadPool(numUsers);
        AtomicInteger atomicInteger = new AtomicInteger(0);
        Collection<Callable<Void>> callables = new ArrayList<>(numUsers);
        RateLimiter rateLimiter = RateLimiter.create(messageRatePerSecond);
        for (int i = 0; i < numUsers; i++) {
            callables.add(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Settings settings = ImmutableSettings.settingsBuilder()
                            .put("cluster.name", "dev-es-attensity").build();

                    Client client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("54.148.165.96", 9300));

                    UserThread userThread = new UserThread(atomicInteger.incrementAndGet(), rateLimiter, client, bulkSize, concurrentBulkRequestsPerUser, flushSizeInMB, flushTimeInSeconds);
                    threadList.add(userThread);
                    System.out.printf("Created thread with id: %s\n", userThread.getId());
                    return null;
                }
            });
        }

        List<Future<Void>> futures = threadSetupService.invokeAll(callables);
        for (Future future : futures) {
            future.get();
        }

        System.out.printf("Starting threads...\n");
        System.out.printf("Test running for %s seconds\n", durationInSeconds * 1000);
        threadList.forEach(thread -> executorService.execute(thread));


        Thread.sleep(durationInSeconds * 1000L);

        System.out.println("\nShutting down threads.");
        threadList.forEach(userThread -> userThread.stop());
        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        int totalMessages = 0;
        for (UserThread thread : threadList) {
            totalMessages += thread.getCtr();
        }

        System.out.println("Total messages processed: " + totalMessages);
        System.out.printf("Messages per second (attempted/actual: %s/%s\n", totalMessages / durationInSeconds,
                messageRatePerSecond);
    }
}
