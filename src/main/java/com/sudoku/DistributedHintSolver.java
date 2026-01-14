package com.sudoku;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

//Distributed hint solver that distributes backtracking work across multiple servers
// Uses socket-based communication for coordination
 
public class DistributedHintSolver implements HintSolverInterface {
    private int[][] grid;
    private boolean[][] isFixed;
    private final Queue<HintSolver.HintResult> hintQueue;
    private final List<ServerConnection> servers;
    private final Object lock = new Object();
    
    //Represents a connection to a remote hint server
     
    private static class ServerConnection {
        final String host;
        final int port;
        final String username; // For SSH if needed
        
        ServerConnection(String host, int port, String username) {
            this.host = host;
            this.port = port;
            this.username = username;
        }
        
        @Override
        public String toString() {
            return username != null ? username + "@" + host + ":" + port : host + ":" + port;
        }
    }
    
    /**
     * Create a distributed hint solver with a list of server addresses
     * @param grid The current Sudoku grid
     * @param isFixed Which cells are fixed (part of original puzzle)
     * @param serverConfigs List of server configurations in format "host:port" or "username@host:port"
     */
    public DistributedHintSolver(int[][] grid, boolean[][] isFixed, List<String> serverConfigs) {
        this.grid = deepCopyGrid(grid);
        this.isFixed = deepCopyBooleanGrid(isFixed);
        this.hintQueue = new ConcurrentLinkedQueue<>();
        this.servers = new ArrayList<>();
        
        // Parse server configurations
        for (String config : serverConfigs) {
            String[] parts = config.split("@");
            if (parts.length == 2) {
                // Format: username@host:port
                String username = parts[0];
                String[] hostPort = parts[1].split(":");
                if (hostPort.length == 2) {
                    servers.add(new ServerConnection(hostPort[0], Integer.parseInt(hostPort[1]), username));
                }
            } else {
                // Format: host:port
                String[] hostPort = config.split(":");
                if (hostPort.length == 2) {
                    servers.add(new ServerConnection(hostPort[0], Integer.parseInt(hostPort[1]), null));
                }
            }
        }
        
        if (servers.isEmpty()) {
            throw new IllegalArgumentException("At least one server must be configured");
        }
        
        initializeConstraints();
        generateAllHints();
    }
    
    /**
     * Deep copy a grid
     */
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
    
   
    //Initialize constraints (same as HintSolver)
     
    private boolean[][][] initializeConstraints() {
        boolean[][][] possible = new boolean[9][9][9];
        
        // Initially, all numbers are possible for all empty cells
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
        
        // Apply constraints from all filled cells
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] != 0) {
                    updateConstraintsForGrid(grid, possible, i, j, grid[i][j]);
                }
            }
        }
        
        return possible;
    }
    
    //Update constraints for a grid
    
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
    
    
     //Generate hints by distributing work across servers
     
    private void generateAllHints() {
        synchronized (lock) {
            hintQueue.clear();
            
            // First, fill in obvious hints locally
            boolean[][][] possible = initializeConstraints();
            List<HintSolver.HintResult> localHints = findObviousHints(grid, possible, isFixed);
            
            // Apply local hints to get current state
            int[][] currentGrid = deepCopyGrid(grid);
            for (HintSolver.HintResult hint : localHints) {
                currentGrid[hint.row][hint.col] = hint.num;
                updateConstraintsForGrid(currentGrid, possible, hint.row, hint.col, hint.num);
            }
            
            // If we've filled everything or have enough hints, return
            if (isComplete(currentGrid) || localHints.size() >= 50) {
                hintQueue.addAll(localHints);
                return;
            }
            
            // Find branching points (cells with multiple possibilities)
            List<BranchPoint> branchPoints = findBranchPoints(currentGrid, possible, isFixed);
            
            if (branchPoints.isEmpty()) {
                hintQueue.addAll(localHints);
                return;
            }
            
            // Distribute branches across servers
            BranchPoint firstBranch = branchPoints.get(0);
            int[] possibilities = getPossibilities(possible, firstBranch.row, firstBranch.col);
            
            // Distribute each possibility to a different server
            ExecutorService executor = Executors.newFixedThreadPool(servers.size());
            List<Future<List<HintSolver.HintResult>>> futures = new ArrayList<>();
            
            for (int i = 0; i < Math.min(possibilities.length, servers.size()); i++) {
                int num = possibilities[i];
                ServerConnection server = servers.get(i % servers.size());
                final int number = num;
                
                futures.add(executor.submit(() -> {
                    return solveBranchOnServer(server, currentGrid, possible, isFixed, 
                                             firstBranch.row, firstBranch.col, number, localHints);
                }));
            }
            
            // Collect results from all servers
            List<HintSolver.HintResult> bestSolution = null;
            int maxHints = localHints.size();
            
            for (Future<List<HintSolver.HintResult>> future : futures) {
                try {
                    List<HintSolver.HintResult> result = future.get(30, TimeUnit.SECONDS);
                    if (result != null && result.size() > maxHints) {
                        bestSolution = result;
                        maxHints = result.size();
                    }
                } catch (Exception e) {
                    System.err.println("Error getting result from server: " + e.getMessage());
                }
            }
            
            executor.shutdown();
            
            // Add the best solution to the queue
            if (bestSolution != null && !bestSolution.isEmpty()) {
                hintQueue.addAll(bestSolution);
            } else {
                hintQueue.addAll(localHints);
            }
        }
    }
    
    /**
     * Find obvious hints (cells with only one possibility)
     */
    private List<HintSolver.HintResult> findObviousHints(int[][] grid, boolean[][][] possible, boolean[][] isFixed) {
        List<HintSolver.HintResult> hints = new ArrayList<>();
        boolean progress = true;
        
        while (progress) {
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
        
        return hints;
    }
    
    /**
     * Find branch points (cells with multiple possibilities)
     */
    private List<BranchPoint> findBranchPoints(int[][] grid, boolean[][][] possible, boolean[][] isFixed) {
        List<BranchPoint> branches = new ArrayList<>();
        
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (grid[i][j] == 0 && !isFixed[i][j]) {
                    int[] poss = getPossibilities(possible, i, j);
                    if (poss.length > 1 && poss.length <= servers.size()) {
                        branches.add(new BranchPoint(i, j, poss.length));
                    }
                }
            }
        }
        
        // Sort by number of possibilities (fewer = better)
        branches.sort(Comparator.comparingInt(b -> b.possibilityCount));
        return branches;
    }
    
    /**
     * Solve a branch on a remote server
     */
    private List<HintSolver.HintResult> solveBranchOnServer(ServerConnection server, int[][] grid, 
                                                           boolean[][][] possible, boolean[][] isFixed,
                                                           int row, int col, int num, 
                                                           List<HintSolver.HintResult> baseHints) {
        try {
            // Connect to server
            Socket socket = new Socket(server.host, server.port);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            
            // Send work request
            WorkRequest request = new WorkRequest();
            request.grid = deepCopyGrid(grid);
            request.isFixed = deepCopyBooleanGrid(isFixed);
            request.branchRow = row;
            request.branchCol = col;
            request.branchNum = num;
            request.baseHints = new ArrayList<>(baseHints);
            
            out.writeObject(request);
            out.flush();
            
            // Receive result
            WorkResponse response = (WorkResponse) in.readObject();
            
            socket.close();
            
            return response.hints;
        } catch (Exception e) {
            System.err.println("Error communicating with server " + server + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get possibilities for a cell
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
     * Check if grid is complete
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
     * Find a hint from the queue
     */
    public HintSolver.HintResult findHint() {
        synchronized (lock) {
            return hintQueue.poll();
        }
    }
    
    /**
     * Update grid and regenerate hints only if queue is empty
     * This avoids unnecessary server calls when we still have cached hints
     */
    public void updateGrid(int[][] newGrid) {
        synchronized (lock) {
            this.grid = deepCopyGrid(newGrid);
            // Only regenerate if we've used up all the hints we already have
            if (hintQueue.isEmpty()) {
                generateAllHints();
            }
            // Otherwise, keep using the cached hints from the queue
        }
    }
    
   
    //  Update constraints when a number is placed
    //  For distributed solver, this is handled by updateGrid() regeneration,
    //  but we provide this method for interface compatibility
     
    public void updateConstraints(int row, int col, int num) {
        // Constraints are automatically updated when updateGrid() is called
        // This method exists for interface compatibility
    }
    
    
     //Represents a branching point in the search tree
    
    private static class BranchPoint {
        final int row;
        final int col;
        final int possibilityCount;
        
        BranchPoint(int row, int col, int possibilityCount) {
            this.row = row;
            this.col = col;
            this.possibilityCount = possibilityCount;
        }
    }
    
    
    //Work request sent to remote server
   
    public static class WorkRequest implements Serializable {
        public int[][] grid;
        public boolean[][] isFixed;
        public int branchRow;
        public int branchCol;
        public int branchNum;
        public List<HintSolver.HintResult> baseHints;
    }
    

     //Work response from remote server
   
    public static class WorkResponse implements Serializable {
        public List<HintSolver.HintResult> hints;
    }
}

