# Backlog of the book

What is not written or not settled, in the order I would take it. `PLAN.md` is the contract (claims, rules,
chapter states, the questions only the author can answer); this file is the work. An item names the section it
touches and what "done" is.

## Corrections waiting on a fact or a run

- **A single-session run for §11.5.** The three figures (0.24, 2.75 and 5.9 µs per 1,000 operations) come from a
  note, and the replay figure from an earlier session than the other two. Done: one run of the three lanes in
  one session, the commit and the command in the text, or the figures removed. (PLAN question 24.)
- **The compiler version in §10.7.** The note behind the capture-checking paragraph names a compiler build for
  the transaction check; the section leaves it out and says the check was not re-run. Done: re-run on a named
  build and state it, or keep the sentence as an account of a note. (Question 22, second half.)
- **A test for an interceptor in front of a bracket.** §7.6 now says the real kernel hides a bracket's body
  from an interceptor, read from the code. Done: a test in okay-tx that puts an observer between a program and
  `Atomic` and asserts what it sees; then §7.6 cites the test. If the answer should be different (an audit that
  sees inside), that is a design item for the kernel, not for the book.
- **A test for the dropped rest in okay-core.** §6.8 states that a rest kept and never called releases nothing.
  okay-scala pins this (`EnsuringSuite`: "neither resumed nor unwound"); okay-core, the kernel the platform runs
  on, has no test in either direction and nothing in its backlog. Done: the test exists, and §6.8 can say "both".
- **The source's own labels.** `okay/docs/continuations/20-the-costs-measured.md` calls 726 B and 69 ns the cost
  of "a capture" (it is a whole generator step) and dates the 104 B row 2026-09-17 (the run is of 2026-09-15, a
  different benchmark). Chapter 13 now says so; the companion text itself is not corrected.
- **Propagation modes, run.** §7.6 states an independent transaction and a savepoint as design. Neither exists
  in okay-tx. Done: both as handlers in `code/scala/transaction.scala` with assertions, or the two sentences cut.
- **A test for an interceptor around a bracket built as a block.** §7.6 now argues the direct-style case; see
  "A transaction in direct style" below.

Done 2026-11-18: `code/scala/examples.scala:62` (122 characters; the fold in `AllChoices`) is wrapped, and the
one row of chapter 3's table that was 124 wide is shortened. Chapter 3 does not quote the wrapped line; the six
mains and `run-all.sh` pass after the change.

## The reference demonstration (chapter 3i): what is left

Done 2026-11-18: four stages under `code/scala/path/` (five mains, under `run-all.sh`), chapter 3i, the
section in `PLAN.md`, pointers from chapters 3, 3b and 3h. PLAN questions 43 to 50 are the author's.
Done 2026-11-18, second pass, on the author's answers to 43, 44 and 47: `kernel.scala` is stage 0 and its
`handle` has no cast; stage 4 has `remap` and interceptors over a `pay` at a fixed row; the texts follow.

- Done 2026-11-18, on the author's word: no cast and no `@unchecked` is left under `code/scala`. The journals
  of `control.scala` and `stopping.scala` keep answers as text; the replaying handler matches the question
  and reads the text as that question's answer type. Still crude: questions are compared by printed text.
- **`remap`'s cost is one figure** (72 bytes for each operation, 1,000 operations of one effect, one
  interceptor, three runs). Not measured: several interceptors, or an operation of another effect passing a
  renamed program. What `remap` allocates (an `Inject`, a `Placed`, a closure) is read from its four lines.
- **`same` answers `No` where the checker cannot follow** (two fall-through cases). That is sound; it also
  means a place the stage cannot recognise is passed further in, never intercepted. No program in the stage
  reaches those cases, and none tests them.
- **"Forty lines" is 38** lines of file, 22 of them code, before and after the change; the title stands.

- **Claims in §3i.2 made from memory**, each worded narrowly, none checked against a source in this pass:
  that Rúnar Bjarnason's "Stackless Scala with Free Monads" (2012) is where the free monad with a bind node,
  stepped by re-association, was described for Scala, and that this is "the usual" remedy on the JVM (said as
  "as far as I know"); that "Reflection without remorse" keeps the pending functions in a queue built for
  cheap appending, and that Kiselyov and Ishii's freer monad uses such a queue. Voigtländer and the two
  papers are already in §15.4 and §15.8; Bjarnason's is not in §15.8, and chapter 15's rule (PLAN question
  22) would want it there or out.
- **No program is known on which stage 4 is quadratic, and none is proved absent.** `forced` measures one
  shape: binds nested to the left under one handler, 1,000 to 4,000. Not measured: left-nesting across
  several handlers, or a handler that binds on the left of what `k` returns, repeatedly.
- **The quadratic cost of chapter 3's own `flatMap`** is stated in §3i.2 by reading its two lines and by
  chapter 3b; it is not counted. Done: a counter in a copy of the forty lines, n and 2n, in a file that runs.
- **The figures in bytes** (§3i.5) are from one machine and one JVM (Temurin 26.0.2, arm64), after 300
  warm-up runs; with 20 warm-up runs one of them varied between runs (208 or 192), which is why 300. They are
  what the JVM allocated after its own optimisations. `forced` asserts their order only. No timing was taken.
- **The stack depth after `Delay`** is asserted only as "100,000 operations do not overflow"; stage 4 does
  not sample the depth as stage 1 does. Where stage 3 begins to overflow was seen once outside the repository
  (3,000 operations run, 10,000 overflow, on the same machine) and is not in a file; the chapter does not
  quote it.
- **The two compiler messages** quoted in `stage3.scala` and §3i.4 were produced by compiling the commented
  lines once (Scala 3.3.8); nothing re-checks them. A third way of forgetting a handler, written inline
  (`run(fixedClock(42)(refusals(pay[Plain](…))))`), gives a longer message about the argument of `refusals`
  and is not quoted.
- **Left out of the path**, and listed in the comment that closes `stage4.scala`: `handleAt` anywhere in the
  row, `Place.Only` and `Place.Absurd`, the renamings `toEnd` and `subPure`/`subOne`/`subMore` (`split`,
  `embed`), the in-place handler with a state in a cell, the `Runner` interface. Finalizers are in no stage;
  axis (f) is not on this path at all.
- **Stage 4 differs from `Free.scala` in shape** in two places a reader will notice: one shared loop (`head`)
  where the library has the loop in each interpreter, and no `@unchecked`. Neither was measured against the
  library, and the library was not changed or run in this pass.
- **The sixteen and the two.** `run-all.sh` now prints eighteen lines of "same"; the chapters and the README
  still say sixteen renderings, counting the stages as states of one implementation (PLAN question 45).

## The one idea as the spine: what is left

Done 2026-11-18: chapter 2b (the statement); chapter 5 as the map, with command objects, callbacks, `call/cc`,
`shift`/`reset`, threads and the matrix; chapter 16 (the picture again); the ties in the other chapters;
the inventory (kept private) and its check (kept private).

- Done 2026-11-18: chapter 4, the laws, which §2b.3, chapter 16 and Part III each owed. What it leaves is in
  the section "The laws (chapter 4): what is left" below.
- **The map before the constructions?** PLAN question 36.
- **The matrix of §5.12 against sources.** Its cells for other people's mechanisms are from memory; the list
  "Third-party claims to check" below has the ones I was least sure of.
- **The inventory is as good as its readers.** Chapters 00 to 03c and 03d to 03h, 13 to 15 were inventoried by
  two helpers and not re-read item by item against the text by me; those chapters were changed by additions
  only (the diff against `ab92f94` shows the removed lines, all of them rewordings listed in the inventory).

## The book belongs to no implementation: what is left

Done 2026-11-18: chapter 2 in the book's own notation; chapter 3 as one rendering among sixteen; Part III
(3a to 3h); sixteen renderings in thirteen languages under `code/`, checked by `code/run-all.sh`; the preface,
README, chapter 5 and the sentences that said "the kernel" re-pointed; the appendix folded into Part III.

Done 2026-11-18, second pass: chapters 6, 7, 10, 11 and 12 show their listings in the notation of §2.0, each
section naming the file, main and block under `code/scala/` that checks it and what the section needs of a
technique; §5.6 quotes the Haskell signature that runs and §5.8 the Scala one that runs. What stays in a real
language, on purpose: chapter 3 (§3.1 to §3.6, the one rendering built in full), §10.7's capability types
(Scala, illustrative), §11.2's two listings (a closure in Scala, a state-machine value in Rust), and one line
each of Rust and Koka in §6.5 and §6.7. Chapters 13 and 14 quote no code; chapter 13's tables are of my
libraries only, and say so.

**More renderings of the later examples.** `continuation`, `control`, `stopping`, `transaction` and `agents`
exist in Scala alone, so the chapters' "what it needs" paragraphs are reasoning from each technique and not
evidence. Done when at least one direct-style rendering and one state-machine rendering of each print the same
lines, under `run-all.sh`. In order of what it would settle:

- **A line for the way back** (PLAN question 39). A seventh line of the scenario — a handler that continues
  the program and reports how it ended, and one that puts the rest aside and resumes it later — in all sixteen
  renderings, printing the line or `7 …: cannot:` with the reason. It would turn two columns of §6.8's table
  from "read from the handler interface" into "run", and would make the five reply-returning renderings (Rust,
  Zig, C#, Go, Java on threads) either gain a second handler interface or say exactly why not.
- **A transaction in direct style** (PLAN question 40): §7.6's bracket as a block that installs a handler, in
  OCaml or Koka, with an interceptor outside and inside it. §7.6's second bullet is argued until then.
- **Replay in a second rendering** (§10.3, §11.3): a generator or a state machine recording and replaying its
  own journal; the chapters say every technique can and only Scala does.

**Part III against the sources.** Every description of someone else's language or library in 3a to 3h was
written from what I know of its documentation and not re-read in this pass. Done: each read against the
current documentation; better, the bank written on ZIO, Cats Effect, Kyo, Effect, Arrow, polysemy, effectful
and Eio, printing `code/expected.txt`, so that description becomes evidence.

**Left out for lack of certainty** (a blank in the book, not a claim):

- whether Effekt, Unison and Frank let a continuation be resumed more than once (the table in 3f has dashes);
- polysemy's internal representation of a program;
- the state of Jane Street's effect-related work in their extended OCaml;
- Kyo's internals (only the shape of its type is stated);
- whether Arrow still ships multi-shot or delimited continuations;
- any well-known effect library for Rust, Go, Python, Java or Swift beyond those named;
- the syntax of Eff, Unison and Frank handlers (described in prose, no listing);
- any benchmark of anyone else's library. The book quotes none.

**Third-party claims to check** (made in the pass of 2026-11-18, from memory, each worded as narrowly as I
could; none was verified against a source in that pass):

- §2b.3: that every library and language in this family uses the free tree with no equations (said as "that I
  know of"); that Plotkin and Pretnar's handlers are models and handling is the map out of the free model
  (standard, stated without a page); Wu, Schrijvers and Hinze, "Effect handlers in scope", Haskell Symposium
  2014, as where scoped operations were set out.
- §2b.2 and §15.5: that handlers and delimited control can each define the other (Kammar, Lindley and Oury;
  Forster et al.), with "fine print about types" left to the references and not spelled out.
- §5.1: Java's `throws` as a row for the one built-in effect. §5.2b: the Elm architecture (an update function
  returns a model and a command; the result arrives as a message).
- §5.7: Effect (TypeScript) as the same shape as ZIO; Kyo left unplaced. Cats Effect's `IO[A]` has no error or
  environment type in it (the table says "services and errors, in some").
- §5.9: that no mainstream platform offers a copy of a generator, a task or a thread to user code (chapter 3d
  already said so of JavaScript, Python, the JDK and OCaml).
- §5.9b: callbacks as how single-threaded servers did non-blocking I/O "for years"; "some compilers" use
  continuation-passing style internally.
- §5.9c: Scheme's `dynamic-wind` named as the construct for guarding regions against re-entry.
- §5.9d: Racket has delimited continuations and prompts natively; recent GHC exposes primitives (already in
  chapter 3g). Left out for lack of certainty: Scala's old continuations plugin, OCaml's `delimcc`, and any
  claim about which languages' `shift` is typed.
- §5.10: middleware that calls `next` and then acts on what came back (the shape of Koa and of ASP.NET Core
  middleware; the chapter names neither).
- §5.12, the Koka row: whether Koka's `resume` can be stored or sent is left as a dash.
- §6.4: that OCaml's `continue k x` returns to the handler what the rest ended in (deep handlers), and that
  Kotlin's `Continuation.resume` returns nothing to its caller.

**Renderings not written.** Swift (installed); Effekt, Unison, Eff, Frank (not installed, no install tried);
Scala on a machine instead of the tree; a Kotlin rendering that copies frames; Rust on `async` (one-shot)
beside the hand-written state machine; C# on `async` beside the iterator.

**Thin places in the renderings**, said in `code/README.md` and here: no rendering has finalizers of its own
(the direct-style ones unwind through the host's `finally`, and no line of the scenario proves it); the
journal sits outside the refusal handler in the pure renderings (Haskell, Lean, Koka) and inside it in the
mutable ones, which is why both print the same line 5; the Rust, Zig, C#, Go and Java-thread handlers answer
by returning a reply and do not receive the rest as a callable value, so "on the way back" (chapter 6) is not
expressible in them as written; line 6 in `haskell/Tagless.hs` labels its two outcomes by position. The third
of these was not changed in the code in the pass of 2026-11-18: the limit is now stated exactly in chapter 3d
("In these two renderings, a handler that only answers"), chapter 3e ("The handler, in this family") and
chapter 6 (§6.4, and the table in §6.8), and the item "A line for the way back" above is what would close it.

## The laws (chapter 4): what is left

Done 2026-11-18: `chapters/04-the-laws.md`; `code/laws-expected.txt` and a law file beside each of the sixteen
renderings and for stage 4; three files that must not compile; `code/scala/laws/` (the twelve lines on the
forty lines and by capabilities, a generator, a reference evaluator, twelve laws on 1,000 programs);
`code/scala/path/laws4.scala` (the lines and ten laws on stage 4); `code/lean/Laws.lean` (the lines and 24
theorems); all under `run-all.sh`. PLAN questions 51 to 58 are the author's.

Not covered, exactly:

- **Stages 1, 2 and 3 of the path** print no law lines and have no generated checks. Stage 3 could carry
  stage 4's file nearly unchanged (it lacks the handler with a state of its own, so its `cell` would be
  written as the forty lines write it). Stages 1 and 2 have one closed vocabulary and no rows.
- **Generated programs outside Scala.** Fifteen renderings are held to twelve programs each. Done: the
  generator and the reference of `terms.scala` in at least one direct-style rendering and one state machine,
  drawing the same thousand terms from the same seed (the generator is a linear congruence and would port).
  For Rust and Zig that needs a machine that interprets a term, since a term cannot be compiled to a
  hand-written machine at run time.
- **A proof about any rendering but the Lean tree.** In particular nothing says that `handle` of
  `kernel.scala` is the `fold` the theorems are about; the chapter says "I believe".
- **The general commuting law.** "Two handlers that only answer commute" is argued; `cell_fixedClock` proves
  one pair. Done: a definition of "only answers" on a clause, and the theorem (PLAN question 55).
- **Laws 6 and 7 of §4.11 are not proved.** That a handler's state inside a handler that resumes twice is
  each path's own is run on eight renderings and on stage 4 and tested on generated programs; in Lean it
  holds of the example by
  evaluation and is not stated as a theorem. A statement would be about `allChoices (cell p s)` for every
  `p`, by induction, with a lemma for the fold over the options.
- **"Nothing after the question happens"** is seen through the log on line 6 (one program). In the pure
  renderings there is nothing else to see; in the ones with mutable handlers a stronger line was possible
  (the cell after an unanswered question) and was not written, to keep one text for all.
- **Finalizers.** No law file has one, so "a dropped rest is not cleaned up" (§4.8) is stated from §6.8 and
  not run in the law files. Since chapter 8 it is run in one rendering: §8.6's scope on the forty lines
  (`code/scala/catalogue.scala`, block 6), left open by a rest dropped from outside and closed twice by a
  rest resumed twice.
- **Shallow handlers** are described in §4.2 and no rendering is one.
- **The reply-returning renderings' order** (Java on threads, Go, C#) is a position in a driver's list, and
  lines 8 and 9 hold by how the driver folds `done`. PLAN question 54.
- **Tests of the tests.** Beyond the one breach the generated mains carry with them (the variable), one
  mutation was tried, outside the repository: a `Cell` that ignores `Put(3)`. The law "a run is what the
  reference says" failed on it at once; the laws of sequencing and of the fold, which are about the kernel
  and not about `Cell`, rightly did not. No mutation of `kernel.scala` itself was run: the two I wrote
  (a foreign question passed on with its rest unhandled; the rest dropped in `flatMap`) do not type-check,
  which is the kernel's types doing a test's work. A systematic pass, one mutation for each law, is not done.
- **The equality the tests use** is weaker than the chapter's definition: twelve questions deep with sampled
  answers on the forty lines, one stack of handlers from two cell contents on stage 4. Said in the chapter.

To check, from this pass (each stated in the chapter as an observation or left narrow):

- **Koka: a local `var` in a handler is each resumption's own.** Line 10 of `koka/laws.kk` prints the
  per-path cells with `cell` written with `var`. Observed, not read in Koka's documentation; §4.6 says so.
- **Koka refuses the unhandled effect at `main`, not at the expression** (`unanswered.kk`: "there are
  unhandled effects for the main expression"). Observed with Koka 3.2.9; whether a non-`main` function with
  a declared total type is refused earlier was not tried.
- **Kotlin: context parameters** are used by `laws.kt` and accepted by `kotlinc` 2.4.21 with no flag. Since
  which version they are stable was not checked, and `code/README.md`'s toolchain note does not say.
- **Kotlin: the second `resume`** of a continuation re-enters the state machine at its last state and then
  throws a `NullPointerException` in `ContinuationImpl.releaseIntercepted` on this JVM. One observation;
  §4.10 says "on this JVM".
- **Zig 0.17: `@hasDecl` reports only `pub` declarations**, even in the same file (met while writing
  `laws.zig`; not in the chapter). And `laws.zig` holds at most four waiting copies and eight log lines.
- **Claims from memory about the literature in chapter 4:** that a handler given the rest unhandled is
  called *shallow* and one given it handled *deep* (standard usage; no citation given); that the laws of
  sequencing are "the laws of a monad". No paper is cited in the chapter, so chapter 15's list is unchanged.

## The catalogue (chapter 8): what is left

Done 2026-11-18: `chapters/08-a-catalogue-of-standard-effects.md`; `code/scala/catalogue.scala`, main
`book.catalogue`, on the forty lines, every listing of the chapter an assertion, under `run-all.sh`; pointers
from §4.7, §4.8, §5.9, §6.8 and §7.6. PLAN questions 59 to 68 are the author's.

Not covered, exactly:

- **Any rendering but the Scala tree.** "Every technique can write the scheduler and the callbacks" (§8.9)
  and "a stage is an interceptor" (§8.10) are argued from what each needs. Done: the scheduler of §8.9 in
  OCaml or Koka (a fork with a thunk; the three `pick`s; the lost update), and §8.10's stages on a
  TypeScript or Python generator, each under `run-all.sh`. (PLAN question 67.)
- **The body of a fork in direct style.** §8.9 argues that handlers between the forking code and the
  scheduler are not on a new fibre's stack. Not run. The OCaml scheduler above would settle it: a state
  handler installed between, and what the forked thunk's `Get` meets.
- **The body of a fork with rows.** §8.9 says a row would make "nobody answers Get" a compile error. Not
  built: on stage 4 the effect `Fork` would have to mention the row its body is over, which is the row it
  sits in. Done: `Fork` on stage 4 with its body at the scheduler's outside row, and the line that does not
  compile, checked with `typeCheckErrors`; or a paragraph saying why Scala 3.3 will not have it.
- **A scheduler that answers `Sleep` and `Await`.** §8.7 and §8.9 name it: sleeping rests ordered by the
  time they wake, a virtual clock moved on when nobody is ready, so that a timeout between fibres is tested
  in no time. Also not built: joining a fibre for its result, cancelling one, fairness when a fibre does
  not yield. (PLAN question 64.)
- **Finalizers that survive a dropped rest.** §8.6 has `Guarded`, which releases when a `Refuse` goes by,
  and shows by §8.10's run that it does nothing for a consumer that stops. A "no more" on the value
  `Pulled` hands out, acted on by the scope, is described and not built. Nor is a resource counted across
  the branches of a search.
- **A bracket as a node** (`Bracket(acquire, release, body)`) beside the scope as a handler, with the same
  five runs. `transaction.scala` is the nearest thing.
- **Two states in one program** (§8.1): said from §4.3, not run. An assertion would be one line.
- **`FirstSuccess` is the first of `AllChoices`** (§8.5) is asserted on one program, not stated as a check
  over generated programs; `code/scala/laws/terms.scala` has the generator that could ask it.
- **The equations of §8.1 to §8.10 are each run on a few programs written by hand**, not on generated
  ones and not proved. `stateOwes` uses two contents and one rest. Chapter 4's generated checks cover the
  state under `Cell` only.
- **A pipe**: a consumer that is a program asking `Next`, and a handler holding the rest of a producer and
  of a consumer in turn; merging; chunks (§8.10, "Not built").
- **The stack.** The last block asserts that 100,000 ticks under `carry` overflow on the forty lines. The
  catalogue's handlers were not rewritten on stage 4, whose handler with a state and whose `answer` are what
  they would use; the chapter points to stage 4's own main for the length.
- **the notes' THESES list (kept private), row a20** still says note 04 is "not used". It is used in §8.9. The file
  was left as it stood in this pass (its rows are far wider than 120 characters); the row should read
  "added: §8.9".

To check, from this pass:

- **Python: a generator object's `close`** raises an exception at the suspended `yield` so that enclosing
  blocks unwind (§8.10). Stated from memory of the language's documentation, worded narrowly; the chapter
  does not say when the interpreter calls it by itself.
- **My libraries' pages, quoted or reported in §8.5, §8.6 and §8.9**, were read and not run:
  `okay-simple/docs/modules/resource.md` ("handle errors inside"; "scopes are for programs that resume
  once"), `okay/docs/effects/resource.md` (holders counted across branches), `okay/docs/effects/choice.md`
  (a fair "or"), `okay/docs/effects/state.md` (a row holds one state), and note 04 for the schedulers and
  the JavaScript platform. Whether each still describes its kernel is PLAN question 62.
- **`Thread.sleep(2)` and the clock.** The file asserts only that two readings of the machine's clock
  around a sleep of 2 ms differ by at least 1 ms, to stay clear of the platform's rounding.
- **The figures in §8.5, §8.8 and §8.9** that depend on a seed ((10, 2); [2, 5, 3]; [4, 0, 2]; 278 and
  231; the interleaving under seed 2; the cell under seeds 1 and 3) are what the file's generator gives, a
  linear congruence written in three lines. They are asserted, and are not a claim about any generator a
  reader will meet.

## Screens, servers and stores (chapter 9): what is left

Done 2026-11-18: `chapters/09-screens-servers-and-stores.md`; `code/scala/applications.scala`, main
`book.applications`, on the forty lines, every listing an assertion, under `run-all.sh`; pointers from the
opening of chapter 7, §8.6, §11.1 and §11.3. PLAN questions 69 to 78 are the author's.

Not covered, exactly:

- **A database that runs the statements.** §9.2's SQL is text, asserted. `AsSql` over `NoDatabase` fails
  two of the four things a store owes, and the chapter says so. Done: an agreement test between `InTables`
  and `AsSql` on an engine, which needs a dependency the book's code does not take (PLAN question 76); or
  the same test in a language whose standard library has an SQL engine, as a second rendering.
- **Any rendering but the Scala tree.** `Paged` on a generator (TypeScript or Python) would be short and
  would show the limit the chapter only states: a page's rest that can be resumed once cannot go back.
  `together` in a direct-style rendering would show whether parts can be stopped side by side there.
- **Rows.** "With rows a missing store would not compile" (§9.4) is argued. None of the file is on stage 4.
- **A step committed in one piece.** `Stated` saves the order and writes the new state in two steps; a
  crash between them doubles the order. Named in §9.3, not run. Done: a platform operation that commits
  several writes with what was read, a handler with faults (a crash before or after the commit) as
  okay-serverless's memory platform has, and the doubled order as a failing then passing assertion.
- **The session counter.** `fresh` reads and writes a counter in two steps. Named, not run.
- **`together` beyond `Find`.** It parks at `Find` only, takes one table a round, and does not batch a
  `Query`. A part that fails, and a part that never asks, are not asserted. Whether `together` is a
  function over parts, as here, or an operation that holds programs (with the two prices of §7.6 and §8.9)
  is not discussed in the chapter.
- **`Atomically` under SQL as a real transaction.** The log is written at the end as plain statements; no
  `BEGIN` or `COMMIT` is said, so under a database the saves are not atomic. The chapter claims only that
  nothing is said before the end.
- **Timers.** `Alarm`, a session forgotten when idle, a wake-up: none is built (as in chapter 8).
- **The codecs** of `Replayed`, `Recalled` and `Stated` are a few lines each and break on a value that
  contains `&`, `=`, `;`, `?`, `|` or a line end. They show what is stored, not how to store it.
- **A real terminal.** `Console(println, readLine)` is described in a comment and not run.

To check, from this pass:

- **Web frameworks built on continuations** came before the libraries of this book (§9.1). From memory:
  Seaside in Smalltalk, and the Racket web server's `send/suspend`. Read their documentation before any
  is named in the chapter; chapter 15 does not mention them either.
- **Batching by a loader** (§9.2 names no product): from memory, Haxl in Haskell and the DataLoader of
  the GraphQL world collect the keys asked for in one round and ask once. To read, and to compare with
  `together`: in particular what each requires the program to say about independence.
- **What ORMs call the remedies for N+1** (§9.2: "fetch this relation eagerly, load in batches of this
  size", "declared per query or per mapping") is general knowledge worded without a product. To check
  against one ORM's documentation if a product is ever named (PLAN question 75).
- **The SQL itself.** `INSERT … ON CONFLICT (key) DO UPDATE SET c = excluded.c` and `WHERE key IN (?, ?)`
  are written from memory of SQLite's and PostgreSQL's dialects and never executed here.
- **My libraries, as read and not run**: `okay-ui/README.md`, `specs/business.md`, `specs/reference.md`,
  `core/src/main/scala/okay/business/Business.scala`; `okay-faces/README.md`, `specs/cloudflare.md`;
  `okay-store/README.md`, `specs/okay-store.md`, `core/src/main/scala/okay/store/SqlData.scala`;
  `okay-serverless/README.md`, `specs/cloud.md`, `specs/durable.md`,
  `core/src/main/scala/okay/serverless/cloud/Platform.scala`. "okay-store has no batching" is from a search
  of its core for the words, not from its author. Whether each still describes its library is PLAN
  question 77; the bounded replay of okay-faces' hosting is question 69.

## The tree in Java (chapter 3j): what is left

Done 2026-11-27: chapter 3j on okay-tiny at `ece0f7e`; the Java claims of 3, 3b, 3e, 3g, 3h and 5.2a rewritten in
place (question 80, settled); 3b, 3g (the Java section and both tables), 3h, 5.2a and
13.6 point to it; PLAN questions 79 to 81.

- **Nothing of 3j runs under `run-all.sh`.** Done: a rendering of the bank and a law file in okay-tiny's style
  under `code/java/`, printing `code/expected.txt` and `code/laws-expected.txt` (question 79), or the chapter
  keeps citing tests at a commit and says so, as now.
- **The figures of 3j.7 and 3j.9 are `DESIGN.md`'s**, taken on different days as the kernel changed. Done: one
  JMH session of the seven lanes at one commit, the command in the text.
- **No comparison in one session** with `code/java/Tree.java`, okay-java or the reference kernels of 13.6.
- **The javac messages quoted in 3j** are the first lines `NegativeTest` matches, not whole messages; a newer
  JDK may word them differently. `NegativeTest` will say so when it does.

## Chapters not written

- **What the Rust and Goose ports cost** (notes 08, 21) — once planned for an appendix; now a paragraph each in
  chapter 3g, not yet written.
- **Answer-type modification and the typestate protocol** (notes 06, 07) — a section or nothing, after PLAN
  question 3 is answered.

## Measurements the book says it lacks

- **Agents with and without the rules** (chapter 12, §12.4): the same tasks, the same model, hidden tests and
  the boundary audit as the judge, enough repetitions to beat the noise. okay-loop can run it. Until then the
  first half of chapter 12 stays marked "argued".
- **The "costed" reading of a process** in §10.9 is marked not built.

## Sources not checked

- The copies of posts in `~/Downloads` were not compared with the texts recovered from the sessions.
- No session before 2026-10-07 was searched for notes; the three older files in the author's earlier notes have no recovered
  session behind them.
- The three notes saved earlier in the author's earlier notes were matched against the chapters by the index, not re-read whole.
- Which notes were actually published is known for one (note 22).

## Only the author can settle

These stay in `PLAN.md` as open questions: 1 (title, publish as it grows or hold), 2 (one running example), 3
(how much Scala machinery), 5 (run the agents' measurement?), 6, 7, 8, 15, 20, 26 (wording and anecdotes in the
first person), 11, 12 (the `[AUTHOR:]` markers in §13.8 and §14.4: users, contributors, another team), 14 (two
theory chapters used beyond the plan's list), 16 (which notes may be cited), 17, 18 (the narrower claims kept
against notes 10, 19, 20), 23 (soften or keep §10.9 and "effects, not frameworks"); and from the pass that made
the one idea the spine: 35 (the wording of "algebraic"), 36 (the map before or after Part III), 37 (a fifth
paper outside the companion texts), 41 (whether figures from notes move into chapter 13).

## Settled 2026-11-18, by reading the code

- **Finalizers (question 19).** Not "by any road". An abort and an explicit unwind or cancel run them, with
  tests in okay-core (`TestGuard`) and okay-scala (`EnsuringSuite`); a rest kept and never called releases
  nothing; okay-simple's machine has no finalizers. §3.7, §6.8 and §7.6 corrected.
- **Durable rest (question 9, the marker in §11.4).** Only okay-goose round-trips a captured rest through bytes,
  inside one process. No kernel restores one in another process. §11.4 says so; the marker is gone.
- **104 B and 726 B (question 10).** Different things, both loosely labelled in the source. §13.5 explains.
- **Transactions (question 21).** okay-tx flattens nested brackets; no independent transaction, no savepoint;
  okay-jdbc refuses a nested one. A bracket's body is opaque to an interceptor in the real kernel too, by
  reading. §7.6 corrected.
- **Eff and Unison (question 13, the marker in §15.6)** added with their references; the two papers that came
  from notes (question 22, first half) moved into §15.8.
