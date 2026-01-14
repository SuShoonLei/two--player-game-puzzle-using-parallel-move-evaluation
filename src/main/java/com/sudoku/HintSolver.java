package com.sudoku;

import java.util.*;
import java.util.concurrent.*;

public class HintSolver implements HintSolverInterface {
    private boolean[][][] possible;
    private int[][] grid;
    private boolean[][] isFixed;
    
    private final Queue<HintResult> hintQueue;
    private final Object lock = new Object(); 
    
    public HintSolver(int[][] grid, boolean[][] isFixed) {
        this.grid = deepCopyGrid(grid);
        this.isFixed = deepCopyBooleanGrid(isFixed);
        this.possible = new boolean[9][9][9];
        this.hintQueue = new ConcurrentLinkedQueue<>();
        initializeConstraints();
        generateAllHints();
    }
    
    private int[][] deepCopyGrid(int[][] original) {
        int[][] copy = new int[9][9];
        for (int i = 0; i < 9; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, 9);
        }
        return copy;
    }
    
    /**
     * Deep copy a boolean grid
     */
    private boolean[][] deepCopyBooleanGrid(boolean[][] original) {
        boolean[][] copy = new boolean[9][9];
        for (int i = 0; i < 9; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, 9);
        }
        return copy;
    }
    
    /**
     * Initialize constraints based on current grid state
     */
    private void initializeConstraints() {
        // Initially, all numbers are possible for all empty cells
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] == 0) {
                    // Empty cell: all numbers are possible
                    for (int num = 0; num < 9; num++) {
                        possible[i][j][num] = true;
                    }
                } else {
                    // Filled cell: no numbers are possible
                    for (int num = 0; num < 9; num++) {
                        possible[i][j][num] = false;
                    }
                }
            }
        }
        
        // Apply constraints from all filled cells
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] != 0) {
                    updateConstraints(i, j, grid[i][j]);
                }
            }
        }
    }
    
    /**
     * Update constraints when a number is placed
     */
    public void updateConstraints(int row, int col, int num) {
        // First, mark the filled cell as having no possibilities
        for (int n = 0; n < 9; n++) {
            possible[row][col][n] = false;
        }
        
        // Update constraints for other cells in the same row
        for (int j = 0; j < 9; j++) {
            if (j != col && grid[row][j] == 0) {
                possible[row][j][num - 1] = false;
            }
        }
        
        // Update constraints for other cells in the same column
        for (int i = 0; i < 9; i++) {
            if (i != row && grid[i][col] == 0) {
                possible[i][col][num - 1] = false;
            }
        }
        
        // Mark this number as impossible for all other cells in the same 3x3 block
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int i = boxRow; i < boxRow + 3; i++) {
            for (int j = boxCol; j < boxCol + 3; j++) {
                if ((i != row || j != col) && grid[i][j] == 0) {
                    possible[i][j][num - 1] = false;
                }
            }
        }
    }
    
    /**
     * Generate hints using backtracking
     * Generates a reasonable number of hints (up to 50) to avoid generating the entire solution
     * This method explores possible solution paths to find hints
     */
    private void generateAllHints() {
        synchronized (lock) {
            hintQueue.clear();
            
            // Create a working copy for backtracking
            int[][] workingGrid = deepCopyGrid(grid);
            boolean[][][] workingPossible = deepCopyPossible(possible);
            
            // Use backtracking to find a valid solution path
            // Limit to generating up to 50 hints at a time
            List<HintResult> hints = solveWithBacktracking(workingGrid, workingPossible, isFixed, 50);
            
            // Add hints to the queue
            if (hints != null && !hints.isEmpty()) {
                hintQueue.addAll(hints);
            }
        }
    }
    
    /**
     * Deep copy the possibilities array
     */
    private boolean[][][] deepCopyPossible(boolean[][][] original) {
        boolean[][][] copy = new boolean[9][9][9];
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                System.arraycopy(original[i][j], 0, copy[i][j], 0, 9);
            }
        }
        return copy;
    }
    
    /**
     * Solve using backtracking and return the sequence of hints
     * This handles naked pairs by trying both possibilities
     * @param maxHints Maximum number of hints to generate
     */
    private List<HintResult> solveWithBacktracking(int[][] grid, boolean[][][] possible, boolean[][] isFixed, int maxHints) {
        List<HintResult> hints = new ArrayList<>();
        
        // First, fill in all obvious cells (only one possibility)
        boolean progress = true;
        while (progress) {
            progress = false;
            
            // Find cells with only one possibility
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    if (grid[i][j] == 0 && !isFixed[i][j]) {
                        int[] poss = getPossibilities(possible, i, j);
                        if (poss.length == 1) {
                            // Only one possibility - this is a hint
                            int num = poss[0];
                            grid[i][j] = num;
                            updateConstraintsForGrid(grid, possible, i, j, num);
                            hints.add(new HintResult(i, j, num));
                            progress = true;
                        }
                    }
                }
            }
            
            // Also check for hidden singles (number can only go in one place in a unit)
            if (!progress) {
                progress = findHiddenSingles(grid, possible, isFixed, hints);
            }
        }
        
        // If we've generated enough hints, return what we have
        if (hints.size() >= maxHints) {
            return hints;
        }
        
        // If we've filled everything, we're done
        if (isComplete(grid)) {
            if (isValidSolution(grid)) {
                return hints;
            }
            return null; // Invalid solution
        }
        
        // Now we need to handle cases with multiple possibilities (like naked pairs)
        // Find the cell with the fewest possibilities
        int minRow = -1, minCol = -1;
        int minPoss = 10;
        
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] == 0 && !isFixed[i][j]) {
                    int[] poss = getPossibilities(possible, i, j);
                    if (poss.length > 0 && poss.length < minPoss) {
                        minPoss = poss.length;
                        minRow = i;
                        minCol = j;
                    }
                }
            }
        }
        
        // If we found a cell with multiple possibilities, try each one
        if (minRow >= 0 && minCol >= 0) {
            int[] poss = getPossibilities(possible, minRow, minCol);
            
            // Try each possibility
            for (int num : poss) {
                // Create a copy for this branch
                int[][] branchGrid = deepCopyGrid(grid);
                boolean[][][] branchPossible = deepCopyPossible(possible);
                
                // Try placing this number
                branchGrid[minRow][minCol] = num;
                updateConstraintsForGrid(branchGrid, branchPossible, minRow, minCol, num);
                
                // Create the hint list for this branch
                List<HintResult> branchHints = new ArrayList<>(hints);
                branchHints.add(new HintResult(minRow, minCol, num));
                
                // Recursively solve from here
                List<HintResult> solution = solveWithBacktracking(branchGrid, branchPossible, isFixed, maxHints);
                
                // If this branch leads to a solution, use it
                if (solution != null && !solution.isEmpty()) {
                    // Prepend our current hints and the choice we made
                    List<HintResult> fullSolution = new ArrayList<>(hints);
                    fullSolution.add(new HintResult(minRow, minCol, num));
                    fullSolution.addAll(solution);
                    
                    // If we got more hints, return them
                    if (fullSolution.size() > hints.size()) {
                        return fullSolution;
                    }
                }
            }
        }
        
        // If we get here, no valid solution was found from this state
        // Return the hints we have so far (they're still valid)
        return hints.isEmpty() ? null : hints;
    }
    
    /**
     * Find hidden singles and add them as hints
     */
    private boolean findHiddenSingles(int[][] grid, boolean[][][] possible, boolean[][] isFixed, List<HintResult> hints) {
        boolean found = false;
        
        // Check each number 1-9
        for (int num = 1; num <= 9; num++) {
            int numIndex = num - 1;
            
            // Check rows
            for (int row = 0; row < 9; row++) {
                int count = 0;
                int col = -1;
                for (int j = 0; j < 9; j++) {
                    if (grid[row][j] == 0 && !isFixed[row][j] && possible[row][j][numIndex]) {
                        count++;
                        col = j;
                    }
                }
                if (count == 1 && grid[row][col] == 0) {
                    grid[row][col] = num;
                    updateConstraintsForGrid(grid, possible, row, col, num);
                    hints.add(new HintResult(row, col, num));
                    found = true;
                }
            }
            
            // Check columns
            for (int c = 0; c < 9; c++) {
                int count = 0;
                int r = -1;
                for (int i = 0; i < 9; i++) {
                    if (grid[i][c] == 0 && !isFixed[i][c] && possible[i][c][numIndex]) {
                        count++;
                        r = i;
                    }
                }
                if (count == 1 && grid[r][c] == 0) {
                    grid[r][c] = num;
                    updateConstraintsForGrid(grid, possible, r, c, num);
                    hints.add(new HintResult(r, c, num));
                    found = true;
                }
            }
            
            // Check boxes
            for (int boxRow = 0; boxRow < 3; boxRow++) {
                for (int boxCol = 0; boxCol < 3; boxCol++) {
                    int count = 0;
                    int r = -1, c = -1;
                    for (int i = boxRow * 3; i < boxRow * 3 + 3; i++) {
                        for (int j = boxCol * 3; j < boxCol * 3 + 3; j++) {
                            if (grid[i][j] == 0 && !isFixed[i][j] && possible[i][j][numIndex]) {
                                count++;
                                r = i;
                                c = j;
                            }
                        }
                    }
                    if (count == 1 && grid[r][c] == 0) {
                        grid[r][c] = num;
                        updateConstraintsForGrid(grid, possible, r, c, num);
                        hints.add(new HintResult(r, c, num));
                        found = true;
                    }
                }
            }
        }
        
        return found;
    }
    
    /**
     * Update constraints for a specific grid and possibilities array
     */
    private void updateConstraintsForGrid(int[][] grid, boolean[][][] possible, int row, int col, int num) {
        // Mark the filled cell as having no possibilities
        for (int n = 0; n < 9; n++) {
            possible[row][col][n] = false;
        }
        
        // Update constraints for other cells in the same row
        for (int j = 0; j < 9; j++) {
            if (j != col && grid[row][j] == 0) {
                possible[row][j][num - 1] = false;
            }
        }
        
        // Update constraints for other cells in the same column
        for (int i = 0; i < 9; i++) {
            if (i != row && grid[i][col] == 0) {
                possible[i][col][num - 1] = false;
            }
        }
        
        // Mark this number as impossible for all other cells in the same 3x3 block
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int i = boxRow; i < boxRow + 3; i++) {
            for (int j = boxCol; j < boxCol + 3; j++) {
                if ((i != row || j != col) && grid[i][j] == 0) {
                    possible[i][j][num - 1] = false;
                }
            }
        }
    }
    
    /**
     * Get array of possible numbers for a cell
     */
    private int[] getPossibilities(boolean[][][] possible, int row, int col) {
        int count = 0;
        for (int n = 0; n < 9; n++) {
            if (possible[row][col][n]) {
                count++;
            }
        }
        
        int[] result = new int[count];
        int index = 0;
        for (int n = 0; n < 9; n++) {
            if (possible[row][col][n]) {
                result[index++] = n + 1; // Convert to 1-9
            }
        }
        return result;
    }
    
    /**
     * Check if the grid is complete
     */
    private boolean isComplete(int[][] grid) {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] == 0) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Check if a grid is a valid solution
     */
    private boolean isValidSolution(int[][] grid) {
        if (!isComplete(grid)) {
            return false;
        }
        
        // Check rows
        for (int i = 0; i < 9; i++) {
            boolean[] seen = new boolean[10];
            for (int j = 0; j < 9; j++) {
                int num = grid[i][j];
                if (num < 1 || num > 9 || seen[num]) {
                    return false;
                }
                seen[num] = true;
            }
        }
        
        // Check columns
        for (int j = 0; j < 9; j++) {
            boolean[] seen = new boolean[10];
            for (int i = 0; i < 9; i++) {
                int num = grid[i][j];
                if (num < 1 || num > 9 || seen[num]) {
                    return false;
                }
                seen[num] = true;
            }
        }
        
        // Check boxes
        for (int boxRow = 0; boxRow < 3; boxRow++) {
            for (int boxCol = 0; boxCol < 3; boxCol++) {
                boolean[] seen = new boolean[10];
                for (int i = boxRow * 3; i < boxRow * 3 + 3; i++) {
                    for (int j = boxCol * 3; j < boxCol * 3 + 3; j++) {
                        int num = grid[i][j];
                        if (num < 1 || num > 9 || seen[num]) {
                            return false;
                        }
                        seen[num] = true;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Find a hint - returns the next pre-computed hint from the queue
     * Thread-safe for multi-server distribution
     */
    public HintResult findHint() {
        synchronized (lock) {
            return hintQueue.poll();
        }
    }
    
    /**
     * Result class for hint information
     * Implements Serializable for network transmission
     */
    public static class HintResult implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        public final int row;
        public final int col;
        public final int num;
        
        public HintResult(int row, int col, int num) {
            this.row = row;
            this.col = col;
            this.num = num;
        }
    }
    
    /**
     * Update the grid reference and regenerate hints
     * Called when grid changes (e.g., when a hint is used)
     */
    public void updateGrid(int[][] newGrid) {
        synchronized (lock) {
            this.grid = deepCopyGrid(newGrid);
            initializeConstraints();
            generateAllHints();
        }
    }
}
