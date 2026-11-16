import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class Screen extends JComponent implements KeyListener {
    boolean[][] gfx;
    int scale;
    private static final boolean[] key = new boolean[16];

    public Screen(int scale) {
        this.scale = scale;
        this.gfx = new boolean[32][64];
    }

    @Override
    public void paintComponent(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0,0,64*scale,32*scale);

        g.setColor(Color.WHITE);
        for (int y = 0; y < gfx.length; y++) {
            for (int x = 0; x < gfx[y].length; x++) {
                if (gfx[y][x]) {
                    g.fillRect(x*scale, y*scale, scale, scale);
                }
            }
        }
    }

    public void updateGFX(boolean[][] gfx) {
        this.gfx = gfx;
        repaint();
    }

    public boolean[] getKeys() {
        return key;
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_1 -> key[0] = true;
            case KeyEvent.VK_2 -> key[1] = true;
            case KeyEvent.VK_3 -> key[2] = true;
            case KeyEvent.VK_4 -> key[3] = true;
            case KeyEvent.VK_Q -> key[4] = true;
            case KeyEvent.VK_W -> key[5] = true;
            case KeyEvent.VK_E -> key[6] = true;
            case KeyEvent.VK_R -> key[7] = true;
            case KeyEvent.VK_A -> key[8] = true;
            case KeyEvent.VK_S -> key[9] = true;
            case KeyEvent.VK_D -> key[10] = true;
            case KeyEvent.VK_F -> key[11] = true;
            case KeyEvent.VK_Z -> key[12] = true;
            case KeyEvent.VK_X -> key[13] = true;
            case KeyEvent.VK_C -> key[14] = true;
            case KeyEvent.VK_V -> key[15] = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }
}
