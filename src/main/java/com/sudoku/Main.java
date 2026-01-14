package com.sudoku;

import javax.swing.SwingUtilities;

/**
 * Main entry point for the Sudoku game application
 */
public class Main {
    public static void main(String[] args) {
        // Run GUI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                SudokuGUI gui = new SudokuGUI();
                gui.setVisible(true);
            } catch (Exception e) {
                System.err.println("Error starting application: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}

