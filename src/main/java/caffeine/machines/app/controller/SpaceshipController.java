package caffeine.machines.app.controller;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class SpaceshipController {

  @PostMapping("/move")
  public Map<String, String> makeMove(@RequestBody GameState gameState) {
    char[][] field = parseField(gameState.getField());
    int[] playerPosition = findPlayer(field);
    char playerDirection = getPlayerDirection(field, playerPosition);

    String move = calculateBestMove(field, playerPosition, playerDirection, gameState.getNarrowingIn());
    return Map.of("move", move);
  }

  private char[][] parseField(List<List<String>> fieldList) {
    char[][] field = new char[fieldList.size()][fieldList.get(0).size()];
    for (int i = 0; i < fieldList.size(); i++) {
      for (int j = 0; j < fieldList.get(i).size(); j++) {
        String cell = fieldList.get(i).get(j);
        field[i][j] = cell.isEmpty() ? '_' : cell.charAt(0);
      }
    }
    return field;
  }

  private int[] findPlayer(char[][] field) {
    for (int i = 0; i < field.length; i++) {
      for (int j = 0; j < field[i].length; j++) {
        if (field[i][j] == 'P') {
          return new int[]{i, j};
        }
      }
    }
    throw new IllegalStateException("Player not found on the field");
  }

  private char getPlayerDirection(char[][] field, int[] position) {
    return field[position[0]][position[1] + 1];
  }

  private String calculateBestMove(char[][] field, int[] playerPosition, char playerDirection, int narrowingIn) {
    int rows = field.length;
    int cols = field[0].length;

    // Get all coins and enemies on the field
    List<int[]> coins = findEntities(field, 'C');
    List<int[]> enemies = findEntities(field, 'E');

    // Avoid narrowing zones
    if (isInNarrowingZone(playerPosition, narrowingIn, rows, cols)) {
      int[] safeZone = {rows / 2, cols / 2};
      return moveToTarget(playerPosition, safeZone, playerDirection, field);
    }

    // Evaluate possible actions
    Action bestAction = evaluateBestAction(field, playerPosition, playerDirection, coins, enemies);
    return bestAction.command;
  }

  private boolean isInNarrowingZone(int[] position, int narrowingIn, int rows, int cols) {
    return position[0] < narrowingIn || position[0] >= rows - narrowingIn ||
        position[1] < narrowingIn || position[1] >= cols - narrowingIn;
  }

  private Action evaluateBestAction(char[][] field, int[] playerPosition, char playerDirection, List<int[]> coins, List<int[]> enemies) {
    Action bestAction = new Action("M", Integer.MIN_VALUE);

    // Evaluate collecting coins
    for (int[] coin : coins) {
      int score = 10 - distance(playerPosition, coin); // Higher score for closer coins
      String move = moveToTarget(playerPosition, coin, playerDirection, field);
      if (!move.equals("X") && score > bestAction.score) { // Avoid invalid moves
        bestAction = new Action(move, score);
      }
    }

    // Evaluate attacking enemies
    for (int[] enemy : enemies) {
      if (enemyInRange(playerPosition, playerDirection, List.of(enemy), field)) {
        int score = 15; // Fixed score for attack
        if (score > bestAction.score) {
          bestAction = new Action("F", score);
        }
      }
    }

    // Evaluate survival (default move)
    String safeMove = findSafeMove(playerPosition, playerDirection, field);
    if (!safeMove.equals("X")) {
      bestAction = new Action(safeMove, 5); // Low score but ensures movement
    }

    return bestAction;
  }

  private String findSafeMove(int[] playerPosition, char playerDirection, char[][] field) {
    int dx = 0, dy = 0;
    switch (playerDirection) {
      case 'N': dx = -1; break;
      case 'S': dx = 1; break;
      case 'E': dy = 1; break;
      case 'W': dy = -1; break;
    }

    int nx = playerPosition[0] + dx;
    int ny = playerPosition[1] + dy;

    // Check if the forward cell is valid
    if (nx >= 0 && nx < field.length && ny >= 0 && ny < field[0].length && field[nx][ny] == '_') {
      return "M";
    }

    // Default to rotation if forward move is blocked
    return "R";
  }

  private String moveToTarget(int[] playerPosition, int[] target, char playerDirection, char[][] field) {
    int dx = target[0] - playerPosition[0];
    int dy = target[1] - playerPosition[1];

    // Avoid obstacles
    if (dx == 0 && dy == 0) return "X"; // Already at the target
    if (field[target[0]][target[1]] == 'A') return "X"; // Avoid asteroid

    // Determine movement or rotation
    switch (playerDirection) {
      case 'N': return dx < 0 ? "M" : (dy > 0 ? "R" : "L");
      case 'S': return dx > 0 ? "M" : (dy > 0 ? "L" : "R");
      case 'E': return dy > 0 ? "M" : (dx > 0 ? "R" : "L");
      case 'W': return dy < 0 ? "M" : (dx > 0 ? "L" : "R");
    }
    return "R"; // Default to rotation
  }

  private int distance(int[] pos1, int[] pos2) {
    return Math.abs(pos1[0] - pos2[0]) + Math.abs(pos1[1] - pos2[1]);
  }

  private boolean enemyInRange(int[] playerPosition, char playerDirection, List<int[]> enemies, char[][] field) {
    int dx = 0, dy = 0;
    switch (playerDirection) {
      case 'N': dx = -1; break;
      case 'S': dx = 1; break;
      case 'E': dy = 1; break;
      case 'W': dy = -1; break;
    }

    for (int i = 1; i <= 4; i++) {
      int nx = playerPosition[0] + i * dx;
      int ny = playerPosition[1] + i * dy;

      // Stop at borders or obstacles
      if (nx < 0 || nx >= field.length || ny < 0 || ny >= field[0].length || field[nx][ny] == 'A') break;

      for (int[] enemy : enemies) {
        if (enemy[0] == nx && enemy[1] == ny) return true;
      }
    }
    return false;
  }

  private List<int[]> findEntities(char[][] field, char entityType) {
    List<int[]> positions = new ArrayList<>();
    for (int i = 0; i < field.length; i++) {
      for (int j = 0; j < field[i].length; j++) {
        if (field[i][j] == entityType) {
          positions.add(new int[]{i, j});
        }
      }
    }
    return positions;
  }

  class Action {
    String command;
    int score;

    public Action(String command, int score) {
      this.command = command;
      this.score = score;
    }
  }
}

class GameState {
  private List<List<String>> field;
  private int narrowingIn;
  private int gameId;

  public List<List<String>> getField() {
    return field;
  }

  public int getNarrowingIn() {
    return narrowingIn;
  }

  public int getGameId() {
    return gameId;
  }
}
