package nachos.threads;

import java.util.Queue;
import java.util.LinkedList;

import java.util.HashMap;
import java.util.Map;

import nachos.machine.*;
import java.util.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
	
    public Rendezvous () {
    	valuesofThread1 = new HashMap<Integer, Integer>();
    	valuesofThread2 = new LinkedList<Integer>();
    	conditionLock = new Lock();
    	tagMap = new HashMap<Integer,Condition>();
    }
    
    /*
    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exchange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) { 
    	conditionLock.acquire();
    	int res = 0;
    	if(!tagMap.containsKey(tag)) {
    		Condition cvSingleTag = new Condition(conditionLock);
    		tagMap.put(tag, cvSingleTag); 
    		valuesofThread1.put(tag, value);
    		cvSingleTag.sleep(); // Store T1/3/5 in cvSingleTag and Put T1/3/5 to sleep
    		res = valuesofThread2.poll(); // Pull T2/4/6's value out 
    	}
    	else {
    		Condition cvSingleTag = tagMap.get(tag); 
    		int temp = valuesofThread1.get(tag);
    		tagMap.remove(tag);
    		valuesofThread1.remove(tag);
    		valuesofThread2.offer(value);
    		res = temp;
    		cvSingleTag.wake(); // Wake Up T1/3/5 and put it to readyQueue
    	}
		conditionLock.release();
		return res;
    }
    
    // Place Rendezvous test code inside of the Rendezvous class.
    
    public static void selfTest() {
		// place calls to your Rendezvous tests that you implement here
		rendezTest5();
		rendezTest3();
		rendezTest2();
		rendezTest1();
    }
    
    public static void rendezTest1() {
    	final Rendezvous r = new Rendezvous();
	
		KThread t1 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = -1;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t1.setName("t1");
		
		KThread t2 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 1;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t2.setName("t2");
		
		KThread t3 = new KThread( new Runnable () {
			public void run() {
			    int tag = 1;
			    int send = 2;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 3, "Was expecting " + 3 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t3.setName("t3");
		
		t1.fork(); t3.fork(); t2.fork();
		// assumes join is implemented correctly
		t1.join(); t3.join(); t2.join();
    }
    
    public static void rendezTest2() {
	final Rendezvous r = new Rendezvous();

	KThread t1 = new KThread( new Runnable () {
		public void run() {
		    int tag = 0;
		    int send = -1;

		    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
		    int recv = r.exchange (tag, send);
		    Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
		    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	    });
		t1.setName("t1");
		
		KThread t2 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 1;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t2.setName("t2");
		
		KThread t3 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 2;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 3, "Was expecting " + 3 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t3.setName("t3");
		
		KThread t4 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 3;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t4.setName("t4");
		
		KThread t5 = new KThread( new Runnable () {
			public void run() {
			    int tag = 1;
			    int send = 4;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 5, "Was expecting " + 5 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t5.setName("t5");
		
		KThread t6 = new KThread( new Runnable () {
			public void run() {
			    int tag = 1;
			    int send = 5;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 4, "Was expecting " + 4 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t6.setName("t6");
		
		KThread t7 = new KThread( new Runnable () {
			public void run() {
			    int tag = 2;
			    int send = 6;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 7, "Was expecting " + 7 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t7.setName("t7");
		
		KThread t8 = new KThread( new Runnable () {
			public void run() {
			    int tag = 2;
			    int send = 7;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 6, "Was expecting " + 6 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t8.setName("t8");
	
		t1.fork(); t5.fork(); t2.fork(); t7.fork(); t3.fork(); t6.fork(); t4.fork(); t8.fork();
		// assumes join is implemented correctly
		t1.join(); t5.join(); t2.join(); t7.join(); t3.join(); t6.join(); t4.join(); t8.join();
    }

    public static void rendezTest3() {
	final Rendezvous r = new Rendezvous();

		KThread t1 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = -1;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t1.setName("t1");
		
		KThread t2 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 1;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t2.setName("t2");
		
		KThread t3 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 2;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 3, "Was expecting " + 3 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t3.setName("t3");
		
		KThread t4 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 3;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t4.setName("t4");
		
		KThread t5 = new KThread( new Runnable () {
			public void run() {
			    int tag = 1;
			    int send = 4;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 5, "Was expecting " + 5 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t5.setName("t5");
		
		KThread t6 = new KThread( new Runnable () {
			public void run() {
			    int tag = 1;
			    int send = 5;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 4, "Was expecting " + 4 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t6.setName("t6");
		
		KThread t7 = new KThread( new Runnable () {
			public void run() {
			    int tag = 1;
			    int send = 6;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 7, "Was expecting " + 7 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t7.setName("t7");
		
		KThread t8 = new KThread( new Runnable () {
			public void run() {
			    int tag = 1;
			    int send = 7;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r.exchange (tag, send);
			    Lib.assertTrue (recv == 6, "Was expecting " + 6 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t8.setName("t8");
	
		t1.fork(); t5.fork(); t2.fork(); t6.fork(); t3.fork(); t7.fork(); t4.fork(); t8.fork();
		// assumes join is implemented correctly
		t1.join(); t5.join(); t2.join(); t6.join(); t3.join(); t7.join(); t4.join(); t8.join();
    }
    
    public static void rendezTest5() {
	final Rendezvous r_1 = new Rendezvous();
	final Rendezvous r_2 = new Rendezvous();

		KThread t1_1 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = -1;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r_1.exchange (tag, send);
			    Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t1_1.setName("t1_1");
		
		KThread t1_2 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 1;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r_1.exchange (tag, send);
			    Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t1_2.setName("t1_2");
		
		KThread t2_1 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 2;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r_2.exchange (tag, send);
			    Lib.assertTrue (recv == 3, "Was expecting " + 3 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t2_1.setName("t2_1");
		
		KThread t2_2 = new KThread( new Runnable () {
			public void run() {
			    int tag = 0;
			    int send = 3;
	
			    System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
			    int recv = r_2.exchange (tag, send);
			    Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
			    System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
			}
		    });
		t2_2.setName("t2_2");
		
		
	
		t1_1.fork(); t2_1.fork(); t1_2.fork(); t2_2.fork();
		// assumes join is implemented correctly
		t1_1.join(); t2_1.join(); t1_2.join(); t2_2.join(); 
    }
    
    private Lock conditionLock;
    private Map<Integer,Integer> valuesofThread1;
    private Queue<Integer> valuesofThread2;
    private Map<Integer,Condition> tagMap;
}
