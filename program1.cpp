/**
 *  File containing all source code needed for Project 1.
 *  Simulates memory and CPU of a PC within separate processes 
 *  and uses a inputted program to run interactions between them.
 *  @author Nikhil Narvekar
 */

#include <iostream>
#include <unistd.h>
#include <string>
#include <vector>
#include <fstream>
#include <algorithm>

using namespace std;

/** 
 * If any error occurs in CPU or Memory processes, display 
 * error reason and exit both processes. Only called from
 * CPU/Parent process.
 * @param error_reason reason to throw error
 */
void throw_process_error(string error_reason, int cpu_pipe[2], int mem_pipe[2]) {
    
    // display error
    cout << error_reason << endl;

    // tell mem process to exit
    write(mem_pipe[1], "e", sizeof("e"));

    // close pipes
    close(cpu_pipe[0]);
    close(cpu_pipe[1]);
    close(mem_pipe[0]);
    close(mem_pipe[1]);

    // exit with error
    exit(1);
}

/**
 * Writes data over given pipe.
 * @param data the int value to be written
 * @param pipe the pipe to write over
 */
void write_over_pipe(string prefix, int data, int pipe[2]) {
    char buffer[10];
    snprintf(buffer, sizeof(buffer), "%s%d", prefix.c_str(), data);
    write(pipe[1], buffer, sizeof(buffer));
}

/**
 * Reads data sent over given pipe and returns as string.
 * @param pipe the pipe to read from
 */
string read_over_pipe(int pipe[2]) {
    char buffer[10];
    read(pipe[0], buffer, sizeof(buffer));            
    string data(buffer);
    return data;
}

/**
 * Given a program file line, keeps the starting address (ex: .1000)
 * or starting number (ex: 4) but removes all other non-digits like comments
 * @param input_line line to filter 
 */
string filter_line(string input_line) {
    int i = 0;
    string result;
    while (
        i < input_line.length() &&
        (isdigit(input_line[i]) ||
        (i == 0 && input_line[0] == '.'))
    ) {
        result += input_line[i];
        i++;
    }
    return result;
}


int main(int argc, char** argv) {
    
    // create pipes between CPU and Memory processes
    int cpu_to_mem [2];
    int mem_to_cpu [2];
    pipe(cpu_to_mem);
    pipe(mem_to_cpu);

    // if 3 command-line arguments not given, throw error
    if (argc != 3) {
        throw_process_error("Error: Invalid number of cmd arguments", cpu_to_mem, mem_to_cpu);
    }
    
    // open program file to read in
    string file_name(argv[1]);
    ifstream input_stream(file_name);
    if (!input_stream) {
        throw_process_error("Error: Unable to open/find program file", cpu_to_mem, mem_to_cpu);
    }

    // generate seed for random numbers
    srand(time(0));

    // fork into child (Memory) and parent (CPU) processes
    int pid = fork();

    // child (Memory) process, only manages read/writes
    if (pid == 0) 
    {
        // declare main memory array with size 2K
        vector<int> mem;
        mem.resize(2000);

        // read input program file line by line
        // assumes program has valid syntax
        string line;
        int idx = 0;
        while(getline(input_stream, line)) {

            line = filter_line(line);

            // exception is an address, which starts with '.'
            if (line[0] == '.') {
                idx = stoi(line.substr(1, line.length()));
            } else if (line.length()) {
                mem[idx] = stoi(line);
                idx++;
            }
        }

        // main memory loop that runs until CPU signals exit
        while(true) {

            // wait for instruction from CPU
            string cpu_msg = read_over_pipe(cpu_to_mem);

            // read instruction following format -> "rADDRESS"
            if (cpu_msg[0] == 'r')
            {
                // write data back
                int address(stoi(cpu_msg.substr(1, cpu_msg.length())));
                write_over_pipe("", mem[address], mem_to_cpu);
            }
            // write instruction following format -> "wADDRESS"
            else if (cpu_msg[0] == 'w')
            {
                // CPU first sends mem address to write to
                int address(stoi(cpu_msg.substr(1, cpu_msg.length())));
                
                // CPU then sends data to write at given address
                int data = stoi(read_over_pipe(cpu_to_mem).substr(1, cpu_msg.length()));
                mem[address] = data;
            }
            // exit signaled by CPU, exit process
            else if (cpu_msg[0] == 'e')
            {
                exit(0);
            }
        }
    }
    // parent (CPU) process, runs program instructions from memory
    else {

        // declare timer
        string timer_param(argv[2]);
        int timer = stoi(timer_param);

        // stack starting indices
        int user_stack_idx = 999;
        int sys_stack_idx = 1999;

        // registers
        int pc = 0;
        int sp = user_stack_idx;
        int ir = 0;
        int ac = 0;
        int x = 0;
        int y = 0;

        // interrupt variables
        bool user_mode = true;
        int instruction_count = 0;
        int scheduled_interrupt_num = 0;


        // main CPU loop that runs until program is finished or error encountered
        while(true) {

            // keeps track of whether current instruction has caused jump
            // and thus PC does not need to be incremented
            bool has_jumped = false;
            
            // currently in kernel mode handling an interrupt and timer expires, 
            // so add a scheduled interrupt
            if (!user_mode && instruction_count > 0 && (instruction_count % timer) == 0) {
                scheduled_interrupt_num++;
            }

            // check if timer has expired and interrupt needs to be run
            // condition 1 - in user mode and timer expires
            // condition 2 - in user mode and scheduled interrupt needs to be run
            // if in kernel mode, already running interrupt so don't run timed interrupt
            if ((user_mode && instruction_count > 0 && (instruction_count % timer) == 0) ||
                (user_mode && scheduled_interrupt_num))
            {
                // decrement scheduled interrupts in any case
                // if scheduled interrupts was previously 0, will be reset to 0 next loop
                scheduled_interrupt_num--;

                // switch to kernel mode
                user_mode = false;

                // save user variables
                int user_pc = pc;
                int user_sp = sp;

                // switch to system variables
                pc = 1000;
                sp = sys_stack_idx;

                // add user SP to system stack
                write_over_pipe("w", sp, cpu_to_mem);
                write_over_pipe("w", user_sp, cpu_to_mem);
                sp--;

                // add user PC to system stack
                write_over_pipe("w", sp, cpu_to_mem);
                write_over_pipe("w", user_pc, cpu_to_mem);
                sp--;

                // since address jumped update bool variable to reflect jump
                has_jumped = true;
                continue;
            }
            
            // fetch next instruction from mem
            write_over_pipe("r", pc, cpu_to_mem);
            int instruction = stoi(read_over_pipe(mem_to_cpu));
            instruction_count++;
            ir = instruction;

            // load given value into AC
            if (ir == 1) {

                // fetch value from memory (next line/instruction)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int val = stoi(read_over_pipe(mem_to_cpu));

                // update AC
                ac = val;
            }
            
            // load value at given address into AC
            else if (ir == 2) {

                // fetch address of value from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr = stoi(read_over_pipe(mem_to_cpu));

                // load value in address if in bounds of user memory
                if (addr > 999 && user_mode) {
                    throw_process_error(
                        "Memory violation: accessing system address " + to_string(addr) + " in user mode",
                        cpu_to_mem,
                        mem_to_cpu
                    );
                } else {
                    write_over_pipe("r", addr, cpu_to_mem);
                    int val = stoi(read_over_pipe(mem_to_cpu));

                    // update AC
                    ac = val;
                }
            }
            
            // load value from address inside given address into AC
            else if (ir == 3) {
                
                // fetch first address of target address from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr1 = stoi(read_over_pipe(mem_to_cpu));

                // load value in address if in bounds of user memory
                if (addr1 > 999 && user_mode) {
                    throw_process_error(
                        "Memory violation: accessing system address " + to_string(addr1) + " in user mode",
                        cpu_to_mem,
                        mem_to_cpu
                    );
                } else {

                    write_over_pipe("r", addr1, cpu_to_mem);
                    int addr2 = stoi(read_over_pipe(mem_to_cpu));

                    // make sure second address also accessible
                    if (addr2 > 999 && user_mode) {
                        throw_process_error(
                            "Memory violation: accessing system address " + to_string(addr2) + " in user mode",
                            cpu_to_mem,
                            mem_to_cpu
                        );
                    } else {
                        
                        write_over_pipe("r", addr2, cpu_to_mem);
                        int val = stoi(read_over_pipe(mem_to_cpu));

                        // update AC
                        ac = val;
                    }
                }
            }
            
            // load value at given address + X into AC
            else if (ir == 4) {

                // fetch given address from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr = stoi(read_over_pipe(mem_to_cpu));

                // add value from X
                addr += x;

                // load value in address if in bounds of user memory
                if (addr > 999 && user_mode) {
                    throw_process_error(
                        "Memory violation: accessing system address " + to_string(addr) + " in user mode",
                        cpu_to_mem,
                        mem_to_cpu
                    );
                } else {

                    write_over_pipe("r", addr, cpu_to_mem);
                    int val = stoi(read_over_pipe(mem_to_cpu));

                    // update AC
                    ac = val;
                }
            }
            
            // load value at given address + Y into AC
            else if (ir == 5) {

                // fetch given address from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr = stoi(read_over_pipe(mem_to_cpu));

                // add value from X
                addr += y;

                // load value in address if in bounds of user memory
                if (addr > 999 && user_mode) {
                    throw_process_error(
                        "Memory violation: accessing system address " + to_string(addr) + " in user mode",
                        cpu_to_mem,
                        mem_to_cpu
                    );
                } else {

                    write_over_pipe("r", addr, cpu_to_mem);
                    int val = stoi(read_over_pipe(mem_to_cpu));

                    // update AC
                    ac = val;
                }
            }
            
            // load value at SP + X into AC
            else if (ir == 6) {
                
                // add SP and X to get address
                int addr = sp + x;

                // load value in address if in bounds of user memory
                if (addr > 999 && user_mode) {
                    throw_process_error(
                        "Memory violation: accessing system address " + to_string(addr) + " in user mode",
                        cpu_to_mem,
                        mem_to_cpu
                    );
                } else {

                    write_over_pipe("r", addr + 1, cpu_to_mem);
                    int val = stoi(read_over_pipe(mem_to_cpu));

                    // update AC
                    ac = val;
                }
            }
            
            // store value in AC into given address
            else if (ir == 7) {

                // get address
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr = stoi(read_over_pipe(mem_to_cpu));

                // write AC value into fetched address
                write_over_pipe("w", addr, cpu_to_mem);
                write_over_pipe("w", ac, cpu_to_mem);
            }
            
            // put random number into AC
            else if (ir == 8) {
                ac = (rand() % 100) + 1;
            }
            
            // write AC to screen as either int or char
            else if (ir == 9) {

                // fetch given port number from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int port_number = stoi(read_over_pipe(mem_to_cpu));

                // print char or int based on port
                if (port_number == 1) {
                    cout << ac;
                } else if (port_number == 2) {
                    cout << char(ac);
                }
            }
            
            // add X value to AC
            else if (ir == 10) {
                ac += x;
            }
            
            // add Y value to AC
            else if (ir == 11) {
                ac += y;
            }
            
            // subtract X value from AC
            else if (ir == 12) {
                ac -= x;
            }
            
            // subtract Y value from AC
            else if (ir == 13) {
                ac -= y;
            }
            
            // copy value in AC to X
            else if (ir == 14) {
                x = ac;
            }
            
            // copy value in X to AC
            else if (ir == 15) {
                ac = x;
            }
            
            // copy value in AC to Y
            else if (ir == 16) {
                y = ac;
            }
            
            // copy value in Y to AC
            else if (ir == 17) {
                ac = y;
            }
            
            // copy value in AC to SP
            else if (ir == 18) {
                sp = ac;
            }
            
            // copy value in SP to AC
            else if (ir == 19) {
                ac = sp;
            }
            
            // jump to the given address
            else if (ir == 20) {
                
                // fetch given address from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr = stoi(read_over_pipe(mem_to_cpu));

                // update program counter to reflect jump
                pc = addr;
                has_jumped = true;
            }
            
            // jump to the given address if AC is 0
            else if (ir == 21) {
                
                // fetch given address from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr = stoi(read_over_pipe(mem_to_cpu));

                // update program counter to reflect jump
                if (ac == 0) {
                    pc = addr;
                    has_jumped = true;
                }
            }
            
            // jump to the given address if AC is not 0
            else if (ir == 22) {

                // fetch given address from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr = stoi(read_over_pipe(mem_to_cpu));

                // update program counter to reflect jump
                if (ac != 0) {
                    pc = addr;
                    has_jumped = true;
                }
            }
            
            // call address -> jump to new address but save return address in stack
            else if (ir == 23) {
                
                // fetch jump address from memory (next line)
                pc++;
                write_over_pipe("r", pc, cpu_to_mem);
                int addr = stoi(read_over_pipe(mem_to_cpu));

                // push next address to run (return address) to stack
                write_over_pipe("w", sp, cpu_to_mem);
                write_over_pipe("w", pc + 1, cpu_to_mem);
                sp--;

                // update program counter to reflect jump
                pc = addr;
                has_jumped = true;
            }
            
            // return -> jumps to return address in stack
            else if (ir == 24) {

                // fetch return address at stack pointer
                sp++;
                write_over_pipe("r", sp, cpu_to_mem);
                int return_addr = stoi(read_over_pipe(mem_to_cpu));

                // update program counter to reflect jump
                pc = return_addr;
                has_jumped = true;
            }
            
            // increment X
            else if (ir == 25) {
                x++;
            }
            
            // decrement X
            else if (ir == 26) {
                x--;
            }
            
            // pushes AC onto stack
            else if (ir == 27) {
                write_over_pipe("w", sp, cpu_to_mem);
                write_over_pipe("w", ac, cpu_to_mem);
                sp--;
            }
            
            // pops from stack into AC
            else if (ir == 28) {
                sp++;
                write_over_pipe("r", sp, cpu_to_mem);
                int ac_stack = stoi(read_over_pipe(mem_to_cpu));
                ac = ac_stack;
            }
            
            // interrupt -> performs a system call
            else if (ir == 29 && user_mode) {
                
                // save user variables
                int user_pc = pc + 1;
                int user_sp = sp;

                // switch to kernel mode
                user_mode = false;

                // switch to system variables
                pc = 1500;
                sp = sys_stack_idx;

                // add user SP to system stack
                write_over_pipe("w", sp, cpu_to_mem);
                write_over_pipe("w", user_sp, cpu_to_mem);
                sp--;

                // add user PC to system stack
                write_over_pipe("w", sp, cpu_to_mem);
                write_over_pipe("w", user_pc, cpu_to_mem);
                sp--;

                // since address jumped update bool variable to reflect jump
                has_jumped = true;
            }
            
            // iret -> returns from an interrupt into user program
            else if (ir == 30) {

                // switch to user mode
                user_mode = true;

                // read user PC from system stack
                sp++;
                write_over_pipe("r", sp, cpu_to_mem);
                int user_pc = stoi(read_over_pipe(mem_to_cpu));
                pc = user_pc;

                // read user SP from system stack
                sp++;
                write_over_pipe("r", sp, cpu_to_mem);
                int user_sp = stoi(read_over_pipe(mem_to_cpu));
                sp = user_sp;

                // since address jumped update bool variable to reflect jump
                has_jumped = true;
            }
            
            // end execution of CPU and Memory
            else if (ir == 50) {

                // tell child (Memory) process to exit
                write_over_pipe("e", 0, cpu_to_mem);

                // close all pipes
                close(cpu_to_mem[0]);
                close(cpu_to_mem[1]);
                close(mem_to_cpu[0]);
                close(mem_to_cpu[1]);

                // exit CPU process
                return 0;
            }

            // increment PC for next instruction if no jump occurred
            if (!has_jumped) {
                pc++;
            }
        }
    }

    return 0;
};