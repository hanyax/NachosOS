package nachos.threads;

import java.util.*;

import nachos.machine.*;

import java.util.HashMap;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
		
		waitQueue = new HashMap<KThread, Long>();
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		LinkedList<KThread> temp = new LinkedList<>();
		for (KThread key : waitQueue.keySet()) {
			if (waitQueue.get(key) <= Machine.timer().getTime()) {
				if (!key.getCalled()) {
					key.setCalled();
					key.ready();
				}
				temp.add(key);
			}
		}
		// delete threads after for loop
		for(KThread t : temp) {
			waitQueue.remove(t);
		}
		KThread.yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;

		if (x > 0) {
			boolean intStatus = Machine.interrupt().disable();
			KThread thread = KThread.currentThread();
			waitQueue.put(thread, wakeTime);
			KThread.sleep();
			Machine.interrupt().restore(intStatus);
		}
	}

	/**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
    public boolean cancel(KThread thread) {
    	if (waitQueue.remove(thread) != null) {
    		boolean intStatus = Machine.interrupt().disable();
    		thread.ready();
    		Machine.interrupt().restore(intStatus);
    		return true;
    	} else {
    		return false; 
    	}
	}
        
    private Map<KThread, Long> waitQueue;
    
    // Implement more test methods here ...

    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() { 	
    	// Invoke your other test methods here ...
    	alarmTest1();
    	alarmTest3();
    }
    
    // Add Alarm testing code to the Alarm class
    public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
	
		for (int d : durations) {
		    t0 = Machine.timer().getTime();
		    ThreadedKernel.alarm.waitUntil (d);
		    t1 = Machine.timer().getTime();
		    System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
    }


	private static class WaitingThread implements Runnable {
		WaitingThread(int time) {
			this.time = time;
		}
		public void run() {
			long t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(time);
			long t1 = Machine.timer().getTime();
			System.out.println("alarmTest3: thread " + KThread.currentThread().getName() +
					" waited for " + (t1 - t0) + " ticks");
		}
		private int time;
	}

	public static void alarmTest3() {
		int durations[] = {80000, 5000, 5000, -1, 100, 100, 0, 8000};

		for(int d : durations) {
			KThread t = new KThread(new WaitingThread(d)); // creates new thread

			t.fork(); // puts thread on ready queue
			t.setName(Integer.toString(d));
		}

		ThreadedKernel.alarm.waitUntil(100000);
	}
}
