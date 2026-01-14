# Sudoku Game

A Java-based Sudoku game with a graphical user interface that fetches new puzzles from the Dosuku API.

## Features

- **GUI Interface**: Clean and intuitive Swing-based interface
- **API Integration**: Automatically fetches new puzzles from `https://sudoku-api.vercel.app/api/dosuku`
- **Game Logic**: Full Sudoku validation and win detection
- **Hint System**: 
  - Smart hint finder using backtracking
  - Handles naked pairs and complex situations
  - **Distributed Mode**: Distribute hint generation across multiple servers
- **User-Friendly**: 
  - Click cells to select them
  - Use number keys (1-9) to enter values
  - Use arrow keys to navigate
  - Clear cells with Delete/Backspace or 0
  - Fixed cells (from puzzle) are highlighted and cannot be modified
- **Auto New Game**: Automatically loads a new puzzle when you win
- **Reset Function**: Reset current puzzle to original state

## Project Structure

```
Project3/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── sudoku/
│       │           ├── Main.java          # Entry point
│       │           ├── SudokuGUI.java      # GUI implementation
│       │           ├── SudokuGame.java     # Game logic
│       │           └── ApiHandler.java    # API integration
│       └── resources/
├── pom.xml                                 # Maven configuration
└── README.md
```

## Requirements

- Java 11 or higher
- Maven 3.6 or higher
- Internet connection (for fetching puzzles)

## Building and Running

### Using Maven:

1. **Compile the project:**
   ```bash
   mvn compile
   ```

2. **Run the application:**
   ```bash
   mvn exec:java -Dexec.mainClass="com.sudoku.Main"
   ```

3. **Create a JAR file:**
   ```bash
   mvn package
   java -jar target/sudoku-game-1.0.0.jar
   ```

### Manual Compilation (without Maven):

1. **Download JSON library:**
   - Download `json-20231013.jar` from https://mvnrepository.com/artifact/org.json/json
   - Place it in a `lib` folder

2. **Compile:**
   ```bash
   javac -cp "lib/json-20231013.jar" -d out src/main/java/com/sudoku/*.java
   ```

3. **Run:**
   ```bash
   java -cp "out:lib/json-20231013.jar" com.sudoku.Main
   ```

## How to Play

1. **Start the game**: The application will automatically load a puzzle when it starts
2. **Select a cell**: Click on any empty cell (non-gray cells)
3. **Enter a number**: Type a number from 1-9
4. **Navigate**: Use arrow keys to move between cells
5. **Clear a cell**: Press Delete, Backspace, or 0
6. **Reset**: Click "Reset" to clear all your entries and start over
7. **New Game**: Click "New Game" to fetch a completely new puzzle
8. **Win**: When you complete the puzzle correctly, a new puzzle will automatically load after 2 seconds

## API Integration

The game fetches puzzles from the Dosuku API:
- **Endpoint**: `https://sudoku-api.vercel.app/api/dosuku`
- **Format**: JSON response with a 9x9 grid (0 = empty cell)
- **Auto-fetch**: New puzzles are fetched when:
  - The game starts
  - You win a puzzle
  - You click "New Game"

## Distributed Hint Solver

The project includes a distributed hint solver that can split work across multiple servers. This is useful for handling complex puzzles with many branching points (like naked pairs).

### Quick Start (Distributed Mode)

1. **Start servers on remote machines:**
   ```bash
   # On server1
   ssh user@server1.cs.university.edu
   cd ~/sudoku
   java -cp target/classes com.sudoku.HintServer 8888
   
   # On server2 (in another terminal)
   ssh user@server2.cs.university.edu
   cd ~/sudoku
   java -cp target/classes com.sudoku.HintServer 8889
   ```

2. **Configure the GUI to use distributed solver** (modify `SudokuGUI.java`):
   ```java
   List<String> servers = Arrays.asList(
       "server1.cs.university.edu:8888",
       "server2.cs.university.edu:8889"
   );
   hintSolver = new DistributedHintSolver(game.getGrid(), fixed, servers);
   ```

3. **Run the game** - hints will now be generated using distributed servers!

See `DISTRIBUTED_SETUP.md` for detailed setup instructions.

### How Distributed Solving Works

1. **Local Processing**: Obvious hints (single possibility) are found locally
2. **Branch Detection**: When encountering cells with multiple possibilities, these become branch points
3. **Work Distribution**: Each possibility is sent to a different server
4. **Parallel Solving**: Each server explores its branch using backtracking
5. **Result Collection**: The client collects results and uses the best solution

## Notes

- Fixed cells (from the original puzzle) are displayed with a gray background and cannot be modified
- Your entries are displayed in blue
- Hints are displayed in green
- Invalid moves will briefly show a red background
- Selected cells are highlighted in light blue

