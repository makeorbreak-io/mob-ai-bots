import multipaint.Action;
import multipaint.Board;
import multipaint.Runner;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

public class MakeLoveNotWar implements multipaint.Bot {
    private String playerId;
    public Random r;

    public static String[] ActionTypes = new String[]{"shoot", "walk"};
    public static int[][] ActionDirections = new int[][]{
            {-1, -1}, { 0, -1}, { 1, -1},
            {-1,  0},           { 1,  0},
            {-1,  1}, { 0,  1}, { 1,  1},
    };

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
        this.r = new Random();
    }

    public int[] myPosition(Board board) {
        return board.player_positions.get(playerId).clone();
    }

    class Score {
        public int mine;
        public int theirs;
        public Score(int _mine, int _theirs) {
            mine = _mine;
            theirs = _theirs;
        }
    }

    private Score score(Board board) {
        int mine = 0;
        int theirs = 0;
        for (int y = 0; y < board.height;++y) {
            for (int x = 0; x < board.width;++x) {
                if (myColor(board.colors[y][x])) {mine += 1;}
                else if (board.colors[y][x] != null) {theirs += 1;}
            }
        }
        return new Score(mine, theirs);
    }

    public Action[] possibleActions(Board state) {
        LinkedList<Action> list = new LinkedList<Action>();
        for (String t : ActionTypes) {
            for (int[] d : ActionDirections) {
                int nextY = myPosition(state)[0] + d[0];
                int nextX = myPosition(state)[1] + d[1];
                if (nextY >= 0 && nextY < state.height && nextX >= 0 && nextX < state.width) {
                    Action action = new Action();
                    action.type = t;
                    action.direction = d;
                    list.add(action);
                }
            }
        }
        return list.toArray(new Action[0]); // topkek, java
    }

    public Board cloneBoard(Board board) {
        Board newBoard = new Board();
        newBoard.width = board.width;
        newBoard.height = board.height;
        newBoard.colors = new String[board.colors.length][];
        for (int i = 0; i < newBoard.colors.length; ++i) {
            newBoard.colors[i] = new String[board.colors[i].length];
            for (int j = 0; j < newBoard.colors[i].length; ++j) {
                if (board.colors[i][j] != null) {
                    newBoard.colors[i][j] = board.colors[i][j];
                }
            }
        }
        newBoard.player_positions = new HashMap<String, int[]>(board.player_positions);
        newBoard.previous_actions = board.previous_actions; // Not actually cloned, I'll never mutate this
        newBoard.turns_left = board.turns_left;
        return newBoard;
    }

    public boolean outOfBounds(Board state, int y, int x) {
        return  (y < 0 || y >= state.height || x < 0 || x >= state.width);
    }

    public boolean myColor(String color) {
        return color != null && color.equals(playerId);
    }

    public int tailSize(Board state, int[] tailDirection) {
        int[] currPos = myPosition(state);
        int sizeAcc = -1;
        while (myColor(state.colors[currPos[0]][currPos[1]])) {
            sizeAcc += 1;
            currPos[0] += tailDirection[0];
            currPos[1] += tailDirection[1];
            if (outOfBounds(state, currPos[0], currPos[1])) {
                return Math.max(sizeAcc, 1);
            }
        }
        return Math.max(sizeAcc, 1);
    }

    public Board nextState(Board state, Action action) {
        int[] currPos = myPosition(state);
        if (action.type.equals("walk")) {
            int[] nextPos = {currPos[0] + action.direction[0], currPos[1] + action.direction[1]};
            if (outOfBounds(state, currPos[0], currPos[1])) {
                return state;
            } else {
                Board newState = cloneBoard(state);
                newState.player_positions.put(playerId, nextPos);
                newState.colors[nextPos[0]][nextPos[1]] = playerId;
                return newState;
            }
        }
        else if (action.type.equals("shoot")) {
            int[] tailDir = {action.direction[0] * -1, action.direction[1] * -1};
            int tail = tailSize(state, tailDir);
            Board newState = cloneBoard(state);
            for (int i = 1; i <= tail; ++i) {
                currPos[0] += action.direction[0];
                currPos[1] += action.direction[1];
                if (outOfBounds(state, currPos[0], currPos[1])) { // TODO check other players
                    return newState;
                }
                newState.colors[currPos[0]][currPos[1]] = playerId;
            }
            return newState;
        }
        else {return state;}
    }

    class TailScore {
        public int total;
        public int effective;
        public int paint;
        public TailScore(int _total, int _effective, int _paint) {
            total = _total;
            effective = _effective;
            paint = _paint;
        }
    }

    public TailScore largestTailSize(Board state) {
        TailScore largest = new TailScore(0,0,0);
        for (int[] ActionDirection : ActionDirections) {
            int[] currPos = myPosition(state);
            int[] tailDir = {ActionDirection[0] * -1, ActionDirection[1] * -1};
            int tail = tailSize(state, tailDir);
            int effective = 0;
            int paint = 0;
            for (int i = 1; i <= tail; ++i) {
                currPos[0] += ActionDirection[0];
                currPos[1] += ActionDirection[1];
                if (!outOfBounds(state, currPos[0], currPos[1])) {
                    effective++;
                    if (!myColor(state.colors[currPos[0]][currPos[1]])) {
                        paint++;
                    }
                }
            }
            if (tail > largest.total) largest.total = tail;
            if (effective > largest.effective) largest.effective = effective;
            if (paint > largest.paint) largest.paint = paint;
        }
        return largest;
    }

    // Secret sauce
    class Weights {
        public double myScoreWeight = 1.0;
        public double theirScoreWeight = -1.0;
        public double totalTailWeight = 0.0;
        public double effectiveTailWeight = 0.1;
        public double paintTailWeight = 0.2;

        private double readEnvVar(String envVar, double defaultValue) {
            String envResult = System.getenv(envVar);
            if (envResult == null) return defaultValue;
            else return Double.parseDouble(envResult);
        }

        public Weights() {
            myScoreWeight = readEnvVar("MY_SCORE", 1.0);
            theirScoreWeight = readEnvVar("THEIR_SCORE", -1.0);
            totalTailWeight = readEnvVar("TOTAL_TAIL", 0.0);
            effectiveTailWeight = readEnvVar("EFFECTIVE_TAIL", 0.1);
            paintTailWeight = readEnvVar("PAINT_TAIL", 0.2);
            System.err.println("Using Weights: " + toString());
        }

        @Override
        public String toString() {
            return "(" + myScoreWeight + ", " +
                    theirScoreWeight + ", " +
                    totalTailWeight + ", " +
                    effectiveTailWeight + ", " +
                    paintTailWeight + ")";
        }
    }

    public Weights weights = new Weights();

    public double value(Board state) {
        Score scores = score(state);
        TailScore tailScores = largestTailSize(state);
        return weights.myScoreWeight*scores.mine +
                weights.theirScoreWeight*scores.theirs +
                weights.totalTailWeight*tailScores.total +
                weights.effectiveTailWeight*tailScores.effective +
                weights.paintTailWeight*tailScores.paint;
    }

    public Action nextMove(Board state) {
        Action[] actions = possibleActions(state);
        double bestValue = Double.NEGATIVE_INFINITY;
        Action bestAction = actions[0];
        for (Action action : actions) {
            double expectedValue = value(nextState(state, action));
            if (expectedValue > bestValue) {
                bestValue = expectedValue;
                bestAction = action;
            } else if (expectedValue == bestValue && bestAction.type.equals("shoot") && this.r.nextBoolean()){
                bestValue = expectedValue;
                bestAction = action;
            }
        }

        return bestAction;
    }

    public static void main(String[] args) {
        Runner.run(new Bot());
    }
}
