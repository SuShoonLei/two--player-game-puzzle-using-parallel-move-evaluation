package com.sudoku;

/**
 * Common interface for hint solvers (local and distributed)
 */
public interface HintSolverInterface {
    /**
     * Find the next hint
     */
    HintSolver.HintResult findHint();
    
    /**
     * Update the grid when it changes
     */
    void updateGrid(int[][] newGrid);
    
    /**
     * Update constraints when a number is placed
     */
    void updateConstraints(int row, int col, int num);
}

