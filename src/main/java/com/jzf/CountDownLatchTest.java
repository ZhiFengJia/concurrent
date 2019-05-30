package com.jzf;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CountDownLatch 门闩,
 * 创建时指定一个数量,然后调用countDown()方法递减到指定数量后,await()方法才可以通过.
 *
 * @author Jia ZhiFeng <312290710@qq.com>
 * @date 2019/5/30 14:07:46
 */
public class CountDownLatchTest {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);

        new Thread(() -> {
            System.out.println(Thread.currentThread().getName() + ": 等待5秒后调用countDown()方法");
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            cdl.countDown();
        }).start();

        cdl.await();

        System.out.println("end");
    }
}
