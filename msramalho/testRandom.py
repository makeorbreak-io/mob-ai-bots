import sys
import os
import json
import random
import operator
from timeit import default_timer
from itertools import product
from copy import deepcopy
from collections import defaultdict
import numpy as np
# from time import sleep

import multipaint

# function names camelCase -  properties names snake_case

#---------------------------------------------UTILS

random.seed()


def p(string=""):  # pragma: no cover
    print(string, file=sys.stderr)

# https://gist.github.com/cgoldberg/2942781


class Timer(object):  # pragma: no cover
    def __init__(self, name="", verbose=True):
        self.name = " for %s" % name
        self.verbose = verbose
        self.timer = default_timer

    def __enter__(self):
        self.start = self.timer()
        return self

    def __exit__(self, *args):
        end = self.timer()
        self.elapsed_secs = end - self.start
        self.elapsed = self.elapsed_secs * 1000  # millisecs
        if self.verbose:
            p("elapsed time%s: %f ms, %f s" % (self.name, self.elapsed, self.elapsed_secs))


#---------------------------------------------STATE
# height = number of rows
# width  = number of columns
# player position: {x: , y: } where x is the row (height) and y is the column (width)

possible_directions = [
    np.array([-1, -1]), np.array([-1,  0]), np.array([-1,  1]),
    np.array([0, -1]), np.array([0,  1]),
    np.array([1, -1]), np.array([1,  0]), np.array([1,  1])
]


class State:
        # me(my id), turns_left, rows, columns, players (dict of id=>(x,y)), board(ndarray), previous_actions (only if specified)
    def __init__(self, s, me, previous_actions=False):
        self.turns_left = int(s["turns_left"])
        self.rows = int(s["height"])
        self.columns = int(s["width"])

        # get numpy board from matrix of strings, must come before self.players due to self.player_ids
        self.loadBoard(s["colors"], s["player_positions"].keys())

        self.me = self.player_ids[me]

        # convert the json into a dict of {player1Name: (x, y), ...}
        self.players = {self.player_ids[p]: np.array([s["player_positions"][p][0], s["player_positions"][p][1]])
                        for p in s["player_positions"].keys()}

        # optional (because this may be irrelevant and occupies a lot of space)
        self.previous_actions = s["previous_actions"] if previous_actions else []

        # load the player scores
        self.loadPlayerScores()

    # generate board - (-1 is for empty, 0 is for objects, 1 to \inf is for players' colors)
    def loadBoard(self, colors, player_names):
        self.player_ids = {}  # {-1: "neutral", 0: "object"}  # dict mapping player ids into names, useful to know a player from its number in the board
        temp = np.array(colors)
        condlist = [temp == None]
        choicelist = [-1]
        for i, player in enumerate(player_names):
            condlist.append(temp == player)
            choicelist.append(i + 1)
            self.player_ids[player] = i + 1  # update the player_ids dict
        self.board = np.select(condlist, choicelist)

    # given a game state (self) and a player id, not name, get the valid moves for that player
    def getValidMovesForPlayer(self, player):
        pos = self.players[player]  # (line, column) or (x, y)
        valid = []
        for d in possible_directions:
            if self.insideBoardAfterMove(pos, d):
                # t = d.tolist()  # temp, just so operation happens only once
                valid.extend([{"type": "shoot", "direction": d}, {"type": "walk", "direction": d}])
        return valid

    # return True if the position p, after moving in direction d, is inside the board
    def insideBoard(self, p):
        return (0 <= p[0] and p[0] < self.rows) and (0 <= p[1] and p[1] < self.columns)

    # return True if the position p, after moving in direction d, is inside the board
    def insideBoardAfterMove(self, pos, direction):
        p = pos + direction
        return self.insideBoard(p)

    # load self.scores with a dict of {player=>score}
    def loadPlayerScores(self):
        s1, s2 = np.unique(self.board, return_counts=True)  # unique, counts
        self.scores = dict(zip(s1, s2))

    # get all the combinations of moves for all the players, for N players -> max is 16^N (256 for 2 players)
    def getAllMoveCombinations(self):
        return list(product(*[self.getValidMovesForPlayer(p) for p in self.players.keys()]))

    # given a position, a color, and a direction, count cells of the same color in the opposite direction
    def shotBackCount(self, pos, direction, color):
        res = 0
        bp = pos - direction  # back pos
        while self.insideBoard(bp) and self.board[bp[0], bp[1]] == color:
            bp -= direction  # back pos
            res += 1
        return max(res, 1)  # minimum shot size is 1

    # given a position, a direction, and the max value for it, calculate the distance to the edges [0---pos---end]
    def distanceToEnd(self, pos, direction, end):
        if direction == -1:  # backwards is the distance to the start, pos - 0
            return pos
        elif direction == 1:  # forwards is the distance to the end, end - pos
            return end - pos
        else:  # 0 means it stays put, return more than the max, in any direction
            return self.columns + self.rows

    # given a position and a direction return the shot range
    # minimum of the distance in each of the axis to the border
    def shotRange(self, pos, d):
        # had to take 1 from the rows and columns because it is zero indexed
        return min(self.distanceToEnd(pos[0], d[0], self.rows - 1), self.distanceToEnd(pos[1], d[1], self.columns - 1))

    # given a player and a direction get the range of the shot (max between shot load and board limits)
    def playerShotRange(self, player, d):
        return min(self.shotBackCount(self.players[player], d, player), self.shotRange(self.players[player], d))

    # given a tuple of moves for the players index is 1 to n (can use self.player_ids)
    # apply those moves and return a new state
    # if load scores is True then loadPlayerScores is called
    def getNewState(self, actions):
        state = deepcopy(self)  # create a copy of the current state

        # ---------1. all movement actions are applied

        # 1.0 create useful variables
        # dict of playerIndex: (moveUndone, move) if action is walk
        bot_moves = {i + 1: [False, m] for i, m in enumerate(actions) if m["type"] == "walk"}

        # 1.1 each avatar is placed in its new position
        for id, (_, action) in bot_moves.items():
            state.players[id] += action["direction"]  # sum pos with dir to give new pos

        # 1.2 while there is a square with two or more avatars in it, actions from all avatars in the square are undone
        overlaping = True
        while overlaping:
            overlaping = False
            move_squares = defaultdict(list)
            for pl in state.players:
                move_squares[str(state.players[pl])].append(pl)  # create or extend
            for square, bots in move_squares.items():
                if len(bots) > 1:
                    overlaping = True
                    for b in bots[:]:
                        if b in bot_moves and bot_moves[b][0] == False:
                            bot_moves[b][0] = True  # set undone as True
                            bots.remove(b)
                            state.players[b] -= bot_moves[b][1]["direction"]  # calculate the old position

        # 1.3 paint the squares occupied by all the avatars
        bot_squares = set()  # dict of cell -> bot_id_in_that_cell
        for player_id, pos in state.players.items():
            state.board[pos[0], pos[1]] = player_id  # update the board with the new positions
            bot_squares.add((pos[0], pos[1]))  # tuple and not string for 2.3.3

        # ---------2 all shooting actions are applied

        # 2.1 each action's range is calculated
        # dict of playerIndex: [range, pos, dir, active] if action is shoot
        bot_shots = {i + 1: [self.playerShotRange(i + 1, m["direction"]), self.players[i + 1], m["direction"], True] for i, m in enumerate(actions) if m["type"] == "shoot"}

        # 2.2 consider each shot a projectile that moves one square at a time, starting at the avatar's position
        # 2.3 while there are active shots:
        activeShots = len(bot_shots)
        k = 1  # number of iterations
        paintedInThisTurn = set()  # list of squares that were painted in this turn
        while activeShots > 0:
            # 2.3.1 advance all shots one square
            squares = defaultdict(list)  # dict of cell -> [bot_ids]
            for id, [r, pos, d, active] in bot_shots.items():
                if active:
                    # 2.3.4 disable any shots that have reached their maximum range
                    if k == r + 1:
                        bot_shots[id][3] = False  # set inactive
                        activeShots -= 1  # update active count
                    elif k <= r:  # must be active and within range
                        nPos = pos + k * d  # new position
                        squares[(nPos[0], nPos[1])].append(id)  # use tuple as dict key instead of str for 2.3.3
            # 2.3.2 any shots that share a square with other shots or with any avatars, or that are in squares painted in this turn, are disabled
            for square, bots in squares.copy().items():  # copy because the original will be changed
                if len(bots) > 1 or square in bot_squares or square in paintedInThisTurn:
                    squares.pop(square)  # remove this shot from the future painted shots
                    for b in bots[:]:
                        bot_shots[b][3] = False  # disable this shot
                        activeShots -= 1  # update active count

            # 2.3.3 paint the squares of the remaining active shots
            for l, c in squares:
                state.board[l, c] = squares[(l, c)][0]
                paintedInThisTurn.add((l, c))

            k += 1

        state.loadPlayerScores()

        return state

    # given the current game state and a next state, calculate the value of the next state
    def getGreedyHeuristic(self, next_state):
        totalSquares = self.rows * self.columns
        prev_empty = self.scores[-1]
        next_empty = next_state.scores[-1]

        prev_squares = self.scores[self.me]  # my previous score
        next_squares = next_state.scores[self.me]  # my current score

        prev_opponents = totalSquares - prev_squares
        next_opponents = totalSquares - next_squares
        return 0.7 * (next_squares - prev_squares) - 0.3 * (next_opponents - prev_opponents)
        # opponents = totalSquares - mySquares
        # return 0.6 * mySquares - 0.4 * opponents

    # given a state, get the next move GREEDY
    def getNextMove(self):
        allMoves = self.getAllMoveCombinations()  # tipically 0.1 ms
        moveScores = defaultdict(list)
        best = - self.rows * self.columns - 1  # worse than worst case
        for move in allMoves:
            s = self.getNewState(move)  # typically 0.25 ms
            score = self.getGreedyHeuristic(s)
            moveScores[score].append(move)
            if score > best:
                best = score
        return random.choice(moveScores[best])[self.me - 1]

    def __repr__(self):
        return "turns_left: %d\nplayers: %s,\nboard: %s" % (self.turns_left, self.players, self.board)


#---------------------------------------------BOT

class Bot(object):
    def __init__(self, player_id):
        self.player_id = player_id

    def load_state(self, s):
        self.state = State(s, self.player_id)

    def clearChoice(self, c):  # pragma: no cover
        c["direction"] = c["direction"].tolist()
        return c

    def next_move(self, s):  # pragma: no cover
        self.load_state(s)  # tipically 0.1 ms
        return self.clearChoice(self.state.getNextMove())


if __name__ == "__main__":  # pragma: no cover
    # p(list(combinations([1,2,3,4], 2)))
    multipaint.run(Bot)
