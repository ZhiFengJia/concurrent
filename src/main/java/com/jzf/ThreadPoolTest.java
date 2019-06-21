package com.jzf;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手动创建线程池
 *
 * @author Jia ZhiFeng <312290710@qq.com>
 * @date 2019/6/20 10:37:29
 */
@SuppressWarnings("all")
public class ThreadPoolTest {
    private static final int TASK_COUNT = 10;//任务数
    private static final CountDownLatch cdl = new CountDownLatch(TASK_COUNT);

    public static void main(String[] args) {
        threadPoolExecutorTest();
//        forkJoinPoolTest();
//        scheduledThreadPoolTest();

        try {
            cdl.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("done");
    }

    private static void threadPoolExecutorTest() {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                4, 10,
                1L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(16),
                new CustomThreadFactory(true),
                new ThreadPoolExecutor.AbortPolicy());

        for (int i = 0; i < TASK_COUNT; i++) {
            threadPoolExecutor.execute(() -> {
                System.out.println(Thread.currentThread().getName());
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cdl.countDown();
            });
        }
    }

    private static void forkJoinPoolTest() {
        ForkJoinPool forkJoinPool = new ForkJoinPool(
                Runtime.getRuntime().availableProcessors(),
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null, true);

        for (int i = 0; i < TASK_COUNT; i++) {
            forkJoinPool.execute(() -> {
                System.out.println(Thread.currentThread().getName());
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cdl.countDown();
            });
        }
    }

    private static void scheduledThreadPoolTest() {
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(
                4,
                new CustomThreadFactory(true),
                new ThreadPoolExecutor.AbortPolicy());

        for (int i = 0; i < TASK_COUNT; i++) {
            scheduledThreadPoolExecutor.schedule(() -> {
                System.out.println(Thread.currentThread().getName());

                cdl.countDown();
            }, i, TimeUnit.SECONDS);
        }
    }

    static class CustomThreadFactory implements ThreadFactory {
        private static final ThreadGroup group;
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(0);
        private String namePrefix;
        private boolean isDaemon;

        static {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
        }

        CustomThreadFactory(boolean isDaemon) {
            this("customPool-" + poolNumber.getAndIncrement() + "-thread-", isDaemon);

        }

        CustomThreadFactory(String namePrefix, boolean isDaemon) {
            this.namePrefix = namePrefix;
            this.isDaemon = isDaemon;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            t.setDaemon(isDaemon);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}