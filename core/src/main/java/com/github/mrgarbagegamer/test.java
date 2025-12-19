package com.github.mrgarbagegamer;

import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// import org.openjdk.jol.info.ClassLayout;
// import org.openjdk.jol.vm.VM;

// import jdk.incubator.vector.*;


public class test {
    public static final Logger logger = LogManager.getLogger(test.class);

    public static void main(String[] args) {
        // vectorTest();
        // objectSizeTest();
        converter();
    }

    public static void vectorTest()
    {
        // // Check if the Vector API is available by checking if the VectorSpecies class is loaded
        // if (!VectorSpecies.class.isAssignableFrom(ShortVector.class)) {
        //     logger.error("Vector API is not available. Please ensure you are using a compatible JDK version.");
        //     return;
        // }
        

        // // Get the preferred vector API instance
        // VectorSpecies<Short> species = ShortVector.SPECIES_PREFERRED;
        // // Output bit length of the vector species
        // System.out.println("Vector species bit length: " + species.elementSize());
    }
    
    public static void converter() {
        Scanner input = new Scanner(System.in);
        while (true) {
            System.out.print(
                    "Would you like to convert packed ints to indices or vice versa? (Enter '1' for packed to index, '2' for index to packed, '0' to exit) ");
            int choice = input.nextInt();
            input.nextLine(); // Consume newline character
            if (choice == 0) {
                System.out.println("Exiting.");
                break;
            } else if (choice == 1) {
                // Stay in packed to index mode until user enters negative number
                while (true) {
                    System.out.print("Enter packed int to convert to index (negative number to return to menu): ");
                    short packedInt = input.nextShort();
                    input.nextLine(); // Consume newline character
                    if (packedInt < 0)
                        break;
                    int index = Grid.packedToIndex(packedInt);
                    System.out.println("Converted index: " + index);
                }
            } else if (choice == 2) {
                // Stay in index to packed mode until user enters negative number
                while (true) {
                    System.out.print("Enter index to convert to packed int (negative number to return to menu): ");
                    int index = input.nextInt();
                    input.nextLine(); // Consume newline character
                    if (index < 0)
                        break;
                    int packedInt = Grid.indexToPacked((short) index);
                    System.out.println("Converted packed int: " + packedInt);
                }
            } else {
                System.out.println("Invalid choice.");
            }
        }
        input.close();
    }

    // public static void objectSizeTest()
    // {
    //     System.out.println(VM.current().details());

    //     // Set numClicks for classes that require it
    //     final int numClicks = 17;
    //     ArrayPool.setNumClicks(numClicks);
    //     WorkBatch.setNumClicks(numClicks);

    //     System.out.println("=======================================================================================================");
    //     System.out.println("                                             MEMORY LAYOUTS");
    //     System.out.println("=======================================================================================================");

    //     // Grid and its subclasses
    //     System.out.println(ClassLayout.parseClass(Grid.class).toPrintable());
    //     System.out.println(ClassLayout.parseClass(Grid13.class).toPrintable());
    //     System.out.println(ClassLayout.parseClass(Grid22.class).toPrintable());
    //     System.out.println(ClassLayout.parseClass(Grid35.class).toPrintable());

    //     // Core data structures
    //     System.out.println(ClassLayout.parseClass(WorkBatch.class).toPrintable());
    //     System.out.println(ClassLayout.parseClass(ArrayPool.class).toPrintable());
    //     System.out.println(ClassLayout.parseClass(TaskPool.class).toPrintable());

    //     // Queueing infrastructure
    //     System.out.println(ClassLayout.parseClass(CombinationQueue.class).toPrintable());
    //     System.out.println(ClassLayout.parseClass(CombinationQueueArray.class).toPrintable());

    //     // Main task-related classes
    //     System.out.println(ClassLayout.parseClass(CombinationGeneratorTask.class).toPrintable());
    //     System.out.println(ClassLayout.parseClass(TestClickCombination.class).toPrintable());
        
    //     // Orchestrator
    //     System.out.println(ClassLayout.parseClass(StartYourMonkeys.class).toPrintable());


    //     System.out.println("=======================================================================================================");
    //     System.out.println("                                         INSTANCE MEMORY LAYOUTS");
    //     System.out.println("=======================================================================================================");
        
    //     // Grid instances
    //     System.out.println(ClassLayout.parseInstance(new Grid35()).toPrintable());

    //     // Core data structure instances
    //     System.out.println(ClassLayout.parseInstance(new WorkBatch(80)).toPrintable());
    //     System.out.println(ClassLayout.parseInstance(new ArrayPool(128)).toPrintable());
    //     System.out.println(ClassLayout.parseInstance(new TaskPool(128)).toPrintable());
        
    //     // Queueing infrastructure instances
    //     CombinationQueueArray queueArray = CombinationQueueArray.getInstance(8);
    //     System.out.println(ClassLayout.parseInstance(queueArray).toPrintable());
    //     System.out.println(ClassLayout.parseInstance(queueArray.getQueue(0)).toPrintable());
        
    //     // Main task-related class instances
    //     Grid grid = new Grid35();
    //     short[] trueCells = grid.findTrueCells();
    //     System.out.println(ClassLayout.parseInstance(new CombinationGeneratorTask(numClicks, queueArray, trueCells, 108)).toPrintable());
    //     System.out.println(ClassLayout.parseInstance(new TestClickCombination("test-monkey", queueArray.getQueue(0), queueArray, grid)).toPrintable());
    // }
}