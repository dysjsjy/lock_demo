package tech.insight2;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

public class MyLock {

    AtomicInteger state = new AtomicInteger(0);

    Thread owner = null;

    AtomicReference<Node> head = new AtomicReference<>(new Node());

    AtomicReference<Node> tail = new AtomicReference<>(head.get());

    void lock() {
        //如果state为0，则说明当前没有锁，可以直接上锁，
        //否则就对比owner是不是当前的线程只有当前的线程才能对当前锁继续重入
        if (state.get() == 0) {
            if (state.compareAndSet(0, 1)) {
                System.out.println(Thread.currentThread().getName() + "直接拿到了锁。");
                owner = Thread.currentThread();
                return;
            }
        } else {
            if (owner == Thread.currentThread()) {
                System.out.println(Thread.currentThread().getName() + "拿到了重入锁，当前重入次数为：" + state.incrementAndGet());
                return;
            }
        }

        //如果都不是则，则放入链表中
        Node current = new Node();
        current.thread = Thread.currentThread();
        while (true) {
            Node currentTail = tail.get();
            if (tail.compareAndSet(currentTail, current)) {
                System.out.println(Thread.currentThread().getName() + "加入到了链表尾");
                current.pre = currentTail;
                currentTail.next = current;
                break;
            }
        }
        //一直等待他拿到锁
        while (true) {
            if (head.get() == current.pre && state.compareAndSet(0, 1)) {
                owner = Thread.currentThread();
                head.set(current);
                current.pre.next = null;
                current.pre = null;
                System.out.println(Thread.currentThread().getName() + "被唤醒后拿到了锁。");
                return;
            }
            LockSupport.park();
        }

    }

    void unlock() {
        if (Thread.currentThread() != this.owner) {
            throw new IllegalStateException("当前线程没有锁。");
        }
        int i = state.get();
        if (i > 1) {
            state.set(i - 1);
            System.out.println(Thread.currentThread().getName() + "解锁了重入锁，重入锁剩余次数为" + (i - 1));
            return;
        }
        if (i <= 0) {
            throw new IllegalStateException("重入锁解锁错误。");
        }
        Node headNode = head.get();
        Node next = headNode.next;
        state.set(0);
        if (next != null) {
            System.out.println(Thread.currentThread().getName() + "唤醒了" + next.thread.getName());
            LockSupport.unpark(next.thread);
        }
    }

    class Node {
        Node pre;
        Node next;
        Thread thread;
    }
}
