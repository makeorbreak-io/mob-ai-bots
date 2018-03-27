import random
import json
import sys

import multipaint

board
boardp
type = ["walk", "shoot"]
direction = [[1, 0], [-1, 0], [0, 1], [0, -1]]
mposition
players = []
ix
jx
idx

class MegaBot4000(object):
    def __init__(self, player_id):
        self.player_id = player_id
        create_board(200)
        idx = player_id

    def next_move(self, state):
        j = json.loads(state)
        ix = j["height"]
        jx = j["height"]
        players.clear()
        for attribute, value in j["player_positions"]:
            if attribute == idx:
                mposition = value
            else:
                boardp[value[0]][value[1]] = 1

        update_board(j["colors"])
        v = -500
        t = 0
        d = 0
        for tt in type:
            for dd in diretion:
                xx = val(tt, dd)
                if xx > v:
                    v = xx
                    t = tt
                    d = dd

        return {
            "type": type[t],
            "direction": direction[d],
        }

def create_board(a):
    board = [[0 for x in range(a)] for y in range(a)]

def val(ty, dir):
    v = 0
    i = mposition[0]
    j = mposition[1]
    if type == "walk":
        ki = i - dir[0]
        kj = j - dir[1]
        while 0<=ki<ix and 0<=kj<jx and board[ki][kj] == idx and boardp[ki][kj] != 1:
            v = v+1
            ki = i - dir[0]
            kj = j - dir[1]
        return v
    else:
        ki = i - dir[0]
        kj = j - dir[1]
        while 0<=ki<ix and 0<=kj<jx and board[ki][kj] == idx and boardp[ki][kj] != 1:
            v = v+1
            ki = i - dir[0]
            kj = j - dir[1]
        return v*2

def update_board(bb):
    for i in range(ix):
        for j in range(jx):
            board[i][j]=bb[i][j]
            boardp[i][j]=0

if __name__ == "__main__":
    multipaint.run(MegaBot4000)
