# Opening analysis database

Open **Analysis → Analysis database settings** (分析 → 分析数据库设置).
The database is disabled initially. Its default file is
`~/.lizzieyzy/analysis.sqlite3` (`~` is the current user's home directory).
The default range is move 0 through move 20, inclusive. Setup stones do not
count as played moves; a played pass does. This is a local SQLite database,
independent of SGF files. No database server is required.

Settings control automatic reading, saving after completion, rotation/reflection
matching, and skipping ordinary searches whose visit targets are already satisfied.
Sufficient pass results are reused independently; use **Ignore database and
reanalyze this position** to force fresh ordinary and pass analysis.
The model/configuration file fields are fallbacks for commands that cannot be
inspected locally. For remote engines they must point to local copies of the
actual remote model and effective configuration (including command-line overrides).
Changing settings does not delete existing records.

## Identity and transformations

The key contains board size, black/white stones, player to move, and the current
simple-ko forbidden point. Move order and consecutive-pass history are deliberately
ignored, as requested. Consequently this identifies a reusable board evaluation,
not the full history-dependent KataGo state; different histories can still produce
different fresh estimates.

There are eight rotation/reflection transforms for square boards. Rectangular
boards use the four transforms preserving their dimensions. A deterministic
canonical orientation is chosen for the board and ko point. Candidate coordinates,
PVs, symmetry-source coordinates and ownership arrays are transformed with it,
and mapped back when restored. Pass and resign remain unchanged. Colors are not
exchanged.

The context contains the SHA-256 of the model's decompressed contents, any human
model, normalized full rules, komi, relevant configuration and runtime search
parameters (including PDA). Executable path and engine version are not identities.
A readable model and complete engine rules are required. Model hashes are reused
while the path, size and modification time remain unchanged. Always restart the
engine after replacing its loaded model/configuration on disk. Database format 1
also fixes the pass-value meaning: an independent post-pass root, with its leading
candidate's score converted to the original player's perspective.

## Saving and restoring

Only completed analysis is saved: ordinary limits/automatic-analysis conditions,
required SGF/candidate top-ups, and enabled pass-value visits must all finish.
Unlimited manual pondering is not treated as completed. Ordinary analysis and
pass analysis occupy independent rows and update only when the new visit count
is at least the stored count. Root statistics and ordinary ranking stay together;
supplemental candidate values cannot replace ordinary root statistics. Different
runs' visits are never added.

When a position is entered, lookup runs off the UI/engine threads. Restored data is
bound to the current position and engine context; results from a lookup invalidated
by navigation, settings changes or stopping are ignored. The comment panel marks
database hits. The last-move loss is derived from the currently open SGF's previous
node; user comments and SGF-branch identity are not copied from another game.
Lookup also runs after navigation while analysis is stopped, without starting a
search. If navigation invalidates a lookup that was delaying analysis startup,
startup resumes for the current position. After a completed save, the current
position displays the database's merged ordinary/pass results; disabling reads
or explicitly ignoring the position prevents this automatic restoration.

Ordinary and pass components are checked independently against the active targets.
A full hit can complete automatic analysis without a new search. A missing pass
component starts only the private pass engine. A missing ordinary component runs
the ordinary engine while reusing a sufficient pass result. SGF continuations and
candidate top-ups are checked against the current game's targets before declaring
completion. A time-only condition is not considered satisfied by a visit-count
cache. Search trees are not stored; a fresh engine does not continue at the cached
visit count.

**Ignore database and reanalyze this position** bypasses database reads for the
current node for this session, clears its in-memory analysis cache, and starts
analysis. New completed results can still upgrade the database.
**Clear analysis database** deletes the external records after confirmation; it
does not delete SGFs. Enabled automatic saving can populate it again later.

SQLite transactions, WAL and a background writer keep completed ordinary/pass
updates atomic. Database errors leave live engine analysis available and are
reported in the comment panel. Unrecognized future database formats and corrupt
files are not overwritten. Pending database jobs are given up to ten seconds to
finish at normal JVM shutdown.
