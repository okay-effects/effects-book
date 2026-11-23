# The code of the book

One scenario, written sixteen times in thirteen languages: a small bank (balance, move, clock, refusal), a
program `pay` over it, and six things done to that program from outside. Every rendering prints the lines of
`expected.txt`:

1. the program run under a bank in memory and a fixed clock;
2. the same program refused, and no money moved;
3. the same program under a journal it knows nothing of;
4. the same program under a limit that refuses a large move before the bank sees it;
5. leaving early: after a refusal nothing more was asked;
6. the rest of the program resumed twice — or, where the technique cannot, a line beginning `6 twice: cannot:`
   that says why.

`sh code/run-all.sh` runs them all and compares. A toolchain that is not installed is reported and skipped.

| rendering | technique | chapter | line 6 |
| --- | --- | --- | --- |
| `scala/bank.scala` on `scala/kernel.scala` | a tree and closures | 3, 3b | yes |
| `java/Tree.java` | a sealed tree and an interpreter | 3b | yes |
| `haskell/Bank.hs` | a tree over a GADT | 3b | yes |
| `lean/Bank.lean` | a tree over an indexed family | 3b | yes |
| `haskell/Tagless.hs` | tagless final | 3c | yes |
| `scala/capabilities.scala` | capabilities as context parameters | 3c | cannot |
| `typescript/bank.ts` | generators | 3d | cannot |
| `python/bank.py` | generators | 3d | cannot |
| `java/Loom.java` | direct style on virtual threads | 3d | cannot |
| `go/bank.go` | direct style on goroutines | 3d | cannot |
| `rust/bank.rs` | a state machine that is `Clone`, by hand | 3e | yes |
| `zig/bank.zig` | a state machine copied by value, by hand | 3e | yes |
| `csharp/bank.cs` | the compiler's iterator state machine | 3e | cannot |
| `kotlin/bank.kt` | `suspend` functions; the continuation as an object | 3c, 3e | cannot |
| `ocaml/bank.ml` | OCaml 5's own effect handlers | 3f | cannot |
| `koka/bank.kk` | Koka's effect types and handlers | 3f | yes |

The one command for each, from the root of the book:

```sh
scala-cli run code/scala --main-class book.bank
scala-cli run code/scala --main-class book.capabilities
java code/java/Tree.java
java code/java/Loom.java
runghc code/haskell/Bank.hs
runghc code/haskell/Tagless.hs
lean --run code/lean/Bank.lean
node code/typescript/bank.ts
python3 code/python/bank.py
go run code/go/bank.go
rustc --edition 2021 -O code/rust/bank.rs -o /tmp/bank-rs && /tmp/bank-rs
zig run code/zig/bank.zig
dotnet run code/csharp/bank.cs
kotlinc code/kotlin/bank.kt -include-runtime -d /tmp/bank-kt.jar && java -jar /tmp/bank-kt.jar
ocaml code/ocaml/bank.ml
koka -e code/koka/bank.kk
```

## The laws: twelve more lines, generated programs, theorems

Chapter 4. Beside each rendering is a law file by the same technique, printing the twelve lines of
`laws-expected.txt`: each line runs both sides of one law over a vocabulary of its own (a state `Get`/`Put`
with the handler `Cell`, `Refuse`, `Now`, `Choose`) and prints what was seen. Where a technique cannot state
a line it prints the line's name, `cannot:` and the reason; where the types refuse the program of line 7 it
prints `7 nobody answers: does not compile:` and the reason. `run-all.sh` accepts those two forms and nothing
else.

| law file | not the expected line |
| --- | --- |
| `scala/laws/laws.scala`, main `book.laws.laws` | |
| `scala/laws/capabilities.scala`, main `book.laws.capabilities` | 7 does not compile; 10, 11 cannot |
| `java/TreeLaws.java` | |
| `java/LoomLaws.java` | 10, 11 cannot |
| `haskell/Laws.hs` | |
| `haskell/TaglessLaws.hs` | 7 does not compile |
| `lean/Laws.lean` | |
| `typescript/laws.ts`, `python/laws.py` | 10, 11 cannot |
| `go/laws.go` | 10, 11 cannot |
| `rust/laws.rs`, `zig/laws.zig` | |
| `csharp/laws.cs` | 10, 11 cannot |
| `kotlin/laws.kt` | 7 does not compile; 10, 11 cannot |
| `ocaml/laws.ml` | 10, 11 cannot |
| `koka/laws.kk` | 7 does not compile |
| `scala/path/laws4.scala`, main `book.path.stage4.laws` | 7 does not compile |

Each is run as its bank file is, with the file name changed (`java code/java/TreeLaws.java`,
`runghc code/haskell/Laws.hs`, `lean --run code/lean/Laws.lean`, `node code/typescript/laws.ts`, and so on;
`run-all.sh` has every command).

"Does not compile" is a constant line, so something else has to show it. The two Scala mains ask their own
compiler while they are compiled (`scala.compiletime.testing.typeCheckErrors`) and assert that the program
with its clock missing is refused and the same program with a clock is not. For the other three there is a
file that must fail to compile, and `run-all.sh` requires the failure and the compiler's words:
`haskell/TaglessUnanswered.hs` ("No instance for"), `kotlin/unanswered.kt` ("no context argument"),
`koka/unanswered.kk` ("unhandled effect").

The laws on generated programs, in Scala:

| file | what it is |
| --- | --- |
| `scala/laws/terms.scala` | a small language of programs, a generator with a fixed seed, a reference evaluator |
| `scala/laws/generated.scala`, main `book.laws.generated` | twelve laws on 1,000 programs, on `kernel.scala` |
| `scala/path/laws4.scala`, main `book.path.stage4.generated` | ten laws on the same 1,000, on stage 4 |

Both mains also print what they are built to catch: a state kept in a variable of the handler fails "the
rest called twice ends twice the same" (12 of the 1,000 on the forty lines; 18 on stage 4's in-place
handler), and the mains assert that it does fail. No library is used: the generator is a class of
`terms.scala` with a seed and two methods.

`lean/Laws.lean` is also the proofs: twenty-four theorems about the tree over any signature and about this
chapter's handlers, checked when Lean reads the file (`lean code/lean/Laws.lean` prints nothing). The Lean
bank file is not changed and not used by it.

Four law files differ from their bank file in one respect each, because the bank file's form could not
state a law (chapter 4, §4.10): `rust/laws.rs` and `zig/laws.zig` make a handled machine a machine again
(`Under`), where the bank files have a flat list of handlers; `kotlin/laws.kt` names each effect as a context
parameter, where `bank.kt` has one receiver; `haskell/TaglessLaws.hs` adds a continuation monad for a choice
outside a state; and the law files of the Scala and Java trees pass a handler's state along where the bank
files keep it in a field.

## The path: one implementation, grown in four stages

`scala/path/` is chapter 3i: the reference demonstration, a free monad with rows in Scala 3, built from the
simplest form to the shape of a library. Stage 0 of the path is `scala/kernel.scala`, chapter 3's forty lines
(38 as the file stands), which is also the first of the sixteen renderings and is counted once. Stages 1 to 4
are four files, each self-contained and in a package of its own, each
with a main that asserts what it claims. They are not among the sixteen renderings; they are later states of
the first.

| file | what it adds | main, under `book.path` | prints |
| --- | --- | --- | --- |
| `path/stage1.scala` | three nodes and a loop: a free monad with a trampoline | `stage1.stage1` | `stage 1 holds` |
| `path/stage2.scala` | the handler is given the rest of the program | `stage2.stage2` | `stage 2 holds` |
| `path/stage3.scala` | rows as typed places; `run` for the empty row only | `stage3.stage3` | `expected.txt` |
| `path/stage4.scala` | `Inject`, `Delay`, a handler's state, `answer`, `remap` | `stage4.stage4` | `expected.txt` |
| the same | the runs of stage 3 that force each addition; the measurements | `stage4.forced` | `stage 4 holds` |

```sh
scala-cli run code/scala --main-class book.path.stage1.stage1      # and so on
```

`run-all.sh` checks stages 3 and 4 against `expected.txt` like any rendering, and runs the three other mains,
which must exit 0. Stage 4 has two mains because a rendering must print the ten lines and nothing else.
`forced` reads the JVM's count of bytes allocated by the thread; it asserts the order of those figures and not
their values, which are the JVM's to change.

Nothing under `code/scala` has a cast or an `@unchecked`. The journals of `control.scala` and `stopping.scala`
keep answers as text, and a replaying handler reads one back as the type its question is answered with, which
the match on the question gives (chapters 10 and 11 say so).

The end point of the path is `Free.scala` in okay-simple, the author's library; the comment that closes
`stage4.scala` lists what that file has and stage 4 does not. Nothing here depends on it.

## Toolchains

Already on the machine, and left as they were: scala-cli 1.17 (Scala 3.3.8), Java 26, rustc 1.92, Node 26,
Python 3.14, GHC 9.14, Lean 4.36, Go 1.27.

Installed for this code on 2026-11-18, with one command and no password:

```sh
brew install kotlin zig ocaml koka dotnet
```

| toolchain | version |
| --- | --- |
| Kotlin (`kotlinc`) | 2.4.21 |
| Zig | 0.17.1 |
| OCaml | 5.5.2 |
| Koka | 3.2.9 |
| .NET SDK (`dotnet`) | 11.0.100 |

Homebrew installed their dependencies with them (icu4c 79, llvm 22 and lld 22, openssl 4) and, as part of the
same command, upgraded two libraries that were already present: ca-certificates and lz4 (1.10.0 to 1.11.1).

Not installed, and so not run: Effekt, Eff, Unison, Frank, Swift's rendering (Swift is on the machine; no
rendering was written). Chapter 3f describes the first four without code.

## What these files are

No rendering of the sixteen is the reference; the one implementation that is grown in full is the path above
(chapter 3i). The Scala tree is the one chapter 3 builds line by line, and the later chapters'
examples are written on it for now: `scala/examples.scala`, `control.scala`, `continuation.scala`,
`stopping.scala`, `transaction.scala`, `agents.scala`, `catalogue.scala`, `applications.scala`, each with
assertions, each a main class of the same project (`scala-cli run code/scala --main-class book.examples`, and
so on).

Chapters 6 to 12 show their listings in the book's own notation (chapter 2, §2.0) and name
these files as what checks them:

| chapter | sections | checked by | prints |
| --- | --- | --- | --- |
| 2, 3 | all | `scala/examples.scala`, main `book.examples` | `all examples hold` |
| 6 | 6.2 to 6.8 | `scala/continuation.scala`, main `book.continuation`, blocks 1 to 6 | `continuation examples hold` |
| 7 | 7.6 | `scala/transaction.scala`, main `book.transaction` | `transaction examples hold` |
| 8 | 8.1 to 8.10, 8.12 | `scala/catalogue.scala`, main `book.catalogue`, in its blocks | `catalogue examples hold` |
| 9 | 9.1 to 9.4 | `scala/applications.scala`, main `book.applications` | `application examples hold` |
| 10 | 10.2, 10.3, 10.6 | `scala/control.scala`, main `book.control` | `control examples hold` |
| 11 | 11.1 to 11.4 | `scala/stopping.scala`, main `book.stopping` | `stopping examples hold` |
| 12 | 12.9 | `scala/agents.scala`, main `book.agents` | `agent examples hold` |

§10.2 is also checked by line 4 of every rendering, and §6.7 by line 6. §11.2 quotes a value from
`rust/bank.rs` beside the Scala listing.

`scala/catalogue.scala` is chapter 8: ten standard effects (state, reader, writer, failure, choice,
resources, time, randomness, async, streams), each with its usual handlers, the equations they owe, and the
pairs whose order matters, on the forty lines. Of the mains in this table it is the one `run-all.sh` runs
(`holds    scala: chapter 8, the catalogue of standard effects`). Its definitions are in an object, `std`,
because the package already has other chapters' `take` and `Journal`; it uses `Refuse`, `Refusals`, `Now`,
`FixedClock`, `Choose` and `AllChoices` of `examples.scala` and `Emit` and `Collected` of
`continuation.scala`. A handler that remembers something carries it as a value (block 0, `carry`), as
chapter 4 asks; three keep a variable on purpose, each a world that is really shared: a console, the
machine's clock, an event loop. One assertion asks the compiler (`typeCheckErrors`): `await` without the
capability to block must not compile. The last block asserts that 100,000 operations overflow the stack on
the forty lines, as stage 2 of the path asserts of itself.

`scala/applications.scala` is chapter 9: a screen (`Show`, `Enter`, `Press`, `Fill`, answered by a terminal,
by a script and a page at a time), a store (`Find`, `Save`, `Query`, `FindMany`: in memory, as a journal, as
SQL text over no database; a transaction around a part; `together`, which holds the rests of several parts
and asks once), a platform (`KvGet`, `KvPut`, `Fetch`: in memory and on a directory of files), three hosts
of a session (rests kept, answers replayed, a state written by hand), and one order desk under three stacks.
`run-all.sh` runs it (`holds    scala: chapter 9, screens, servers and stores`). Its definitions are in an
object, `app`; it uses `Refuse` of `examples.scala` and `carry`, `refusals`, `allChoices` and `among` of
`catalogue.scala`. No network, no database and no dependency: SQL is text that is asserted and never
executed, and a request and a page are values. Two handlers keep a world on purpose: `Console`, which is two
functions (the tests give it a list of lines; a real keyboard is not run), and `Directory`, which writes
files in a temporary directory and removes it. One assertion is kept because it failed first: a replaying
host that journals only what the person answered saves the order twice (`replayedPersonOnly`).

## What the sixteen renderings do not show

- **"On the way back".** In `rust/bank.rs`, `zig/bank.zig`, `csharp/bank.cs`, `go/bank.go` and `java/Loom.java`
  a handler answers by returning a reply and is never handed the rest of the program. It can answer, refuse,
  ask something else instead, and see an answer on its way back; it cannot continue the program and then act
  on how the program ended, or keep the rest for later. Chapters 3d, 3e and 6 (§6.4, and the table in §6.8) say
  so exactly. No line of the scenario exercises it in any rendering, so that table is read from the handler
  interfaces and not run.
- **Finalizers.** No rendering has finalizers of its own; the direct-style ones unwind through the host's
  `finally`, and no line of the scenario proves it.
- **Where the journal stands.** It sits outside the refusal handler in the pure renderings (Haskell, Lean,
  Koka) and inside it in the mutable ones, which is why both print the same line 5.
- **Line 6 in `haskell/Tagless.hs`** labels its two outcomes by position.

Each rendering is the shortest honest one of its technique, not a library: no rows in the types beyond what the
host language gives for nothing, recursion where a library has a loop, no finalizers beyond what the host does
by itself. Chapter 3 says what a library adds; chapters 3a to 3h say which of those additions each technique
makes easy or hard.

`python/bank.py` is book content, kept for the comparison with the TypeScript generators.
