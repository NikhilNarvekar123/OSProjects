
import java.util.concurrent.Semaphore;
import java.util.Random;
import java.util.ArrayList;

public class Program1 {
    
    // how many customers and workers
    public static final int NUM_CUSTOMERS = 50;
    public static final int NUM_WORKERS = 3;

    // semaphores

    // post office only has 10 customers inside at a time, want fairness (longest waiting thread run first)
    public static Semaphore postOfficeEntry = new Semaphore(10, true);




    // driver
    public static void main(String[] args) {
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
        public int taskDelay;
        public int id;

        // gave random task to each customer
        public CustomerThread(int id) {
            Random rand = new Random();
            int randNum = rand.nextInt(3) + 1;
            switch(randNum) {
                case 1:
                    task = CustomerTask.BUY_STAMPS;
                    taskDelay = 60;
                    break;
                case 2:
                    task = CustomerTask.MAIL_LETTER;
                    taskDelay = 90;
                    break;
                case 3:
                    task = CustomerTask.MAIL_PACKAGE;
                    taskDelay = 120;
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

                System.out.println("DEBUG: " + id + " customer entered post office");

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

            } catch (Exception e) {
                System.out.println("Worker Thread error " + e);
            }
        }
    }
}


