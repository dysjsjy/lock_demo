package tech.insight2;

import java.util.concurrent.atomic.AtomicReference;

public class test {
}

class ReentrantFairLock {

    private static class Node {
        Thread thread; // 当前线程
        Node prev;    // 前驱节点
        Node next;    // 后继节点
        int lockCount = 0; // 重入计数

        Node(Thread thread) {
            this.thread = thread;
        }
    }

    private final AtomicReference<Node> head = new AtomicReference<>(null); // 链表头
    private final AtomicReference<Node> tail = new AtomicReference<>(null); // 链表尾
    private final AtomicReference<Thread> owner = new AtomicReference<>(null); // 当前持有锁的线程
    private final ThreadLocal<Node> currentNode = ThreadLocal.withInitial(() -> null); // 当前线程对应的节点

    // 加锁
    public void lock() {
        Node node = currentNode.get();
        if (node != null && node.thread == Thread.currentThread()) {
            node.lockCount++; // 重入计数增加
            return;
        }

        node = new Node(Thread.currentThread());
        currentNode.set(node);

        // 将当前节点添加到链表尾部
        Node prev = tail.getAndSet(node);
        if (prev != null) {
            prev.next = node;
            node.prev = prev;

            // 自旋等待，直到当前节点成为链表头
            while (head.get() != node) {
                Thread.yield(); // 让出 CPU 时间片，避免忙等待
            }
        } else {
            // 如果链表为空，当前节点成为头节点
            head.set(node);
        }

        owner.set(Thread.currentThread()); // 设置当前线程为锁的持有者
        node.lockCount = 1; // 初始化重入计数
    }

    // 解锁
    public void unlock() {
        Node node = currentNode.get();
        if (node == null || node.thread != Thread.currentThread()) {
            throw new IllegalMonitorStateException("Current thread does not hold the lock");
        }

        node.lockCount--; // 重入计数减少
        if (node.lockCount == 0) {
            // 如果重入计数为 0，释放锁并移除节点
            if (node.next != null) {
                head.set(node.next);
                node.next.prev = null;
            } else {
                head.set(null);
                tail.set(null);
            }

            owner.set(null); // 释放锁
            currentNode.remove(); // 清除当前线程的节点
        }
    }

    // 测试
    public static void main(String[] args) {
        ReentrantFairLock lock = new ReentrantFairLock();

        Runnable task = () -> {
            lock.lock();
            try {
                System.out.println(Thread.currentThread().getName() + " acquired the lock");
                Thread.sleep(1000); // 模拟任务执行
                lock.lock(); // 重入锁
                System.out.println(Thread.currentThread().getName() + " re-entered the lock");
                lock.unlock(); // 释放重入锁
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
                System.out.println(Thread.currentThread().getName() + " released the lock");
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);

        t1.start();
        t2.start();
    }
}