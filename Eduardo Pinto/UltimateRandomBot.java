import java.util.Random;
import multipaint.Action;
import multipaint.Board;
import multipaint.Runner;

public class Bot implements multipaint.Bot {
  private String playerId;
  public Random r;

  public static String[] ActionTypes = new String[]{"shoot", "walk"};
  public static int[][] ActionDirections = new int[][]{
    {-1, -1}, { 0, -1}, { 1, -1},
    {-1,  0},           { 1,  0},
    { 1,  1}, { 0,  1}, { 1,  1},
  };

  public void setPlayerId(String playerId) {
    this.playerId = playerId;
    this.r = new Random();
  }

  public Action nextMove(Board state) {

    System.err.println("Turns left:" + state.turns_left);
    Action a = new Action();

    a.type = this.ActionTypes[1];
    int attempts = 0;
    do {
      a.direction = this.ActionDirections[this.r.nextInt(8)];
      System.err.println("new direction" + a.direction[0] + " - " + a.direction[1]);
      attempts++;
    } while(attempts < 10 && !isEmptyOrOpponent(a.direction, state.colors, state.player_positions.get(playerId)));

    return a;
  }

  public boolean isEmptyOrOpponent(int[] direction, String[][] colors, int[] currentPos) {
    int newX = currentPos[0]+direction[0];
    int newY = currentPos[1]+direction[1];

    if (newX >= colors.length || newX < 0 || newY >= colors[0].length || newY < 0) return false;

    System.err.println( "New pos " + newX + " - " + newY);
    //System.err.println( colors[newX][newY]);
    return null == colors[newX][newY] || !playerId.equals(colors[newX][newY]);
  }

  public static void main(String[] args) {
    Runner.run(new Bot());
  }
}
