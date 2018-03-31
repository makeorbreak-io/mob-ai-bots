import java.util.*;
import java.util.stream.Stream;

import multipaint.Action;
import multipaint.Board;
import multipaint.Runner;

public class Bot implements multipaint.Bot {
    private String playerId;
    Random r;

    static class Coordinate {
        int x, y;

        Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object that) {
            if (that == null)
                return false;
            if (! (that instanceof Coordinate))
                return false;

            Coordinate other = (Coordinate) that;
            return other.x == this.x && other.y == this.y;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(x+y);
        }

        public int[] toArray() {
            return new int[] {x, y};
        }
    }

    static Coordinate N = new Coordinate(0, -1);
    static Coordinate NW = new Coordinate(-1, -1);
    static Coordinate NE = new Coordinate(1, -1);
    static Coordinate S = new Coordinate(0, 1);
    static Coordinate SE = new Coordinate(1, 1);
    static Coordinate SW = new Coordinate(-1, 1);
    static Coordinate E = new Coordinate(1, 0);
    static Coordinate W = new Coordinate(-1, 0);

    public static Coordinate[] actions = new Coordinate[]{
            NW, N, NE, W, E, SW, S, SE
    };

    static String[] ActionTypes = new String[]{"shoot", "walk"};
    static int[][] ActionDirections = new int[][]{
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1},
    };

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
        this.r = new Random();
    }

    public Action nextMove(Board state) {

        System.err.println("\n\nTurns left:" + state.turns_left);
        Action a = new Action();

        int[] currentPos = state.player_positions.get(playerId);

        int[][] points = classifyPosition(state, currentPos);
        int[] walkPoints = points[0];
        int[] shootPoints = points[1];

        Integer[] indexes = new Integer[]{0, 1, 2, 3, 4, 5, 6, 7};
        Arrays.sort(indexes, (o1, o2) -> Integer.compare(walkPoints[o1], walkPoints[o2]));

        Integer[] indexesShoot = new Integer[]{0, 1, 2, 3, 4, 5, 6, 7};
        Arrays.sort(indexesShoot, (o1, o2) -> Integer.compare(shootPoints[o1], shootPoints[o2]));

        if (walkPoints[indexes[7]] >= shootPoints[indexesShoot[7]]) {
            a.direction = ActionDirections[indexes[7]];
            a.type = ActionTypes[1];
        } else {
            a.direction = ActionDirections[indexesShoot[7]];
            a.type = ActionTypes[0];
        }

        if (walkPoints[indexes[7]] <= 0 && shootPoints[indexesShoot[7]] <= 0) {
            a.type = ActionTypes[1];
            String closestOpponent = findClosestOpponent(state.player_positions);
            a.direction = moveCloserToOpponent(closestOpponent, state.player_positions.get(closestOpponent), currentPos);

            System.err.println("Decided to move closer to opponent");
        }

        System.err.println("Play: " + actionToString(a));

        return a;
    }

    private int[][] classifyPosition(Board state, int[] currentPos) {
        int[][] points = new int[2][ActionDirections.length];

        for (int i = 0; i < ActionDirections.length; i++) {
            int[] nextPos = new int[]{currentPos[0] + ActionDirections[i][0], currentPos[1] + ActionDirections[i][1]};
            points[0][i] = classifyWalk(nextPos, state.colors);
            points[1][i] = classifyShoot(ActionDirections[i], currentPos, state.colors);
        }

        return points;
    }

    String findClosestOpponent(Map<String, int[]> players) {
        return players.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(playerId))
            .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), distance(entry.getValue(), players.get(playerId))))
            .max(Map.Entry.comparingByValue())
            .get()
            .getKey();
    }

    int[] moveCloserToOpponent(String opponentId, int[] opponentPos, int[] currentPos) {
        return Stream.of(ActionDirections)
                .map(c -> new AbstractMap.SimpleEntry<>(new Coordinate(c[0], c[1]), distance(calculateNewPos(currentPos, c), opponentPos)))
                .min(Map.Entry.comparingByValue())
                .get()
                .getKey()
                .toArray();
    }

    int classifyShoot(int[] actionDir, int[] currentPos, String[][] colors) {
        int points = 0;

        int[] paintPos = new int[]{currentPos[0] + actionDir[0], currentPos[1] + actionDir[1]};
        if (!isWithinLimits(paintPos, colors))
            return points;

        // check opposite direction points
        int paintedOwn = 0;
        for (int[] nextPos = new int[]{currentPos[0] - actionDir[0], currentPos[1] - actionDir[1]};
             isWithinLimits(nextPos, colors) && playerId.equals(colors[nextPos[0]][nextPos[1]]);
             nextPos = new int[]{nextPos[0] - actionDir[0], nextPos[1] - actionDir[1]}) {
            paintedOwn++;
        }

        // point forward shoot
        for (int[] nextPos = paintPos.clone();
             isWithinLimits(nextPos, colors) && paintedOwn > 0;
             paintedOwn--) {
            if (null == colors[nextPos[0]][nextPos[1]])
                points += 1;
            else if (!isOwnColor(nextPos, colors))
                points += 2;
        }

        if (surroundedIfShoot(paintPos, currentPos, colors))
            points -= 1;

        System.err.println("aval: " + pointToString(actionDir) + " - points: " + points);

        return points;
    }

    boolean surroundedIfShoot(int[] shootPos, int[] currPos, String[][] colors) {
        boolean surrounded = true;
        for (int i = 0; i < ActionDirections.length; i++) {
            int[] pos = new int[]{currPos[0] + ActionDirections[i][0], currPos[1] + ActionDirections[i][1]};
            if (pos[0] != shootPos[0] && pos[1] != shootPos[1])
                surrounded &= isOwnColor(pos, colors);
        }

        return surrounded;
    }

    int classifyWalk(int[] nextPos, String[][] colors) {
        int points = 0;
        if (!isWithinLimits(nextPos, colors))
            return points;

        if (null == colors[nextPos[0]][nextPos[1]])
            points += 1;
        else if (!playerId.equals(colors[nextPos[0]][nextPos[1]]))
            points += 2;

        if (isAtBorder(nextPos, colors))
            points -= 1;

        return points;
    }

    int[] calculateNewPos(int[] current, int[] dir) {
        return new int[]{current[0] + dir[0], current[1] + dir[1]};
    }

    double distance(int[] a, int[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    boolean isOwnColor(int[] pos, String[][] colors) {
        return isWithinLimits(pos, colors) && playerId.equals(colors[pos[0]][pos[1]]);
    }

    boolean isAtBorder(int[] pos, String[][] colors) {
        return pos[0] == 0 || pos[0] == colors[0].length - 1 || pos[1] == 0 || pos[1] == colors.length - 1;
    }

    boolean isWithinLimits(int[] pos, String[][] colors) {
        return pos[0] < colors[0].length && pos[0] >= 0 && pos[1] < colors.length && pos[1] >= 0;
    }

    String pointToString(int[] p) {
        return "(" + p[0] + ", " + p[1] + ")";
    }

    String actionToString(Action a) {
        return a.type + " (" + a.direction[0] + ", " + a.direction[1] + ")";
    }

    public static void main(String[] args) {
        Runner.run(new Bot());
    }
}
