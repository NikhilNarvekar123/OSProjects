
import java.util.concurrent.Semaphore;
import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;

public class Program1 {
    
    // how many customers and workers
    public static final int NUM_CUSTOMERS = 11;
    public static final int NUM_WORKERS = 3;

    // time table mapping
    public static HashMap<CustomerTask, Integer> timeTable = new HashMap<CustomerTask, Integer>();


    // semaphores

    // post office only has 10 customers inside at a time, want fairness (longest waiting thread run first)
    public static Semaphore postOfficeEntry = new Semaphore(10, true);

    // single semaphore that keeps track of busy workers
    // when a worker is available, signals this semaphore and then its respective individual semaphore
    public static Semaphore workerAvailable = new Semaphore(0, true);

    // have a binary semaphore for each worker, since each worker can only take 1 customer at a time 
    public static Semaphore[] workerSemaphores = new Semaphore[] {new Semaphore(0, true), new Semaphore(0, true), new Semaphore(0, true)};

    // when processing a customer, have some back-forth between worker and customer
    // use this semaphore to handle that back and forth
    public static Semaphore[] workerCustomerSemaphore = new Semaphore[] {new Semaphore(0, true), new Semaphore(0, true), new Semaphore(0, true)};
    public static Semaphore[] customerWorkerSemaphore = new Semaphore[] {new Semaphore(0, true), new Semaphore(0, true), new Semaphore(0, true)};



    // GLOBALS

    public static int[] customersBeingServiced = new int[3];
    public static CustomerTask[] workerTasks = new CustomerTask[3]; 

    // driver
    public static void main(String[] args) {

        // init hashmap with time value
        timeTable.put(CustomerTask.BUY_STAMPS, 60);
        timeTable.put(CustomerTask.MAIL_LETTER, 90);
        timeTable.put(CustomerTask.MAIL_PACKAGE, 120);

        System.out.println("Simulating Post Office with 50 customers and 3 postal workers");

        // init all threads
        ArrayList<Thread> customers = new ArrayList<Thread>();
        for (int i = 0; i < NUM_CUSTOMERS; i++) {
            customers.add(new Thread(new CustomerThread(i)));
            customers.get(i).start();
        }

        ArrayList<Thread> workers = new ArrayList<Thread>();
        for (int i = 0; i < NUM_WORKERS; i++) {
            workers.add(new Thread(new WorkerThread(i)));
            workers.get(i).start();
        }

        // wait for each customer to be finished
        for(int i = 0; i < customers.size(); i++) {
            try {
                customers.get(i).join();
            } catch (Exception e) {}
        }

        // close worker threads
        for(int i = 0; i < workers.size(); i++) {
            workers.get(i).interrupt();
        }

        
        




    }

    static class CustomerThread implements Runnable {

        public CustomerTask task;
        public int id;

        // gave random task to each customer
        public CustomerThread(int id) {
            Random rand = new Random();
            int randNum = rand.nextInt(3) + 1;
            switch(randNum) {
                case 1:
                    task = CustomerTask.BUY_STAMPS;
                    break;
                case 2:
                    task = CustomerTask.MAIL_LETTER;
                    break;
                case 3:
                    task = CustomerTask.MAIL_PACKAGE;
                    break;
            }
            this.id = id;
        }

        //
        @Override
        public void run() {
            try {

                System.out.println("Customer " + id + " created");

                // wait for entry into post office
                postOfficeEntry.acquire();
                System.out.println("Customer " + id + " enters post office");

                // wait for worker to be available
                workerAvailable.acquire();

                // instaneous check to see what worker is free, use that worker
                int workerID = -1;
                if(!workerSemaphores[0].tryAcquire()) {
                    if(!workerSemaphores[1].tryAcquire()) {
                        workerSemaphores[2].acquire();
                        workerID = 2;
                    } else {
                        workerID = 1;
                    }
                } else {
                    workerID = 0;
                }

                // using worker ID, save data into global vars for worker to use
                // worker will be waiting on semaphore to make sure global data saved b4 access
                customersBeingServiced[workerID] = this.id;
                workerTasks[workerID] = this.task;
                customerWorkerSemaphore[workerID].release();

                // wait for worker to be done with printing its init messages then print action intent
                workerCustomerSemaphore[workerID].acquire();
                switch(this.task) {
                    case BUY_STAMPS:
                        System.out.println("Customer " + id + " asks postal worker " + workerID + " to buy stamps");
                        break;
                    case MAIL_LETTER:
                        System.out.println("Customer " + id + " asks postal worker " + workerID + " to mail a letter");
                        break;
                    case MAIL_PACKAGE:
                        System.out.println("Customer " + id + " asks postal worker " + workerID + " to mail a package");
                        break;     
                }

                while(true){}

            } catch (Exception e) {
                System.out.println("Thread error " + e);
            }
        }

    }

    /** possible customer tasks */
    enum CustomerTask {
        BUY_STAMPS, MAIL_LETTER, MAIL_PACKAGE;
    }

    static class WorkerThread implements Runnable {

        public int id;

        public WorkerThread(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {

                System.out.println("Postal worker " + id + " created");

                // run infinite loop in order to service as many customers as possible 
                // before driver code causes interrupt (all X customers finished)
                while(true) {
                    
                    // release individual semaphore first, then overall semaphore variable
                    // this is because the customer first checks semaphore variable then instantly
                    // individual semaphore, so doing it other way around might cause issues
                    workerSemaphores[id].release();
                    workerAvailable.release();

                    // wait for customer to update global data
                    customerWorkerSemaphore[id].acquire();

                    // get customer data
                    int customerID = customersBeingServiced[this.id];
                    CustomerTask customerTask = workerTasks[this.id];

                    // print message and let customer print its message
                    System.out.println("Postal worker " + id + " serving " + customerID);
                    workerCustomerSemaphore[id].release();

                    while(true) {}

                }




            } catch (Exception e) {
                System.out.println("Worker Thread error " + e);
            }
        }
    }
}


