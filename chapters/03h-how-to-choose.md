# 3h · How to choose

Six chapters of options need an end. This one is the decision: from the problem and the language to a
technique, with the reasons, and then the whole part on one page.

The choice here is among *constructions of the idea*: how to have both halves of chapter 2b in your language.
The other choice — between the idea and a neighbour that has one half, such as plain injection, a command log
or the language's own `async` — is chapter 5's, and question 3 below is where the two meet.

What is argued here and not measured is the weighting. The facts each rule rests on were shown in the chapters
before, by code that runs or by a cited source. That a given fact should decide *your* case is a judgement, and
I mark the places where mine is no better than yours.

## Start from what the problem needs

Five questions, in the order in which they remove options. Most projects are decided by the first three.

**1. Must a handler ever use the rest of the program more than once?**
Backtracking, search, parsers, probabilistic programs, "what would it do if", retrying the rest of a caller.

- *No* — the usual answer. Every family is open. Go on.
- *Yes* — the rest must be a value that survives use: a tree with closures (3b), a machine with immutable
  frames (3b), a state machine that can be copied (3e), a monad that provides it (3c), or a language where it
  is built in and multi-shot (Koka; 3f). Direct style on generators, threads, goroutines, a compiler's own
  `async`, or OCaml's fibres is out. Of the sixteen renderings, eight print line 6 and all eight are from this
  list.
- *Yes, but only in one corner of the system* — use two representations and a type that tells them apart:
  direct style for the bulk, data for the corner. That is what my TypeScript, Kotlin and Rust libraries each
  ended up doing, independently, which is some evidence that it is where the constraints lead.

**2. Must the rest survive the process — be stored, sent, resumed after a restart?**

- *No.* Go on.
- *Yes* — only a rest that is data all the way down qualifies: a state machine (3e) or a machine's frames (3b),
  with a plan for versioning. Otherwise use explicit state and a step function, or a journal with replay and
  its determinism rule (chapter 11). Be careful with claims here, your own included: a rest that *could* be
  written down and a rest that *has been* restored in another process are different facts.

**3. Must something outside the program see every operation, whether or not the code cooperates?**
Audit, policy, sandboxing an agent, a journal that can be trusted.

- *No* — visibility of what you chose to wrap is enough. Capabilities (3c) will do.
- *Yes* — the program must be unable to act except by asking. That is true by construction of a tree (3b) and
  of a generator (3d), and of a language with handlers built in. It is not true of capabilities or tagless
  final, where an interceptor is a wrapper. And in every family it still needs the check of chapter 10 that the
  body does not reach past its row.

**4. Is this a hot path?**

- *No* — most business logic. Ignore cost; choose on the other questions.
- *Yes* — prefer, in this order: capabilities or a final encoding (a direct call); direct style with answers in
  place (no capture for the common operation); a state machine (no allocation). If it must be a machine over
  data, insist on in-place answers and measure. Chapter 13 has what I measured, and the caution that one
  benchmark proves one benchmark.

**5. What must it live with?**

- *An async runtime that owns the threads* — build on it, do not compete with it. One-shot effects on the
  language's `async`, or handlers whose answers are that runtime's tasks.
- *A large body of existing blocking code* — direct style on threads (3d): it is the only family that needs no
  mark on functions.
- *A team that will not learn a new style* — capabilities (3c). It is dependency injection with a type-level
  list, and can be adopted one function at a time.

## Then from the language

| if you write | and need one resumption | and need many | and need to store the rest |
| --- | --- | --- | --- |
| Scala | context parameters; or a machine under `for` | a machine over data | explicit state |
| Java | virtual threads | a tree or machine; or bytecode rewriting | explicit state |
| Kotlin | `suspend` interfaces in context | a data machine beside it | explicit state |
| Rust | `async` with your own driver | a `Clone` state machine, by macro | a state machine, serialised |
| TypeScript | generators | programs as data | explicit state |
| Python | generators | programs as data | explicit state |
| Go | interfaces; goroutines for the rest | programs as data | explicit state |
| C# | `async` with your own driver | programs as data, with LINQ | explicit state |
| Swift | protocols; `async` | programs as data | explicit state |
| Zig | `comptime` contexts | a union copied by value | the same union, written out |
| Haskell | any; `effectful`-style or `mtl` | a freer tree; or a monad that has it | explicit state |
| OCaml | the built-in handlers | a tree | explicit state |
| Lean | a tree | a tree | explicit state |
| any, language open | OCaml 5 | Koka | — |

The third column is mostly "explicit state" for a plain reason. Storing a rest is the hardest thing on any
axis, almost nothing does it, and a small state type with a step function is cheap, honest and already
understood. It is the right default until a system has enough workflows that writing them as states is the
main cost.

## The families, on one page

The three families in which the program is a value or is handed its handlers:

| | tree / machine (3b) | tagless final (3c) | capabilities (3c) |
| --- | --- | --- | --- |
| a. the program is | data | a polymorphic function | plain code |
| b. the rest is | a closure; frames | what the monad's bind makes of it | the host's stack |
| c. handler found | dynamically | by instance | lexically |
| d. types can say | anything the host can | constraints | parameters |
| e. many resumptions | **yes** | if the monad has them | no |
| f. leaving early | finalizer nodes | the monad's | host exceptions |
| g. store the rest | frames, in principle | no | no |
| h. cost per operation | an allocation | a call | a call |
| i. host traces | the interpreter's | the host's | the host's |
| cannot go around | **by construction** | a wrapper | a wrapper |
| programs read as | `for`, `do`, or chains | `do` | the language |
| run here in | Scala, Java, Haskell, Lean | Haskell | Scala |

The three in which the program is code that stops:

| | generators, threads (3d) | state machine (3e) | built in (3f) |
| --- | --- | --- | --- |
| a. the program is | plain code that stops | plain code, transformed | plain code |
| b. the rest is | a generator; a thread | a value of an enum | a fibre; `resume` |
| c. handler found | by the driver | by the driver | varies by language |
| d. types can say | the host's | the host's | rows, or nothing |
| e. many resumptions | no | **yes**, if it can be copied | Koka yes; OCaml no |
| f. leaving early | host `finally` | drop it; destructors | `discontinue`; `finally` |
| g. store the rest | no | **yes** | no |
| h. cost per operation | none, or a switch | none | low |
| i. host traces | the host's | the step function's | the host's |
| cannot go around | generators yes; threads no | **by construction** | yes |
| programs read as | the language | the language | the language |
| run here in | TypeScript, Python, Java, Go | Rust, Zig, C#, Kotlin | OCaml, Koka |

Three things the table says that are easy to lose in the detail.

("Threads no" in the row *cannot go around*: code on a thread can call anything the host lets it call; asking
is a convention there, where for a generator it is the only channel to its driver.)

*No column is all yes.* The tree has every capability and the worst ergonomics and cost. Direct style has the
best ergonomics and one resumption. The state machine has everything at run time and needs a transform. A
language with handlers built in comes closest to all of it, and is a different language.

*"Reads as the language" and "many resumptions" are opposed, outside Koka and a transform.* That single
tension explains most of the variety in existing libraries.

*The row "cannot go around" does not follow the others.* It is the one this book cares most about from Part VI
on, and it is decided by whether the program is able to do anything but ask — not by speed, types or style.

## What, why and how, in five sentences

If you need to say what code reaches for and swap it in tests, pass capabilities and stop there; you already
know how.

If you need the program to stop where it asks and be continued once — streams, async, approval inside one
process, cancellation, early exit with clean-up — use the suspension your language already has, and put a
handler loop under it.

If you need a handler to see everything or to decide the caller's future more than once, make the program
data, and pay for the notation and the interpreter.

If you need that and speed, or that and no garbage collector, or the rest on disk, make the rest a state
machine, and find or build the transform.

And if you are free to choose the language, choose one that has handlers, and read the rest of this book as
its user's guide.

## What this part did not settle

- **Laws.** Sixteen renderings print the same ten lines. That is a test of one scenario. Chapter 4 is where
  "the same" is stated as laws that any implementation must satisfy, and where each rendering prints twelve
  more lines for them. What it leaves open is in its own closing section: the laws are asked of generated
  programs in Scala only, and proved for the Lean tree only.
- **Performance across families on one machine.** The figures in this part come from my libraries' own
  documents, each with its own protocol; nothing here measures the sixteen renderings against each other, and
  they should not be measured — each is the shortest honest form, not a tuned one.
- **Other people's libraries on this scenario.** I have described them from their documentation. Writing the
  bank on each of them, and printing the same ten lines, would turn description into evidence. It is in the
  backlog.

One choice is followed to the end in the next chapter: chapter 3i takes the tree, on the JVM, with rows in the
types, and builds it from three nodes to the shape of a library, each step after a run that failed. Chapter 3j
then takes the same tree into Java, which chapter 3g first credited with no typed answers and no usable row, and
shows how much of both javac can check.
