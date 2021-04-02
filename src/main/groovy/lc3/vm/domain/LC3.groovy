package lc3.vm.domain

class LC3 {

    def memory = new short[Short.SIZE]
    def reg = new short[SIZE]

    /**
     * General purpose registers
     */
    public static final short R_R0 = 0, R_R1=1, R_R2=2, R_R3=3, R_R4=4, R_R5=5, R_R6=6, R_R7=7

    /**
     * Program counter
     */
    public static final short R_PC=8

    /**
     * Logical registers
     */
    public static final short R_COND=9, R_COUNT=10

    /**
     * Number of registers
     */
    public static final short SIZE = 10

    /**
     * Program counter starting memory address
     */
    public static final short PC_START = 0x3000

    /**
     * Instruction set
     */
    public static final short OP_BR = 0          // branch
    public static final short OP_ADD = 1         // add
    public static final short OP_LD = 2          // load
    public static final short OP_ST = 3          // store
    public static final short OP_JSR = 4         // jump register
    public static final short OP_AND = 5         // bitwise and
    public static final short OP_LDR = 6         // load register
    public static final short OP_STR = 7         // store register
    public static final short OP_RTI = 8         // unused
    public static final short OP_NOT = 9         // bitwise not
    public static final short OP_LDI = 10        // load indirect
    public static final short OP_STI = 11        // store indirect
    public static final short OP_JMP = 12        // jump
    public static final short OP_RES = 13        // reserved (unused)
    public static final short OP_LEA = 14        // load effective address
    public static final short OP_TRAP = 15       // execute trap

    /**
     * Condition flags
     */
    public static final short FL_POS = (1 << 0) as short  // P
    public static final short FL_ZRO = (1 << 1) as short  // Z
    public static final short FL_NEG = (1 << 2) as short  // N


    /**
     * Trap routines codes
     */
    public static final short TRAP_GETC = 0x20      // get character from keyboard, not echoed onto the terminal
    public static final short TRAP_OUT = 0x21
    public static final short TRAP_PUTS = 0x22
    public static final short TRAP_IN = 0x23
    public static final short TRAP_PUTSP = 0x24
    public static final short TRAP_HALT = 0x25

    /**
     * Memory mapped registers
     */
    public static final short MR_KBSR = 0xFE00          // keyboard status
    public static final short MR_KBDR = 0xFE02          // keyboard data


    /**
     * Memory functions
     */

    /**
     * Write a value in a specific memory address.
     *
     * @param address - Memory address where value should be inserted.
     * @param value - Value about to be inserted.
     */
    void memWrite(short address, short value) {
        memory[address] = value
    }

    short memRead(short address) {

        if(address == MR_KBSR) {

            if (checkKey()) {
                memory[MR_KBSR] = (1 << 15)
                memory[MR_KBDR] = getKey()
            }
        }
        else {
            memory[MR_KBSR] = 0
        }

        return memory[address]
    }


    void updateFlags(short r) {

        if(reg[r] == 0) {
            reg[R_COND] = FL_ZRO
        }
        else if(reg[r] >> 15) {  // a 1 in the left-most bit indicates negative
            reg[R_COND] = FL_NEG
        }
        else {
            reg[R_COND] = FL_POS
        }
    }

    short signExtend(short x, int bitCount) {
        if ((x >> (bitCount -1)) & 1) {
            x |= (0xFFFF << bit_count)
        }
        return x
    }


    /**
     * Instructions implementation
     * */

    /**
     *
     * @param instr
     */
    void add(instr) {

        /** destination register */
        short r0 = (instr >> 9) & 0x7

        /** first operand */
        short r1 = (instr >> 6) & 0x7

        /** wheter we are in immediate mode */
        short imm_flag = (instr >> 5) & 0x1

        if(imm_flag) {
            short imm5 = signExtend((instr & 0x1f) as short, 5)
            reg[r0] = reg[r1] + imm5 as short
        }
        else {
            short r2 = instr & 0x7
            reg[r0] = reg[r1] + reg[r2] as short
        }

        updateFlags(r0)
    }

    void and() {
    }


    void ldi(instr) {
        short r0 = (instr >> 9) & 0x7
        short pcOffset = this.signExtend( (instr & 0x1FF) as short, 9)

        this.updateFlags(r0)
    }

    void bitwiseAnd(instr) {
        short r0 = (instr >> 9) & 0x7
        short r1 = (instr >> 6) & 0x7
        short immFlag = (instr >> 5) & 0x01

        if(immFlag) {
            short imm5 = signExtend((instr >> 0x1F) as short, 5)
            reg[r0] = (reg[r1] & imm5) as short
        }
        else {
            short r2 = instr & 0x7
            reg[r0] = (reg[r1] & reg[r2]) as short
        }
        updateFlags(r0)
    }

    void bitwiseNot(instr) {
        short r0 = (instr >> 9) & 0x7
        short r1 = (instr >> 6) & 0x7

        reg[r0] = ~reg[r1] as short

        updateFlags(r0)
    }

    void branch(instr) {
        short pcOffset = signExtend((instr >> 0x1FF) as short, 9)
        short condFlag = (instr >> 9) & 0x7

        if(condFlag & reg[R_COND]) {
            reg[R_PC] += pcOffset
        }
    }

    void jump(instr) {
        short r1 = (instr >> 6) & 0x7
        reg[R_PC] reg[r1]
    }

    void jumpRegister(instr) {
        short longFlag = (instr >> 11) & 1
        reg[R_R7] = reg[R_PC]

        if(longFlag) {
            short longPCOffset = signExtend((instr & 0x7FF) as short, 11)
            reg[R_PC] += longPCOffset
        }
        else {
            short r1 = (instr >> 6) & 0x7
            reg[R_PC] = reg[r1]
        }
    }

    void load(instr) {
        short r0 = (instr >> 9) & 0x7
        short pcOffset = signExtend((instr & 0x1FF) as short, 9)
        reg[r0] = memRead((reg[R_PC] + pcOffset) as short)
        updateFlags(r0)
    }

    void loadRegister(instr) {
        short r0 = (instr >> 9) & 0x7
        short r1 = (instr >> 6) & 0x7
        short offset = signExtend((instr & 0x3F) as short, 6)
        reg[r0] = memRead((reg[r1] + offset) as short)
        updateFlags(r0)
    }

    void loadEffectiveAddress(instr) {
        short r0 = (instr >> 9) & 0x7
        short pcOffset = signExtend((instr & 0x1FF) as short, 9)
        reg[r0] = (reg[R_PC] + pcOffset) as short
        updateFlags(r0)
    }

    void loadIndirect(instr) {
        short r0 = (instr >> 9) & 0x7
        short pcOffset = signExtend((instr & 0x1FF) as short, 9)
        reg[r0] = memRead(memRead((reg[R_PC + pcOffset]) as short))

    }

    void store(instr) {
        short r0 = (instr >> 9) & 0x7
        short pcOffset = signExtend((instr & 0x1FF) as short, 9)
        memWrite((reg[R_PC] + pcOffset) as short, reg[r0])
    }

    void storeIndirect(instr) {
        short r0 = (instr >> 9) & 0x7
        short pcOffset = signExtend((instr & 0x1FF) as short, 9)
        memWrite(memRead((reg[R_PC] + pcOffset) as short), reg[r0])

    }

    void storeRegister(instr) {

        short r0 = (instr >> 9) & 0x7
        short r1 = (instr >> 6) & 0x7
        short offset = signExtend((instr & 0x3F) as short, 6)
        memWrite((reg[r1] + offset) as short, reg[r0])
    }

    void trap(instr) {

        switch(instr & 0xFF) {

        // get character from keyboard, not echoed onto the terminal
            case TRAP_GETC:
                trapGetc()
                break

        // output a character into terminal output stream.
            case TRAP_OUT:
                trapOut()
                break

        // output a word string into terminal output stream.
            case TRAP_PUTS:
                break

        // get character from keyboard, echoed into the terminal
            case TRAP_IN:
                break

        // output a byte string
            case TRAP_PUTSP:
                break

        // halt the program
            case TRAP_HALT:
                break
        }
    }

    void trapPuts() {
    }

    void trapGetc() {
    }

    void trapOut() {
    }

    void trapIn() {
    }

    void  trapPutSP() {
    }

    void trapHalt() {
    }

    void readImageFile(String filePath) {

        def program = new File(filePath).readBytes()

        // def pcStart = combineBytes(program[0], program[1])

        short currentAddress = PC_START

        1.step(program.size(), 2) {index ->
            memory[currentAddress.shortValue()] = combineBytes(program[index], program[index+1])
            currentAddress += 1
        }
    }

    void readImage(imagePath) {
    }

    void error() {
        print "Unexpected condition."
    }

    private short combineBytes(Byte firstByte, Byte secondByte) {
        (firstByte.shortValue() >> 8) | (secondByte.shortValue() & 0xFF)
    }
}