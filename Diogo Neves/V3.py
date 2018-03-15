import random
import json
import sys

import multipaint


class MegaBot4000(object):

    move_number = 1
    move_type = "walk"
    direction = [1, 0]

    def __init__(self, player_id):
        self.player_id = player_id

    def next_move(self, state):
        if move_number % 2 == 0:
            move_type = "walk"
            direction = random.choice([[1, 0], [-1, 0], [0, 1], [0, -1]])
        else:
            move_type = "shot"

        move_number += 1
        return {
            "type": move_type,
            "direction": direction,
        }


if __name__ == "__main__":
    multipaint.run(MegaBot4000)