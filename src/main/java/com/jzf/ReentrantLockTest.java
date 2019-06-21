package com.jzf;

import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使用ReentrantLock + ArrayList实现阻塞队列
 * 利用生产者/消费者测试
 *
 * @author Jia ZhiFeng <312290710@qq.com>
 * @date 2019/5/30 17:50:43
 */
public class ReentrantLockTest {
    /**
     * 测试生产者/消费者
     */
    public static void main(String[] args) {
        MyArrayBlockingQueue<String> queue = new MyArrayBlockingQueue<>(5);
//        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(5);

        //5个消费者
        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    String str = null;
                    try {
                        str = queue.take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println(Thread.currentThread().getName() + "消费消息: " + str);
                }
            }, "c" + i).start();
        }

        //5个生产者
        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    try {
                        queue.put(System.currentTimeMillis() + "");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println(Thread.currentThread().getName() + "生产消息");
                }
            }, "p" + i).start();
        }

//        new Thread(() -> {
//            while (true){
//                System.out.println(Thread.currentThread().getName() + "监控消息:" + queue.size());
//            }
//        }, "m").start();

    }

    /**
     * 自定义阻塞队列(FIFO)
     * 利用ReentrantLock锁实现
     *
     * @author Jia ZhiFeng <312290710@qq.com>
     * @date 2019/5/30 17:46:41
     */
    static class MyArrayBlockingQueue<T> {
        Lock lock = new ReentrantLock(true);
        Condition c = lock.newCondition();
        Condition p = lock.newCondition();
        private ArrayList<T> list = new ArrayList();
        private volatile int size = 0;
        private int capacity;

        public MyArrayBlockingQueue(int capacity) {
            this.capacity = capacity;
        }

        public void put(T o) throws InterruptedException {
            lock.lock();
            try {
                while (size == capacity) {
                    p.await();
                }
                list.add(o);
                size++;
                c.signalAll();
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw e;
            } finally {
                lock.unlock();
            }
        }

        public T take() throws InterruptedException {
            lock.lock();
            try {
                while (list.isEmpty()) {
                    c.await();
                }

                T t = list.remove(0);
                size--;
                p.signalAll();
                return t;
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw e;
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }
    }
}

