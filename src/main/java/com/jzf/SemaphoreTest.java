package com.jzf;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author Jia ZhiFeng <312290710@qq.com>
 * @date 2019/7/2 15:27:34
 */
public class SemaphoreTest {
    public static void main(String[] args){
        Semaphore semaphore = new Semaphore(2);

//        semaphore.reducePermits();

        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                semaphore.acquireUninterruptibly();
                try {
                    System.out.println(Thread.currentThread() + "run...");
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    semaphore.release();
                }
            }).start();
        }
    }
}
