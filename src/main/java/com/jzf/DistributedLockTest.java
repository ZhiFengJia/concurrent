package com.jzf;

import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

public class DistributedLockTest {
    private static final Logger logger = LoggerFactory.getLogger(DistributedLockTest.class);
    private static int count = 1000;

    public static void main(String[] args) throws InterruptedException {
        final int lockCount = count;
        CountDownLatch cdl = new CountDownLatch(lockCount);
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                10, 10,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(lockCount),
                new DefaultThreadFactory("app-thread"),
                new ThreadPoolExecutor.AbortPolicy());

//        ReentrantLock lock = new ReentrantLock(true);
        DistributedLock lock = new DistributedLock("192.168.2.11:2101,192.168.2.11:2102,192.168.2.11:2103");

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < lockCount; i++) {
            threadPoolExecutor.execute(() -> {
                try {
                    lock.lock();
                    lock.lock();// 测试锁可重入特性
                    logger.info("{}", --count);
                    cdl.countDown();
                } finally {
                    lock.unlock();
                    lock.unlock();
                }
            });
        }
        cdl.await();
        long endTime = System.currentTimeMillis();
        logger.info("{}个锁总耗时:{}ms", lockCount, endTime - startTime);
    }
}

/**
 * 可重入,独占的,公平的,基于ZK中间件的分布式锁
 */
class DistributedLock extends AbstractOwnableSynchronizer implements Lock {
    private static final Logger logger = LoggerFactory.getLogger(DistributedLock.class);
    private static final int SESSION_TIMEOUT = 10 * 60 * 1000;//10分钟
    // 根节点
    private static final String ROOT_LOCK = "/lock";
    private static final String LOCK_NAME = "app";
    private static final String SPLIT_STR = "_lock_";
    private ZooKeeper zk;

    // 存储当前线程所拥有的锁
    private static final ThreadLocal<String> threadLocal = new ThreadLocal<>();
    // 存储当前JVM中所有的锁和与之对应的线程
    private static final ConcurrentSkipListMap<String, Thread> nodes = new ConcurrentSkipListMap<>(String::compareTo);

    /**
     * The synchronization state.
     */
    private volatile int state;

    public DistributedLock(String connectString) {
        // 连接zookeeper
        try {
            this.zk = new ZooKeeper(connectString, SESSION_TIMEOUT, new NodeDeletedWatcher());
            Stat stat = zk.exists(ROOT_LOCK, false);
            if (stat == null) {
                // 如果根节点不存在，则创建根节点
                zk.create(ROOT_LOCK, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (IOException | KeeperException | InterruptedException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            logger.info("创建zk连接异常");
            e.printStackTrace();
        }
    }

    private class NodeDeletedWatcher implements Watcher {
        @Override
        public void process(WatchedEvent event) {
            if (event.getType().equals(Event.EventType.NodeDeleted)) {
                logger.info(event.toString());
                String path = event.getPath().substring(event.getPath().lastIndexOf("/") + 1);

                // 当前JVM中,其中一个线程在ZK中的锁被意外删除,JVM中将对应的线程重新在ZK中创建一个锁,并watch到ZK中最后一个锁
                // 这种情况可以不用处理,除非人为操作,否则锁被意外删除时,JVM必定已经意外退出;
                if (nodes.containsKey(path)) {
                    Thread eventThread = nodes.remove(path);
                    logger.info("当前JVM中,其中一个线程在ZK中的锁被意外删除,JVM中将对应的线程重新在ZK中创建一个锁,并watch到ZK中最后一个锁 {},{}", path, eventThread);
                    LockSupport.unpark(eventThread);
                    return;
                }

                //zk回调当前jvm,那么下个等待获取锁的线程必然在当前JVM.
                Map.Entry<String, Thread> firstEntry = nodes.firstEntry();
                logger.info("唤醒下个线程运行状态 {},{}", firstEntry.getKey(), firstEntry.getValue());
                //恢复下个节点的运行状态
                LockSupport.unpark(firstEntry.getValue());
            }
        }
    }

    @Override
    public void lock() {
        while (!tryAcquire(1)) {
            LockSupport.park(this);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        while (!tryAcquire(1)) {
            LockSupport.park(this);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
    }

    @Override
    public boolean tryLock() {
        return tryAcquire(1);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
//        while (!tryAcquire(1)){
//            LockSupport.park(this);
//            //如果是中断原因的话,继续剩余时间循环执行
//            //如果不是中断原因的，
//            if(Thread.interrupted()){
//                //如果是中断原因的话,继续循环执行
//            }
//        }
        throw new UnsupportedOperationException("Codition not support");
    }

    @Override
    public void unlock() {
        tryRelease(1);
    }


    private boolean tryAcquire(int acquires) {
        int c = getState();
        if (isHeldExclusively()) {
            // 这里是实现重入锁的关键.
            int nextc = c + acquires;
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        } else if (c == 0) {
            // try to acquire normally
            if (tryMultijVMAcquire() && compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
        }
        return false;
    }

    /**
     * 看当前节点是否可以获取到锁
     *
     * @return
     */
    private boolean tryMultijVMAcquire() {
        try {
            Thread currentThread = Thread.currentThread();
            String currentLock = threadLocal.get();
            if (currentLock == null ||
                    !nodes.containsKey(currentLock)) {// 表示threadLocal中存储的lock已经过期,需要重新创建锁
                //如果当前线程没有创建过节点的话,就创建一个lock节点.
                currentLock = zk.create(ROOT_LOCK + "/" + LOCK_NAME + SPLIT_STR, new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

                currentLock = currentLock.substring(currentLock.lastIndexOf("/") + 1);
                threadLocal.set(currentLock);
                nodes.put(currentLock, currentThread);
                logger.info("创建锁 {}", currentLock);
            }

            // 获取取所有子节点,取出所有lockName的锁
            List<String> subNodes = zk.getChildren(ROOT_LOCK, false);
            List<String> lockObjects = new ArrayList<>();
            for (String node : subNodes) {
                String _node = node.split(SPLIT_STR)[0];
                if (_node.equals(LOCK_NAME)) {
                    lockObjects.add(node);
                }
            }
            if (lockObjects.isEmpty()) {
                logger.info("zk中没有锁 {}", currentLock);
                // 表示zk中最后一个锁被意外删除,这种情况下对应的JVM必定已经意外退出,所以无需处理
                return false;
            }
            lockObjects = lockObjects.stream().sorted().collect(Collectors.toList());
            // 若当前节点为最小节点，则获取锁成功
            if (currentLock.equals(lockObjects.get(0))) {
                logger.info("获取锁 {}", currentLock);
                return true;
            }

            //找到当前锁的索引
            int index = Collections.binarySearch(lockObjects, currentLock);
            if (index == -1) {
                logger.info("zk中没有找到当前锁 {}", currentLock);
                // 表示zk中最后一个锁被意外删除后,后续又创建新锁,这种情况下对应的JVM必定已经意外退出,所以无需处理
                return false;
            }
            // 若不是最小节点，则找到自己的前一个节点
            String waitPrevLock = lockObjects.get(index - 1);
            Stat stat = zk.exists(ROOT_LOCK + "/" + waitPrevLock, true);
            if (stat == null) {
                logger.info("上个锁已释放 {}", waitPrevLock);
                return true;
            } else {
                logger.info("等待上个锁释放 {}", waitPrevLock);
            }
        } catch (KeeperException | InterruptedException e) {
            logger.info("获取锁异常1");
            e.printStackTrace();
        } catch (Throwable e) {
            logger.info("获取锁异常2");
            e.printStackTrace();
        }
        return false;
    }

    private boolean tryRelease(int releases) {
        int c = getState() - releases;
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        setState(c);
        boolean free = false;
        if (c == 0) {
            try {
                String currentLock = threadLocal.get();
                //删除当前锁
                nodes.remove(currentLock);
                threadLocal.remove();
                free = true;
                setExclusiveOwnerThread(null);
                logger.info("释放锁 {}", currentLock);
                zk.delete(ROOT_LOCK + "/" + currentLock, -1);
            } catch (InterruptedException | KeeperException e) {
                logger.info("释放锁异常1");
                e.printStackTrace();
            } catch (Throwable e) {
                logger.info("释放锁异常2");
                e.printStackTrace();
            }
        }
        return free;
    }

    private boolean isHeldExclusively() {
        return super.getExclusiveOwnerThread() == Thread.currentThread();
    }

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     *
     * @return current state value
     */
    private int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     *
     * @param newState the new state value
     */
    private void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     * value was not equal to the expected value.
     */
    private boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    private static final Unsafe unsafe;
    private static final long stateOffset;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);

            stateOffset = unsafe.objectFieldOffset
                    (DistributedLock.class.getDeclaredField("state"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Codition not support");
    }
}