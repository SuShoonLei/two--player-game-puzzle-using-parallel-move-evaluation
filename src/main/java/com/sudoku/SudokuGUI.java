package com.sudoku;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.awt.event.*;
import java.util.concurrent.CompletableFuture;


public class SudokuGUI extends JFrame {
    private SudokuGame game;
    private HintSolverInterface hintSolver;
    private JTextField[][] cells;
    private JButton resetButton;
    private JButton newGameButton;
    private JButton hintButton;
    private JLabel statusLabel;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean[][] isHint; // Track which cells are hints (green numbers)
    
    private static final Color FIXED_CELL_BG = new Color(240, 240, 240);
    private static final Color SELECTED_CELL_BG = new Color(240, 240, 240);
    private static final Color ERROR_CELL_BG = new Color(255, 200, 200);
    private static final Color NORMAL_CELL_BG = Color.WHITE;
    private static final Color HINT_COLOR = new Color(0, 150, 0); // Green for hints
    
    public SudokuGUI() {
        setTitle("Sudoku Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Create UI components first
        createUI();
        
        // Initialize with a puzzle after UI is ready
        loadNewGame();
        
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }
    
    private void createUI() {
        // Main game panel
        JPanel gamePanel = new JPanel(new GridLayout(3, 3, 5, 5));
        gamePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        gamePanel.setBackground(Color.BLACK);
        
        cells = new JTextField[9][9];
        
        // Create 9 sub-grids (3x3 boxes)
        for (int box = 0; box < 9; box++) {
            JPanel subGrid = new JPanel(new GridLayout(3, 3, 2, 2));
            subGrid.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            subGrid.setBackground(Color.BLACK);
            
            int boxRow = (box / 3) * 3;
            int boxCol = (box % 3) * 3;
            
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    int row = boxRow + i;
                    int col = boxCol + j;
                    
                    JTextField cell = new JTextField();
                    cell.setHorizontalAlignment(JTextField.CENTER);
                    cell.setFont(new Font("Arial", Font.BOLD, 20));
                    cell.setPreferredSize(new Dimension(50, 50));
                    cell.setEditable(false); // Prevent direct text editing, we'll handle input via key events
                    
                    // Add mouse listener for selection
                    final int r = row;
                    final int c = col;
                    cell.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            selectCell(r, c);
                        }
                    });
                    
                    // Add key listener for input
                    cell.addKeyListener(new KeyAdapter() {
                        @Override
                        public void keyPressed(KeyEvent e) {
                            handleKeyPress(r, c, e);
                        }
                    });
                    
                    // Add focus listener
                    cell.addFocusListener(new FocusAdapter() {
                        @Override
                        public void focusGained(FocusEvent e) {
                            selectCell(r, c);
                        }
                    });
                    
                    cells[row][col] = cell;
                    subGrid.add(cell);
                }
            }
            
            gamePanel.add(subGrid);
        }
        
        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> resetGame());
        resetButton.setFont(new Font("Arial", Font.PLAIN, 14));
        
        newGameButton = new JButton("New Game");
        newGameButton.addActionListener(e -> loadNewGame());
        newGameButton.setFont(new Font("Arial", Font.PLAIN, 14));
        
        hintButton = new JButton("Hint");
        hintButton.addActionListener(e -> showHint());
        hintButton.setFont(new Font("Arial", Font.PLAIN, 14));
        hintButton.setBackground(new Color(144, 238, 144)); // Light green
        
        statusLabel = new JLabel("Select a cell and enter a number (1-9)");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        
        controlPanel.add(resetButton);
        controlPanel.add(newGameButton);
        controlPanel.add(hintButton);
        controlPanel.add(statusLabel);
        
        add(gamePanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);
        
        updateDisplay();
    }
    
    private void loadNewGame() {
        statusLabel.setText("Loading new puzzle...");
        statusLabel.setForeground(Color.BLUE);
        
        // Load puzzle asynchronously to avoid blocking UI
        CompletableFuture.supplyAsync(() -> {
            try {
                return ApiHandler.fetchNewPuzzle();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "Error loading puzzle: " + e.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }).thenAccept(puzzle -> {
            if (puzzle != null) {
                SwingUtilities.invokeLater(() -> {
                    game = new SudokuGame(puzzle);
                    boolean[][] fixed = new boolean[9][9];
                    for (int i = 0; i < 9; i++) {
                        for (int j = 0; j < 9; j++) {
                            fixed[i][j] = game.isFixed(i, j);
                        }
                    }
                    // hintSolver = new HintSolver(game.getGrid(), fixed);// replace this with the ones from server if running on server(s)
                    List<String> servers = Arrays.asList(
                        "pi.cs.oswego.edu:8888",
                        "rho.cs.oswego.edu:8889"
                    );
                    hintSolver = new DistributedHintSolver(game.getGrid(), fixed, servers);


                    isHint = new boolean[9][9];
                    updateDisplay();
                    statusLabel.setText("New puzzle loaded! Select a cell and enter a number (1-9)");
                    statusLabel.setForeground(Color.BLACK);
                });
            }
        });
    }
    
    private void resetGame() {
        if (game != null) {
            game.reset();
            // Reset hint solver
            if (hintSolver != null) {
                boolean[][] fixed = new boolean[9][9];
                for (int i = 0; i < 9; i++) {
                    for (int j = 0; j < 9; j++) {
                        fixed[i][j] = game.isFixed(i, j);
                    }
                }
                hintSolver.updateGrid(game.getGrid());
            }
            // Clear all hints
            if (isHint != null) {
                isHint = new boolean[9][9];
            }
            updateDisplay();
            statusLabel.setText("Game reset. Select a cell and enter a number (1-9)");
            statusLabel.setForeground(Color.BLACK);
        }
    }
    
    private void showHint() {
        if (game == null || hintSolver == null) {
            statusLabel.setText("No puzzle loaded!");
            statusLabel.setForeground(Color.RED);
            return;
        }
        
        statusLabel.setText("Finding hint...");
        statusLabel.setForeground(Color.BLUE);
        
        // Find hint asynchronously to avoid blocking UI
        CompletableFuture.supplyAsync(() -> {
            return hintSolver.findHint();
        }).thenAccept(hint -> {
            SwingUtilities.invokeLater(() -> {
                if (hint != null) {
                    // Place the hint number
                    if (game.placeNumber(hint.row, hint.col, hint.num)) {
                        // Mark as hint
                        isHint[hint.row][hint.col] = true;
                        // Update hint solver grid reference to ensure it's in sync
                        hintSolver.updateGrid(game.getGrid());
                        // Update hint solver constraints (this will also mark the cell as filled)
                        hintSolver.updateConstraints(hint.row, hint.col, hint.num);
                        updateCellDisplay(hint.row, hint.col);
                        checkWin();
                        statusLabel.setText("Hint: " + hint.num + " at row " + (hint.row + 1) + ", col " + (hint.col + 1));
                        statusLabel.setForeground(Color.GREEN);
                    } else {
                        statusLabel.setText("Could not place hint. Try again.");
                        statusLabel.setForeground(Color.RED);
                    }
                } else {
                    statusLabel.setText("No hint available. Puzzle may be unsolvable or complete.");
                    statusLabel.setForeground(Color.ORANGE);
                }
            });
        });
    }
    
    private void selectCell(int row, int col) {
        // Deselect previous cell
        if (selectedRow >= 0 && selectedCol >= 0) {
            updateCellDisplay(selectedRow, selectedCol);
        }
        
        selectedRow = row;
        selectedCol = col;
        updateCellDisplay(row, col);
        cells[row][col].requestFocus();
    }
    
    private void handleKeyPress(int row, int col, KeyEvent e) {
        if (game == null || game.isFixed(row, col)) {
            e.consume();
            return;
        }
        
        char keyChar = e.getKeyChar();
        int keyCode = e.getKeyCode();
        
        if (keyChar >= '1' && keyChar <= '9') {
            e.consume(); // Prevent default text field behavior
            int num = keyChar - '0';
            if (game.placeNumber(row, col, num)) {
                // Clear hint status if user manually enters a number
                isHint[row][col] = false;
                // Update hint solver grid reference and constraints
                if (hintSolver != null) {
                    hintSolver.updateGrid(game.getGrid());
                    hintSolver.updateConstraints(row, col, num);
                }
                updateCellDisplay(row, col);
                checkWin();
            } else {
                // Invalid move - show error briefly
                cells[row][col].setBackground(ERROR_CELL_BG);
                Timer timer = new Timer(500, evt -> {
                    updateCellDisplay(row, col);
                });
                timer.setRepeats(false);
                timer.start();
                statusLabel.setText("Invalid move! Try again.");
                statusLabel.setForeground(Color.RED);
            }
        } else if (keyChar == '0' || keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_DELETE) {
            e.consume(); // Prevent default text field behavior
            game.clearCell(row, col);
            // Clear hint status
            isHint[row][col] = false;
            // Reinitialize hint solver to recalculate constraints
            if (hintSolver != null) {
                boolean[][] fixed = new boolean[9][9];
                for (int i = 0; i < 9; i++) {
                    for (int j = 0; j < 9; j++) {
                        fixed[i][j] = game.isFixed(i, j);
                    }
                }
                hintSolver.updateGrid(game.getGrid());
            }
            updateCellDisplay(row, col);
            statusLabel.setText("Cell cleared. Select a cell and enter a number (1-9)");
            statusLabel.setForeground(Color.BLACK);
        } else if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN ||
                   keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT) {
            e.consume(); // Prevent default text field behavior
            // Handle arrow keys for navigation
            int newRow = row;
            int newCol = col;
            
            switch (keyCode) {
                case KeyEvent.VK_UP:
                    newRow = Math.max(0, row - 1);
                    break;
                case KeyEvent.VK_DOWN:
                    newRow = Math.min(8, row + 1);
                    break;
                case KeyEvent.VK_LEFT:
                    newCol = Math.max(0, col - 1);
                    break;
                case KeyEvent.VK_RIGHT:
                    newCol = Math.min(8, col + 1);
                    break;
            }
            
            selectCell(newRow, newCol);
        }
    }
    
    private void updateDisplay() {
        if (game == null) {
            return;
        }
        
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                updateCellDisplay(i, j);
            }
        }
    }
    
    private void updateCellDisplay(int row, int col) {
        if (game == null) {
            return;
        }
        
        JTextField cell = cells[row][col];
        int value = game.getValue(row, col);
        
        if (value == 0) {
            cell.setText("");
        } else {
            cell.setText(String.valueOf(value));
        }
        
        // Set background color
        // Keep all cells non-editable - input is handled via key events
        cell.setEditable(false);
        
        if (row == selectedRow && col == selectedCol) {
            cell.setBackground(SELECTED_CELL_BG);
        } else if (game.isFixed(row, col)) {
            cell.setBackground(FIXED_CELL_BG);
        } else {
            cell.setBackground(NORMAL_CELL_BG);
        }
        
        // Set text color
        if (game.isFixed(row, col)) {
            cell.setForeground(Color.BLACK);
        } else if (isHint != null && isHint[row][col]) {
            cell.setForeground(HINT_COLOR); // Green for hints
        } else {
            cell.setForeground(Color.BLUE);
        }
    }
    
    private void checkWin() {
        if (game != null && game.isWon()) {
            statusLabel.setText("Congratulations! You won! Loading new puzzle...");
            statusLabel.setForeground(Color.GREEN);
            
            // Show win message and load new game after a delay
            Timer timer = new Timer(2000, evt -> {
                loadNewGame();
            });
            timer.setRepeats(false);
            timer.start();
        } else {
            statusLabel.setText("Select a cell and enter a number (1-9)");
            statusLabel.setForeground(Color.BLACK);
        }
    }
}

