package lc3.vm.controller

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

class RawConsoleInput {

    static final boolean isWindows = System.getProperty("os.name").startsWith "Windows"
    static final int invalidKey = 0xFFFE
    static final String invalidKeyStr = String.valueOf(invalidKey as char)

    static boolean initDone
    static boolean stdinIsConsole
    static boolean consoleModeAltered

    static int read(boolean wait) throws IOException {

        return isWindows ? readWindows(wait) : readUnix(wait)
    }

    static void resetConsoleMode() throws IOException {

        if (isWindows) {
            resetConsoleModeWindows()
        } else {
            resetConsoleModeUnix()
        }
    }

    private static void registerShutdownHook() {
        Runtime.runtime.addShutdownHook(new Thread() {
            @Override
            void run() {
                shutdownHook()
            }
        })
    }

    private static void shutdownHook() {

        try {
            resetConsoleMode()
        }
        catch (Exception e) {
            e.printStackTrace()
        }
    }

    /**
     * Windows operations
     *
     * The Windows version uses _kbhit() and _getwch() from msvcrt.dll.
     */
    private static Msvcrt msvcrt
    private static Kernel32 kernel32
    private static Pointer consoleHandle
    private static int originalConsoleMode

    private static int readWindows(boolean wait) throws IOException {

        initWindows()

        if (!stdinIsConsole) {
            int c = msvcrt.getwchar()
            return c == 0xFFFF ? -1 : c
        }

        consoleModeAltered = true

        setConsoleMode(consoleHandle, originalConsoleMode & Kernel32Defs.ENABLE_PROCESSED_INPUT)

        return (!wait && msvcrt._kbhit()==0) ? -2: getwch()
    }

    private static int getwch() {

        int c = msvcrt._getwch()

        // check for function key or arrow key
        if(c==0 || c==0xE0) {

            c = msvcrt._getwch()

            // construct key code in private Unicode range
            if (c >= 0 && c <= 0x18FF) {
                return 0xE000 + c
            }
            return invalidKey
        }
        return c
    }

    private static synchronized void initWindows() throws IOException {

        if (initDone) {
            return
        }

        msvcrt = Native.load "msvcrt", Msvcrt.class
        kernel32 = Native.load"kernel32", Kernel32.class

        try {
            consoleHandle = getStdInputHandle()
            originalConsoleMode = getConsoleMode(consoleHandle)
            stdinIsConsole = true
        }
        catch (IOException e) {
            stdinIsConsole = false
            e.printStackTrace()
        }

        if (stdinIsConsole) {
            registerShutdownHook()
        }

        initDone = true
    }

    private static Pointer getStdInputHandle() throws IOException {

        Pointer handle = kernel32.GetStdHandle(Kernel32Defs.STD_INPUT_HANDLE)

        long val = Pointer.nativeValue(handle)

        if(val == 0 || val == Kernel32Defs.INVALID_HANDLE_VALUE) {
            throw new IOException("GetStdHandle(STD_INPUT_HANDLE) failed.")
        }

        return handle
    }

    private static int getConsoleMode(Pointer handler) throws IOException {

        def mode = new IntByReference()

        int rc = kernel32.GetConsoleMode(handler, mode)

        if(rc==0) {
            throw new IOException("GetConsoleMode() failed.")
        }

        return mode.value
    }

    private static void setConsoleMode(Pointer handle, int mode) throws IOException {

        int rc = kernel32.SetConsoleMode(handle, mode)

        if(rc==0) {
            throw new IOException("SetConsoleMode() failed.")
        }
    }

    private static void resetConsoleModeWindows() throws IOException {

        // check is reset console mode operation makes sense.
        if(!initDone || !stdinIsConsole || !consoleModeAltered) {
            return
        }
        setConsoleMode(consoleHandle, originalConsoleMode)
        consoleModeAltered = false
    }

    private static interface Msvcrt extends Library {

        int _kbhit()
        int _getwch()
        int getwchar()

    }

    private static class Kernel32Defs {
        static final int  STD_INPUT_HANDLE       = -10
        static final long INVALID_HANDLE_VALUE   = (Native.POINTER_SIZE == 8) ? -1 : 0xFFFFFFFFL
        static final int  ENABLE_PROCESSED_INPUT = 0x0001

        /**
         * Unused
         */
        /*
        static final int  ENABLE_LINE_INPUT      = 0x0002
        static final int  ENABLE_ECHO_INPUT      = 0x0004
        static final int  ENABLE_WINDOW_INPUT    = 0x0008
         */
    }

    private static interface Kernel32 extends Library {
        int GetConsoleMode (Pointer hConsoleHandle, IntByReference lpMode)
        int SetConsoleMode (Pointer hConsoleHandle, int dwMode)
        Pointer GetStdHandle (int nStdHandle)
    }

    /**
     * Unix operations
     */
    private static final int               stdinFd = 0
    private static Libc                    libc
    private static CharsetDecoder           charsetDecoder
    private static Termios                 originalTermios
    private static Termios                 rawTermios
    private static Termios                 intermediateTermios

    private static int readUnix (boolean wait) throws IOException {

        initUnix()

        // STDIN is not a console
        if (!stdinIsConsole) {
            return readSingleCharFromByteStream(System.in)
        }

        consoleModeAltered = true

        // switch off canonical mode, echo and signals
        setTerminalAttrs(stdinFd, rawTermios)

        try {
            if (!wait && System.in.available() == 0) {
                // no input available
                return -2
            }
            return readSingleCharFromByteStream(System.in)
        }
        finally {
            // reset some console attributes
            setTerminalAttrs(stdinFd, intermediateTermios)
        }
    }

    private static Termios getTerminalAttrs (int fd) throws IOException {

        Termios termios = new Termios()

        if (libc.tcgetattr(fd, termios) != 0) {
            throw new RuntimeException("tcgetattr() failed.")
        }

        return termios
    }

    private static void setTerminalAttrs (int fd, Termios termios) throws IOException {

        if (libc.tcsetattr(fd, LibcDefs.TCSANOW, termios) != 0) {
            throw new RuntimeException("tcsetattr() failed.")
        }
    }

    private static int readSingleCharFromByteStream (InputStream inputStream) throws IOException {

        byte[] inBuf = new byte[4]
        int    inLen = 0

        while (true) {

            // input buffer overflow
            if (inLen >= inBuf.length) {
                return invalidKey
            }

            // read next byte
            int b = inputStream.read()

            if (b == -1) {
                // EOF
                return -1
            }

            inBuf[inLen++] = (byte) b

            int c = decodeCharFromBytes(inBuf, inLen)

            if (c != -1) {
                return c
            }
        }
    }

    // (This method is synchronized because the charsetDecoder must only be used by a single thread at once.)
    private static synchronized int decodeCharFromBytes (byte[] inBytes, int inLen) {

        charsetDecoder.reset()
        charsetDecoder.onMalformedInput(CodingErrorAction.REPLACE)
        charsetDecoder.replaceWith(invalidKeyStr)
        ByteBuffer input = ByteBuffer.wrap(inBytes, 0, inLen)
        CharBuffer out = CharBuffer.allocate(1)
        charsetDecoder.decode(input, out, false)

        if (out.position() == 0) {
            return -1
        }

        return out.get(0)
    }

    private static synchronized void initUnix() throws IOException {

        if (initDone) {
            return
        }

        libc = Native.loadLibrary("c", Libc.class) as Libc
        stdinIsConsole = libc.isatty(stdinFd) == 1
        charsetDecoder = Charset.defaultCharset().newDecoder()

        if (stdinIsConsole) {
            originalTermios = getTerminalAttrs(stdinFd)
            rawTermios = new Termios(originalTermios)
            rawTermios.c_lflag &= ~(LibcDefs.ICANON | LibcDefs.ECHO | LibcDefs.ECHONL | LibcDefs.ISIG)
            intermediateTermios = new Termios(rawTermios)
            intermediateTermios.c_lflag |= LibcDefs.ICANON
            // Canonical mode can be switched off between the read() calls, but echo must remain disabled.
            registerShutdownHook()
        }

        initDone = true
    }

    private static void resetConsoleModeUnix() throws IOException {

        if (!initDone || !stdinIsConsole || !consoleModeAltered) {
            return
        }
        setTerminalAttrs(stdinFd, originalTermios)
        consoleModeAltered = false
    }

    protected static class Termios extends Structure {

        // termios.h
        public int      c_iflag
        public int      c_oflag
        public int      c_cflag
        public int      c_lflag
        public byte     c_line
        public byte[]   filler = new byte[64]                  // actual length is platform dependent

        @Override protected List<String> getFieldOrder() {
            return Arrays.asList("c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_line", "filler")
        }

        Termios() {}

        Termios(Termios t) {
            c_iflag = t.c_iflag
            c_oflag = t.c_oflag
            c_cflag = t.c_cflag
            c_lflag = t.c_lflag
            c_line  = t.c_line
            filler  = t.filler.clone()
        }
    }

    private static class LibcDefs {
        // termios.h
        static final int ISIG    = 0000001
        static final int ICANON  = 0000002
        static final int ECHO    = 0000010
        static final int ECHONL  = 0000100
        static final int TCSANOW = 0
    }

    private static interface Libc extends Library {

        // termios.h
        int tcgetattr(int fd, Termios termios) throws Exception
        int tcsetattr(int fd, int opt, Termios termios) throws Exception

        // unistd.h
        int isatty (int fd)
    }

    static void main(String[] args) {
        int c = read(false)
        print c
    }
}




