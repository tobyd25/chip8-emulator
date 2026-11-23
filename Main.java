import javax.swing.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final ConsoleHandler console = new ConsoleHandler();

    // 16 bit opcode
    private static short opcode;
    // 4kb memory
    private static final byte[] memory = new byte[4096];

    // general registers 8 bit + carry (VF) 8 bit
    private static final byte[] V = new byte[16]; // general 8 bit registers
    private static short I; // index register 16 bit
    private static short pc; // program counter 16 bit

    private static byte delay_timer; // 8 bit
    private static byte sound_timer; // 8 bit

    // screen
    private static final boolean[][] gfx = new boolean[32][64];
    private static boolean drawFlag = false; // for efficiency

    private static final short[] stack = new short[16];
    private static byte sp; // stack pointer

    private static boolean[] key = new boolean[16];

    // fontset 8 bit * 80
    private static final short[] chip8_fontset = {
            0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
            0x20, 0x60, 0x20, 0x20, 0x70, // 1
            0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
            0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
            0x90, 0x90, 0xF0, 0x10, 0x10, // 4
            0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
            0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
            0xF0, 0x10, 0x20, 0x40, 0x40, // 7
            0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
            0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
            0xF0, 0x90, 0xF0, 0x90, 0x90, // A
            0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
            0xF0, 0x80, 0x80, 0x80, 0xF0, // C
            0xE0, 0x90, 0x90, 0x90, 0xE0, // D
            0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
            0xF0, 0x80, 0xF0, 0x80, 0x80 // F
    };

    // 16 bit max
    private static String decToHex(int n) {
        StringBuilder res = new StringBuilder();
        char num = (char) n;
        while (num > 0) {
            int r = num % 16;

            if (r < 10)
                res.append(r);
            if (r == 10)
                res.append("A");
            if (r == 11)
                res.append("B");
            if (r == 12)
                res.append("C");
            if (r == 13)
                res.append("D");
            if (r == 14)
                res.append("E");
            if (r == 15)
                res.append("F");
            num /= 16;
        }
        if (res.isEmpty())
            res.append("0");
        res.reverse();
        return res.toString();
    }

    private static void initialize() {
        // initialize registers
        pc = 0x200; // program code starts at 0x200
        opcode = 0;
        I = 0;
        sp = 0;

        // clear display
        for (boolean[] row : gfx) {
            Arrays.fill(row, false);
        }

        // clear stack
        Arrays.fill(stack, (short) 0);

        // clear registers v0-vf
        Arrays.fill(V, (byte) 0);

        // clear memory
        Arrays.fill(memory, (byte) 0);

        // load font set
        for (int i = 0; i < 80; ++i) {
            memory[i] = (byte) chip8_fontset[i];
        }

        // reset timers
        delay_timer = 0;
        sound_timer = 0;
    }

    private static void emulateCycle() {
        // fetch opcode
        opcode = (short) (memory[pc] << 8 | (memory[pc + 1] & 0xFF)); // masking to prevent 2nd byte from stealing all
                                                                      // the bits if negative

        // decode opcode
        switch (opcode & 0xF000) {
            case 0x0000:
                switch (opcode & 0x00FF) {
                    case 0x0000: // 0000: NOP
                        IO.println(decToHex(pc) + ": NOP");
                        pc += 2;
                        break;
                    case 0x00E0: // 00E0: clear screen
                        for (boolean[] col : gfx) {
                            Arrays.fill(col, false);
                        }
                        // IO.println(decToHex(pc) + ": CLS");
                        break;
                    case 0x00EE: // 00EE: return
                        // untested
                        IO.println(decToHex(pc) + ": RET");
                        sp--;
                        pc = (short) (stack[sp] + 2);
                        // stack not set to null idk if this is a problem
                        break;
                    default:
                        IO.println("Unknown opcode: " + decToHex(opcode) + " at: " + decToHex(pc));
                        System.exit(-1);
                }
                break;
            case 0x1000: // 1nnn: JP nnn
                I = (short) (opcode & 0x0FFF);
                IO.println(decToHex(pc) + ": JP " + decToHex(I));
                pc = I;
                break;
            case 0x2000: // 2nnn: CALL nnn
                stack[sp] = pc;
                ++sp;
                IO.println(decToHex(pc) + ": CALL " + decToHex(opcode & 0x0FFF));
                pc = (short) (opcode & 0x0FFF);
                break;
            case 0x3000: // 3xkk - SE Vx, kk (if Vx == kk, skip next instruction
                IO.println(((int) V[(opcode & 0x0F00) >> 8]) + ", " + (opcode & 0x00FF));
                IO.println(decToHex(pc) + ": SE V" + ((opcode & 0x0F00) >> 8) + ", " + decToHex(opcode & 0x00FF));
                if (V[(opcode & 0x0F00) >> 8] == (opcode & 0x00FF)) {
                    pc += 2;
                }
                pc += 2;
                break;
            case 0x4000: // 4xkk - SNE Vx, kk (if Vx != kk, skip next instruction
                IO.println((V[(opcode & 0x0F00) >> 8] & 0xFF) + ", " + (opcode & 0x00FF));
                IO.println(decToHex(pc) + ": SNE V" + ((opcode & 0x0F00) >> 8) + ", " + decToHex(opcode & 0x00FF));
                if ((V[(opcode & 0x0F00) >> 8] & 0xFF) != (opcode & 0x00FF)) {
                    pc += 2;
                }
                pc += 2;
                break;
            case 0x5000: // 5xy0 - SE Vx, Vy (if Vx == Vy, skip next instruction
                IO.println(((int) V[(opcode & 0x0F00) >> 8]) + ", " + (int) V[(opcode & 0x00F0) >> 4]);
                IO.println(
                        decToHex(pc) + ": SE V" + ((opcode & 0x0F00) >> 8) + ", V" + decToHex((opcode & 0x00F0) >> 4));
                if (V[(opcode & 0x0F00) >> 8] == V[(opcode & 0x00F0) >> 4]) {
                    pc += 2;
                }
                pc += 2;
                break;
            case 0x6000: // 6xkk - LD Vx, kk
                V[(opcode & 0x0F00) >> 8] = (byte) (opcode & 0x00FF);
                IO.println(
                        decToHex(pc) + ": LD V" + decToHex((opcode & 0x0F00) >> 8) + ", " + decToHex(opcode & 0x00FF));
                pc += 2;
                break;
            case 0x7000: // 7xkk - ADD Vx, kk
                if ((opcode & 0x00FF) > (0xFF - V[(opcode & 0x0F00) >> 8])) { // if kk > 255 - Vx
                    V[0xF] = 1; // carry
                    V[(opcode & 0x0F00) >> 8] = (byte) (V[(opcode & 0x0F00) >> 8] + (opcode & 0x00FF) - 256);
                } else {
                    V[0xF] = 0;
                    // Vx += Vy
                    V[(opcode & 0x0F00) >> 8] += (byte) (opcode & 0x00FF);
                }
                IO.println((opcode & 0x00FF) + "+" + (int) V[(opcode & 0x0F00) >> 8] + ", C: " + (int) V[0xf]);
                IO.println((int) V[(opcode & 0x0F00) >> 8]);

                IO.println(
                        decToHex(pc) + ": ADD V" + decToHex((opcode & 0x0F00) >> 8) + ", " + decToHex(opcode & 0x00FF));
                pc += 2;
                break;
            case 0x8000:
                switch (opcode & 0x000F) {
                    case 0x0000: // 8xy0: LD Vx, Vy
                        V[(opcode & 0x0F00) >> 8] = V[(opcode & 0x00F0) >> 4];
                        IO.println(
                                decToHex(pc) + ": LD V" + ((opcode & 0x0F00) >> 8) + ", V" + ((opcode & 0x00F0) >> 4));
                        pc += 2;
                        break;
                    case 0x0001: // 8xy1: OR Vx, Vy
                        V[(opcode & 0x0F00) >> 8] |= V[(opcode & 0x00F0) >> 4];
                        IO.println(
                                decToHex(pc) + ": OR V" + ((opcode & 0x0F00) >> 8) + ", V" + ((opcode & 0x00F0) >> 4));
                        pc += 2;
                        break;
                    case 0x0002: // 8xy2: AND Vx, Vy
                        V[(opcode & 0x0F00) >> 8] &= V[(opcode & 0x00F0) >> 4];
                        IO.println(
                                decToHex(pc) + ": AND V" + ((opcode & 0x0F00) >> 8) + ", V" + ((opcode & 0x00F0) >> 4));
                        pc += 2;
                        break;
                    case 0x0003: // 8xy3: XOR Vx, Vy
                        V[(opcode & 0x0F00) >> 8] ^= V[(opcode & 0x00F0) >> 4];
                        IO.println(
                                decToHex(pc) + ": XOR V" + ((opcode & 0x0F00) >> 8) + ", V" + ((opcode & 0x00F0) >> 4));
                        pc += 2;
                        break;
                    case 0x0004: // 8xy4: ADD Vx, Vy
                        if (V[(opcode & 0x00F0) >> 4] > (0xFF - (V[(opcode & 0x0F00) >> 8]) & 0xFF)) { // if y > 255 - x
                            V[0xF] = 1; // carry
                            V[(opcode & 0x0F00) >> 8] = (byte) (V[(opcode & 0x0F00) >> 8] + V[(opcode & 0x00F0) >> 4]
                                    - 256);
                        } else {
                            V[0xF] = 0;
                            // Vx += Vy
                            V[(opcode & 0x0F00) >> 8] += V[(opcode & 0x00F0) >> 4];
                        }

                        IO.println(decToHex(pc) + ": ADD V" + decToHex((opcode & 0x0F00) >> 8) + ", V"
                                + decToHex((opcode & 0x00F0) >> 4));
                        pc += 2;
                        break;
                    case 0x0005: // 8xy5: SUB Vx, Vy
                        IO.println((V[((opcode & 0x0F00) >> 8)] & 0xFF) + " " + (V[((opcode & 0x00F0) >> 4)] & 0xFF));
                        IO.println(decToHex(pc) + ": SUB V" + decToHex((opcode & 0x0F00) >> 8) + ", V"
                                + decToHex((opcode & 0x00F0) >> 4));
                        if ((V[((opcode & 0x0F00) >> 8)] & 0xFF) >= (V[((opcode & 0x00F0) >> 4)] & 0xFF)) {
                            V[0xF] = 1;

                            // Vx -= Vy
                            V[(opcode & 0x0F00) >> 8] -= (byte) (V[(opcode & 0x00F0) >> 4] & 0xFF);
                        } else {
                            V[0xF] = 0;

                            V[(opcode & 0x0F00) >> 8] = (byte) (0x100
                                    - (V[(opcode & 0x00F0) >> 4] - V[(opcode & 0x0F00) >> 8]));
                        }
                        pc += 2;
                        break;
                    case 0x0006: // 8xy6 - SHR Vx
                        // If the least-significant bit of Vx is 1, then VF is set to 1, otherwise 0. Then Vx is divided by 2.
                        if ((V[(opcode & 0x0F00) >> 8] & 0x0001) == 0x0001) {
                            V[0xF] = 1;
                        } else {
                            V[0xF] = 0;
                        }
                        V[(opcode & 0x0F00) >> 8] = (byte) (V[(opcode & 0x0F00) >> 8] >>> 1);
                        IO.println(decToHex(pc) + ": SHR V" + decToHex((opcode & 0x0F00) >> 8));
                        pc += 2;
                        break;
                    case 0x0007: // 8xy7 - SUBN Vx, Vy
                        // untested
                        V[(opcode & 0x0F00) >> 8] = (byte) (V[(opcode & 0x00F0) >> 4] - V[(opcode & 0x0F00) >> 8]);
                        IO.println(decToHex(pc) + ": SUBN V" + decToHex((opcode & 0x0F00) >> 8) + ", V"
                                + decToHex((opcode & 0x00F0) >> 4));
                        pc += 2;
                        break;
                    case 0x000E: // 8xyE - SHL Vx
                        if ((V[(opcode & 0x0F00) >> 8] & 0x0001) == 0x0001) {
                            V[0xF] = 1;
                        } else {
                            V[0xF] = 0;
                        }
                        V[(opcode & 0x0F00) >> 8] = (byte) (V[(opcode & 0x0F00) >> 8] << 1);
                        IO.println(decToHex(pc) + ": SHL V" + decToHex((opcode & 0x0F00) >> 8) + ", V"
                                + decToHex((opcode & 0x00F0) >> 4));
                        pc += 2;
                        break;
                    default:
                        IO.println("Unknown opcode: " + decToHex(opcode) + " at: " + decToHex(pc));
                        System.exit(-1);
                }
                break;
            case 0x9000: // 9xy0 - SNE Vx, Vy (if Vx != Vy, skip next instruction
                IO.println(((int) V[(opcode & 0x0F00) >> 8]) + ", " + ((int) V[(opcode & 0x00F0) >> 4]));
                IO.println(
                        decToHex(pc) + ": SNE V" + ((opcode & 0x0F00) >> 8) + ", V" + decToHex((opcode & 0x00F0 >> 4)));
                if (V[(opcode & 0x0F00) >> 8] != (V[(opcode & 0x00F0) >> 4])) {
                    pc += 2;
                }
                pc += 2;
                break;
            case 0xA000: // Annn: Sets I to the address nnn
                // execute opcode
                I = (short) (opcode & 0x0FFF);
                IO.println(decToHex(pc) + ": LD I, " + decToHex(I));
                pc += 2;
                break;
            case 0xD000: // Dxyn: DRW Vx, Vy, n (draws n bytes from sprite located at I to coords (Vx,
                         // Vy))
                short x = (short) V[(opcode & 0x0F00) >> 8];
                short y = (short) V[(opcode & 0x00F0) >> 4];
                short n = (short) (opcode & 0x000F);
                for (int i = 0; i < n; i++) {
                    V[0xF] = 0;
                    for (int j = 0; j < 8; j++) {
                        if (gfx[y + i][x + j] && ((0b10000000 >> j) & memory[I + i]) == (0b10000000 >> j)) {
                            V[0xF] = 1;
                            gfx[y + i][x + j] = false;
                        } else if (((0b10000000 >> j) & memory[I + i]) == (0b10000000 >> j)) {
                            gfx[y + i][x + j] = true;
                        }
                    }
                }
                IO.println(decToHex(I) + " -> (" + x + ", " + y + ")");
                IO.println(decToHex(pc) + ": DRW V" + ((opcode & 0x0F00) >> 8) + ", V" + ((opcode & 0x00F0) >> 4) + ", "
                        + n);
                drawFlag = true;
                pc += 2;
                break;
            case 0xF000:
                switch (opcode & 0x00FF) {
                    case 0x0033: // Fx33: LD B, Vx
                        // store binary representation of Vx in I, I+1, I+2
                        memory[I] = (byte) (V[(opcode & 0x0F00) >> 8] / 100);
                        memory[I + 1] = (byte) ((V[(opcode & 0x0F00) >> 8] / 10) % 10);
                        memory[I + 2] = (byte) (V[(opcode & 0x0F00) >> 8] % 10);
                        IO.println(decToHex(pc) + ": LD B, V" + decToHex((opcode & 0x0F00) >> 8));
                        pc += 2;
                        break;
                    case 0x001E: // Fx1E: ADD I, Vx
                        // untested
                        if (V[(opcode & 0x00F0) >> 4] > (0xFF - I)) { // if y > 255 - x
                            V[0xF] = 1; // carry
                        } else {
                            V[0xF] = 0;
                        }
                        // I += Vy
                        I += V[(opcode & 0x00F0) >> 4];

                        IO.println(decToHex(pc) + ": ADD I, V" + decToHex((opcode & 0x00F0) >> 4));
                        pc += 2;
                        break;
                    case 0x0055: // Fx55: LD [I], Vx
                        for (int i = 0; i <= (opcode & 0x0F00) >> 8; i++) {
                            memory[I + i] = V[i];
                        }
                        IO.println(decToHex(pc) + ": LD [I], V" + decToHex((opcode & 0x0F00) >> 8));
                        pc += 2;
                        break;
                    case 0x0065: // Fx65: LD Vx, [I]
                        // reads memory from V0-Vx into memory starting at I
                        for (int i = 0; i <= (opcode & 0x0F00) >> 8; i++) {
                            V[i] = memory[I + i];
                        }
                        IO.println(decToHex(pc) + ": LD V" + decToHex((opcode & 0x0F00) >> 8) + ", [I]");
                        pc += 2;
                        break;
                    default:
                        IO.println("Unknown opcode: " + decToHex(opcode) + " at: " + decToHex(pc));
                        System.exit(-1);
                }
                break;
            default:
                IO.println("Unknown opcode: " + decToHex(opcode) + " at: " + decToHex(pc));
                System.exit(-1);
                break;
        }

        // update timers
        if (delay_timer > 0)
            delay_timer--;
        if (sound_timer > 0) {
            if (sound_timer == 1) {
                IO.println("BEEP!"); // play sound
                sound_timer--;
            }
        }
    }

    private static void load(String filename) {
        try (InputStream is = new FileInputStream(filename)) {
            int data;
            for (int i = 0; (data = is.read()) != -1; ++i) {
                memory[i + 0x200] = (byte) data;
            }
        } catch (IOException e) {
            IO.println("Error: " + e);
        }
    }

    public static void main(String[] args) {
        LOGGER.addHandler(console);
        console.setLevel(Level.INFO);
        LOGGER.setLevel(Level.INFO);

        // setup render system and register input callbacks
        int scale = 20;
        JFrame frame = new JFrame();
        Screen screen = new Screen(scale);
        frame.setSize(64 * scale, 32 * scale);
        frame.setResizable(false);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(screen);
        frame.addKeyListener(screen);

        double start;
        double end;

        // initialize the chip8 system and load the game into the memory
        initialize();
        load(args[0]);

        // emulation loop
        while (true) {
            start = System.currentTimeMillis();
            // emulate 1 cycle
            emulateCycle();

            // if the draw flag is set update the screen
            if (drawFlag) {
                screen.updateGFX(gfx);
                drawFlag = false;
            }

            // store key press state (press and release)
            key = screen.getKeys();

            end = System.currentTimeMillis();
            if (end - start < 1000 / 60) {
                try {
                    Thread.sleep(Math.round((double) 1000 / 60 - (end - start)));
                } catch (Exception e) {
                    IO.println(e.getMessage());
                }
            }
        }
    }
}
