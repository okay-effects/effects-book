# The plan of the book

Working title: **Programs That Ask — effects as the architecture and the control of software**.
Started 2026-11-17. This file is the contract each chapter is written against: its thesis, where its material
comes from, and its state. English; the reader is a working engineer in any language, and the person who decides
what their team builds.

## What the book claims

1. Most of what makes software hard to trust is one thing: a piece of code can reach the world without saying so.
2. An **effect** makes every reach a question, as data; a **handler** is the one place that answers it. This is a
   small idea and it is explained here in full, in a notation of its own, and built in every way it can be: the
   shortest construction is forty lines, and the reader can run sixteen.
3. Seen that way, a large part of what teams build separately — dependency injection, mocks, middleware, retries,
   audit logs, feature flags, sandboxes, workflow engines — is one mechanism used in different places.
4. So effects are not a technique inside a program but its **architecture** (the data model is the effects, every
   implementation is a handler, the program runs in one line) and its **control** (what is not answered does not
   happen; what is answered is seen).
5. Code written this way is easier for an AI agent to write correctly, and an agent whose every action is an
   effect is one that can be watched, limited, replayed and stopped.
6. It has costs, and places where it is the wrong tool. They are measured or named, not waved away.

## The book belongs to no implementation (the author, 2026-11-18)

The book is not tied to one implementation in one language. It explains the idea in a notation of its own, then
lays out **every way the idea can be built** — which mechanisms exist, in which languages each is available,
what each gives and costs — so that a reader who finishes knows what to choose for their language and their
problem, and why. Consequences:

- **The idea is stated in a neutral notation** (chapter 2): operations, a program that asks, a handler, the rest
  of the program. No language's syntax is needed to follow it.
- **A part of its own, "Ways to build it"**, is the centre of the book's engineering: the axes of the design
  (how a program is represented, what the rest of a program is made of, how a handler is found, what the types
  say, one resumption or many, how leaving early cleans up, whether the rest can be stored, what it costs), then
  each family of technique with its strengths first, then language by language, then how to choose.
- **The same small program runs in several languages** under `code/<language>/`, each by the technique natural
  to that language, each printing the same lines. A chapter quotes whichever rendering shows its point; no
  rendering is "the" kernel.
- **My own libraries are evidence, not the subject.** They appear where a measurement, a test or a mistake of
  mine supports a claim, named as one implementation among those surveyed, beside other people's libraries and
  languages, which are described fairly and from their own documentation.
- The Scala kernel of forty lines stays as one rendering among the others. Chapters 6, 7, 10, 11 and 12, which
  were written against it, show their listings in the notation of §2.0 since 2026-11-18; the Scala files under
  `code/scala/` are what checks them, and each section says which file and main. Chapters 8 and 9 were
  written in the notation from the start, and are checked the same way. Chapter 9 is where my libraries are
  the subject of whole paragraphs; each such paragraph is headed "In my library", cites files, and stands
  beside a miniature that does not depend on the library.

## The reference demonstration (the author, 2026-11-18)

> "The Scala implementation with a free monad and rows is the reference demonstration. The book can show the
> whole path of its development: start with the simplest free monad with a trampoline, then add rows and more
> constructors."

The implementation meant is `Free.scala` in okay-simple (282 lines; `Places.scala` for the rows). This does not
cancel the section above; it is reconciled with it so:

- **One implementation is grown in full**, in stages, so that the reader sees each decision forced by
  something that failed in the stage before. The path is stage 0 to stage 4 (the author, 2026-11-18): stage 0
  is chapter 3's forty lines, `code/scala/kernel.scala`, written with a GADT match and no cast; stages 1 to 4
  are chapter 3i and four self-contained files under `code/scala/path/`. The kernel is at once the first of
  the sixteen renderings and stage 0, and is counted once. The book's code does not depend on the library;
  the last stage ends with a list of what the library has and the stage does not.
- **The others remain comparisons.** The sixteen renderings, Part III's families and chapter 3g's languages
  are unchanged, and chapter 3i says in a sentence each what another family does at each step (§3i.7).
- **The neutral notation stays the language of the idea.** Chapter 3i is in Scala because its subject is one
  implementation in Scala; chapters 2, 2b and 6 to 12 are not touched by it.
- "Reference" is the demonstration's role in the book — the one path walked from nothing to a library — and
  not a ranking of implementations. The library is named as mine and as one among those surveyed.

## The one idea everything hangs on (the author, 2026-11-18)

Algebraic effects are **two things together: algebraic data and delimited continuations.** The operations are
data — a signature, and a program is the tree those operations freely generate, an algebraic data type. A
handler is a fold over that tree which, at each operation, holds **the rest of the program up to itself**: a
delimited continuation, as a value it may drop, call once, call many times, or keep. Every equivalent or
neighbouring mechanism is this with one half restricted or missing, and its limits follow from which:

- data without the continuation (command objects, interpreters of plain lists, dependency injection): the
  answer comes back once, in place;
- the continuation without the data (callbacks, `call/cc`, raw `shift`/`reset`, threads, coroutines,
  generators, async): control, but nothing to inspect, type, record or send, and often one resumption only;
- both, but fused into one fixed stack of meanings (monads and their transformers): an order chosen in the
  types, not by the handler;
- both, by encoding (free and freer monads, tagless final, effect runtimes): the same idea, at a price or with
  something hidden.

The book must make this picture explicit, early and again at the end, and every comparison must be placed on
it. Nothing already written that is good is to be lost in a restructuring: material that no longer fits a
chapter moves to a specialised section or chapter, and the move is recorded.

**Where this lives in the book (decided 2026-11-18, and why).**

- **The statement: chapter 2b**, a short chapter of its own directly after chapter 2. Not a section of chapter
  2, because chapter 2 is written for a reader who has never met the subject and must stay five small notions;
  2b is the one place a reader who knows the subject looks for the exact claim. It says: the first half (a
  signature; the free tree, an algebraic data type; a handler as a fold), the second half (the delimited
  continuation, as the second component of a node or as a piece of stack), what "algebraic" means in Plotkin
  and Power and in Plotkin and Pretnar (operations commute with sequencing; equations; a handler as a model and
  handling as the map out of the free model), what is therefore not an operation (catching, a changed setting,
  the bracket of §7.6: handlers, or scoped operations), that practice uses the free tree with no equations and
  checks laws by tests, and the map in four lines.
- **The map: chapter 5**, restructured in place and renamed "The map: everything else, on the two halves". It
  stays after Part III because six of its mechanisms are also ways of building the idea and are easier to place
  once seen built; chapter 2b gives the four-line version early and points forward. Every section keeps "what
  it does well" first and now ends "on the two halves". Added: 5.0 (the ruler), 5.2b (command objects and
  interpreters of plain data), threads and goroutines in 5.9, 5.9b (callbacks and CPS), 5.9c (`call/cc`), 5.9d
  (`shift`/`reset` and prompts), OCaml 5's one-shot choice in 5.11, and in 5.12 a matrix of seventeen
  mechanisms by seven questions before the table of five requirements. Section numbers were kept and new
  sections lettered, so no cross-reference moved.
- **How four chapters are kept from saying the same thing.** 2b states the idea and has the map in four lines.
  5 places the *neighbours* (mechanisms as ordinarily used). 3a and 3h are about *constructions* of the idea
  (axes; how to choose), and each now says so in a paragraph that sends the other question to chapter 5. 15 is
  the same ground *in time* (who found which half, what each step gave up), and points to 5.9b–5.9d for the
  same rows as mechanisms. Each of the four opens or closes with that division.
- **The return: chapter 16**, "The picture, again", a short closing chapter: what the reader now knows on the
  two halves, a table of which half did the work for each claim of the book, and what stays open.
- **The ties.** Chapter 6 opens and closes as the second half; 7 and 10 as the first; 11 as the place where the
  rest must become data too (new §11.8); 12 as both (§12.6, §12.11); the preface, 1, 1b, 3, 3a, 3h, 13 and 15
  each carry one sentence or paragraph.

**Qualifications to the two-word summary, as the book words them (§2b.5).** (1) "Algebraic" names the theory —
operations that commute with sequencing, with equations — and not the data type; the two meet because the free
model of a signature is an algebraic data type. (2) The continuation is delimited *by the handler*: a handler
is a boundary together with the clauses for what crosses it. (3) An implementation may restrict the
continuation to one resumption, and most do; it is still the idea. And rows are a third thing, not part of the
definition.

## Rules the book is written under

- **Code that appears compiles and runs.** One scenario is written sixteen times in thirteen languages under
  `code/<language>/`, and `sh code/run-all.sh` checks that every rendering prints `code/expected.txt`
  (`code/README.md`: the table, the commands, the toolchains). The examples of the later chapters are under
  `code/scala/` (`scala-cli run code/scala`, eight mains: `book.examples`, `control`, `continuation`,
  `stopping`, `transaction`, `agents`, and `catalogue` and `applications`, which `run-all.sh` also runs), and
  the four stages of
  chapter 3i under `code/scala/path/` (five mains, run by `run-all.sh`). The laws of chapter 4 are a second
  agreed output, `code/laws-expected.txt`, printed by a law file beside each rendering, with generated
  checks and Lean theorems beside them, all under the same script. A chapter shows them in the notation of
  §2.0 and names the file and main that checks each; a listing whose point is a feature of one language
  stays in that language and says which file runs it. A snippet that was not run is marked "illustrative,
  not run here"; today that is one line of Effekt in chapter 3f, one Scala signature in §5.6, the capability
  types of §10.7, and the pseudocode of chapters 1b, 5 and 14.
- **Nothing good is lost in a restructuring.** the inventory (kept private) lists every item of every chapter as it
  stood before the pass of 2026-11-18 and where it is now; its check (kept private) checks numbers,
  paths, cross-references and code spans mechanically. A later restructuring extends both.
- **Other people's libraries and languages are described from their own documentation, and only what is
  certain.** A detail in doubt is left out and listed in the backlog, not guessed.
- **A number has a source**: the benchmark, the commit, the protocol — or it is not in the book.
- **A comparison is fair to the other side**: what the alternative does well is said first.
- **What is argued and not measured is marked as argued.** Chapter 12 in particular.
- No marketing words. If a sentence would survive in a brochure, it is rewritten.

## Three readers

| reader | what they need by the end |
| --- | --- |
| the engineer who has never met effects | to recognise the shape in their own code and write a handler correctly |
| the engineer who knows monads, ZIO, tagless | what is the same, what is different in kind, the costs with numbers |
| the architect or manager | what the team stops building, what it can prove to an auditor, when to say no |

## Sources being merged

- **A** `okay/docs/continuations/` — "Continuations: a working book", 28 chapters, complete, on the classic road
  (okay-freer). The case chapters (1–2), the shapes (4–9), multi-shot (13), durable workflows (22–23), the costs
  (20), the history (28) come into this book condensed and re-based on the notation of chapter 2 and the
  rendering of chapter 3.
- **B** `okay/docs/theory/` — "The theory of Okay", 13 chapters with the papers. The source for Part VI and for
  every "who found this" note.
- **C** `okay/docs/effects/` (13 short pages, one a standard effect), `okay/docs/guide.md`, `tutorial.md`,
  `your-own-effect.md`, `okay-simple/docs/guide` — the catalogue of chapter 8 and the worked examples.
- **D** `okay-effects/SPEC.md`, its README and the agents' skill (`plugins/okay-effects/skills/effects/`) — the
  model as now stated, the design rules, "when not to".
- **E** the LinkedIn notes: the author's notes (kept private) (47 notes recovered 2026-11-18, with `INDEX.md`) and four older
  ones in the author's earlier notes (the effect boundary checked at every build; speed is the least interesting
  part; "impossible" is a claim you can test; a draft of questions, unused). the notes' THESES list (kept private) is the
  audit of every thesis in them: in the book already, added (where), or not used (why). Chapters 2, 5, 7, 10, 11,
  12, 13 and 14 draw on them. Only note 22 is known to be published.
- **F** the applications as evidence: okay-guard (policy and journal over an agent's calls), okay-audit (the
  boundary from bytecode), okay-serverless (one program on Cloudflare and on the JVM), okay-ui (screens as data),
  okay-remote, okay-llm (a model as an effect).
- **G** the ports, as evidence for Part III of what each language allowed and cost — one implementer's record,
  cited by file: `okay-effects` (README, SPEC.md), `okay-simple` (README, docs/modules/resource.md),
  `okay-scala/docs/design.md`, `okay-java` (README, docs/architecture.md, resumable/README.md), `okay-direct`
  (docs/design.md, docs/implementations.md), `okay-kotlin` (README, docs/design.md), `okay-rust` (README,
  machine/README.md), `okay-ts/README.md`, `okay-zig` (README, specs/design.md), `okay-lean` (README,
  AGENTS.md), `okay-goose`. `okay-rs` is a stale copy of okay-rust and is not cited.
- **H** other people's languages and libraries, from their own documentation, for Part III: OCaml 5, Koka,
  Effekt, Eff, Unison, Frank; ZIO, Cats Effect, Kyo, atnos-eff, Ox, dotty-cps-async, Gears; Arrow,
  kotlinx.coroutines; Effect, redux-saga; mtl, free, freer-simple, polysemy, fused-effects, effectful,
  bluefin, eff; Eio; language-ext; swift-dependencies. Read from memory of that documentation in this pass,
  not re-fetched: see the backlog item "Part III against the sources".

## The chapters

State: ✓ drafted · ◐ outlined here · ○ not started.

### Part I · The case
- ✓ **0 Preface** — what this is and is not.
- ✓ **1 A function that does not say what it does** — the type lists the arguments and the result and is silent
  about the database, the clock and the network; everything else in the book follows from taking that seriously.
  Sources: E (audit note), D.
- ✓ **1b Five programs that are hard to write** — leave early, push as pull, stop and continue tomorrow, on the
  way back, let someone else decide. Source: A 1–2, condensed to one chapter.

### Part II · The idea, in full
- ✓ **2 Asking** — an operation is data; a program is a value that asks; a handler answers; the rest of the
  program is a value too; a row says what may still be asked. Each notion with the smallest example. Rewritten
  2026-11-18 in a notation of the book's own (§2.0); no language's syntax appears in it. §2.0 gained four rows
  for the later chapters (a sub-program, `back <- rest(v)`, the cases of a result, `run(p) = v`).
- ✓ **2b The idea, exactly: algebraic data and delimited continuations** — the statement the book hangs on:
  the two halves; what "algebraic" means and what is therefore not an operation; equations in the theory and
  tests in practice; why it takes both; the two-word summary with its three qualifications; which chapter uses
  which half. Written 2026-11-18. No code of its own; it cites chapters 3 and 7 for what runs.
- ✓ **3 One rendering, built in full: forty lines** — a tree and closures in Scala 3, built and run; zero, one
  and many resumptions; what this technique owes that others do not; §3.9 lists the fifteen other renderings.
  Code: `code/scala/kernel.scala`, `examples.scala`, `bank.scala`. Since 2026-11-18 it is also stage 0 of the
  path that chapter 3i completes, and its `handle` has no cast: §3.4 explains the type pattern that names the
  hidden answer type. The file is 38 lines, as it was.

### Part III · Ways to build it
Files `chapters/03a` to `03j`, lettered as `01b` is so that no later chapter was renumbered. Sources: G, H, and
the renderings under `code/`.
- ✓ **3a The axes of the design** — nine decisions: how a program is represented; what the rest is made of; how
  a handler is found; what the types say; one resumption or many; leaving early; storing the rest; cost; living
  with the host. For each, the options, what each gives first, what it costs, what it forces.
- ✓ **3b Programs as data, and an interpreter** — the freer tree in Scala, Java, Haskell, Lean; the type of an
  answer as what the host language decides; from a tree to a machine; where a handler's state lives.
- ✓ **3c Without the tree: final encodings and capabilities** — tagless final (Haskell, run: the rest is what
  the monad's bind makes of it, and the list monad resumes twice) and capability passing (Scala, run).
- ✓ **3d Direct style on a stack the runtime keeps** — generators (TypeScript, Python), virtual threads (Java),
  goroutines (Go); why one resumption; the three ways to buy the second back.
- ✓ **3e The compiler writes the state machine** — by hand (Rust, Zig), by the compiler (C# iterators, Kotlin
  `suspend`), by a macro, by bytecode rewriting; `async`/`await` as this family with one effect.
- ✓ **3f Languages with handlers built in** — OCaml 5 and Koka, run; Effekt, Eff, Unison, Frank from their
  documentation.
- ✓ **3g Language by language** — Scala, Java, Kotlin, Rust, TypeScript, Python, Go, C#, Swift, Zig, Haskell,
  OCaml, Lean, the effect languages: what each gives, which families are open, the best fit, what is painful,
  other people's libraries, my port where one exists; two tables.
- ✓ **3h How to choose** — five questions from the problem; a table from the language; the families on the
  axes; five sentences.
- ✓ **3i One implementation, grown in four steps** — the reference demonstration, from stage 0 (chapter 3's
  forty lines) in four steps: a free monad with a
  trampoline (the data half); the handler given the rest (the continuation half); rows as typed places, `run`
  for the empty row only; then `Delay`, `Inject`, a handler's own state, answers in place, and places renamed
  (`remap`) so that an interceptor goes in front of a program already written, each after the
  run of the stage before that fails or wastes, or the line that does not compile. A table of why each
  constructor and type parameter exists;
  what the other families do at each step; the end point named as my `Free.scala`. Written 2026-11-18. Code:
  `code/scala/path/stage1.scala` to `stage4.scala`, five mains, under `run-all.sh`.
- ✓ **3j The same tree in Java, with the row in the type** — the tree of chapter 3 for a language without GADTs
  or higher kinds, as code that runs: `code/java/rows/` (`Fx.java`, 79 lines of code, one cast; `Bank.java`,
  the scenario; `Laws.java`, the twelve laws with line 7 the compiler's; eight programs under `refused/` javac
  must refuse), under `run-all.sh`, not counted among the sixteen renderings (like the path of 3i). The answer
  type read off each operation, its own witness (`on`, `answer`), so no handler has a cast and the kernel has
  one; the row as an intersection bound, a set that javac checks, made anew and never cast where it changes;
  handlers put outermost first, each with exactly the row around it, which closes a hole (a clause that runs
  its rest compiles and fails when its row is less than the truth: shown against okay-tiny, and against
  `? super W`); completeness by a `switch` over a sealed effect; a handler as a fold and a monad, its state a
  value given to the rest, so §4.6's law holds by construction. Then okay-tiny, the library it is distilled
  from (Java 21, 398 lines), cited by test at `8962bdf` (100 + 22 + 10 tests): its loop, each node forced by a
  measurement, `leave`, `ensuring`, `keep`, the observer, costs, the family's TCK digest, Spring, direct style
  as a javac plugin. What Java gives this family and what it costs. Written 2026-11-27; the code the same day,
  on the author's word that the book's version be minimal and fully typed, like the forty lines. 3, 3b, 3e, 3g,
  3h, 4 (§4.4, §4.6, §4.9, §4.10), 5.2a and 13.6 point to it.
- ✓ **4 The laws** — what must hold of any implementation, in three groups: laws of the idea (sequencing and
  the algebraic property; a handler is a fold, so handling a sequence is handling its parts; a handler is
  offered every question of its effect and no other's; a question nobody answers has no value and, with
  rows, does not compile; the rest is a value, zero times, once, twice; a handler's state is part of the
  rest), laws particular handlers owe (the four equations of a state; an observer changes nothing; the one
  equation that the order of state and failure decides), and what is not a law. Written 2026-11-18, in the
  notation, with a vocabulary of its own (state, failure, clock, choice). Sources: D (`Properties`, for the
  list of what a kernel is held to) and `okay-lean` (for which laws can be theorems); nothing is copied from
  either. Checked three ways, all under `run-all.sh`: twelve agreed lines (`code/laws-expected.txt`) printed
  by a law file beside each of the sixteen renderings and by stage 4, a line a technique cannot state
  replaced by `cannot:` with the reason, and "does not compile" backed by the compiler (two Scala mains ask
  theirs; three files must fail to compile); the laws asked of 1,000 generated programs on the forty lines
  and on stage 4 (`code/scala/laws/`, `code/scala/path/laws4.scala`; the generator and a reference
  evaluator are written by hand); and 24 theorems about the tree in `code/lean/Laws.lean`. Its finding: a
  handler that keeps its state in a variable is not a fold over the rest, and the order of handlers no
  longer decides whether a state is each path's own (§4.6). Not covered: stages 1 to 3 of the path;
  generated programs outside Scala; a proof about any rendering but the Lean tree.

### Part IV · Compared with everything else
- ✓ **5 The map: everything else, on the two halves** — plain code and exceptions; interfaces and dependency
  injection; ports and adapters (hexagonal architecture); command objects and interpreters of plain data; mocks;
  monads and their transformers; free
  monads; tagless final; effect runtimes (ZIO, Cats Effect; Effect and Kyo as far as I am sure); capabilities;
  async/await, generators, coroutines, threads and goroutines; callbacks and CPS; `call/cc`; `shift`/`reset`
  and prompts; aspects, proxies and middleware; languages with effects built in, with OCaml 5's one-shot
  choice. For each: what it does well, where it ends, and on the two halves why. Two tables: seventeen
  mechanisms by seven questions, and by the five requirements. Sources: A 16–16b, B 1, 4, 6, 8, E. From the
  notes (2026-11-18): injection as an effect system that stopped halfway and the proxy that a self-call walks
  past (5.2); swapping a service against giving an operation a meaning, and two runtimes in one system (5.7).
  It compares each approach as ordinarily used, and says how that differs from the same approach as a
  substrate in Part III; the last row of each table is the idea, not an implementation. Restructured as the
  map 2026-11-18 (the section above says what was added). Added 2026-11-27: 5.2a, ports and adapters — the
  architecture built on 5.2: what it gets right (it finds the signature: a port is an effect's vocabulary, an
  adapter a handler, the composition root the one line), where it ends (the boundary is a rule about source
  files; an adapter answers and that is all, so every policy about what comes after the answer is a shape of
  the use case), and that a team with ports has only to make the call a value. No row of its own in the tables
  (it shares 5.2's). One paragraph "In my library" cites okay-tiny's tests by name; nothing of it is run here.
- ✓ **6 What a handler can do that a wrapper cannot** — the second half alone: the six things a handler can do
  with the rest. Source: A 3, 11, 19. In the notation since 2026-11-18; checked by
  `code/scala/continuation.scala`, blocks 1 to 6; each section says what it needs of a technique, and §6.8 has
  a table of what each of the sixteen renderings can follow (two of its columns read from the code, not run).

### Part V · Effects as architecture
- ✓ **7 The data model is the effects** — name what is asked, then who answers; the application in one line; tests
  swap handlers; one program on several platforms and in several languages. Sources: D, F. From the notes: 7.6,
  a transaction as a node with a subtree (E 11–13). In the notation since 2026-11-18; 7.6 is checked by
  `code/scala/transaction.scala` and now says that the bracket is a scoped operation and what an interceptor
  sees under each way of building one (the second way argued, not run).
- ✓ **8 A catalogue of standard effects** — state, reader, writer, failure, choice, resources, time, randomness,
  async, streams: each as its operations, what its handlers do with the rest (in place, dropped, later, many
  times; and "at the end", a `done` line that acts), its usual handlers, the equations they owe (as section
  4.7 promised) and what goes wrong. Its spine: six of the ten need the data half alone; the trouble is in
  the constructs that act on a stretch (a changed environment, a catch, a scope, a deadline, a fork), five
  built as handlers around a part and one as a node. Ten pairs of effects, each run in both orders. Its
  findings: a scope written as a handler releases on its `done` line, so a rest dropped from outside leaves
  it open and a rest resumed twice closes it twice and uses it after the first (what §4.8 said was not a law
  and nothing tested); a retry with the state inside it cannot get on; a generator of random numbers inside
  a choice gives every branch the same draws; the body of a fork runs outside every handler between the
  fork and the scheduler. Written 2026-11-18, in the notation, building on chapter 4's vocabulary. Sources:
  C (for which effects and handlers are usual, and two statements about my libraries' resources, cited as
  read) and E 04 (§8.9: a fibre is a program on a scheduler that can be exchanged; blocking as a
  capability). Nothing is copied from either. Checked by `code/scala/catalogue.scala`, main `book.catalogue`,
  on the forty lines, every listing an assertion, under `run-all.sh`. Not covered: any rendering but the
  Scala tree; rows (three places named); a scheduler that answers `Sleep` or `Await`; the effects of source
  C that were left out (question 60).
- ✓ **9 Screens, servers and stores as effects** — chapter 7's claim made concrete, three times small and
  once together. Each subject in three parts kept apart: the idea in miniature, in the notation and run;
  what my library does for it, from its files; what the usual tool does well and where it ends. A screen:
  `Show`, `Enter`, `Press`, answered in place by a terminal and by a script, and by a handler that must
  return to its caller and so hands the rest out (a page at a time; back is the rest called again); a form
  as one question that holds data, against a sequence of questions. A store: `Find`, `Save`, `Query` in
  memory, as a journal, and as SQL text asked of a lower effect; what a store owes; a transaction as a
  handler around a part (a dropped rest is a rollback; a rest resumed twice commits twice); the N+1 query
  seen by an observer and removed by a handler that holds the rest of several parts and asks once. A
  server: a platform as an effect (memory, a directory of files), and a host as the handler of the world's
  question, three times: keeping rests, keeping answers (replay), keeping a state written by hand. One
  order desk under three stacks of handlers. Its findings: a replaying host that journals only the person's
  answers saves the order twice, because the store's answers are part of the session; SQL over no database
  is chapter 8's stub; a form field by field and a report row by row are one mistake, a sequence that
  promises a dependence nobody needed; and, from my own files, that okay-faces' hosting on Workers resumes
  a session by a bounded replay, which section 11.6 does not say. Written 2026-11-18, in the notation.
  Sources: F, read and cited by file (okay-ui, okay-faces, okay-store, okay-serverless; okay-shop read and
  not described); nothing of theirs is run or copied. Checked by `code/scala/applications.scala`, main
  `book.applications`, on the forty lines, every listing an assertion, under `run-all.sh`. Not covered: a
  database that runs the statements; any rendering but the Scala tree; rows; a commit of a step in one
  piece; timers; any product named in the comparisons.

### Part VI · Effects as control
- ✓ **10 What is not answered does not happen** — interception, the journal, replay, policy, sensitive effects that
  cannot be handled without an audit; the boundary checked from the bytecode; what an auditor is shown. Sources:
  E, D, F (okay-guard, okay-audit). From the notes: 10.6 a program that picks its own handler (E 42, runs in
  `code/scala/control.scala`); 10.7 what types can take over and what only the platform can ensure (E 19, 42); 10.9
  three layers that are usually one, for the manager: change the policy, not the process (E 41). In the
  notation since 2026-11-18: 10.2 is checked by line 4 of all sixteen renderings and by `control.scala`; 10.3
  and 10.6 by `control.scala` alone; the capability types of 10.7 stay in Scala, illustrative.
- ✓ **11 Stopping in the middle** — the rest of a program kept, moved, resumed: durable workflows, approvals, no
  replay. Source: A 7, 22–23. From the notes: 11.5, the same two roads when the rest is needed twice (E 21, 22).
  In the notation since 2026-11-18, checked by `code/scala/stopping.scala`; 11.2 is in two real languages (a
  closure that cannot be written, a state-machine value that could be); new 11.8 puts the two roads on the two
  halves.

### Part VII · Agents
- ✓ **12 Why an agent writes better code with effects, and why its own actions should be effects** — the argument,
  what has been observed, what has not been measured. Sources: D (the skill), F (okay-guard, okay-remote). From
  the notes: locally correct and globally unaware (12.1, E 10); 12.5 what the agent did not change (E 15, 45,
  46); 12.9 a supervisor is one more handler, a decision that needs the past, the audit as the same function
  (E 10, 25–27). 12.9 in the notation since 2026-11-18, checked by `code/scala/agents.scala`.

### Part VIII · The costs
- ✓ **13 What it costs** — speed with the benchmarks and their protocol, memory, the types' error messages, the
  learning curve, one author. Sources: A 20, E ("impossible"), `okay/docs/benchmarks.md`.
- ✓ **14 When not to** — pure computation, one implementation for ever, a team that will not read it. Source: D.

### Part IX · Where it comes from
- ✓ **15 A short history** — Moggi, Plotkin and Power, Plotkin and Pretnar, Kiselyov; Koka, Eff, OCaml 5, Unison.
  Source: B, A 28. Since 2026-11-18 it names the two halves where its lines meet (15.5), adds scoped operations
  (Wu, Schrijvers and Hinze; a fifth paper not from the companion texts), and points to 5.9b–5.9d.

### Part X · The picture, again
- ✓ **16 The picture, again** — the idea restated with what the book added; a table of which half did the work
  for each claim; what stays open; chapter 2's sentence read as the two halves. Written 2026-11-18.

The appendix once planned here ("the same kernel in Java, Rust, Kotlin, TypeScript, Zig, Lean") is gone: it
became Part III and the renderings under `code/`, where no rendering is "the same kernel" of another.

## Open questions for the author

SETTLED 2026-11-18 by reading the code of the libraries, and corrected in the chapters: questions 9, 10, 13, 19,
21 and the first half of 22. What was found is in `BACKLOG.md` ("Settled"), which also holds every correction
that waits on a run or a test, the chapters not written, and the list of questions below that only the author
can answer. The numbering is kept so that the notes' THESES list (kept private) still points right.

1. The title, and whether the book is published as it grows (and where) or held until complete. PARTLY RESOLVED
   2026-11-24: published as it grows, at github.com/okay-effects/effects-book — the text under CC BY 4.0, the
   code under Apache-2.0, the notes and the inventory kept private (a fresh history: nothing of them in it). The
   title stays open.
2. One running example for the whole book? The drafts use a small bank (balance, move, clock, refusal).
3. How much of source A's Scala-specific machinery (prompts, answer types, the one tree) belongs here, and how much
   stays in that book as the advanced companion.
4. RESOLVED 2026-11-18: the LinkedIn notes not saved in the author's earlier notes were recovered into the author's notes (kept private) and
   worked into the chapters; the notes' THESES list (kept private) says which thesis went where. Questions 16–26 below
   come from that work.
5. Chapter 12 states plainly that "agents work better with effects" is argued and observed, not benchmarked. A
   measurement (the same tasks with and without the skill, checked by tests and by the audit) would settle it;
   okay-loop could run it.
6. Chapter 12 §12.3 says that most of the code behind the book was written with coding agents under these rules,
   and reports one observation from testing okay-remote (a local model corrected itself after a refusal with a
   reason). Both are taken from the project's notes: confirm the wording, or strike it.
7. The preface speaks in the first person about thirty years of backend work and five recurring questions:
   confirm it is how you want to open.
8. Chapter 11: source A (22–23) says a paused program "cannot be serialised, and you do not need to" and builds on
   replay; the chapter states the later position (no replay) and calls that engine "an earlier engine of mine". Confirm.
9. Chapter 11 marker: does any kernel write a captured rest to storage today, or is all durable work okay-serverless's
   explicit state and step? The sources show the rest as a value only in memory.
10. Chapter 13: A 20 gives a capture's allocation as 104 B in one table and "726 B" later; which is right?
11. Chapter 13 marker: anything factual on users, a second contributor or a support policy for the "one author" cost?
12. Chapter 14 marker: has a team other than you and your agents adopted or dropped the approach?
13. Chapter 15 marker: Eff and Unison are in this plan and in chapter 5, but in neither source B nor A 28; cite or drop.
14. Chapter 15 uses theory chapters 9 (conditions) and 13 (Leijen 2014) beside the five the plan lists. Keep?
15. Chapter 6 says the first `take` written for the chapter pulled one item too many; true of the draft, keep?
16. The notes' publication status is unknown except for note 22 (published). The chapters cite notes by file
    (note NN) and call only 22 published. Which others were posted, and should any that were not
    be cited at all?
17. Note against chapter, the chapter kept. Note 10 says a program on effects "cannot open a socket" because
    nothing of the kind exists inside it; chapter 1 and 10.5 say every mainstream platform lets the body reach
    past the row. Likewise note 19 ("the compiler proves it is pure") against the later note 42 and 10.7 ("not
    enough alone"). Confirm the narrower claim is the one you hold.
18. Note against chapter, the chapter kept. Note 20 is headed "control and audit cost nothing", and the older
    note says the price of control "does not exist"; chapter 13 prices a handler at 2.8 to 20 times a plain loop
    and an audited operation at 33–67 ns. Section 10.8 now says each is true of a different road (handlers known
    at compile time; the program as data). Is that the right reconciliation?
19. Note against chapter, the chapter kept. Note 14 (a draft, 2026-10-15) records on a branch that a scope "is
    not released when a handler outside drops k as a value". Sections 3.7 and 6.8 say a real kernel's finalizer
    runs "when a program is left by any road". Is that true of the kernel today? If not, 6.8 needs the exception.
20. Note 10 says the agents' mistakes were "never a silent behaviour change"; note 14 records one that passed
    every test and was found by a benchmark. Section 12.3 says "most". Confirm, and whether the anecdote may stay.
21. Section 7.6 states that an independent transaction is a second handler on a second connection and a nested
    one a savepoint, and runs neither. Does `okay-tx` have both? If so the sentence can cite it. The section
    also asserts a limit of the forty lines (an interceptor between the program and the transaction handler does
    not see inside `Atomic`), so the notes' "the same tree under an audit that refuses writes" is not claimed
    here. How does the real kernel treat an operation that holds a program?
22. Two citations came from the notes and not from sources A or B: Brachthäuser, Schuster, Ostermann, "Effects
    as Capabilities", OOPSLA 2020 (10.7), and Petricek, Orchard, Mycroft, "Coeffects", 2014 (12.9). Chapter 15's
    rule is to cite only what the companion texts give. Add both to 15.8, or keep them inline as "as my note
    cites it"? Section 10.7 also leaves out the compiler version note 19 names for the transaction check, and
    says it was not re-run; give the version and the test if it should be stated as fact.
23. Section 10.9 (for the manager) names five readings of one process; "costed" is marked as not built. Section
    12.9's "give agents effects, not frameworks" is marked argued. Both follow notes 41 and 10; soften or keep?
24. Section 11.5 quotes note 22's figures (0.24, 2.75 and 5.9 µs per 1,000 operations) with the caveat that the
    replay figure comes from an earlier session. If a single-session run exists, replace them. Note 21 says a
    Goose continuation can be "saved as bytes": is that exercised as durable storage? It bears on question 9.
25. Not used, waiting on other questions: answer-type modification and the typestate protocol (notes 06, 07;
    question 3), what the Rust and Goose ports cost (notes 08, 21; now chapter 3g, which does not yet use
    them). Fibres without a fibre runtime (note 04) is used since 2026-11-18, in §8.9; the row for it in
    the notes' THESES list (kept private) (a20) still says "not used" and was left as it stood. Notes on the weights
    work (31–33, 37–39) were kept out except three sentences of principle in 12.10: the same operation
    answered by a model, an exact part or a person.
26. Section 12.5 uses two incidents from note 45 without names: an instruction that had to be repeated, and a
    plan that another agent had already measured to be worse. Confirm they may appear.

From making the book implementation-neutral (2026-11-18):

27. The notation of §2.0 (`x <- Op(…)`, `Op(…), rest => …`, `p under h`, `A ! (E + R)`) is mine to propose and
    yours to accept. Still to decide: whether `under` reading left to right (innermost first) is right. SETTLED
    2026-11-18 by your instruction: chapters 6 to 12 are in this notation, with the renderings as the runnable
    witnesses and a real language only where a listing's point is a feature of one.
28. Part III is lettered 3a to 3h so that chapters 5 to 15 kept their numbers and every cross-reference stayed
    true; the Parts were renumbered (Compared is now IV, architecture V, control VI, agents VII, costs VIII,
    history IX). Renumber the chapters properly once the list is stable, or keep the letters?
29. Other people's libraries and languages in Part III are described from what I know of their documentation;
    nothing was re-fetched in this pass. Cells and details I was not sure of were left out and are listed in
    the backlog ("Left out for lack of certainty"). Before publication each description should be read against
    the project's current documentation, ideally by running the bank on it.
30. Part III names your repositories and quotes their documents (source G), some of them private or
    unreleased, and quotes figures whose protocols are partial (a tool and a date, no commit; "under other
    load"). Each figure is given with what its document says of how it was taken. Keep them, replace them with
    a single-session run, or drop them?
31. A sentence in §3a(e) says of replay as a way to a second resumption: "I have used it and removed it." It
    rests on okay-rust's history (the stale `okay-rs` README still describes replay). Confirm the wording.
32. Two renderings print "cannot" for a reason that is the standard library's and not the language's: Kotlin
    (frames can be copied by reflection, as okay-kotlin does) and Java on Espresso. The chapters say so. Should
    the renderings stay the shortest honest form, as now, or should a second Kotlin rendering copy frames?
33. Not written: a Swift rendering (Swift is installed), and renderings in Effekt, Unison, Eff or Frank (not
    installed; no install was attempted). Wanted?
34. The appendix planned in question 25's time is folded into Part III. Notes 08 and 21 (what the Rust and
    Goose ports cost) are still unused; okay-goose appears in Part III only where chapter 11 already cited it.

From making the one idea the spine (2026-11-18):

35. Chapter 2b words your two-word summary with three qualifications (they are quoted in the section "The one
    idea everything hangs on" above). The first is the one you may want to weigh: the book says that
    "algebraic" names the theory (operations that commute with sequencing, with equations) and that the
    algebraic data type enters because the free model of a signature is one — so "algebraic data types" is
    right as engineering and is not where the name comes from. Is that the emphasis you want?
36. The map is chapter 5, after Part III, with a four-line version in §2b.4. The alternative was a chapter of
    its own before Part III. I kept it late because six mechanisms on it are also substrates that Part III
    builds on. If you want the full map before the constructions, chapter 5 can move to 2c with little change;
    its references to chapters 3a to 3f would then point forward.
37. Section 2b.3 cites Wu, Schrijvers and Hinze, "Effect handlers in scope" (Haskell Symposium 2014), for
    scoped operations. It is a fifth paper that is not in your companion texts (chapter 15's rule; question
    22). Keep, or say the same without the citation?
38. Section 2b.3 states that every library and language "I know of" in this family drops the theory's
    equations and checks laws by tests. That is from memory of their documentation, like the rest of source H.
    Chapter 4 (the laws) is now owed by two chapters, 2b and 16, as well as by Part III.
39. Section 6.8 has a table of what each of the sixteen renderings can follow in chapter 6. Its columns for
    leaving, instructing and resuming twice are run (lines 2 and 6 of the scenario); its columns for "later"
    and "look back" are read from each rendering's handler interface. A seventh line of the scenario would
    make them evidence, in all sixteen files. Wanted? (Backlog: "A line for the way back".)
40. Section 7.6 now argues what an interceptor sees when a bracket is a block that installs a handler (direct
    style) and not a node. No rendering runs it. A transaction in one direct-style rendering — OCaml or Koka
    would be the shortest — would settle it.
41. Statements about my own libraries were kept where their subject is, in paragraphs marked as mine (§6.8,
    §7.6, §11.4), and collected by name in chapter 3g under Scala. Measurements from notes stayed in §10.8 and
    §11.5, where chapter 13 (§13.6) already says they are. Move those figures into chapter 13 as well?
42. Chapter 5 says of Kyo only what chapter 3g already said, and of Effect (TypeScript) only that its shape is
    that of ZIO's, "as far as I can describe it". Both are in the backlog as claims to check.

From the reference demonstration (2026-11-18):

43. SETTLED 2026-11-18 by the author: chapter 3i stays at the end of Part III, after 3h. (The question was
    whether it should follow chapter 3 or 3b directly, as the continuation of the forty lines.)
44. SETTLED 2026-11-18 by the author: yes, chapter 3's forty lines are stage 0 of the path, 0 to 4, and
    "in stage 0, the forty lines, remove the type casts — use GADT." Done: `kernel.scala`'s `handle` binds
    the hidden answer type with a type pattern and has no cast; chapters 3, 3b and 3i say so. (They are stage
    4's `Return` and `Inject` with a substituting `flatMap`; §3i.5 says so through the type of `head`.)
45. Stages 3 and 4 print the scenario and are checked by `run-all.sh`, but the book still says "sixteen
    renderings": the stages are counted as states of one implementation and not as renderings. Keep that
    count, or say eighteen?
46. The plan for the path expected that a journal could not be written before rows. It can: in stage 1 as a
    wrapper of the answer function, in stage 2 as a handler that asks the question again. The chapter says
    what fails instead (the types say nothing; one closed vocabulary; the stack). Confirm that telling.
47. SETTLED 2026-11-18 by the author: yes, stage 4 grows `remap` as a fifth addition, because interceptors
    are the capability that matters most. Done: `Renaming`, `interposing`, `remap`, `interposed` in
    `stage4.scala`; the journal and the limit are interceptors over a `pay` written once at a fixed row;
    stage 3 keeps its way (a row that names `Accounts` twice and a `pay` written for any row), which is what
    stage 4 improves on. §3i.5, "Places renamed".
48. Stage 4 shares one loop, `head`, among its interpreters; `Free.scala` writes the loop out in each. The
    stage reads better for it and is further from the file the reader is sent to. Keep, or write it out?
49. The stages need no `@unchecked` under Scala 3.3.8 (patterns with every type argument a variable; a place
    `Nowhere` for the empty row). `Free.scala` and `Places.scala` have several. Whether the library could drop
    them the same way is a question for the library, not answered here.
50. Chapter 3i states figures in bytes from one machine and one JVM, taken by the JVM's per-thread allocation
    counter. Chapter 13's rule is a benchmark with a protocol. Are these acceptable as illustrations in 3i,
    or should they move behind chapter 13's protocol?

From the laws (chapter 4, 2026-11-18):

51. The chapter has a vocabulary of its own (`Get`, `Put`, `Cell`; `Refuse`, `Now`, `Choose` kept from
    chapter 2) because the bank has no operation whose laws are the standard ones. Keep that, or state the
    laws on the bank (`Balance` and `Move` as a state) so that the book has one running example (question 2)?
52. §4.6 calls a handler with its state in a variable a breach of a law, and chapter 3b says of the same
    thing "neither is wrong; a design must say which it is". The chapter keeps both: the design is allowed,
    and what it gives up is that the order of handlers decides. Is that the position you hold, given that
    chapter 3's own `InMemory`, the bank files' `Accounts` and most of the renderings are written that way?
    If the law is to be taken strictly, the bank files of the Scala and Java trees should pass their state
    along, and §3.6 would change.
53. The law files of four renderings are not the technique of their bank files in one respect each, because
    the bank file could not state a law: Rust and Zig add a machine `Under(handler, machine)` where the bank
    files have a flat list of handlers; Kotlin names effects as context parameters where `bank.kt` has one
    receiver; tagless adds a hand-written continuation monad for a choice outside a state; the Java and
    Scala trees pass state along. Should the bank files be brought to the same form, so that each rendering
    is one technique in two files, or is the difference itself worth a paragraph in Part III?
54. Three of the direct-style law files (Java on threads, Go, C#) keep "the order of handlers" as a position
    in a list that one driver walks; §4.5 says so and the table marks those cells "driver". A reader may
    think that is not the law at all. Rewrite those three with nested handlers (each handler a driver of the
    one inside it), or keep them as the honest picture of that technique?
55. "Any two handlers that only answer commute" is argued in §4.5 and proved for two handlers. A general
    proof needs a definition of "only answers" as a property of a fold's clause. Wanted in the Lean file,
    or is the pair enough for this book?
56. The chapter states sequencing as four equations and says "the laws of a monad" once. Is one mention the
    right amount of that word for the three readers, or should it go to a footnote for the second reader?
57. Sources. D's `Properties` and okay-lean's `Laws.lean` were read for what a kernel is held to and what
    can be proved; the chapter's list is narrower than D's (no constant stack, no finalizers, no streams, no
    asynchrony, no audit) because those are properties of a library and not laws of the idea. Chapter 8 or
    13 could carry them. Is the cut in the right place? In particular: is "constant stack" a law a reader
    should demand of any implementation?
58. The generated checks assert that a failure happens (`FAILS for 12 of 1000` is asserted to be more than
    none) and print the count. The counts are quoted in §4.6 as what the run printed. Chapter 13's rule is
    for measurements; these are counts over a fixed seed. Acceptable in the text, or only in the file?

From the catalogue (chapter 8, 2026-11-18):

59. The chapter's headline is a count: six of the ten standard effects never need the rest of the program,
    so command objects and an interpreter would answer them, and what is hard is the constructs that act on
    a stretch. Is that the emphasis you want for a catalogue, or does it undersell the continuation half in
    the one chapter where a reader meets the standard library?
60. Left out, from source C's own lists: a value that may be absent (`Maybe`), errors that accumulate
    (`Chronicle`), a supply of fresh values (`Fresh`, `Supply`), a sub-program run at most once (`Once`),
    weighted choice with inference as a handler (`Prob`), `Lift` and `Control`. `Store` appears only as the
    lower vocabulary of a state. Which of these belong in the book's catalogue? `Fresh` is the likeliest:
    §7.1 already has `Ids.fresh` in its example vocabulary.
61. The names are the book's (`Get`/`Put`, `Env`, `Tell`, `Refuse`, `Choose`, `Defer`, `Now`/`Sleep`, `Draw`,
    `Yield`/`Fork`/`Await`, `Emit`), kept from chapters 2, 4 and 6 where they existed. Your libraries say
    `get`/`set` or `put`, `ask`, `tell`, `raise`, `choose` or `among`, `defer`, `spawn`, `emit`. Keep the
    book's own, or add a line under each entry with the usual names in the libraries a reader will meet?
62. §8.6 says two things of your libraries from their pages, not from a run: that okay-simple's scope drops
    its finalizers when an error is handled outside it and is "for programs that resume once"; and that the
    larger library counts the holders of a resource across the branches of a search. The second page also
    says that on the kernel a scope whose rest a handler outside drops "releases before it goes, once".
    Is that the state of each kernel today? It bears on question 19 and on §6.8's paragraph "In my own
    libraries", which this chapter did not change.
63. The finding of §8.9: on the forty lines, a handler between the code that forks and the scheduler does
    not apply to the forked body (a `Cell` there leaves the body's `Get` unanswered). The chapter argues,
    without a run, that rows would make it a type error and that direct style has the same rule. How do your
    kernels type `spawn(p)`: what row may `p` have, and is a handler between it and the scheduler possible?
64. §8.9 shows async as two things that do not meet in the file: a scheduler as a handler (`Yield`, `Fork`)
    and a program that returns at an `Await` (`start`, an event loop, `CanBlock`). Joining a fibre,
    cancelling one, a sleep that is a timer, and an `Await` inside a scheduled fibre are named and not
    built. Is the split the right teaching, or should the chapter build one run loop that does both, at the
    price of a section several times as long?
65. `CanBlock` is shown in miniature: a Scala `using`, one JVM, the compiler asked with `typeCheckErrors`.
    Note 04's claim is about platforms (a blocking join is a compile error on Scala.js). No Scala.js build is
    under `code/`. Is the miniature enough, or is a second platform wanted as evidence?
66. Note 04's publication status is unknown (question 16); §8.9 cites it by file and reports from it what
    the library's schedulers are. Its row in the notes' THESES list (kept private) (a20) was not updated by this pass.
67. The chapter has one rendering, the Scala tree, like chapters 6, 7, 10, 11 and 12. A scheduler in OCaml or
    Koka and a stream on a generator would each be short and would turn "every technique can" in §8.9 and
    §8.10 from argument into a run. Wanted now, or with the backlog item "More renderings of the later
    examples"?
68. The chapter is the longest in the book (about 1,100 lines of file against 741 for chapter 3i). Each entry is the
    same five parts. If it should be shorter, the candidates to move out are §8.9's second half (callbacks
    and blocking, which could be a section of chapter 11) and §8.11's first table.

From screens, servers and stores (chapter 9, 2026-11-18):

69. §9.3 reports from `okay-faces/specs/cloudflare.md` (2026-10-25) that a session on Workers is resumed by
    starting the program at its last point and playing again the requests since, and that a program with
    no points is kept by all its requests. §11.6 says "I no longer build on it" of replay as the means of
    resuming, and your rule elsewhere is no replay. The chapter says both and calls the first a bounded
    replay. Is that the state of the hosting today, and should §11.6 carry the exception, or the hosting
    change?
70. The chapter's order is screen, store, server, against its title, because the server hosts a program
    that saves. Keep the title and the order, or retitle "Screens, stores and servers"?
71. `together` (§9.2), the handler that holds the rests of several parts and asks once for all their keys,
    is in none of your libraries; okay-store decides that relations are keys and a second `find`, and a
    join is the program's. The chapter says so. Is batching something you would build (a `GetMany` of
    `Data`, a `together` over `Mapped`), or should the section be marked as the book's own construction?
72. The book now has three positions on a transaction: a node that holds its body (§7.6, and okay-tx), a
    handler around a part that writes on its `done` line (§9.2, and okay-store's `Datas.atomically` for a
    whole program), and okay-store's "a transaction within a program has no place to be said: split the
    program". Is the spread right, or should one be recommended?
73. `Find`, `Save`, `Query` and `KvGet` are vocabulary at the level of the transport, which §7.1 and §14.7
    warn against; §9.2 says the store is at the right level "only by courtesy". A domain effect (`Orders`)
    handled into `Store`, as okay-store's three levels have it, would be about thirty lines more. Wanted?
74. The worked application is an order desk invented for the chapter. okay-shop is a real one on the same
    three libraries and was read, not described. Should the closing section be built on okay-shop's own
    vocabulary (`Store`, `Mail`, `Captcha`, `Ids`, `Notify`), or stay invented so that nothing private is
    near the book?
75. The comparisons (a component framework, an ORM, a web framework) name no product, by the rule that
    nothing about other people's tools is said from memory. Chapter 5 names its mechanisms. Should chapter
    9 name the ones a reader knows, after their documentation is read (backlog), or stay general?
76. The SQL of §9.2 is asserted as text and never executed, because the book's code takes no dependency;
    the chapter says that this is exactly what cannot find the NULL of okay-store's spec. Is one test
    dependency (an embedded SQL engine) acceptable under `code/` for an agreement test between `InTables`
    and `AsSql`, or does the rule stand?
77. The descriptions of okay-ui, okay-faces, okay-store and okay-serverless quote their READMEs and specs
    by file, like source G (question 30); two figures of theirs are in the text ("twenty of twenty", a
    test on a local DynamoDB; the decimal `12.5`). Confirm each still describes its library, in particular
    that okay-store has no batching and that a transaction inside a program is still not offered.
78. The chapter is about 8,000 words, the top of what was asked. If it should be shorter, the candidates
    are `FieldByField` with its paragraph, the second round of `together`, and the paragraph on what
    okay-ui has beyond the miniature.

From chapter 3j, okay-tiny (2026-11-27):

79. SETTLED 2026-11-27 by the author: the code comes into the book, not whole but as small as it can be and
    fully typed, optimised for minimalism and not for speed, as the forty lines are: `code/java/rows/`.
80. SETTLED 2026-11-27 by the author: what a later result shows wrong is rewritten in place, as if it had
    been known from the start; no "as first written", no history of the mistake in the text. Done for Java in
    chapters 3, 3b, 3e, 3g (the section, the pattern paragraph and both tables), 3h (the table and the
    pointer), 5.2a, and 3j itself (its opening and 3j.11 state the results, not the corrections).
81. SETTLED 2026-11-27 by the author: yes, the laws. `code/java/rows/Laws.java` prints the twelve lines (7 is
    the compiler's, `refused/NobodyAnswers.java`), and §4.10 has its row; §4.4, §4.6 and §4.9 count it.
