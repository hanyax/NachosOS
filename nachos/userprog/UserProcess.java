package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.*;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {		
		// Define fileDescriptor
		fileDescriptor = new OpenFile[16];
		fileDescriptor[0] = UserKernel.console.openForReading();
		fileDescriptor[1] = UserKernel.console.openForWriting();
		
		clockPosition = 0;
		
        // Get a unique process ID
        UserKernel.ProcessIDLock.acquire();
        processId = UserKernel.getFreeProcessId();
        UserKernel.ProcessIDLock.release();
        
        ChildProcesses = new HashMap<UserProcess, Integer>();
        ParentProcess = null; // parent process init to null
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
        String name = Machine.getProcessClassName();
        	        
		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args)) 
			return false;
		
		// A process runs successful, become active
		UserKernel.ActiveProcessLock.acquire();
		UserKernel.increaseActiveProcess();
		UserKernel.ActiveProcessLock.release();
		
		thread = new UThread(this);
		thread.setName(name).fork();
		
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		Processor processor = Machine.processor();
		int remain = length;
		int currVaddr = vaddr;
		int currOffset = offset;
		int sum = 0;
		while (remain > 0) {
			int vpn = Processor.pageFromAddress(currVaddr);
			int pageOffset = Processor.offsetFromAddress(currVaddr);
			int index = -1;
			for(int i = 0; i < pageTable.length; i++) {
				if(pageTable[i].vpn == vpn) {
					index = i;
					if(pageTable[i].valid == false) {
						//processor.writeRegister(processor.regBadVAddr, currVaddr);
						handlePageFault(i);
					}
					break;
				}
			}
			if(index == -1)
				return 0;
			
			int ppn = pageTable[index].ppn;
			VMKernel.pinPage(ppn);
			int paddr = pageSize * ppn + pageOffset;
			int maxAddr = pageSize * (ppn + 1); // Only read current page
			// for now, just assume that virtual addresses equal physical addresses

			if(paddr < 0 || paddr >= memory.length)
				return 0;

			int amount = Math.min(remain, maxAddr - paddr);
			System.arraycopy(memory, paddr, data, currOffset, amount);
			remain -= amount;
			currVaddr += amount;
			currOffset += amount;
			sum += amount;
			VMKernel.unpinPage(ppn);
			
			VMKernel.conditionLock.acquire();
			VMKernel.pinQueue.wake();
			VMKernel.conditionLock.release();
		}
		return sum; 
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		byte[] memory = Machine.processor().getMemory();
		Processor processor = Machine.processor();
		int remain = length;
		int currVaddr = vaddr;
		int currOffset = offset;
		int sum = 0;
		while (remain > 0) {
			int vpn = Processor.pageFromAddress(currVaddr);
			int pageOffset = Processor.offsetFromAddress(currVaddr);
			int index = -1;
			for (int i = 0; i < pageTable.length; i++) {
				if (pageTable[i].vpn == vpn) {
					index = i;
					if(pageTable[i].valid == false) {
						//processor.writeRegister(processor.regBadVAddr, currVaddr);
						handlePageFault(i);
					}
					break;
				}
			}
			if (index == -1) {
				return 0;
			}
		
			int ppn = pageTable[index].ppn;
			VMKernel.pinPage(ppn);
			int paddr = pageSize * ppn + pageOffset;
			int maxAddr = pageSize * (ppn + 1);
			// for now, just assume that virtual addresses equal physical addresses
			if (paddr < 0 || paddr >= memory.length)
				return 0;
			int amount = Math.min(remain, maxAddr - paddr);
			System.arraycopy(data, currOffset, memory, paddr, amount);
			remain -= amount;
			currVaddr += amount;
			currOffset += amount;
			sum += amount;
			VMKernel.unpinPage(ppn);
			
			VMKernel.conditionLock.acquire();
			VMKernel.pinQueue.wake();
			VMKernel.conditionLock.release();
			
			pageTable[index].dirty = true;
		}
		
		return sum;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;
		
		// initiate pageTable
		pageTable = new TranslationEntry[numPages];
		
		if (!loadSections())
			return false;
		
		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		UserKernel.freeListLock.acquire();
		if (numPages > UserKernel.getFreeListSize()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.freeListLock.release();
			return false;
		} 
		
		int index = 0;
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			boolean readyOnly = section.isReadOnly();

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				int ppn = UserKernel.allocatePage();
				
				pageTable[index] = new TranslationEntry(vpn, ppn, true, readyOnly, false, false);
				
				index += 1;
				
				section.loadPage(i, ppn);
			}
		}
		while (index < numPages) {
			int ppn = UserKernel.allocatePage();
			pageTable[index] = new TranslationEntry(index, ppn, true, false, false, false);
			index++;
		}
		
		UserKernel.freeListLock.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// load sections
		UserKernel.freeListLock.acquire();
		for (int i = 0; i < pageTable.length; i++) {
			if (pageTable[i].valid) {
				UserKernel.collectPage(pageTable[i].ppn);
			}
		}
		pageTable = null;
		UserKernel.freeListLock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if (this.processId == 0) {
			Machine.halt();
			Lib.assertNotReached("Machine.halt() did not halt machine!");
			return 0;
		} else {
			return -1;
		}
	}

	/**
	 * Handle the exit() system call.
	 */
	private void handleExit(int status) {
	    // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		
		// Unload all pagetable
		VMKernel.freeSwapPagesLock.acquire();
		for (int i = 0; i < pageTable.length; i++) {
			if (!pageTable[i].valid) {
				VMKernel.collectSPN(pageTable[i].ppn);
			}
		}
		VMKernel.freeSwapPagesLock.release();
		
		System.out.println("normal exit status" + status);
		// Close all file in file table
		for (OpenFile file : this.fileDescriptor) {
			if (file != null) {
				file.close();
			}
		}
		// Delete all memory by calling UnloadSections()
		this.unloadSections();
		
		// Close the coff by calling coff.close()
		this.coff.close();
		
		// If it has a parent process, save the status in a place that the parent can access
		if (this.ParentProcess != null) {
			this.ParentProcess.ChildProcesses.replace(this, status);	
		}

		// The last process should call kernel.kernel.terminate()
		UserKernel.ActiveProcessLock.acquire();

		if (UserKernel.getActiveProcessCount() == 1) {
			UserKernel.decreaseActiveProcess();
			Kernel.kernel.terminate();
		} else {
			UserKernel.decreaseActiveProcess();
		}
		
		UserKernel.ActiveProcessLock.release();

		// Close KThread by calling KThread.finish()
		KThread.currentThread().finish();
	}
		
		/**
		 * Handle the exit() system call in unhandled exception.
		 */
	private void exceptionExit() {					
		// Close all file in file table
		System.out.println("Exception Exit");
		for (OpenFile file : this.fileDescriptor) {
			if (file != null) {
				file.close();
			}
		}
		
		// Unload all pagetable
		VMKernel.freeSwapPagesLock.acquire();
		for (int i = 0; i < pageTable.length; i++) {
			if (!pageTable[i].valid) {
				VMKernel.collectSPN(pageTable[i].ppn);
			}
		}
		VMKernel.freeSwapPagesLock.release();
			
		// Delete all memory by calling UnloadSections()
		this.unloadSections();
			
		// Close the coff by calling coff.close()
		this.coff.close();
			
		// If it has a parent process, save the status in a place that the parent can access
		if (this.ParentProcess != null) {
			this.ParentProcess.ChildProcesses.replace(this, null);		
		}
		
		// The last process should call kernel.kernel.terminate()
		UserKernel.ActiveProcessLock.acquire();
		if (UserKernel.getActiveProcessCount() == 1) {
			Kernel.kernel.terminate();
		} else {
			UserKernel.decreaseActiveProcess();
		}
		UserKernel.ActiveProcessLock.release();
		
		// Close KThread by calling KThread.finish()
		KThread.finish();
	}
	
	/**
	 * Handle the exec() system call.
	 */
	private int handleExec(int coffName, int argc, int argv) {	
		String CoffName = readVirtualMemoryString(coffName, maxStringLength);

		// argc must be non-negative.
		if (CoffName == null || argc < 0) {
			return -1;
		}
		
		String[] Arguments = new String[argc];
		for(int i = 0 ; i < argc; i++) {
			byte[] strAddrInByte = new byte[4];
			if(readVirtualMemory(argv + (4*i), strAddrInByte) != 4) {
				return -1;
			}
			int strAddr = Lib.bytesToInt(strAddrInByte, 0);
			Arguments[i] = readVirtualMemoryString(strAddr, maxStringLength);
			// return -1 if argument is null
			if (Arguments[i] == null) {
				return -1; // 
			}
		}
		//System.out.println("Parent processid before child:" + this.processId);
		UserProcess childProcess = newUserProcess();
		//System.out.println("Parent processid(this):" + this.processId);
		childProcess.ParentProcess = this;
		//System.out.println("Parent processid:" + childProcess.ParentProcess.processId);
		this.ChildProcesses.put(childProcess, 1);  ////initialize the exit status to be 1///////
		//System.out.println("child processid:" + childProcess.processId);
		if (!childProcess.execute(CoffName, Arguments)) {
			// Do we need to clean up in here -- call exit on child?
			return -1;
		} 
		
		return childProcess.processId;
	}
	
	/**
	 * Handle the join() system call.
	 */
	private int handleJoin(int processID, int status) {
		for (UserProcess process : this.ChildProcesses.keySet()) {
			if (process.processId == processID) {			
				process.thread.join();

				//what will happen when process has been terminated
				Integer statusData = this.ChildProcesses.get(process);
				this.ChildProcesses.remove(process);
				if(statusData == null) {
					//System.out.println(6);
					return 0;
				}
				else {
					byte[] statusInByte = new byte[4];
					statusInByte = Lib.bytesFromInt(statusData.intValue());
					Lib.assertTrue(writeVirtualMemory(status, statusInByte) == 4);
					return 1;
				}
			}
		}
		return -1;
	}
	
	/**
	 * Handle the create() system call.
	 */
	private int handleCreate(int status) {
		
		String fileName = readVirtualMemoryString(status, 256);
		
		// handle exist
		boolean exist = false;
		int nextOpen = 16;
		for (int i = 2; i < 16; i++) {
			if (fileDescriptor[i] == null) {
				if (i > 1 && i < nextOpen) {
					nextOpen = i;
				}
			} else {
				if (fileDescriptor[i].getName().equals(fileName)) {
					if (i > 1 && i < nextOpen) {
						exist = true;
					}
				}
			}
		}
		
		OpenFile file = null;
		if (exist) {	// just open 
			file = ThreadedKernel.fileSystem.open(fileName, false);
		} else {	// create and open
			file = ThreadedKernel.fileSystem.open(fileName, true);
		}
		
		if (file != null) {
			if (nextOpen != 16) {
				fileDescriptor[nextOpen] = file;
				return nextOpen;
			} else {
				return -1;
			}
		}
		
		return -1;
	}
	
	/**
	 * Handle the open() system call.
	 */
	private int handleOpen(int status) {

		String fileName = readVirtualMemoryString(status, 256);
				
		for (int i = 2; i < 16; i++) {
			if (fileDescriptor[i] == null) {
				OpenFile file = ThreadedKernel.fileSystem.open(fileName, false); 
				if (file != null) {
					fileDescriptor[i] = file;
					return i;
				} else {
					return -1;
				}
			}
		}
		return -1;
	}
	
	/**
	 * Handle the read() system call.
	 */
	private int handleRead(int status, int bufferAddr, int count) {
		if (status > -1 && status < 16 && count >= 0) {
			
			byte[] buffer = new byte[count]; 
			int actualDataLength = -1;
			
			OpenFile file = fileDescriptor[status];
			
			// Read from File
			if (file != null) {  // file is in file table
				actualDataLength = file.read(buffer, 0, count);
			}
			
			if (actualDataLength != -1) { // file can be read
				byte[] actualData = Arrays.copyOf(buffer, actualDataLength);
				writeVirtualMemory(bufferAddr, actualData);
				return actualDataLength;
			}
		}
		
		return -1;
	}
	
	/**
	 * Handle the write() system call.
	 */
	private int handleWrite(int status, int bufferAddr, int count) {
		if (status > -1 && status < 16 && count >= 0) {			
			OpenFile file = fileDescriptor[status];
			
			if (file != null) {
				byte[] data = new byte[count];
				int actualDataLength = readVirtualMemory(bufferAddr, data);
				if (actualDataLength == count) {
					return file.write(data, 0, count);
				} else {
					return -1;
				}
			}
		}
		
		return -1;
	}
	
	/**
	 * Handle the close() system call.
	 */
	private int handleClose(int status) {
		if (status > -1 && status < 16) {
			int descriptor = status;
			if (fileDescriptor[descriptor] == null) {
				return -1;
			} else {
				OpenFile file = fileDescriptor[descriptor];
				file.close();
				fileDescriptor[descriptor] = null;
				return 0;
			}
		}
		return -1;
	}
	
	/**
	 * Handle the unlink() system call.
	 */
	private int handleUnlink(int status) {
		String fileName = readVirtualMemoryString(status, 256);
		
		if (fileName == null) {
			return -1;
		}

		boolean success = ThreadedKernel.fileSystem.remove(fileName);
		if (success) {
			return 0;
		} else {
			return -1;
		}
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);	
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			exceptionExit();
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			exceptionExit();
			Lib.assertNotReached("Unexpected exception");
		}
	}
	
	public void handlePageFault(int index) {
		System.out.println();
		System.out.println();
		System.out.println("Handle Page Fault Called");
		for (int i = 0; i < numPages; i++) {
			System.out.println("Entry is: vpn " + pageTable[i].vpn + " ppn: " + pageTable[i].ppn + " valid: " + pageTable[i].valid +
					" dirty: " + pageTable[i].dirty);
		}
		boolean find = false;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			boolean readOnly = section.isReadOnly();

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				if(vpn == index) {
					find = true;
					if (UserKernel.getFreeListSize() > 0) { // Read-only COFF sections originate in the COFF
						
						UserKernel.freeListLock.acquire();
						int ppn = UserKernel.allocatePage();
						UserKernel.freeListLock.release();
						section.loadPage(i, ppn);
						
						// Link Physical Frame with process and page table entry
						VMKernel.IPTLock.acquire();
						VMKernel.addPageFrame(this, pageTable[vpn], ppn);
						VMKernel.IPTLock.release();
						
						pageTable[vpn].ppn = ppn;
						pageTable[vpn].used = true;
						pageTable[vpn].readOnly = readOnly;
						pageTable[vpn].valid = true;
					} else {
						
						VMKernel.IPTLock.acquire();
						VMKernel.conditionLock.acquire();
						if (VMKernel.checkPin()) {
							try {
								VMKernel.pinQueue.wait();
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						} 
						VMKernel.conditionLock.release();
						
						TranslationEntry victim = VMKernel.findVictim();
						int victimPPN = victim.ppn;
						if (victim.dirty && (!victim.readOnly)) {
							// read from VM
							byte[] data = VMKernel.getVictimData();
							
							// Invaild victim and change ppn to spn	
							VMKernel.freeSwapPagesLock.acquire();
							int spn = VMKernel.allocateSPN();
							System.out.println("The SPN is:" + spn);
							VMKernel.freeSwapPagesLock.release();
							victim.ppn = spn;
							
							// Write to file
							VMKernel.writeSwapFile(spn * Processor.pageSize, data);
							victim.valid = false;
						}
						victim.valid = false;
						VMKernel.IPTLock.release();
						// Get PPN from victim
						int previousPPN = pageTable[vpn].ppn;
						pageTable[vpn].ppn = victimPPN;
						pageTable[vpn].used = true;
					
						// Update IPT
						VMKernel.IPTLock.acquire();
						VMKernel.updateIPTProcess(this, victimPPN);
						
						// load
						if (previousPPN == -1 || pageTable[vpn].valid || readOnly) {
							section.loadPage(i, pageTable[vpn].ppn);
							pageTable[vpn].readOnly = readOnly;
							pageTable[vpn].valid = true;
						} else {
							// load from file
							byte[] data = new byte[Processor.pageSize];
							VMKernel.readSwapFile(pageTable[vpn].ppn * Processor.pageSize, data);
							pageTable[vpn].valid = true;
							writeVirtualMemory(vpn * Processor.pageSize, data);							
							//pageTable[vpn].dirty = false;
						}
						
						VMKernel.updateIPTEntry(pageTable[vpn], victimPPN);
						VMKernel.IPTLock.release();
					}
				}
			}
		}
		if(find == false) {
			// Other pages should be zero-initialized, and are never read from the COFF
			if (UserKernel.getFreeListSize() > 0) {
				UserKernel.freeListLock.acquire();
				int ppn = UserKernel.allocatePage();
				UserKernel.freeListLock.release();
				
				VMKernel.IPTLock.acquire();
				VMKernel.addPageFrame(this, pageTable[index], ppn);
				VMKernel.IPTLock.release();
				
				pageTable[index].valid = true;
				pageTable[index].ppn = ppn;
				pageTable[index].used = true;
			} else {

				VMKernel.IPTLock.acquire();
				VMKernel.conditionLock.acquire();
				if (VMKernel.checkPin()) {
					try {
						VMKernel.pinQueue.wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				VMKernel.conditionLock.release();
				
				TranslationEntry victim = VMKernel.findVictim();
				int victimPPN = victim.ppn;
				int vpn = index;
				if (victim.dirty) {
					// read from VM
					byte[] data = VMKernel.getVictimData();
					
					// Invaild victim and change ppn to spn	
					VMKernel.freeSwapPagesLock.acquire();
					int spn = VMKernel.allocateSPN();
					VMKernel.freeSwapPagesLock.release();
					victim.ppn = spn;
					
					// Write to file
					VMKernel.writeSwapFile(spn * Processor.pageSize, data);
					victim.valid = false;
				}
				victim.valid = false;
				VMKernel.IPTLock.release();
				// Get PPN from victim
				pageTable[vpn].ppn = victimPPN;
				pageTable[vpn].used = true;
				
				// Update IPT
				VMKernel.IPTLock.acquire();
				VMKernel.updateIPTProcess(this, victimPPN);				
				
				// load
				if (!pageTable[vpn].valid) {
					// load from file
					byte[] data = new byte[Processor.pageSize];
					VMKernel.readSwapFile(pageTable[vpn].ppn * Processor.pageSize, data);
					pageTable[vpn].valid = true;
					writeVirtualMemory(vpn * Processor.pageSize, data);
					//pageTable[vpn].dirty = false;
					pageTable[vpn].readOnly = false;
				}
				VMKernel.updateIPTEntry(pageTable[vpn], victimPPN);
				VMKernel.IPTLock.release();
			}
		}
	}
	
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
    protected UThread thread;
    
    private int processId;
    
    private UserProcess ParentProcess;
    
    // change the key of this map to the Process for the request of the Join method
    private Map<UserProcess, Integer> ChildProcesses;
    
    private int maxStringLength = 256;
    
	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	
	private int clockPosition;

	private static final char dbgProcess = 'a';
	
	private OpenFile[] fileDescriptor;
}
