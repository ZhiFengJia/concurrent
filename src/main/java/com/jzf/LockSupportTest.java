package com.jzf;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 解除指定线程wait状态
 *
 * @author Jia ZhiFeng <312290710@qq.com>
 * @date 2019/6/14 14:32:28
 */
public class LockSupportTest {
    public static void main(String[] args) throws Exception {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("start");
                LockSupport.park(this); //一直waiting,推荐使用该方法，而不是park(),因为这个函数可以记录阻塞的发起者，如果发生死锁方便查看
//                LockSupport.park(); //一直waiting
                System.out.println("continue");
            }
        });
        t.start();

        TimeUnit.SECONDS.sleep(10);
        LockSupport.unpark(t); //t线程解除 WAITING(parking) 状态
    }
}
