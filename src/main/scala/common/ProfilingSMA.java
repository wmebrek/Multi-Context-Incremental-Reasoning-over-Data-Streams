package common;

public class ProfilingSMA {

	/**
	 * The total running time of the engine (nanoseconds)
	 */
	public long totalRunTime = 0;
	/**
	 * The number of events processed
	 */
	public long numberOfEvents = 0;
	/**
	 * The number of runs generated
	 */
	public long numberOfRuns = 0;
	/**
	 * The total run lifetime
	 */
	public long totalRunLifeTime = 0;

	public long numberOfRunsOverTimeWindow = 0;
	/**
	 * The cost on match construction
	 */

	/**
	 * resets the profiling numbers
	 */
	public void resetProfiling() {

		totalRunTime = 0;
		numberOfEvents = 0;
		numberOfRuns = 0;
		totalRunLifeTime = 0;
		numberOfRunsOverTimeWindow = 0;
	}

	/**
	 * prints the profiling numbers in console
	 */
	public void printProfiling() {
	
	//	totalRunTime = eTime - sTime;
		System.out.println();
		System.out.println("**************ProfilingSMA Numbers*****************");
		 System.out.println("Total Running Time: " + 1000000000 + " Ns");
		System.out.println("Number Of Events Processed: " + numberOfEvents);
		System.out.println("Number Of Runs Created: " + numberOfRuns);


		long throughput1 = 0;
		 if(totalRunTime > 0){
		 throughput1 = numberOfEvents; //* 1000000000/totalRunTime ;
		 System.out.println("Throughput: " + throughput1 + " events/second");
		 }

	}

	// sharing numbers
	/**
	 * number of runs merged in the sharing engine
	 */
	public long numberOfMergedRuns = 0;

	public void resetSharingProfiling() {
		numberOfMergedRuns = 0;
	}

	/**
	 * outputs the extra profiling numbers for the sharing engine
	 */
	public void printSharingProfiling() {
		System.out.println("#Merged Runs = " + numberOfMergedRuns);
	}

}
