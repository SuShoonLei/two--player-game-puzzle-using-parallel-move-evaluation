package com.sudoku;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server that runs on remote machines to solve hint branches
 * Listens for work requests and returns hint solutions
 */
public class HintServer {
    private final int port;
    private volatile boolean running = true;
    
    public HintServer(int port) {
        this.port = port;
    }
    
    /**
     * Start the server
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Hint Server started on port " + port);
            System.out.println("Waiting for connections...");
            
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                
                // Handle each client in a separate thread
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle a client connection
     */
    private void handleClient(Socket socket) {
        try {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            
            // Receive work request
            DistributedHintSolver.WorkRequest request = (DistributedHintSolver.WorkRequest) in.readObject();
            
            System.out.println("Received work request: branch at (" + request.branchRow + "," + 
                             request.branchCol + ") with number " + request.branchNum);
            
            // Solve the branch
            List<HintSolver.HintResult> hints = solveBranch(request);
            
            // Send response
            DistributedHintSolver.WorkResponse response = new DistributedHintSolver.WorkResponse();
            response.hints = hints;
            
            out.writeObject(response);
            out.flush();
            
            System.out.println("Sent response with " + (hints != null ? hints.size() : 0) + " hints");
            
            socket.close();
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Solve a specific branch of the backtracking tree
     */
    private List<HintSolver.HintResult> solveBranch(DistributedHintSolver.WorkRequest request) {
        // Create a working copy
        int[][] grid = deepCopyGrid(request.grid);
        boolean[][] isFixed = request.isFixed;
        boolean[][][] possible = initializeConstraints(grid);
        
        // Apply base hints
        List<HintSolver.HintResult> hints = new ArrayList<>(request.baseHints);
        for (HintSolver.HintResult hint : request.baseHints) {
            grid[hint.row][hint.col] = hint.num;
            updateConstraintsForGrid(grid, possible, hint.row, hint.col, hint.num);
        }
        
        // Apply the branch decision
        grid[request.branchRow][request.branchCol] = request.branchNum;
        updateConstraintsForGrid(grid, possible, request.branchRow, request.branchCol, request.branchNum);
        hints.add(new HintSolver.HintResult(request.branchRow, request.branchCol, request.branchNum));
        
        // Continue solving from this branch
        return solveWithBacktracking(grid, possible, isFixed, hints, 50);
    }
    
    /**
     * Solve using backtracking (similar to HintSolver)
     */
    private List<HintSolver.HintResult> solveWithBacktracking(int[][] grid, boolean[][][] possible, 
                                                              boolean[][] isFixed,
                                                              List<HintSolver.HintResult> hints, 
                                                              int maxHints) {
        // Fill in obvious cells
        boolean progress = true;
        while (progress && hints.size() < maxHints) {
            progress = false;
            
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    if (grid[i][j] == 0 && !isFixed[i][j]) {
                        int[] poss = getPossibilities(possible, i, j);
                        if (poss.length == 1) {
                            int num = poss[0];
                            grid[i][j] = num;
                            updateConstraintsForGrid(grid, possible, i, j, num);
                            hints.add(new HintSolver.HintResult(i, j, num));
                            progress = true;
                        }
                    }
                }
            }
        }
        
        if (hints.size() >= maxHints || isComplete(grid)) {
            return hints;
        }
        
        // Find next branch point
        int minRow = -1, minCol = -1, minPoss = 10;
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
        
        // Try first possibility (greedy approach)
        if (minRow >= 0 && minCol >= 0) {
            int[] poss = getPossibilities(possible, minRow, minCol);
            if (poss.length > 0) {
                int[][] branchGrid = deepCopyGrid(grid);
                boolean[][][] branchPossible = deepCopyPossible(possible);
                branchGrid[minRow][minCol] = poss[0];
                updateConstraintsForGrid(branchGrid, branchPossible, minRow, minCol, poss[0]);
                
                List<HintSolver.HintResult> branchHints = new ArrayList<>(hints);
                branchHints.add(new HintSolver.HintResult(minRow, minCol, poss[0]));
                
                List<HintSolver.HintResult> solution = solveWithBacktracking(branchGrid, branchPossible, 
                                                                           isFixed, branchHints, maxHints);
                if (solution != null && solution.size() > hints.size()) {
                    return solution;
                }
            }
        }
        
        return hints;
    }
    
    /**
     * Initialize constraints
     */
    private boolean[][][] initializeConstraints(int[][] grid) {
        boolean[][][] possible = new boolean[9][9][9];
        
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] == 0) {
                    for (int num = 0; num < 9; num++) {
                        possible[i][j][num] = true;
                    }
                } else {
                    for (int num = 0; num < 9; num++) {
                        possible[i][j][num] = false;
                    }
                }
            }
        }
        
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] != 0) {
                    updateConstraintsForGrid(grid, possible, i, j, grid[i][j]);
                }
            }
        }
        
        return possible;
    }
    
    /**
     * Update constraints
     */
    private void updateConstraintsForGrid(int[][] grid, boolean[][][] possible, int row, int col, int num) {
        for (int n = 0; n < 9; n++) {
            possible[row][col][n] = false;
        }
        
        for (int j = 0; j < 9; j++) {
            if (j != col && grid[row][j] == 0) {
                possible[row][j][num - 1] = false;
            }
        }
        
        for (int i = 0; i < 9; i++) {
            if (i != row && grid[i][col] == 0) {
                possible[i][col][num - 1] = false;
            }
        }
        
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
     * Get possibilities
     */
    private int[] getPossibilities(boolean[][][] possible, int row, int col) {
        int count = 0;
        for (int n = 0; n < 9; n++) {
            if (possible[row][col][n]) count++;
        }
        
        int[] result = new int[count];
        int index = 0;
        for (int n = 0; n < 9; n++) {
            if (possible[row][col][n]) {
                result[index++] = n + 1;
            }
        }
        return result;
    }
    
    /**
     * Check if complete
     */
    private boolean isComplete(int[][] grid) {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] == 0) return false;
            }
        }
        return true;
    }
    
    /**
     * Deep copy grid
     */
    private int[][] deepCopyGrid(int[][] original) {
        int[][] copy = new int[9][9];
        for (int i = 0; i < 9; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, 9);
        }
        return copy;
    }
    
    /**
     * Deep copy possibilities
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
     * Stop the server
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Main method to run the server
     */
    public static void main(String[] args) {
        int port = 8888;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        HintServer server = new HintServer(port);
        server.start();
    }
}

