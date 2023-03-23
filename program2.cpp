/**
*/


#include <iostream>
#include <string>
#include <vector>
#include <pthread.h>

using namespace std;


/***/
void execute_worker_thread() {
    cout << "WORKER" << endl;
}

/***/
void execute_customer_thread() {
    cout << "CUSTOMER" << endl;
}



int main() {

    // driver for both threads inside main

    // ...


    pthread_t worker_thread;
    pthread_t customer_thread;

    // start threads
    int worker_thread_id = pthread_create(&worker_thread, NULL, execute_worker_thread);
    int customer_thread_id = pthread_create(&customer_thread, NULL, execute_customer_thread);

    // wait for threads to finish before continuing main
    pthread_join(worker_thread, NULL);
    pthread_join(customer_thread, NULL);


    return 0;
}

