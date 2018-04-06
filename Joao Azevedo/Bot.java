import multipaint.Action;
import multipaint.Board;
import multipaint.Runner;

import java.util.*;

public class Bot implements multipaint.Bot {
    private String playerId;
    private String[] players = null;
    public Random r;

    public class Shot {
        int range;
        int[] direction;
        int[] position;
        boolean disabled;
    }

    public class MyAction {
        String type;
        int[] direction;

        @Override
        public int hashCode() {
            int s = 1;
            if (type.equals("shoot"))
                s = 2;
            return s + 31 * (this.direction[0] + 2) + 31 * 31 * (this.direction[1] + 2);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MyAction) {
                MyAction action = (MyAction) obj;
                return this.type.equals(action.type) && this.direction[0] == action.direction[0] && this.direction[1] == action.direction[1];
            }
            return false;
        }

        public MyAction(Action a) {
            this.type = a.type;
            this.direction = a.direction;
        }
    }

    public static String[] ActionTypes = new String[]{"shoot", "walk"};
    public static int[][] ActionDirections = new int[][]{
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1},
    };

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
        this.r = new Random();
    }

    public int score(Board state) {
        int tot = 0;
        for (int i = 0; i < state.colors.length; i++) {
            for (int j = 0; j < state.colors[i].length; j++) {
                if (state.colors[i][j] != null) {
                    if (state.colors[i][j].equals(this.playerId))
                        tot++;
                    else if (state.colors[i][j] != null)
                        tot--;
                }
            }
        }
        if (tot > 0)
            return 1;
        return 0;
        //return tot;
    }

    int[][] paintedInTurn = null;
    private Action[] moves = null;

    public Action randomMove(Board state, String player) {
        if (moves == null) {
            moves = new Action[16];
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 8; j++) {
                    Action a = new Action();
                    a.type = this.ActionTypes[i];
                    a.direction = this.ActionDirections[j];
                    moves[i * 8 + j] = a;
                }
            }
        }

        Action a = moves[0];
        int[] pos = state.player_positions.get(player);
        int i = 0;
        for (; i < moves.length; i++) {
            if (moves[i].type == "shoot" && rangeForShot(state, player, state.player_positions.get(player), moves[i].direction) == 1)
                continue;
            if ((pos[0] + moves[i].direction[0]) >= 0 &&
                    (pos[0] + moves[i].direction[0]) < state.height &&
                    (pos[1] + moves[i].direction[1]) >= 0 &&
                    (pos[1] + moves[i].direction[1]) < state.width) {
                a = moves[i];
                break;
            }
        }
        int j = 1;
        i++;
        for (; i < moves.length; i++) {
            if (moves[i].type == "shoot" && rangeForShot(state, player, state.player_positions.get(player), moves[i].direction) == 1)
                continue;
            if ((pos[0] + moves[i].direction[0]) >= 0 &&
                    (pos[0] + moves[i].direction[0]) < state.height &&
                    (pos[1] + moves[i].direction[1]) >= 0 &&
                    (pos[1] + moves[i].direction[1]) < state.width) {
                j++;
                int k = r.nextInt(j);
                if (k < 1)
                    a = moves[i];
            }
        }
        return a;
    }

    public int rangeForShot(Board state, String player, int[] pos, int[] direction) {
        int range = 0;
        int i = pos[0] - direction[0];
        int j = pos[1] - direction[1];
        while (i >= 0 && i < state.height && j >= 0 && j < state.width && state.colors[i][j] != null && state.colors[i][j].equals(player)) {
            range++;
            i -= direction[0];
            j -= direction[1];
        }
        return Math.max(range, 1);
    }

    public Board next(Board state, Map<String, Action> moves) {
        // Copy previous board. Don't worry about previous moves.
        Board next = new Board();
        next.turns_left = state.turns_left - 1;
        next.width = state.width;
        next.height = state.height;
        next.colors = new String[next.height][next.width];
        for (int i = 0; i < next.colors.length; i++)
            System.arraycopy(state.colors[i], 0, next.colors[i], 0, next.colors[0].length);
        next.player_positions = new HashMap<>();
        for (Map.Entry<String, int[]> playerPosition: state.player_positions.entrySet()) {
            int[] pos = new int[2];
            pos[0] = playerPosition.getValue()[0];
            pos[1] = playerPosition.getValue()[1];
            next.player_positions.put(playerPosition.getKey(), pos);
        }

        // Apply all movement actions.
        for (Map.Entry<String, Action> move: moves.entrySet()) {
            String player = move.getKey();
            Action action = move.getValue();
            if (action.type == "walk") {
                int[] pos = next.player_positions.get(player);
                pos[0] = Math.min(Math.max(pos[0] + action.direction[0], 0), next.height - 1);
                pos[1] = Math.min(Math.max(pos[1] + action.direction[1], 0), next.width - 1);
            }
        }

        // Check for squares with two or more players in them, and undo actions from them.
        List<String> removedPlayers = new ArrayList<>();
        for (int i = 0; i < players.length; i++) {
            int[] p1 = next.player_positions.get(players[i]);
            for (int j = i + 1; j < players.length; j++) {
                int[] p2 = next.player_positions.get(players[j]);
                if (p1[0] == p2[0] && p1[1] == p2[1]) {
                    removedPlayers.add(players[i]);
                    removedPlayers.add(players[j]);
                }
            }
        }
        for (String player: removedPlayers) {
            moves.remove(player);
            next.player_positions.put(player, state.player_positions.get(player));
        }

        // Keep track of squares painted this turn.
        if (paintedInTurn == null) {
            paintedInTurn = new int[next.height][next.width];
            for (int i = 0; i < next.height; i++) {
                Arrays.fill(paintedInTurn[i], state.turns_left);
            }
        }

        // Paint squares occupied by avatars.
        for (Map.Entry<String, int[]> playerPosition: next.player_positions.entrySet()) {
            String player = playerPosition.getKey();
            int[] pos = playerPosition.getValue();
            next.colors[pos[0]][pos[1]] = player;
            paintedInTurn[pos[0]][pos[1]] = next.turns_left;
        }

        // Apply shooting actions.
        Map<String, Shot> shots = new HashMap<>();
        for (Map.Entry<String, Action> move: moves.entrySet()) {
            String player = move.getKey();
            Action action = move.getValue();
            if (action.type == "shoot") {
                Shot shot = new Shot();
                shot.direction = action.direction;
                shot.range = 0;
                shot.position = new int[2];
                int[] pos = next.player_positions.get(player);
                shot.position[0] = pos[0];
                shot.position[1] = pos[1];
                shot.disabled = false;
                shot.range = rangeForShot(next, player, shot.position, shot.direction);
                shots.put(player, shot);
            }
        }
        while (!shots.isEmpty()) {
            // Advance all shots one square.
            for (String player: shots.keySet()) {
                Shot shot = shots.get(player);
                shot.position[0] += shot.direction[0];
                shot.position[1] += shot.direction[1];
                shot.range--;
            }

            // Disable shots.
            Set<String> disabledShots = new HashSet<>();
            for (String player: shots.keySet()) {
                Shot shot = shots.get(player);
                if (!shot.disabled) {
                    if (shot.position[0] < 0 || shot.position[0] >= next.height || shot.position[1] < 0 ||
                            shot.position[1] >= next.width || paintedInTurn[shot.position[0]][shot.position[1]] == next.turns_left) {
                        shot.disabled = true;
                    }
                }
                if (!shot.disabled) {
                    for (String oPlayer : shots.keySet()) {
                        if (player == oPlayer)
                            continue;
                        Shot oShot = shots.get(oPlayer);
                        if (shot.position[0] == oShot.position[0] && shot.position[1] == oShot.position[1]) {
                            shot.disabled = true;
                            oShot.disabled = true;
                        }
                    }
                }
                if (!shot.disabled) {
                    for (String oPlayer : next.player_positions.keySet()) {
                        int[] pos = next.player_positions.get(oPlayer);
                        if (shot.position[0] == pos[0] && shot.position[1] == pos[1]) {
                            shot.disabled = true;
                            break;
                        }
                    }
                }
                if (shot.disabled)
                    disabledShots.add(player);
            }
            for (String player: disabledShots)
                shots.remove(player);

            // Paint squares of remaining shots.
            for (String player: shots.keySet()) {
                Shot shot = shots.get(player);
                next.colors[shot.position[0]][shot.position[1]] = player;
                paintedInTurn[shot.position[0]][shot.position[1]] = next.turns_left;
            }

            // Remove shots whose range have reached 0.
            Set<String> shotsFinalRange = new HashSet<>();
            for (String player: shots.keySet()) {
                Shot shot = shots.get(player);
                if (shot.range == 0)
                    shotsFinalRange.add(player);
            }
            for (String player: shotsFinalRange)
                shots.remove(player);
        }

        return next;
    }

    public Action nextMove(Board state) {
        long s = System.nanoTime();

        // Extract all players.
        if (this.players == null)
            this.players = state.player_positions.keySet().toArray(new String[state.player_positions.size()]);

        Map<MyAction, Long> wins = new HashMap<>();

        int runs = 0;
        long best = Long.MIN_VALUE;
        Action bestAction = randomMove(state, playerId);

        long e = s;
        while (e - s < 400000000l) {
            runs++;
            Action myMove = randomMove(state, playerId);
            Board next = state;
            do {
                Map<String, Action> actions = new HashMap<>();
                for (String player: this.players) {
                    if (player == playerId && next.turns_left == state.turns_left)
                        actions.put(player, myMove);
                    else
                        actions.put(player, randomMove(next, player));
                }
                next = next(next, actions);
            } while (next.turns_left > 0);
            MyAction myAction = new MyAction(myMove);
            long prevScore = 0;
            if (wins.containsKey(myAction))
                prevScore = wins.get(myAction);
            long nextScore = prevScore + score(next);
            wins.put(myAction, nextScore);
            e = System.nanoTime();
        }

        for (Map.Entry<MyAction, Long> act: wins.entrySet()) {
            MyAction action = act.getKey();
            long w = act.getValue();
            if (w > best) {
                best = w;
                bestAction = new Action();
                bestAction.type = action.type;
                bestAction.direction = action.direction;
            }

//            System.err.println(action.type + " [" + action.direction[0] + "," + action.direction[1] + "] = " + w);
        }

//        System.err.println("Ran " + runs + " simulations");

        return bestAction;
    }

    public static void main(String[] args) {
        Runner.run(new Bot());
    }
}