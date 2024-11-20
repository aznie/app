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
    List<int[]> coins = findEntities(field, 'C');
    List<int[]> enemies = findEntities(field, 'E');

    // Avoid narrowing area
    if (playerPosition[0] < narrowingIn || playerPosition[0] >= rows - narrowingIn ||
        playerPosition[1] < narrowingIn || playerPosition[1] >= cols - narrowingIn) {
      return safeMove(playerDirection, rows, cols, playerPosition);
    }

    // Attack if an enemy is in range
    if (enemyInRange(playerPosition, playerDirection, enemies, field)) {
      return "F"; // Fire
    }

    // Move toward the nearest coin
    if (!coins.isEmpty()) {
      int[] nearestCoin = findNearest(playerPosition, coins);
      return moveToTarget(playerPosition, nearestCoin, playerDirection);
    }

    // Default: Rotate right if nothing else is optimal
    return "R";
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
      if (nx < 0 || nx >= field.length || ny < 0 || ny >= field[0].length || field[nx][ny] == 'A') {
        break; // Stop at borders or asteroids
      }
      for (int[] enemy : enemies) {
        if (enemy[0] == nx && enemy[1] == ny) {
          return true; // Enemy in range
        }
      }
    }
    return false;
  }

  private int[] findNearest(int[] playerPosition, List<int[]> targets) {
    int[] nearest = null;
    int minDistance = Integer.MAX_VALUE;

    for (int[] target : targets) {
      int distance = Math.abs(playerPosition[0] - target[0]) + Math.abs(playerPosition[1] - target[1]);
      if (distance < minDistance) {
        minDistance = distance;
        nearest = target;
      }
    }
    return nearest;
  }

  private String moveToTarget(int[] playerPosition, int[] target, char playerDirection) {
    int dx = target[0] - playerPosition[0];
    int dy = target[1] - playerPosition[1];

    switch (playerDirection) {
      case 'N': return dx < 0 ? "M" : (dy > 0 ? "R" : "L");
      case 'S': return dx > 0 ? "M" : (dy > 0 ? "L" : "R");
      case 'E': return dy > 0 ? "M" : (dx > 0 ? "R" : "L");
      case 'W': return dy < 0 ? "M" : (dx > 0 ? "L" : "R");
      default: return "R";
    }
  }

  private String safeMove(char playerDirection, int rows, int cols, int[] playerPosition) {
    int x = playerPosition[0];
    int y = playerPosition[1];

    if (x > 0 && playerDirection == 'N') return "M";
    if (y > 0 && playerDirection == 'W') return "M";
    if (x < rows - 1 && playerDirection == 'S') return "M";
    if (y < cols - 1 && playerDirection == 'E') return "M";

    return "R"; // Default rotate
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
