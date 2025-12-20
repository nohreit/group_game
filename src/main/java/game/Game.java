package main.java.game;

import javax.swing.*;

public class Game {
    public static final int VIRTUAL_WIDTH = 640;
    public static final int VIRTUAL_HEIGHT = 360;
    public static final int SCALE = 3;

    private static JFrame frame;
    private static GamePanel panel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Zelda-like");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setResizable(false);

            panel = new GamePanel(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, SCALE);
            frame.setContentPane(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.requestFocusInWindow();

            panel.init();
            panel.startLoop();
        });
    }
}
