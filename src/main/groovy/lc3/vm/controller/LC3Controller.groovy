package lc3.vm.controller

import lc3.vm.domain.LC3

class LC3Controller {

    static lc3 = new LC3()

    static showSyntaxMessage() {
        println "lc3 [image-file] ..."
    }

    static readImage(LC3 lc3, String filepath) {
        lc3.readImageFile(filepath)
    }

    static memRead(addr) {
        return addr
    }

    static main(args) {

        if(args.size() < 2) {
            showSyntaxMessage()
            System.exit 2
        }


        for (int i = 1; i < args.size(); ++i) {

            if(!readImage(lc3, args[i])) {
                println "Fail to load image: ${args[i]}"
                System.exit 1
            }
        }


        lc3.reg[LC3.R_PC] = LC3.PC_START

        def running = true

        while(running) {

            short instr = memRead(lc3.reg[LC3.R_PC])
            short op = (instr >> 12) as short

            switch (op) {

            // adding
                case lc3.OP_ADD:
                    lc3.add(instr)
                    break

            // bitwise and
                case lc3.OP_AND:
                    lc3.bitwiseAnd(instr)
                    break

            // bitwise not
                case lc3.OP_NOT:
                    lc3.bitwiseNot(instr)
                    break

            // branch operation - changes the PC register to a new memory address if a condition is true
                case lc3.OP_BR:
                    lc3.branch(instr)
                    break

            // jump operation - set the PC register to a new memory address informed by the own instruction.
                case lc3.OP_JMP:
                    lc3.jump(instr)
                    break

            // jump register operation - set the PC register to a new memory address stored in a register
                case lc3.OP_JSR:
                    lc3.jumpRegister(instr)
                    break

            // load operation - load value in a memory address into a register
                case lc3.OP_LD:
                    lc3.load(instr)
                    break

                case lc3.OP_LDI:
                    lc3.loadIndirect(instr)
                    break

            // load from register operation - load value stored within a register into another register
                case lc3.OP_LDR:
                    lc3.loadRegister(instr)
                    break

            // load effective address - load a memory address into a register
                case lc3.OP_LEA:
                    lc3.loadEffectiveAddress(instr)
                    break

            // store a value into the first register and into memory.
                case lc3.OP_ST:
                    lc3.store(instr)
                    break

            // store indirectly (copy) a value already in memory into the first register and into another address.
                case lc3.OP_STI:
                    lc3.storeIndirect(instr)
                    break

            // store (copy) a value from one register into the first register and into memory.
                case lc3.OP_STR:
                    lc3.storeRegister(instr)
                    break

            // call a trap function
                case lc3.OP_TRAP:
                    lc3.trap(instr)
                    break

                case lc3.OP_RES:
                case lc3.OP_RTI:
                default:
                    // bad code
                    lc3.error()
                    break
            }
        }

        // shutdown

    }
}

