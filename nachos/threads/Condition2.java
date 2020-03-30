package nachos.threads;

import java.util.LinkedList;
import java.util.Queue;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		alarm = new Alarm();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean intStatus = Machine.interrupt().disable();
		// Release lock
		conditionLock.release();
		
		// Thread wait on Condition Variable
		KThread thread = KThread.currentThread();
		waitQueue.add(thread);
		
		// Put the thread to sleep
		KThread.currentThread().sleep();
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);

	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = waitQueue.poll();

		boolean canceled;
		if ( thread != null && !thread.getCalled() ) {
			// if the thread has not been waked by alarm
			canceled = alarm.cancel(thread);
			thread.setCalled();
			if (!canceled) {
				thread.ready();
			}
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean intStatus = Machine.interrupt().disable();
		while (waitQueue.peek() != null) {
			this.wake();
		}
		Machine.interrupt().restore(intStatus);
	}

    /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
    public void sleepFor(long timeout) {
		boolean intStatus = Machine.interrupt().disable();
		// Release lock
		conditionLock.release();
		
		// Add to CV queue
		KThread thread = KThread.currentThread();
		waitQueue.add(thread);
		
		// Add to alarm queue
		alarm.waitUntil(timeout);
		
		Machine.interrupt().restore(intStatus);		
		conditionLock.acquire();
	}
        
    // Invoke Condition2.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
    	// Test for condition 2
        new InterlockTest();
    	cvTest5();
    	testWakeAll();
    	testCallWithoutLock();
    	testWakwOnNoSleep();
    	
    	// Test for sleepfor
    	sleepForTest1();
    	sleepForCallBeforeTimeout();
    }
    
    // Place Condition2 testing code in the Condition2 class.
    
    // Example of the "interlock" pattern where two threads strictly
    // alternate their execution with each other using a condition
    // variable.  (Also see the slide showing this pattern at the end
    // of Lecture 6.)
    private static class InterlockTest {
        private static Lock lock;
        private static Condition2 cv;

        private static class Interlocker implements Runnable {
            public void run () {
                lock.acquire();
                for (int i = 0; i < 10; i++) {
                    System.out.println(KThread.currentThread().getName());
                    cv.wake();   // signal
                    cv.sleep();  // wait
                }
                lock.release();
            }
        }

        public InterlockTest () {
            lock = new Lock();
            cv = new Condition2(lock);

            KThread ping = new KThread(new Interlocker());
            ping.setName("ping");
            KThread pong = new KThread(new Interlocker());
            pong.setName("pong");

            ping.fork();
            pong.fork();

            // We need to wait for ping to finish, and the proper way
            // to do so is to join on ping.  (Note that, when ping is
            // done, pong is sleeping on the condition variable; if we
            // were also to join on pong, we would block forever.)
            // For this to work, join must be implemented.  If you
            // have not implemented join yet, then comment out the
            // call to join and instead uncomment the loop with
            // yields; the loop has the same effect, but is a kludgy
            // way to do it.
            ping.join();
            for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
        }
    }
    
    
    // Test programs should have exactly the same behavior with the
    // Condition and Condition2 classes.  You can first try a test with
    // Condition, which is already provided for you, and then try it
    // with Condition2, which you are implementing, and compare their
    // behavior.

    // Do not use this test program as your first Condition2 test.
    // First test it with more basic test programs to verify specific
    // functionality.
    public static void cvTest5() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }
    

    public static void testWakeAll() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer1 = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    System.out.println("I am awaked!");
                    lock.release();
                }
            });
        

        KThread consumer2 = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    System.out.println("I am awaked!");
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    System.out.println();
                    empty.wakeAll();
                    lock.release();
                }
            });

        consumer1.setName("Consumer1");
        consumer2.setName("Consumer2");
        producer.setName("Producer");
        consumer1.fork();
        consumer2.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer1.join();
        consumer2.join();
        producer.join();
        for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }
    
    // Place sleepFor test code inside of the Condition2 class.
    public static void testCallWithoutLock() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    //lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    //lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    //lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    //lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }
    
    public static void testWakwOnNoSleep() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer1 = new KThread( new Runnable () {
            public void run() {
                lock.acquire();
                while(!list.isEmpty()){
                    empty.sleep();
                }
                Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                System.out.println("I am awaked!");
                lock.release();
            }
        });
        
        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    System.out.println("WakeAll called");
                    
                    empty.wakeAll();
                    lock.release();
                }
            });
  
        producer.setName("Producer");
        consumer1.setName("consumer");

        producer.fork();
        consumer1.fork();
        
        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.

        producer.join();
        
        for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }
    
    private static void sleepForTest1() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
	
		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
				    " woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}
    
    private static void sleepForCallBeforeTimeout() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		long t0 = Machine.timer().getTime();
		
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                lock.acquire();
        		System.out.println (KThread.currentThread().getName() + " sleeping");
        		// no other thread will wake us up, so we should time out
        		cv.sleepFor(200000);
        		long t1 = Machine.timer().getTime();
        		System.out.println (KThread.currentThread().getName() +
        				    " woke up, slept for " + (t1 - t0) + " ticks");
                lock.release();
            }
        });
        
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                lock.acquire();
        		System.out.println (KThread.currentThread().getName() + " sleeping");
        		// no other thread will wake us up, so we should time out
        		cv.wake();
        		long t1 = Machine.timer().getTime();
        		System.out.println (KThread.currentThread().getName() +
        				    " woke up, slept for " + (t1 - t0) + " ticks");
                lock.release();
            }
        });
        
        t1.fork();
        t2.fork();
        
        t1.join();
        t2.join();
	}

    private static void sleepForWakeAll() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		long t0 = Machine.timer().getTime();
		
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                lock.acquire();
        		System.out.println (KThread.currentThread().getName() + " sleeping");
        		// no other thread will wake us up, so we should time out
        		cv.sleepFor(200000);
        		long t1 = Machine.timer().getTime();
        		System.out.println (KThread.currentThread().getName() +
        				    " woke up, slept for " + (t1 - t0) + " ticks");
                lock.release();
            }
        });
        
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                lock.acquire();
        		System.out.println (KThread.currentThread().getName() + " sleeping");
        		// no other thread will wake us up, so we should time out
        		cv.sleepFor(300000);
        		long t1 = Machine.timer().getTime();
        		System.out.println (KThread.currentThread().getName() +
        				    " woke up, slept for " + (t1 - t0) + " ticks");
                lock.release();
            }
        });
        
        KThread t3 = new KThread( new Runnable () {
            public void run() {
                lock.acquire();
        		System.out.println (KThread.currentThread().getName() + " sleeping");
        		// no other thread will wake us up, so we should time out
        		cv.wakeAll();
        		long t1 = Machine.timer().getTime();
        		System.out.println (KThread.currentThread().getName() +
        				    " woke up, slept for " + (t1 - t0) + " ticks");
                lock.release();
            }
        });
        
        t1.fork(); t2.fork(); t3.fork();
        
        t1.join(); t2.join(); t3.join();
	}
    private Lock conditionLock;
	private Queue<KThread> waitQueue = new LinkedList<KThread>();
	private Alarm alarm = new Alarm();
}
