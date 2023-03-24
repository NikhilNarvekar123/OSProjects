import java.util.concurrent.Semaphore;
import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;

/** Class contains code for both Customer and Worker threads, 
 *  alongside main driver code for both. 
 */
public class Project2 {
    
    /* CONSTANTS */
    
    // define how many customers and workers are in simulation,
    public static final int NUM_CUSTOMERS = 50;
    public static final int NUM_WORKERS = 3;

    // define delay values in seconds for each customer task
    public static final int BUY_STAMP_DELAY = 60;
    public static final int MAIL_LETTER_DELAY = 90;
    public static final int MAIL_PACKAGE_DELAY = 120;

    // define how many customers can be in office at a time
    public static final int OFFICE_MAX_CAPACITY = 10;

    // stores the actual simulation delay for each task a customer can have
    // simulation delay is equal to (DELAY / 60) * 1000 ms 
    public static HashMap<CustomerTask, Integer> taskDelayMap = new HashMap<CustomerTask, Integer>();


    /* SEMAPHORES - fairness enabled for all to automatically give longest waiting thread priority */

    // used to ensure only 10 customers inside post office at a time
    public static Semaphore postOfficeSlots = new Semaphore(OFFICE_MAX_CAPACITY, true);

    // used to check if any of the N workers are available - used in conjunction with workerSlots
    public static Semaphore workerAvailable = new Semaphore(0, true);

    // used to mark usage of scale object, which only 1 worker can use at a time to weigh packages
    public static Semaphore scaleResource = new Semaphore(1, true);

    // used to maintain whether a worker is servicing a customer - 1 worker for 1 customer at a time
    public static Semaphore[] workerSlots = new Semaphore[] {
        new Semaphore(0, true),
        new Semaphore(0, true),
        new Semaphore(0, true)
    };

    // used to acknowledge when worker is ready for customer to do something
    public static Semaphore[] workerAcknowledge = new Semaphore[] {
        new Semaphore(0, true),
        new Semaphore(0, true),
        new Semaphore(0, true)
    };
    
    // used to acknowledge when customer is ready for worker to do something
    public static Semaphore[] customerAcknowledge = new Semaphore[] {
        new Semaphore(0, true),
        new Semaphore(0, true),
        new Semaphore(0, true)
    };

    // used to determine whether worker has finished their customer's task
    public static Semaphore[] workerTaskFinished = new Semaphore[] {
        new Semaphore(0, true),
        new Semaphore(0, true),
        new Semaphore(0, true)
    };


    /* GLOBALS - updated/used across threads */

    // the IDs and tasks of the customers currently being serviced by workers
    public static int[] customersBeingServicedIds = new int[3];
    public static CustomerTask[] customersBeingServicedTasks = new CustomerTask[3]; 


    /** Enum used to define different possible customer tasks */
    enum CustomerTask {
        BUY_STAMPS, MAIL_LETTER, MAIL_PACKAGE;
    }


    /** Driver - Spawns the customer and worker threads and runs simulation */
    public static void main(String[] args) {

        // init taskDelay map with simulation delay values
        taskDelayMap.put(CustomerTask.BUY_STAMPS, (int)(BUY_STAMP_DELAY / 60.0) * 1000);
        taskDelayMap.put(CustomerTask.MAIL_LETTER, (int)(MAIL_LETTER_DELAY / 60.0) * 1000);
        taskDelayMap.put(CustomerTask.MAIL_PACKAGE, (int)(MAIL_PACKAGE_DELAY / 60.0) * 1000);
        
        // start simulation
        System.out.println(
            String.format(
                "Simulating Post Office with %d customers and %d postal workers",
                NUM_CUSTOMERS,
                NUM_WORKERS
            )
        );

        // initialize customer threads and start each thread
        ArrayList<Thread> customerThreads = new ArrayList<Thread>();
        for (int i = 0; i < NUM_CUSTOMERS; i++) {
            customerThreads.add(new Thread(new CustomerThread(i)));
            customerThreads.get(i).start();
        }

        // initialize worker threads and start each thread
        ArrayList<Thread> workerThreads = new ArrayList<Thread>();
        for (int i = 0; i < NUM_WORKERS; i++) {
            workerThreads.add(new Thread(new WorkerThread(i)));
            workerThreads.get(i).start();
        }

        // wait for each customer thread to finish
        for (int i = 0; i < customerThreads.size(); i++) {
            try {
                customerThreads.get(i).join();
                System.out.println(String.format("Joined customer %d", i));
            } catch (Exception e) {
                System.out.println("Error with joining customer thread: " + e);
            }
        }

        // close all worker threads via interrupt
        for (int i = 0; i < workerThreads.size(); i++) {
            workerThreads.get(i).interrupt();
        }

    }


    /** Class for threads for each customer in the simulation */
    public static class CustomerThread implements Runnable {

        // the ID and task a customer is assigned
        public int id;
        public CustomerTask task;

        /** Create customer thread with assigned ID and random task 
         *  @param id assigned ID of this customer
         */
        public CustomerThread(int id) {
            
            // set ID
            this.id = id;
            
            // randomly assign task
            int randNum = (new Random()).nextInt(3) + 1;
            if (randNum == 1) {
                this.task = CustomerTask.BUY_STAMPS;
            } else if (randNum == 2) {
                this.task = CustomerTask.MAIL_LETTER;
            } else {
                this.task = CustomerTask.MAIL_PACKAGE;
            }

        }

        /** Runs a customer thread - single run enters post office,
         *  finds a worker, waits for worker to finish its task, then
         *  leaves post office.
         */
        @Override
        public void run() {
            
            try {
                System.out.println(String.format("Customer %d created", id));

                // customer waits for entry into post office
                postOfficeSlots.acquire();
                System.out.println(String.format("Customer %d enters post office", id));

                // customer waits for any worker to be available
                workerAvailable.acquire();

                // customer checks which worker is currently available and gets worker's ID
                // tryAcquire will check if semaphore is available at that instant and acquire if so,
                // return false otherwise
                int workerId = -1;
                for (int i = 0; i < workerSlots.length; i++) {
                    if (workerSlots[i].tryAcquire()) {
                        workerId = i;
                        break;
                    }
                }

                // with workerID, save data into global vars for worker to use
                customersBeingServicedIds[workerId] = id;
                customersBeingServicedTasks[workerId] = task;

                // customer acknowledges that relevant data has been provided to worker
                customerAcknowledge[workerId].release();

                // customer gets worker acknowledgement that worker is ready to start customer task
                workerAcknowledge[workerId].acquire();

                // customer displays what task it needs completed from worker
                String customerIntent = String.format("Customer %d asks postal worker %d ", id, workerId);
                if (task == CustomerTask.BUY_STAMPS) {
                    System.out.println(customerIntent + "to buy stamps");
                } else if (task == CustomerTask.MAIL_LETTER) {
                    System.out.println(customerIntent + "to mail a letter");
                } else {
                    System.out.println(customerIntent + "to mail a package");
                }

                // customer acknowledges that worker can start working on the customer task
                customerAcknowledge[workerId].release();

                // customer gets worker acknowledgement that worker has finished their task
                workerTaskFinished[workerId].acquire();
                
                // customer displays what task its worker just completed
                if (task == CustomerTask.BUY_STAMPS) {
                    System.out.println(String.format("Customer %d finished buying stamps", id));
                } else if (task == CustomerTask.MAIL_LETTER) {
                    System.out.println(String.format("Customer %d finished mailing a letter", id));
                } else {
                    System.out.println(String.format("Customer %d finished mailing a package", id));
                }

                // customer leaves post office, release a post office spot
                System.out.println(String.format("Customer %d leaves post office", id));
                postOfficeSlots.release();
            } catch (Exception e) {
                System.out.println("Error in Customer " + id + " thread: " + e);
            }

        }

    }


    /** Class for threads for each worker in the simulation */
    public static class WorkerThread implements Runnable {

        // the ID a worker is assigned
        public int id;

        /** Create worker thread with assigned ID
         *  @param id assigned ID of this worker
         */
        public WorkerThread(int id) {
            this.id = id;
        }

        /** Runs a worker thread - loops continuously until driver signals
         *  all customers are finished, single run accepts a customer and
         *  performs their task. 
         */
        @Override
        public void run() {

            try {
                System.out.println(String.format("Postal worker %d created", id));

                // run infinite loop in order to service as many customers as possible 
                // before driver code causes interrupt
                while(true) {
                    
                    // worker signals that it is now free to accept more customers
                    workerSlots[id].release();

                    // used to alert customer that one of the workers is free
                    workerAvailable.release();

                    // worker waits for customer to give ID/task data
                    customerAcknowledge[id].acquire();

                    // get customer ID and desired task from global vars
                    int customerId = customersBeingServicedIds[id];
                    CustomerTask customerTask = customersBeingServicedTasks[id];

                    // worker displays what customer it is serving
                    System.out.println("Postal worker " + id + " serving customer " + customerId);
                    
                    // worker acknowledges that it can start customer task
                    workerAcknowledge[id].release();

                    // worker waits for customer to display any other relevant data
                    customerAcknowledge[id].acquire();

                    // workers performs customer task
                    if (customerTask == CustomerTask.BUY_STAMPS) {
                        Thread.sleep(taskDelayMap.get(CustomerTask.BUY_STAMPS));
                    } else if (customerTask == CustomerTask.MAIL_LETTER) {
                        Thread.sleep(taskDelayMap.get(CustomerTask.MAIL_LETTER));
                    } else {
                        // try or wait to acquire scales, perform task, then release scales
                        scaleResource.acquire();
                        Thread.sleep(taskDelayMap.get(CustomerTask.MAIL_PACKAGE));
                        scaleResource.release();
                    }

                    // worker displays it has finished customer task
                    System.out.println(String.format("Postal worker %d finished serving customer %d", id, customerId));
                    
                    // worker lets customer know that their task is finished
                    workerTaskFinished[id].release();
                }
            } catch (Exception e) {
                // disabled to prevent interrupts from showing in final output
                // System.out.println("Error in Worker " + id + " thread: " + e);
            }

        }

    }

}


