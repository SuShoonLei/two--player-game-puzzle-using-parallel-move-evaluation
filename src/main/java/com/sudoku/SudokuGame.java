package com.sudoku;

/**
 * Manages the Sudoku game logic including validation and win detection
 */
public class SudokuGame {
    private int[][] grid;
    private int[][] originalGrid;
    private boolean[][] isFixed;
    
    public SudokuGame(int[][] puzzle) {
        this.grid = new int[9][9];
        this.originalGrid = new int[9][9];
        this.isFixed = new boolean[9][9];
        
        // Copy puzzle to grid and mark fixed cells
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                this.grid[i][j] = puzzle[i][j];
                this.originalGrid[i][j] = puzzle[i][j];
                this.isFixed[i][j] = (puzzle[i][j] != 0);
            }
        }
    }
    
    /**
     * Checks if a number can be placed at the given position
     */
    public boolean isValidMove(int row, int col, int num) {
        if (num < 1 || num > 9) {
            return false;
        }
        
        // Check row
        for (int j = 0; j < 9; j++) {
            if (j != col && grid[row][j] == num) {
                return false;
            }
        }
        
        // Check column
        for (int i = 0; i < 9; i++) {
            if (i != row && grid[i][col] == num) {
                return false;
            }
        }
        
        // Check 3x3 box
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int i = boxRow; i < boxRow + 3; i++) {
            for (int j = boxCol; j < boxCol + 3; j++) {
                if (i != row && j != col && grid[i][j] == num) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Places a number at the given position
     */
    public boolean placeNumber(int row, int col, int num) {
        if (isFixed[row][col]) {
            return false; // Cannot modify fixed cells
        }
        
        if (num == 0 || isValidMove(row, col, num)) {
            grid[row][col] = num;
            return true;
        }
        
        return false;
    }
    
    /**
     * Clears a cell (sets it to 0)
     */
    public boolean clearCell(int row, int col) {
        if (isFixed[row][col]) {
            return false; // Cannot clear fixed cells
        }
        grid[row][col] = 0;
        return true;
    }
    
    /**
     * Checks if the game is won (all cells filled and valid)
     */
    public boolean isWon() {
        // Check if all cells are filled
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] == 0) {
                    return false;
                }
            }
        }
        
        // Check if all cells are valid
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                int num = grid[i][j];
                grid[i][j] = 0; // Temporarily remove to check validity
                if (!isValidMove(i, j, num)) {
                    grid[i][j] = num; // Restore
                    return false;
                }
                grid[i][j] = num; // Restore
            }
        }
        
        return true;
    }
    
    /**
     * Resets the game to the original puzzle
     */
    public void reset() {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (!isFixed[i][j]) {
                    grid[i][j] = 0;
                } else {
                    grid[i][j] = originalGrid[i][j];
                }
            }
        }
    }
    
    /**
     * Gets the value at the given position
     */
    public int getValue(int row, int col) {
        return grid[row][col];
    }
    
    /**
     * Checks if a cell is fixed (part of the original puzzle)
     */
    public boolean isFixed(int row, int col) {
        return isFixed[row][col];
    }
    
    /**
     * Gets the entire grid
     */
    public int[][] getGrid() {
        return grid;
    }
}

