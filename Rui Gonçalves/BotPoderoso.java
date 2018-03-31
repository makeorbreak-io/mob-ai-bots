import multipaint.Action;
import multipaint.Board;
import multipaint.Runner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class Bot implements multipaint.Bot {
  private static final boolean DEBUG = false;
  private static <A> void assertEquals(A a1, A a2) {
    if(!a1.equals(a2)) throw new AssertionError(a1 + " != " + a2);
  }

  private static final String[] ActionTypes = new String[]{"shoot", "walk"};
  private static final int[][] ActionDirections = new int[][]{
    {-1, -1}, { 0, -1}, { 1, -1},
    {-1,  0},           { 1,  0},
    {-1,  1}, { 0,  1}, { 1,  1},
  };
  private static final int[] NullDirection = { 0, 0 };

  private String playerId;

  private static class Score implements Comparable<Score> {
    int score; int weightedScore;

    Score(int score, int weightedScore) {
      this.score = score; this.weightedScore = weightedScore;
    }

    public int compareTo(Score o) {
      return score != o.score ? Integer.compare(score, o.score) : Integer.compare(weightedScore, o.weightedScore);
    }
  }

  private static class ActionResult {
    int[] posDiff;
    Map<int[], String> cellsPainted;
    int score;

    ActionResult() {
      cellsPainted = new HashMap<>();
    }
  }

  private class State {
    Board board;
    Stack<ActionResult> history;
    int[] currPos;
    int score;

    State(Board b) {
      board = b;
      history = new Stack<>();
      currPos = b.player_positions.get(playerId);
      for(String[] row : b.colors) {
        for(String cell : row) {
          if(cell != null) {
            if(cell.equals(playerId)) score++;
            else score--;
          }
        }
      }
    }

    boolean isInvalidAction(int[] dir) {
      return !isValidMove(currPos, dir);
    }

    void doAction(String type, int[] dir) {
      ActionResult res = new ActionResult();
      if(type.equals("walk")) {
        res.posDiff = dir;
        currPos[0] += dir[0]; currPos[1] += dir[1];
        paint(res, currPos);
      } else {
        res.posDiff = NullDirection;
        int[] cur = currPos.clone();
        int turnsLeft = board.turns_left;
        while((turnsLeft--) >= 0 && isValidMove(cur, dir)) {
          cur[0] += dir[0]; cur[1] += dir[1];
          paint(res, cur);
        }
      }
      history.push(res);
      board.turns_left--;
    }

    void undo() {
      ActionResult res = history.pop();
      currPos[0] -= res.posDiff[0]; currPos[1] -= res.posDiff[1];
      score -= res.score;
      for(Map.Entry<int[], String> e : res.cellsPainted.entrySet()) {
        board.colors[e.getKey()[1]][e.getKey()[0]] = e.getValue();
      }
      board.turns_left++;
    }

    private boolean isValidMove(int[] pos, int[] dir) {
      return pos[0] + dir[0] >= 0 && pos[0] + dir[0] < board.width &&
              pos[1] + dir[1] >= 0 && pos[1] + dir[1] < board.height;
    }

    private void paint(ActionResult res, int[] pos) {
      String cellPlayer = board.colors[pos[1]][pos[0]];
      if(playerId.equals(cellPlayer)) return;

      res.cellsPainted.put(pos.clone(), cellPlayer);
      board.colors[pos[1]][pos[0]] = playerId;
      res.score += (cellPlayer == null ? 1 : 2);
      score += (cellPlayer == null ? 1 : 2);
    }
  }

  private Score dfs(State st, int maxDepth) {
    if(st.board.turns_left == 0 || maxDepth == 0) {
      if(DEBUG) {
        for(int i = 0; i < 7 - maxDepth; i++) System.err.print("  ");
        System.err.println((7 - maxDepth + 1) + ". eval: " + st.score);
      }
      return new Score(st.score, st.score);
    }
    Score best = new Score(0, 0);
    for(int[] dir : ActionDirections) {
      if(st.isInvalidAction(dir)) continue;
      for(String type : ActionTypes) {
        st.doAction(type, dir);
        if(DEBUG) {
          for(int i = 0; i < 7 - maxDepth; i++) System.err.print("  ");
          System.err.println((7 - maxDepth + 1) + ". "  + type + " [" + dir[0] + ", " + dir[1] + "] (score: " + st.score + ")");
        }
        Score score = dfs(st, maxDepth - 1);
        score.weightedScore += Math.pow(st.history.peek().score, maxDepth);
        if(score.compareTo(best) > 0) { best = score; }
        st.undo();
      }
    }
    return best;
  }

  // ---

  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  public Action nextMove(Board board) {
    String copy;
    if(DEBUG) {
      copy = Arrays.deepToString(board.colors);
    }

    Score bestScore = new Score(0, 0);
    Action a = new Action();
    State st = new State(board);
    if(DEBUG) {
      System.err.println("player: " + playerId);
      System.err.println("start (" + st.score + ")");
    }
    for(int[] dir : ActionDirections) {
      if(st.isInvalidAction(dir)) continue;
      for(String type : ActionTypes) {
        st.doAction(type, dir);
        if(DEBUG) {
          System.err.println("1. "  + type + " [" + dir[0] + ", " + dir[1] + "] (score: " + st.score + ")");
        }
        Score score = dfs(st, 4);
        if(score.compareTo(bestScore) > 0) {
          bestScore = score;
          a.type = type; a.direction = dir;
        }
        st.undo();
      }
    }
    if(DEBUG) {
      assertEquals(st.history.isEmpty(), true);
      assertEquals(st.currPos, board.player_positions.get(playerId));
      assertEquals(copy, Arrays.deepToString(board.colors));
      System.err.println("bestScore: " + bestScore.score + "/" + bestScore.weightedScore);
    }
    return a;
  }

  public static void main(String[] args) {
    Runner.run(new Bot());
  }
}
