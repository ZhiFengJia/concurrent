package com.jzf;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * https://www.cnblogs.com/waterystone/p/4920797.html
 *
 * @author Jia ZhiFeng <312290710@qq.com>
 * @date 2019/7/2 15:22:05
 */
public class AbstractQueuedSynchronizerTest {
    volatile static int count = 0;

    public static void main(String[] args) throws InterruptedException {
//        ReentrantLock lock = new ReentrantLock();
        JZFReentrantLock lock = new JZFReentrantLock();

        CountDownLatch cdl = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                lock.lock();
                try {
                    for (int j = 0; j < 10000; j++)
                        count++;
                } finally {
                    lock.unlock();
                }
                cdl.countDown();
            }).start();
        }

        cdl.await();
        System.out.println(count);
    }
}

/**
 * 可重入的,独占的,公平锁
 */
class JZFReentrantLock implements Lock {
    private final Sync sync = new Sync();

    private class Sync extends AbstractQueuedSynchronizer {

        protected Sync() {
            super();
        }

        @Override
        protected boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (isHeldExclusively()) {
                // A reentrant acquire; increment hold count
                // 这里是实现重入锁的关键.
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            } else if (hasQueuedPredecessors()) {
                // 表示当前线程之前有一个排队的线程,这里是实现公平锁的关键.
                return false;
            } else if (c == 0) {
                // try to acquire normally
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        @Override
        protected boolean isHeldExclusively() {
            return super.getExclusiveOwnerThread() == Thread.currentThread();
        }
    }

    @Override
    public void lock() {
        sync.acquire(1);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    @Override
    public boolean tryLock() {
        return sync.tryAcquire(1);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(time));
    }

    @Override
    public void unlock() {
        sync.release(1);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Codition not support");
    }
}