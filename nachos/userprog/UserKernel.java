package nachos.userprog;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());
		freeList = new LinkedList<Integer>();
		freeListLock = new Lock();
		ActiveProcessLock = new Lock();
		ProcessIDLock = new Lock();
		freeProcessId = 0;
		activeProcess = 0;
		
		int numPhysPages = Machine.processor().getNumPhysPages();
		for (int i = 0; i < numPhysPages; i++) {
			freeList.add(i);
		}

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		
		System.out.println("Start to run self-test");
		
	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		if (!process.execute(shellProgram, new String[] {})) {
		    System.out.println ("Could not find executable '" +
					shellProgram + "', trying '" +
					shellProgram + ".coff' instead.");
		    shellProgram += ".coff";
		    if (!process.execute(shellProgram, new String[] {})) {
			System.out.println ("Also could not find '" +
					    shellProgram + "', aborting.");
			Lib.assertTrue(false);
		    }
		}
	
		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}
	
	public static int allocatePage() {
		try {
			return freeList.removeFirst();
		} catch(Exception NoSuchElementException) {
			return -1;
		}
	}
	
	public static int collectPage(int ppn) {
		if (freeList.add(ppn)) {
			return 1;
		} else {
			return -1;
		}
	}
	
	public static int getFreeListSize() {
		return freeList.size();
	}

	public static int getFreeProcessId() {
		int temp = freeProcessId;
		freeProcessId += 1;
		return temp;
	}
	
	public static int getActiveProcessCount() {
		return activeProcess;
	}
	
	public static void increaseActiveProcess() {
		activeProcess++;
	}
	
	public static void decreaseActiveProcess() {
		activeProcess--;
	}
	
	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;
	
	private static LinkedList<Integer> freeList;
	
	private static int freeProcessId;
	private static int activeProcess;
	
	public static Lock freeListLock;
	public static Lock ProcessIDLock;
	public static Lock ActiveProcessLock;
}

