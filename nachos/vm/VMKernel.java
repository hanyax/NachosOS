package nachos.vm;

import java.util.LinkedList;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {		
		super.initialize(args);
		int numPhysPages = Machine.processor().getNumPhysPages();
		IPT = new PageFrame[numPhysPages]; // init IPT
		swapFile = this.fileSystem.open(swapFileName, true);
		freeSwapPages = new LinkedList<Integer>();
		swapCount = 0;
		clockPosition = 0;
		
		freeSwapPagesLock = new Lock();
		IPTLock = new Lock();
		conditionLock = new Lock();
		pinQueue = new Condition(conditionLock);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		ThreadedKernel.fileSystem.remove(swapFileName);
		super.terminate();
	}
	
	public static void addPageFrame(UserProcess process, TranslationEntry entry, int ppn) {
		PageFrame frame = new PageFrame(process, entry);
		IPT[ppn] = frame;
	}
	
	public static boolean checkPin() {
		int pinned = 0;
		for (int i = 0; i < IPT.length; i++) {
			if (IPT[clockPosition].entry.valid && IPT[clockPosition].pinned) {
				pinned++;
			}
		}
		return (pinned == IPT.length);
	}
	
	public static TranslationEntry findVictim() {
		while(IPT[clockPosition].entry.used || !IPT[clockPosition].entry.valid || IPT[clockPosition].pinned) {
			IPT[clockPosition].entry.used = false;
			if (clockPosition < IPT.length - 1) {
				clockPosition++;
			} else {
				clockPosition = 0;
			}
		}
		
		return IPT[clockPosition].entry;
	}
	
	public static byte[] getVictimData() {
		byte[] data = new byte[Processor.pageSize];
		IPT[clockPosition].process.readVirtualMemory(IPT[clockPosition].entry.vpn * Processor.pageSize, data);
		return data;
	}
	
	public static void debugHelper() {
		for (int i = 0; i < IPT.length; i++) {
			if (IPT[i] != null) {
				System.out.println("ppn is " + i);
				System.out.println(IPT[i].process);
				System.out.println("Entry is: vpn " + IPT[i].entry.vpn + " ppn: " + IPT[i].entry.ppn + " valid: " + IPT[i].entry.valid +
						" dirty: " + IPT[i].entry.dirty);
			}
		}
	}
	
	public static int allocateSPN() {
		int result = -1;
		if (freeSwapPages.isEmpty()) {
			result = swapCount;
			swapCount++;
		} else {
			try {
				result = freeSwapPages.removeFirst();
			} catch(Exception NoSuchElementException) {
				result = -1;
			}
		}
		return result;
	}
	
	public static int collectSPN(int spn) {
		if (freeSwapPages.add(spn)) {
			return 1;
		} else {
			return -1;
		}
	}
	
	public static void updateIPTProcess(UserProcess process, int ppn) {
		IPT[ppn].process = process;
	}
	
	public static void updateIPTEntry(TranslationEntry entry, int ppn) {
		IPT[ppn].entry = entry;
	}
	
	public static void pinPage(int ppn) {
		IPT[ppn].pinned = true;
	}
	
	public static void unpinPage(int ppn) {
		IPT[ppn].pinned = false;
	}
	
	public static int writeSwapFile(int pos, byte[] buf) {
		return swapFile.write(pos, buf, 0, Processor.pageSize);
	}
	
	public static int readSwapFile(int pos, byte[] buf) {
		return swapFile.read(pos, buf, 0, Processor.pageSize);
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;
	
	private static OpenFile swapFile;
	private static LinkedList<Integer> freeSwapPages;
	private static int swapCount;
	private static PageFrame[] IPT;
	private static int clockPosition;
	private static final String swapFileName = "UniqueUnique";
	
	public static Lock freeSwapPagesLock;
	public static Lock IPTLock;
	public static Lock conditionLock;
	public static Condition pinQueue; 

	private static final char dbgVM = 'v';
	
	
	private static class PageFrame {
		public UserProcess process = new UserProcess();
		public TranslationEntry entry = new TranslationEntry();
		public boolean pinned;
		
		public PageFrame(UserProcess process, TranslationEntry entry) {
			this.process = process;
			this.entry = entry;
			this.pinned = false;
		}
	}
}
