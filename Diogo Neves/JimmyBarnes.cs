using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using mob_ai_csharp.multipaint;
using Newtonsoft.Json;

namespace mob_ai_csharp
{
    public class Bot : IBot
    {
        public List<string> ActionTypes = new List<string>() { "shoot", "walk" };

        public List<List<int>> ActionDirections = new List<List<int>>()
        {
            new List<int>{-1, -1}, // <-^-
            new List<int>{ 0, -1}, // ^-
            new List<int>{ 1, -1}, // ^-->
            new List<int>{-1,  0}, // <-
            new List<int>{ 1,  0}, // ->
            new List<int>{ 1,  1}, // v-<-
            new List<int>{ 0,  1}, // v-
            new List<int>{ 1,  1}, // v-->
        };

        private string PlayerId;

        public Random R;

        private Point myCurrentPos;

        public static List<string> errors = new List<string>();

        private static void Main(string[] args)
        {
            Runner.Run(new Bot());
        }

        public void SetPlayerId(string playerId)
        {
            this.PlayerId = playerId;
            this.R = new Random();
        }

        public Move NextMove(Board boardState)
        {
            myCurrentPos = new Point(boardState.player_positions[this.PlayerId][0], boardState.player_positions[this.PlayerId][1]);

            var atackMove = GetAttackMove(boardState);

            if (atackMove != null)
            {
                Console.Error.WriteLine("Attack");
                return atackMove;
            }

            var point2go = BestMovePoint(boardState);

            Console.Error.WriteLine("Move");

            return GetDirectionForPoint(point2go);
        }

        private Move GetDirectionForPoint(Point point2Go)
        {
            //Without walls detection

            var directions = GetDirectionsFuncs();
            var minDistance = (double)int.MaxValue;
            var pos = 0;

            for (int i = 0; i < directions.Count; i++)
            {
                var newPosition = directions[i](myCurrentPos);

                var distance = Math.Sqrt(Math.Pow(point2Go.X - newPosition.X, 2) +
                                         Math.Pow(point2Go.Y - newPosition.Y, 2));

                if (minDistance > distance)
                {
                    minDistance = distance;
                    pos = i;
                }
            }

            return new Move()
            {
                Direction = ActionDirections[pos],
                // walk
                Type = ActionTypes[1]
            };
        }

        private Point BestMovePoint(Board boardState)
        {
            var possiblePointsToGo = new List<Point>();
            var bestPlayCount = 0;

            for (int i = 0; i < boardState.width; i++)
            {
                for (int j = 0; j < boardState.height; j++)
                {
                    var thisPoint = new Point(i, j);

                    var play = Count(boardState, thisPoint, true).OrderByDescending(x => x).FirstOrDefault();

                    //TODO: Check for null
                    if (play > bestPlayCount)
                    {
                        bestPlayCount = play;
                        Console.Error.WriteLine($"Possible play ({i},{j}) added with {play} count");
                        possiblePointsToGo.Add(thisPoint);
                    }
                }
            }

            return possiblePointsToGo.Last();
        }

        private Move GetAttackMove(Board boardState)
        {
            var bias = 3;

            var board = boardState.colors;

            var count = Count(boardState, myCurrentPos);

            var revesedMoves = new List<List<int>>(ActionDirections);
            revesedMoves.Reverse();

            for (int i = 0; i < count.Count; i++)
            {
                if (count[i] > bias)
                {
                    return new Move()
                    {
                        Type = ActionTypes[0],
                        //Check if: Map this attack direction plz thanks
                        Direction = revesedMoves[i],
                    };
                }
            }

            // Change bias
            return null;
        }

        public List<int> Count(Board boardState, Point initialPoint, bool log = false)
        {
            var board = boardState.colors;

            // Check if X and Y are Qol
            // TODO: plz change this
            var myColor = board[0][0];

            var directions2Check = GetDirectionsFuncs();
            var reversedDirections = GetDirectionsFuncsReversed();

            var counterList = new List<int>();

            for (int i = 0; i < directions2Check.Count; i++)
            {
                var x = directions2Check[i](initialPoint);
                var y = reversedDirections[i](initialPoint);

                var count = 0;

                // Verify if this is ok.
                while (x.Y >= 0 && // Inside Bounds Check
                       x.X >= 0 && // Inside Bounds Check
                       x.Y < board.Count && // Inside Bounds Check
                       x.X < board[0].Count && // Inside Bounds Check (Assuming it's a square...)
                       board[x.Y][x.X] ==
                       myColor && // My Color, how many colores behind me
                       y.Y >= 0 && // Inside Bounds Check
                       y.X >= 0 && // Inside Bounds Check
                       y.Y < board.Count && // Inside Bounds Check
                       y.X < board[0].Count && // Inside Bounds Check
                       board[y.Y][y.X] != myColor) // Not my color so I can shot
                {
                    x = directions2Check[i](x);
                    y = reversedDirections[i](y);

                    count++;
                }

                //if (count > 0 && log)
                //    Console.Error.WriteLine($"Count {count} for: {i} in ({initialPoint.Y},{initialPoint.X})");

                counterList.Add(count);
            }

            return counterList;
        }

        public List<Func<Point, Point>> GetDirectionsFuncs()
        {
            return
                ActionDirections
                    .Select(x => new Func<Point, Point>(y =>
                                 new Point(y.X + x[0], y.Y + x[1])))
                    .ToList();
        }

        public List<Func<Point, Point>> GetDirectionsFuncsReversed()
        {
            var directionFuncs = GetDirectionsFuncs();
            directionFuncs.Reverse();
            return directionFuncs;
        }
    }
}