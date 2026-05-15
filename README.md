![A header image with a logo and the word scalable](https://cdn.modrinth.com/data/cached_images/5d6737bb3322db6fc72111ee9f177ac9c833abfb.png)

Scalable is a server-side optimization mod for Fabric that keeps your server running smoothly under load by intelligently budgeting how much work gets done each tick.

Rather than letting the server fall behind on everything at once, Scalable prioritizes what matters most, the chunks and entities closest to players, and defers the rest to the next available tick. The result is a server that stays responsive even when it's struggling.

> **Note:** Scalable smooths out lag spikes and buys your server breathing room. It won't eliminate lag caused by something consistently eating your entire tick budget (e.g. extreme entity counts), but it will make things significantly more bearable while you address the root cause.

---

## Installation

Requires the latest version of **Fabric Loader** and **Fabric API**. Drop the `.jar` into your `mods` folder. No client installation required.

---

## What does Scalable actually do?

At the start of every server tick, Scalable starts a timer. Chunk ticking and entity ticking proceed normally. But if the budget runs out mid tick, remaining work is **deferred** to the next tick rather than causing the entire tick to overrun.

Scalable sets a time limit and stops when it's hit. The unprocessed remainder is picked back up next tick. Under normal load, everything is ticked every tick as usual. Under heavy load, less important things (far away chunks, distant entities) are delayed, but the things closest to players stay responsive.

### Chunk ticking

Chunks are sorted and processed in this order each tick:

1. **Deferred chunks** from the previous tick (carried over work gets priority)
2. **Priority chunks** those loaded by portals, ender pearls.
3. **Player-assigned chunks** remaining chunks are distributed across players using a **round-robin** scheduler, with each player's queue sorted by proximity to their *projected future position* (based on their current velocity).
4. **Unclaimed chunks** any ticking chunks not assigned to a specific player

If the budget runs out before all chunks are processed, the leftovers are deferred.

### Entity ticking

Entities are also budget-aware and player-assigned:

1. **Players** are always ticked first (priority queue)
2. **Other entities** are assigned to their nearest player and ticked in round-robin order
3. If the budget runs out, remaining entities are simply skipped for that tick

This means that even under heavy load, **players and their immediate surroundings are always prioritized**. You can still open inventories, interact with the world, and move around smoothly. Similar to how mods like TT20 keep the server interactive even when it's overloaded. The advantage here is that the game is actually still running in a broader sense.

---

## Configuration

Config file is at `config/scalable.json` and is created automatically on first run.

```json
{
  "budget_ms": 40,
  "max_deferred": 2048
}
```

| Field | Default | Description |
|---|---|---|
| `budget_ms` | `40` | Milliseconds per tick available for chunk and entity ticking. Set to `-1` for automatic mode (also 40ms). |
| `max_deferred` | `2048` | Maximum number of chunks that can be held in the deferred queue at once. Excess entries are dropped (oldest first). |

### In-game commands

All commands require operator.

| Command | Description |
|---|---|
| `/scalable budget auto` | Switch to automatic budget mode (40ms) |
| `/scalable budget <ms>` | Set a fixed budget in milliseconds (1–50) |
| `/scalable budget query` | Show current budget mode, MSPT, and TPS |
| `/scalable deferred <max>` | Set the max deferred chunk queue size (0–65536) |

Changes made via commands are saved to `scalable.json` immediately.

---

## Limitations

- **Entities are not deferred between ticks** if the budget runs out mid-entity-loop, those entities simply don't tick that frame. Deferred entity carry-over is not yet implemented.
- **Consistent heavy lag** from entities or other non-chunk sources may still cause TPS drops until those sources are addressed.
