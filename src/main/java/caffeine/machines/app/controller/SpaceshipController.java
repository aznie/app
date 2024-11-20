package caffeine.machines.app.controller;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class SpaceshipController {
    private static final char PLAYER = 'P';
    private static final char ENEMY = 'E';
    private static final char COIN = 'C';
    private static final char ASTEROID = 'A';
    private static final char EMPTY = '_';
    private static final int FIRE_RANGE = 4;
    private static final int NARROWING_INTERVAL = 20;
    private static final int FIELD_SIZE = 13;

    private static final Map<String, Double> SCORES = Map.of(
            "survival", 10.0,
            "coin", 20.0,
            "kill", 40.0,
            "narrowing", 10.0
    );

    @PostMapping("/move")
    public Map<String, String> makeMove(@RequestBody GameState gameState) {
        char[][] field = parseField(gameState.getField());
        Position playerPos = findPlayer(field);
        Direction playerDir = Direction.fromChar(getPlayerDirection(field, playerPos));

        String move = calculateBestMove(field, playerPos, playerDir, gameState.getNarrowingIn());
        return Map.of("move", move);
    }

    private char[][] parseField(List<List<String>> fieldList) {
        char[][] field = new char[FIELD_SIZE][FIELD_SIZE];
        for (int i = 0; i < FIELD_SIZE; i++) {
            for (int j = 0; j < FIELD_SIZE; j++) {
                String cell = fieldList.get(i).get(j);
                field[i][j] = cell.isEmpty() ? EMPTY : cell.charAt(0);
            }
        }
        return field;
    }

    private String calculateBestMove(char[][] field, Position playerPos, Direction playerDir, int narrowingIn) {
        // Check for immediate threats first
        String emergencyMove = handleEmergency(field, playerPos, playerDir, narrowingIn);
        if (emergencyMove != null) {
            return emergencyMove;
        }

        // Look for immediate firing opportunities
        String fireMove = checkFiringOpportunity(field, playerPos, playerDir);
        if (fireMove != null) {
            return fireMove;
        }

        // Calculate best strategic move
        return calculateStrategicMove(field, playerPos, playerDir, narrowingIn);
    }

    private String handleEmergency(char[][] field, Position playerPos, Direction playerDir, int narrowingIn) {
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

    private String calculateDodgeMove(char[][] field, Position playerPos, Direction playerDir) {
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
        return options.stream()
                .max(Comparator.comparingDouble(opt -> opt.score))
                .map(opt -> opt.move)
                .orElse("M"); // Default to moving forward if no good options
    }

    private double evaluateDodgePosition(char[][] field, Position pos) {
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
        if (pos.row <= 1 || pos.row >= FIELD_SIZE - 2 ||
                pos.col <= 1 || pos.col >= FIELD_SIZE - 2) {
            safety *= 0.8;
        }

        return safety;
    }


    private boolean isInEnemyFireLine(char[][] field, Position playerPos) {
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

    private String checkFiringOpportunity(char[][] field, Position playerPos, Direction playerDir) {
        List<Position> enemiesInRange = findEnemiesInRange(field, playerPos, playerDir);
        if (!enemiesInRange.isEmpty()) {
            // Only fire if it's safe to do so
            if (isSafeToFire(field, playerPos, playerDir, enemiesInRange)) {
                return "F";
            }
        }
        return null;
    }

    private String calculateStrategicMove(char[][] field, Position playerPos, Direction playerDir, int narrowingIn) {
        List<MoveOption> options = new ArrayList<>();

        // Evaluate coin collection
        List<Position> coins = findEntities(field, COIN);
        for (Position coin : coins) {
            double score = evaluateCoinMove(field, playerPos, coin, narrowingIn);
            String move = getMovementCommand(field, playerPos, playerDir, coin);
            options.add(new MoveOption(move, score));
        }

        // Evaluate strategic positioning
        List<Position> enemies = findEntities(field, ENEMY);
        for (Position enemy : enemies) {
            double score = evaluatePositioning(field, playerPos, enemy, narrowingIn);
            String move = getOptimalPositioningMove(field, playerPos, playerDir, enemy);
            options.add(new MoveOption(move, score));
        }

        // Return highest scored move or safe default
        return options.stream()
                .max(Comparator.comparingDouble(opt -> opt.score))
                .map(opt -> opt.move)
                .orElse(calculateSafeMove(field, playerPos, playerDir));
    }

    private double evaluateCoinMove(char[][] field, Position playerPos, Position coin, int narrowingIn) {
        double baseScore = SCORES.get("coin");
        double distance = playerPos.distanceTo(coin);

        // Reduce score based on distance
        double score = baseScore / (distance + 1);

        // Reduce score if coin is in narrowing zone
        if (isInNarrowingDanger(coin, narrowingIn)) {
            score *= 0.5;
        }

        // Reduce score if path to coin is dangerous
        if (isPathDangerous(field, playerPos, coin)) {
            score *= 0.3;
        }

        return score;
    }

    private boolean isPathDangerous(char[][] field, Position from, Position to) {
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

    private boolean pathCrossesFireLine(char[][] field, Position from, Position to, Position enemy, Direction enemyDir) {
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

    private boolean isInFiringRange(Position from, Position target, Direction direction, char[][] field) {
        Position current = from;

        // Check all positions in firing range
        for (int i = 1; i <= FIRE_RANGE; i++) {
            current = current.move(direction);

            // Check if we've hit a boundary
            if (!isValidPosition(field, current)) {
                return false;
            }

            // If we've found our target
            if (current.equals(target)) {
                return true;
            }

            // Stop checking if we hit an asteroid or another entity that would block the shot
            char cellContent = field[current.row][current.col];
            if (cellContent == ASTEROID || cellContent == ENEMY) {
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

    private double evaluatePositioning(char[][] field, Position playerPos, Position enemy, int narrowingIn) {
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

    private String getOptimalPositioningMove(char[][] field, Position playerPos, Direction playerDir, Position enemy) {
        // Find best position for attack
        Position idealPos = calculateIdealAttackPosition(field, playerPos, enemy);
        if (idealPos != null) {
            return getMovementCommand(field, playerPos, playerDir, idealPos);
        }

        // If no ideal position, try to get closer while staying safe
        return getMovementCommand(field, playerPos, playerDir, enemy);
    }

    private Position calculateIdealAttackPosition(char[][] field, Position playerPos, Position enemy) {
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
                if (isValidPosition(field, candidate) &&
                        candidate.distanceTo(enemy) < FIRE_RANGE &&
                        hasNearbyAsteroid(field, candidate) &&
                        !isInEnemyFireLine(field, candidate)) {

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

    private boolean isValidPosition(char[][] field, Position pos) {
        return pos.row >= 0 && pos.row < field.length &&
                pos.col >= 0 && pos.col < field[0].length &&
                field[pos.row][pos.col] != ASTEROID;
    }

    private boolean hasNearbyAsteroid(char[][] field, Position pos) {
        // Check all adjacent cells including diagonals
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                // Skip the center position itself
                if (dr == 0 && dc == 0) continue;

                int newRow = pos.row + dr;
                int newCol = pos.col + dc;

                // Check if the position is valid and contains an asteroid
                if (isValidPosition(field, new Position(newRow, newCol)) &&
                        field[newRow][newCol] == ASTEROID) {
                    return true;
                }
            }
        }
        return false;
    }

    private String calculateSafeMove(char[][] field, Position playerPos, Direction playerDir) {
        // Try all possible moves and evaluate safety
        List<MoveOption> options = new ArrayList<>();

        // Evaluate moving forward
        Position forward = playerPos.move(playerDir);
        if (isValidPosition(field, forward)) {
            double forwardSafety = evaluateSafety(field, forward);
            options.add(new MoveOption("M", forwardSafety));
        }

        // Evaluate rotating left
        Direction leftDir = playerDir.turnLeft();
        options.add(new MoveOption("L", evaluateSafety(field, playerPos) * 0.9));

        // Evaluate rotating right
        Direction rightDir = playerDir.turnRight();
        options.add(new MoveOption("R", evaluateSafety(field, playerPos) * 0.9));

        // Return the safest move
        return options.stream()
                .max(Comparator.comparingDouble(opt -> opt.score))
                .map(opt -> opt.move)
                .orElse("M");
    }

    private boolean isInNarrowingDanger(Position pos, int narrowingIn) {
        int dangerZone = 2; // Buffer for safety
        return narrowingIn <= dangerZone && (
                pos.row <= narrowingIn || pos.row >= FIELD_SIZE - narrowingIn ||
                        pos.col <= narrowingIn || pos.col >= FIELD_SIZE - narrowingIn
        );
    }

    private Position findSafePosition(char[][] field, int narrowingIn) {
        Position center = new Position(FIELD_SIZE / 2, FIELD_SIZE / 2);
        int safeRadius = Math.max(2, FIELD_SIZE / 2 - narrowingIn - 1);

        // Search in expanding circles from center
        for (int r = 0; r <= safeRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    Position pos = new Position(center.row + dx, center.col + dy);
                    if (isValidPosition(field, pos) && field[pos.row][pos.col] == EMPTY) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean isInCollisionDanger(char[][] field, Position pos, Direction dir) {
        List<Position> enemies = findEntities(field, ENEMY);
        for (Position enemy : enemies) {
            if (willCollide(field, pos, dir, enemy, getEnemyDirection(field, enemy))) {
                return true;
            }
        }
        return false;
    }

    private boolean willCollide(char[][] field, Position pos1, Direction dir1, Position pos2, Direction dir2) {
        Position next1 = pos1.move(dir1);
        Position next2 = pos2.move(dir2);
        return next1.equals(next2) || next1.equals(pos2) || next2.equals(pos1);
    }

    private String calculateEvasiveMove(char[][] field, Position pos, Direction dir) {
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

        return options.stream()
                .max(Comparator.comparingDouble(opt -> opt.score))
                .map(opt -> opt.move)
                .orElse("M");
    }

    private double evaluateSafety(char[][] field, Position pos) {
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
        if (pos.row <= 1 || pos.row >= FIELD_SIZE - 2 ||
                pos.col <= 1 || pos.col >= FIELD_SIZE - 2) {
            safety *= 0.7;
        }

        return safety;
    }

    private List<Position> findEnemiesInRange(char[][] field, Position pos, Direction dir) {
        List<Position> enemies = new ArrayList<>();
        Position current = pos;

        for (int i = 1; i <= FIRE_RANGE; i++) {
            current = current.move(dir);
            if (!isValidPosition(field, current)) break;

            if (field[current.row][current.col] == ENEMY) {
                enemies.add(current);
                break; // Stop at first enemy as shot won't go further
            } else if (field[current.row][current.col] == ASTEROID) {
                break;
            }
        }

        return enemies;
    }

    private boolean isSafeToFire(char[][] field, Position pos, Direction dir, List<Position> targets) {
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

    private boolean canHit(char[][] field, Position from, Position target, Direction dir) {
        Position current = from;
        for (int i = 1; i <= FIRE_RANGE; i++) {
            current = current.move(dir);
            if (!isValidPosition(field, current)) break;

            if (current.equals(target)) return true;
            if (field[current.row][current.col] == ASTEROID) break;
        }
        return false;
    }

    private Direction getEnemyDirection(char[][] field, Position pos) {
        // Look at surrounding cells to determine enemy direction
        if (pos.col + 1 < FIELD_SIZE) {
            char nextChar = field[pos.row][pos.col + 1];
            if (nextChar == 'N' || nextChar == 'S' || nextChar == 'E' || nextChar == 'W') {
                return Direction.fromChar(nextChar);
            }
        }
        return Direction.NORTH; // Default if can't determine
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

        static Direction fromChar(char c) {
            return Arrays.stream(values())
                    .filter(d -> d.symbol == c)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid direction: " + c));
        }

        Direction turnLeft() {
            return values()[(ordinal() + 3) % 4];
        }

        Direction turnRight() {
            return values()[(ordinal() + 1) % 4];
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

    private String getMovementCommand(char[][] field, Position from, Direction currentDir, Position to) {
        Direction targetDir = getTargetDirection(from, to);

        // If we're already facing the right direction, try to move forward
        if (currentDir == targetDir) {
            Position next = from.move(currentDir);
            if (isValidPosition(field, next)) {
                return "M";
            }
        }

        // Otherwise, turn toward the target using the shortest rotation
        // Determine if right turn or left turn is shorter to reach target direction
        return currentDir.turnRight() == targetDir ? "R" : "L";
    }

    private Direction getTargetDirection(Position from, Position to) {
        int dx = to.row - from.row;
        int dy = to.col - from.col;

        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            return dy > 0 ? Direction.EAST : Direction.WEST;
        }
    }

    private List<Position> findEntities(char[][] field, char entityType) {
        List<Position> positions = new ArrayList<>();
        for (int i = 0; i < FIELD_SIZE; i++) {
            for (int j = 0; j < FIELD_SIZE; j++) {
                if (field[i][j] == entityType) {
                    positions.add(new Position(i, j));
                }
            }
        }
        return positions;
    }

    private Position findPlayer(char[][] field) {
        for (int i = 0; i < FIELD_SIZE; i++) {
            for (int j = 0; j < FIELD_SIZE; j++) {
                if (field[i][j] == PLAYER) {
                    return new Position(i, j);
                }
            }
        }
        throw new IllegalStateException("Player not found on field");
    }

    private char getPlayerDirection(char[][] field, Position pos) {
        return field[pos.row][pos.col + 1];
    }

    public class GameState {
        private List<List<String>> field;
        private int narrowingIn;
        private int gameId;

        // Constructor
        public GameState(List<List<String>> field, int narrowingIn, int gameId) {
            this.field = field;
            this.narrowingIn = narrowingIn;
            this.gameId = gameId;
        }

        // Default constructor for JSON deserialization
        public GameState() {
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

        @Override
        public String toString() {
            return "GameState{" +
                    "field=" + field +
                    ", narrowingIn=" + narrowingIn +
                    ", gameId=" + gameId +
                    '}';
        }
    }
}