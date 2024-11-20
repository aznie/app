package caffeine.machines.app.controller;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class SpaceshipController {
    private static final String PLAYER = "P";
    private static final String ENEMY = "E";
    private static final String COIN = "C";
    private static final String ASTEROID = "A";
    private static final String EMPTY = "_";
    private static final int FIRE_RANGE = 4;
    private static final String FIRE_ACTION = "F";
    private static int FIRE_ACTION_COUNTER = 1;
    private static final int FIELD_SIZE = 13;

    private LinkedList<String> lastMoves = new LinkedList<>();

    private List<List<String>> rawField;

    private static final Map<String, Double> SCORES = Map.of("survival", 10.0, "coin", 20.0, "kill", 40.0, "narrowing", 10.0);

    @PostMapping("/move")
    public Map<String, String> makeMove(@RequestBody GameState gameState) {
        try {
            // Store raw field data
            this.rawField = gameState.getField();

            // Log the incoming request
            System.out.println("Received game state:");
            System.out.println("Game ID: " + gameState.getGameId());
            System.out.println("Narrowing In: " + gameState.getNarrowingIn());
            System.out.println("Field state:");
            printField(rawField);

            Position playerPos = findPlayer(rawField);
            System.out.println("Found player at: row=" + playerPos.row + ", col=" + playerPos.col);

            Direction playerDir = getPlayerDirection(playerPos);
            System.out.println("Player direction: " + playerDir);

            String move = calculateBestMove(rawField, playerPos, playerDir, gameState.getNarrowingIn());

            System.out.println("Calculated move: " + move);
            return Map.of("move", move);

        } catch (Exception e) {
            System.err.println("Error calculating move: " + e.getMessage());
            e.printStackTrace();
            return Map.of("move", "M");
        }
    }

    private void printField(List<List<String>> field) {
        System.out.println("Parsed field state:");
        for (int i = 0; i < field.size(); i++) {
            StringBuilder row = new StringBuilder();
            for (int j = 0; j < field.get(i).size(); j++) {
                String cell = field.get(i).get(j);
                String display = cell.isEmpty() ? "_" :
                        cell.equals("*") ? "A" : cell;
                row.append(String.format("%-6s", display));
            }
            System.out.println(row.toString());
        }
    }

    private String calculateBestMove(List<List<String>> field, Position playerPos, Direction playerDir, int narrowingIn) {
        System.out.println("\nCalculating best move:");
        System.out.println("Player position: " + playerPos);
        System.out.println("Player direction: " + playerDir);
        System.out.println("Narrowing in: " + narrowingIn);

        // Track last few moves to detect loops
        if (lastMoves == null) {
            lastMoves = new LinkedList<>();
        }
        if (lastMoves.size() > 4) {
            lastMoves.removeFirst();
        }

      // Check for immediate threats first
      String emergencyMove = handleEmergency(field, playerPos, playerDir, narrowingIn);
      if (emergencyMove != null) {
        lastMoves.add(emergencyMove);
        return emergencyMove;
      }

      // If we can't move and have been rotating, try to move in current direction
      if (lastMoves.size() >= 2 &&
          lastMoves.stream().allMatch(m -> m.equals("L") || m.equals("R"))) {
        if (canMoveForward(field, playerPos, playerDir)) {
          lastMoves.clear();
          return "M";
        }
      }

//        // Check for rotation loop
//        if (lastMoves.size() >= 4) {
//            boolean isLoop = lastMoves.stream().allMatch(m -> m.equals("L") || m.equals("R"));
//            if (isLoop) {
//                System.out.println("Detected rotation loop, forcing forward movement");
//                if (canMoveForward(field, playerPos, playerDir)) {
//                    lastMoves.clear();
//                    return "M";
//                }
//            }
//        }

      // Check for firing opportunities
      String fireMove = checkFiringOpportunity(field, playerPos, playerDir);
      if (fireMove != null) {
        System.out.println("Firing opportunity found");
        return fireMove;
      }

        // Look for coins with direct path
        List<Position> coins = findEntities(field, COIN);
        if (!coins.isEmpty()) {
            Position nearestCoin = findAccessibleCoin(field, playerPos, playerDir, coins);
            if (nearestCoin != null) {
                String move = getMovementCommand(field, playerPos, playerDir, nearestCoin);
                lastMoves.add(move);
                return move;
            }
        }

        // Default to strategic movement
        String move = calculateStrategicMove(field, playerPos, playerDir, narrowingIn);
        lastMoves.add(move);
        return move;
    }

    private boolean canMoveForward(List<List<String>> field, Position pos, Direction dir) {
        Position next = pos.move(dir);
        return isValidPosition(field, next);
    }

    private Position findAccessibleCoin(List<List<String>>  field, Position playerPos, Direction playerDir, List<Position> coins) {
        Position best = null;
        double bestScore = Double.MAX_VALUE;

        for (Position coin : coins) {
            // Calculate actual path considering obstacles
            List<Position> path = findPath(field, playerPos, coin);
            if (!path.isEmpty()) {
                double distance = path.size();
                double rotations = calculateRotations(playerDir, getPathDirection(playerPos, path.get(0)));
                double score = distance + (rotations * 0.5); // Weigh rotations less than distance

                if (score < bestScore) {
                    bestScore = score;
                    best = coin;
                }
            }
        }

        return best;
    }

    private List<Position> findPath(List<List<String>>  field, Position start, Position end) {
        Queue<Position> queue = new LinkedList<>();
        Map<Position, Position> cameFrom = new HashMap<>();
        queue.offer(start);

        while (!queue.isEmpty()) {
            Position current = queue.poll();

            if (current.equals(end)) {
                return reconstructPath(cameFrom, start, end);
            }

            // Try all four directions
            for (Direction dir : Direction.values()) {
                Position next = current.move(dir);
                if (isValidPosition(field, next) && !cameFrom.containsKey(next)) {
                    queue.offer(next);
                    cameFrom.put(next, current);
                }
            }
        }

        return new ArrayList<>();
    }

    private List<Position> reconstructPath(Map<Position, Position> cameFrom, Position start, Position end) {
        List<Position> path = new ArrayList<>();
        Position current = end;

        while (current != null && !current.equals(start)) {
            path.add(0, current);
            current = cameFrom.get(current);
        }

        return path;
    }

    private Direction getPathDirection(Position from, Position to) {
        int dx = to.row - from.row;
        int dy = to.col - from.col;

        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            return dy > 0 ? Direction.EAST : Direction.WEST;
        }
    }

    private int calculateRotations(Direction from, Direction to) {
        if (from == to) return 0;
        int diff = (to.ordinal() - from.ordinal() + 4) % 4;
        return Math.min(diff, 4 - diff);
    }

    private String handleEmergency(List<List<String>> field, Position playerPos, Direction playerDir, int narrowingIn) {
        // Check if we're in immediate danger from narrowing
        if (isInNarrowingDanger(playerPos, narrowingIn)) {
            Position safePos = findSafePosition(field, narrowingIn);
            if (safePos != null) {
                return getMovementCommand(field, playerPos, playerDir, safePos);
            }
        }

        // Check for immediate collision danger
        if (isInCollisionDanger(field, playerPos, playerDir)) {
            return calculateEvasiveMove(field, playerPos, playerDir);
        }

        // Check if we're in enemy's firing line
        if (isInEnemyFireLine(field, playerPos)) {
            return calculateDodgeMove(field, playerPos, playerDir);
        }

        return null;
    }

    private String calculateDodgeMove(List<List<String>>  field, Position playerPos, Direction playerDir) {
        List<MoveOption> options = new ArrayList<>();

        // Evaluate all possible moves
        // 1. Try moving forward
        Position forward = playerPos.move(playerDir);
        if (isValidPosition(field, forward)) {
            double forwardSafety = evaluateDodgePosition(field, forward);
            options.add(new MoveOption("M", forwardSafety));
        }

        // 2. Try rotating left
        Direction leftDir = playerDir.turnLeft();
        Position leftPos = playerPos.move(leftDir);
        if (isValidPosition(field, leftPos)) {
            double leftSafety = evaluateDodgePosition(field, playerPos) * 0.9; // Slight penalty for rotation
            options.add(new MoveOption("L", leftSafety));
        }

        // 3. Try rotating right
        Direction rightDir = playerDir.turnRight();
        Position rightPos = playerPos.move(rightDir);
        if (isValidPosition(field, rightPos)) {
            double rightSafety = evaluateDodgePosition(field, playerPos) * 0.9; // Slight penalty for rotation
            options.add(new MoveOption("R", rightSafety));
        }

        // Return the safest move
        return options.stream().max(Comparator.comparingDouble(opt -> opt.score)).map(opt -> opt.move).orElse("M"); // Default to moving forward if no good options
    }

    private double evaluateDodgePosition(List<List<String>>  field, Position pos) {
        double safety = 1.0;
        List<Position> enemies = findEntities(field, ENEMY);

        for (Position enemy : enemies) {
            Direction enemyDir = getEnemyDirection(field, enemy);

            // Heavy penalty if still in firing line
            if (isInFiringRange(enemy, pos, enemyDir, field)) {
                safety *= 0.2;
                continue;
            }

            // Smaller penalty for being close to enemy
            double distance = pos.distanceTo(enemy);
            if (distance < 3) {
                safety *= 0.7;
            }
        }

        // Bonus for positions near cover (asteroids)
        if (hasNearbyAsteroid(field, pos)) {
            safety *= 1.2;
        }

        // Penalty for being close to walls
        if (pos.row <= 1 || pos.row >= FIELD_SIZE - 2 || pos.col <= 1 || pos.col >= FIELD_SIZE - 2) {
            safety *= 0.8;
        }

        return safety;
    }


    private boolean isInEnemyFireLine(List<List<String>>  field, Position playerPos) {
        List<Position> enemies = findEntities(field, ENEMY);

        // Check if any enemy can hit us
        for (Position enemy : enemies) {
            Direction enemyDir = getEnemyDirection(field, enemy);
            if (isInFiringRange(enemy, playerPos, enemyDir, field)) {
                return true;
            }
        }
        return false;
    }

    private String checkFiringOpportunity(List<List<String>>  field, Position playerPos, Direction playerDir) {
        List<Position> enemiesInRange = findEnemiesInRange(field, playerPos, playerDir);
        if (!enemiesInRange.isEmpty()) {
            // Only fire if it's safe to do so
            if (isSafeToFire(field, playerPos, playerDir, enemiesInRange)) {
                return FIRE_ACTION;
            }
        }
        System.out.println("No enemies in range, doing checks, dir: " + playerDir.toString());

        if(playerDir == Direction.NORTH && field.get(playerPos.row - 1).get(playerPos.col).equals(ASTEROID)) {
          System.out.println("Case 1");
          return null;
        } else if (playerDir == Direction.SOUTH && field.get(playerPos.row + 1).get(playerPos.col).equals(ASTEROID)) {
          System.out.println("Case 2");
          return null;
        } else if (playerDir == Direction.WEST && field.get(playerPos.row).get(playerPos.col - 1).equals(ASTEROID)) {
          System.out.println("Case 3");
          return null;
        } else if (playerDir == Direction.EAST && field.get(playerPos.row).get(playerPos.col + 1).equals(ASTEROID)) {
          System.out.println("Case 4");
          return null;
        }

        System.out.println("Check for the counter. Value: " + FIRE_ACTION_COUNTER);
        if (FIRE_ACTION_COUNTER % 2 == 0) {
          FIRE_ACTION_COUNTER++;
          return FIRE_ACTION;
        }
        FIRE_ACTION_COUNTER++;
        return null;
    }

    private String calculateStrategicMove(List<List<String>>  field, Position playerPos, Direction playerDir, int narrowingIn) {
        List<MoveOption> options = new ArrayList<>();

        // Always consider basic moves with base scores
        Position forward = playerPos.move(playerDir);
        if (isValidPosition(field, forward)) {
            options.add(new MoveOption("M", 1.0)); // Base score for moving
        }
        options.add(new MoveOption("L", 0.5)); // Base score for rotating
        options.add(new MoveOption("R", 0.5)); // Base score for rotating

        // Evaluate coin collection with higher priority
        List<Position> coins = findEntities(field, COIN);
        for (Position coin : coins) {
            double score = evaluateCoinMove(field, playerPos, coin, narrowingIn);
            String move = getMovementCommand(field, playerPos, playerDir, coin);
            options.add(new MoveOption(move, score * 2.0)); // Increased priority for coins
        }

        // Evaluate strategic positioning
        List<Position> enemies = findEntities(field, ENEMY);
        for (Position enemy : enemies) {
            double score = evaluatePositioning(field, playerPos, enemy, narrowingIn);
            String move = getOptimalPositioningMove(field, playerPos, playerDir, enemy);
            options.add(new MoveOption(move, score));
        }

        // Debug logging
        System.out.println("Available moves:");
        for (MoveOption option : options) {
            System.out.println(option.move + ": " + option.score);
        }

        return options.stream().max(Comparator.comparingDouble(opt -> opt.score)).map(opt -> opt.move).orElse("M");
    }


    private double evaluateCoinMove(List<List<String>>  field, Position playerPos, Position coin, int narrowingIn) {
        double score = SCORES.get("coin");
        double distance = playerPos.distanceTo(coin);

        // Increased base score and reduced distance penalty
        score = score / Math.sqrt(distance + 1);  // Using sqrt for less aggressive distance penalty

        // Less aggressive penalties
        if (isInNarrowingDanger(coin, narrowingIn)) {
            score *= 0.8; // Was 0.5
        }

        if (isPathDangerous(field, playerPos, coin)) {
            score *= 0.5; // Was 0.3
        }

        return score;
    }

    private boolean isPathDangerous(List<List<String>> field, Position from, Position to) {
        List<Position> enemies = findEntities(field, ENEMY);

        // Check if path crosses any enemy firing lines
        for (Position enemy : enemies) {
            Direction enemyDir = getEnemyDirection(field, enemy);
            if (pathCrossesFireLine(field, from, to, enemy, enemyDir)) {
                return true;
            }
        }

        return false;
    }

    private boolean pathCrossesFireLine(List<List<String>>  field, Position from, Position to, Position enemy, Direction enemyDir) {
        int steps = (int) from.distanceTo(to);
        Position current = from;

        for (int i = 0; i <= steps; i++) {
            if (isInFiringRange(enemy, current, enemyDir, field)) {
                return true;
            }
            current = moveToward(current, to);
        }

        return false;
    }

    private boolean isInFiringRange(Position from, Position target, Direction direction, List<List<String>> field) {
        System.out.println("Checking firing range from " + from + " to " + target + " in direction " + direction);
        Position current = from;

        for (int i = 1; i <= FIRE_RANGE; i++) {
            current = current.move(direction);
            System.out.println("Checking position: " + current);

            if (!isValidPosition(field, current)) {
                System.out.println("Position invalid or blocked");
                return false;
            }

            if (current.equals(target)) {
                System.out.println("Target found in range");
                return true;
            }

            if (field.get(current.row).get(current.col).equals(ASTEROID)) {
                System.out.println("Blocked by asteroid");
                return false;
            }
        }

        return false;
    }

    private Position moveToward(Position from, Position to) {
        int dx = Integer.compare(to.row - from.row, 0);
        int dy = Integer.compare(to.col - from.col, 0);
        return new Position(from.row + dx, from.col + dy);
    }

    private double evaluatePositioning(List<List<String>> field, Position playerPos, Position enemy, int narrowingIn) {
        double score = SCORES.get("kill");

        // Calculate ideal attack distance (just within firing range)
        double distance = playerPos.distanceTo(enemy);
        double idealDistance = FIRE_RANGE - 1;

        // Penalize positions too close or too far from ideal
        score *= 1.0 / (1 + Math.abs(distance - idealDistance));

        // Penalize positions in narrowing zone
        if (isInNarrowingDanger(playerPos, narrowingIn)) {
            score *= 0.3;
        }

        // Bonus for positions near cover
        if (hasNearbyAsteroid(field, playerPos)) {
            score *= 1.2;
        }

        return score;
    }

    private String getOptimalPositioningMove(List<List<String>>  field, Position playerPos, Direction playerDir, Position enemy) {
        // Find best position for attack
        Position idealPos = calculateIdealAttackPosition(field, playerPos, enemy);
        if (idealPos != null) {
            return getMovementCommand(field, playerPos, playerDir, idealPos);
        }

        // If no ideal position, try to get closer while staying safe
        return getMovementCommand(field, playerPos, playerDir, enemy);
    }

    private Position calculateIdealAttackPosition(List<List<String>>  field, Position playerPos, Position enemy) {
        // Try to find a position that's:
        // 1. Within firing range
        // 2. Has cover nearby
        // 3. Not in enemy's firing line
        // 4. Closest to current position (to minimize movement)

        Position bestPosition = null;
        double bestDistance = Double.MAX_VALUE;
        int searchRadius = FIRE_RANGE;

        for (int r = -searchRadius; r <= searchRadius; r++) {
            for (int c = -searchRadius; c <= searchRadius; c++) {
                Position candidate = new Position(enemy.row + r, enemy.col + c);

                // Check all conditions
                if (isValidPosition(field, candidate) && candidate.distanceTo(enemy) < FIRE_RANGE && hasNearbyAsteroid(field, candidate) && !isInEnemyFireLine(field, candidate)) {

                    // Calculate distance from current position
                    double distanceFromCurrent = playerPos.distanceTo(candidate);

                    // Update best position if this is closer to current position
                    if (distanceFromCurrent < bestDistance) {
                        bestDistance = distanceFromCurrent;
                        bestPosition = candidate;
                    }
                }
            }
        }

        return bestPosition;
    }

    private boolean isValidPosition(List<List<String>> field, Position pos) {
        // First check boundaries
        if (pos.row < 0 || pos.row >= field.size() ||
                pos.col < 0 || pos.col >= field.get(0).size()) {
            System.out.println("Position out of bounds: " + pos);
            return false;
        }

        // Check cell content
        String cell = field.get(pos.row).get(pos.col);
        boolean isValid = cell.equals(EMPTY) || cell.equals(COIN);

        if (!isValid) {
            System.out.println("Invalid cell content at " + pos + ": " + cell);
        }

        return isValid;
    }

    private boolean hasNearbyAsteroid(List<List<String>> field, Position pos) {
        // Check all adjacent cells including diagonals
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                // Skip the center position itself
                if (dr == 0 && dc == 0) continue;

                int newRow = pos.row + dr;
                int newCol = pos.col + dc;

                // Check if the position is valid and contains an asteroid
                if (isValidPosition(field, new Position(newRow, newCol)) && field.get(newRow).get(newCol).equals(ASTEROID)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInNarrowingDanger(Position pos, int narrowingIn) {
        int dangerZone = 2; // Buffer for safety
        return narrowingIn <= dangerZone && (pos.row <= narrowingIn || pos.row >= FIELD_SIZE - narrowingIn || pos.col <= narrowingIn || pos.col >= FIELD_SIZE - narrowingIn);
    }

    private Position findSafePosition(List<List<String>> field, int narrowingIn) {
        Position center = new Position(FIELD_SIZE / 2, FIELD_SIZE / 2);
        int safeRadius = Math.max(2, FIELD_SIZE / 2 - narrowingIn - 1);

        // Search in expanding circles from center
        for (int r = 0; r <= safeRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    Position pos = new Position(center.row + dx, center.col + dy);
                    if (isValidPosition(field, pos) && field.get(pos.row).get(pos.col).equals(EMPTY)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean isInCollisionDanger(List<List<String>>  field, Position pos, Direction dir) {
        List<Position> enemies = findEntities(field, ENEMY);
        for (Position enemy : enemies) {
            if (willCollide(field, pos, dir, enemy, getEnemyDirection(field, enemy))) {
                return true;
            }
        }
        return false;
    }

    private boolean willCollide(List<List<String>>  field, Position pos1, Direction dir1, Position pos2, Direction dir2) {
        Position next1 = pos1.move(dir1);
        Position next2 = pos2.move(dir2);
        return next1.equals(next2) || next1.equals(pos2) || next2.equals(pos1);
    }

    private String calculateEvasiveMove(List<List<String>>  field, Position pos, Direction dir) {
        // Try all possible moves and evaluate safety
        List<MoveOption> options = new ArrayList<>();

        // Evaluate moving forward
        Position forward = pos.move(dir);
        if (isValidPosition(field, forward)) {
            options.add(new MoveOption("M", evaluateSafety(field, forward)));
        }

        // Evaluate turning
        options.add(new MoveOption("L", evaluateSafety(field, pos)));
        options.add(new MoveOption("R", evaluateSafety(field, pos)));

        return options.stream().max(Comparator.comparingDouble(opt -> opt.score)).map(opt -> opt.move).orElse("M");
    }

    private double evaluateSafety(List<List<String>> field, Position pos) {
        double safety = 1.0;

        // Reduce safety for nearby enemies
        List<Position> enemies = findEntities(field, ENEMY);
        for (Position enemy : enemies) {
            double distance = pos.distanceTo(enemy);
            if (distance < 3) {
                safety *= 0.5;
            }
        }

        // Reduce safety for being near walls
        if (pos.row <= 1 || pos.row >= FIELD_SIZE - 2 || pos.col <= 1 || pos.col >= FIELD_SIZE - 2) {
            safety *= 0.7;
        }

        return safety;
    }

    private List<Position> findEnemiesInRange(List<List<String>> field, Position pos, Direction dir) {
        List<Position> enemies = new ArrayList<>();
        Position current = pos;

        for (int i = 1; i <= FIRE_RANGE; i++) {
            current = current.move(dir);
            if (!isValidPosition(field, current)) break;

            if (field.get(current.row).get(current.col).equals(ENEMY)) {
                enemies.add(current);
                break; // Stop at first enemy as shot won't go further
            } else if (field.get(current.row).get(current.col).equals(ASTEROID)) {
                break;
            }
        }

        return enemies;
    }

    private boolean isSafeToFire(List<List<String>> field, Position pos, Direction dir, List<Position> targets) {
        // Check if any non-target enemy can fire back
        List<Position> allEnemies = findEntities(field, ENEMY);
        for (Position enemy : allEnemies) {
            if (!targets.contains(enemy)) {
                Direction enemyDir = getEnemyDirection(field, enemy);
                if (canHit(field, enemy, pos, enemyDir)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canHit(List<List<String>>  field, Position from, Position target, Direction dir) {
        Position current = from;
        for (int i = 1; i <= FIRE_RANGE; i++) {
            current = current.move(dir);
            if (!isValidPosition(field, current)) break;

            if (current.equals(target)) return true;
            if (field.get(current.row).get(current.col).equals(ASTEROID)) break;
        }
        return false;
    }

    private Direction getEnemyDirection(List<List<String>> field, Position pos) {
        String cellContent = rawField.get(pos.row).get(pos.col);
        System.out.println("Enemy cell content: '" + cellContent + "'");

        if (cellContent.startsWith("E")) {
            String dirString = cellContent.substring(1);
            System.out.println("Parsing enemy direction from: '" + dirString + "'");
            Direction dir = Direction.fromString(dirString);
            if (dir != null) {
                System.out.println("Resolved enemy direction to: " + dir);
                return dir;
            }
        }

        Direction defaultDir = getDefaultDirection(pos);
        System.out.println("Using default direction towards center: " + defaultDir);
        return defaultDir;
    }

    private static class Position {
        final int row;
        final int col;

        Position(int row, int col) {
            this.row = row;
            this.col = col;
        }

        Position move(Direction dir) {
            return new Position(row + dir.dx, col + dir.dy);
        }

        double distanceTo(Position other) {
            return Math.sqrt(Math.pow(row - other.row, 2) + Math.pow(col - other.col, 2));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Position)) return false;
            Position pos = (Position) o;
            return row == pos.row && col == pos.col;
        }

        @Override
        public int hashCode() {
            return Objects.hash(row, col);
        }

        @Override
        public String toString() {
            return String.format("(%d,%d)", row, col);
        }
    }

    private enum Direction {
        NORTH('N', -1, 0),
        SOUTH('S', 1, 0),
        EAST('E', 0, 1),
        WEST('W', 0, -1);

        final int dx;
        final int dy;
        final char symbol;

        Direction(char symbol, int dx, int dy) {
            this.symbol = symbol;
            this.dx = dx;
            this.dy = dy;
        }

        static Direction fromString(String s) {
            System.out.println("Parsing direction from string: '" + s + "'");

            if (s == null || s.isEmpty()) {
                System.out.println("Empty direction string");
                return null;  // Return null to handle with getDefaultDirection
            }

            // First try to match the full name
            String upperInput = s.toUpperCase();
            for (Direction d : values()) {
                if (upperInput.contains(d.name())) {
                    System.out.println("Found direction by name contained: " + d);
                    return d;
                }
            }

            // If no full name match, look for the direction symbol
            for (Direction d : values()) {
                if (upperInput.contains(String.valueOf(d.symbol))) {
                    System.out.println("Found direction by symbol contained: " + d);
                    return d;
                }
            }

            return null;  // Return null to handle with getDefaultDirection
        }

        Direction turnLeft() {
            return values()[(ordinal() + 3) % 4];
        }

        Direction turnRight() {
            return values()[(ordinal() + 1) % 4];
        }

        @Override
        public String toString() {
            return name();
        }
    }

    private static class MoveOption {
        final String move;
        final double score;

        MoveOption(String move, double score) {
            this.move = move;
            this.score = score;
        }
    }

    private String getMovementCommand(List<List<String>>  field, Position from, Direction currentDir, Position to) {
        System.out.println("Getting movement command from " + from + " to " + to + ", current direction: " + currentDir);

        Direction targetDir = getTargetDirection(from, to);
        System.out.println("Target direction: " + targetDir);

        // If we're facing the right direction and can move, do it
        if (currentDir == targetDir) {
            Position next = from.move(currentDir);
            if (isValidPosition(field, next)) {
                return "M";
            }
        }

        // Determine shortest rotation
        int currentOrd = currentDir.ordinal();
        int targetOrd = targetDir.ordinal();
        int diff = (targetOrd - currentOrd + 4) % 4;
        return (diff <= 2) ? "R" : "L";
    }

    private Direction getTargetDirection(Position from, Position to) {
        int dx = to.row - from.row;
        int dy = to.col - from.col;

        // Use primary direction (larger delta)
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.SOUTH : Direction.NORTH;
        } else if (Math.abs(dy) > Math.abs(dx)) {
            return dy > 0 ? Direction.EAST : Direction.WEST;
        } else {
            // If deltas are equal, prefer current direction if it works
            if (dx > 0) return Direction.SOUTH;
            if (dx < 0) return Direction.NORTH;
            if (dy > 0) return Direction.EAST;
            return Direction.WEST;
        }
    }

    private List<Position> findEntities(List<List<String>>  field, String entityType) {
        List<Position> positions = new ArrayList<>();
        for (int i = 0; i < FIELD_SIZE; i++) {
            for (int j = 0; j < FIELD_SIZE; j++) {
                if (field.get(i).get(j).equals(entityType)) {
                    positions.add(new Position(i, j));
                }
            }
        }
        return positions;
    }

    private Position findPlayer(List<List<String>> field) {
        for (int i = 0; i < field.size(); i++) {
            for (int j = 0; j < field.get(i).size(); j++) {
                if (field.get(i).get(j).startsWith(PLAYER)) {
                    return new Position(i, j);
                }
            }
        }
        throw new IllegalStateException("Player not found on field");
    }

    private Direction getDefaultDirection(Position pos) {
        int centerRow = FIELD_SIZE / 2;
        int centerCol = FIELD_SIZE / 2;

        // Calculate distances to center
        int verticalDist = centerRow - pos.row;
        int horizontalDist = centerCol - pos.col;

        System.out.println("Calculating default direction from position " + pos +
                " (vertical distance to center: " + verticalDist +
                ", horizontal distance: " + horizontalDist + ")");

        // Choose the direction that gets us closer to center
        if (Math.abs(verticalDist) > Math.abs(horizontalDist)) {
            // Vertical distance is greater
            return verticalDist > 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            // Horizontal distance is greater or equal
            return horizontalDist > 0 ? Direction.EAST : Direction.WEST;
        }
    }


    private Direction getPlayerDirection(Position pos) {
        String cellContent = rawField.get(pos.row).get(pos.col);
        System.out.println("Player cell content: '" + cellContent + "'");

        if (cellContent.length() > 1) {
            String dirString = cellContent.substring(1);
            System.out.println("Parsing player direction from: '" + dirString + "'");
            Direction dir = Direction.fromString(dirString);
            if (dir != null) {
                System.out.println("Resolved player direction to: " + dir);
                return dir;
            }
        }

        Direction defaultDir = getDefaultDirection(pos);
        System.out.println("Using default direction towards center: " + defaultDir);
        return defaultDir;
    }

    public static class GameState {
        private List<List<String>> field;
        private int narrowingIn;
        private int gameId;

        // Default constructor needed for JSON deserialization
        public GameState() {
        }

        // Full constructor
        public GameState(List<List<String>> field, int narrowingIn, int gameId) {
            this.field = field;
            this.narrowingIn = narrowingIn;
            this.gameId = gameId;
        }

        // Getters and setters
        public List<List<String>> getField() {
            return field;
        }

        public void setField(List<List<String>> field) {
            this.field = field;
        }

        public int getNarrowingIn() {
            return narrowingIn;
        }

        public void setNarrowingIn(int narrowingIn) {
            this.narrowingIn = narrowingIn;
        }

        public int getGameId() {
            return gameId;
        }

        public void setGameId(int gameId) {
            this.gameId = gameId;
        }
    }
}