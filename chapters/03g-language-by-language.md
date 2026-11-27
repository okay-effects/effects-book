# 3g · Language by language

The same question for each language: what does it give that matters here, which of the families of chapters 3b
to 3f are therefore open, which fits best, what is painful or impossible, and what exists already.

Three kinds of statement appear, and I keep them apart. *Run here* means a file under `code/` that prints the
scenario. *Others' libraries* are described from their documentation, and only what I am sure of; a library
missing from a list is one I did not feel able to describe accurately, not one I judge. *My own ports* are one
implementation among those listed, cited by file for what they met in that language. Their numbers are quoted
only with the protocol their own documents give.

What a language gives is mostly six things: closures; a way to tie an operation to the type of its answer
(GADTs or indexed families); abstraction over type constructors (higher-kinded types); a suspension mechanism
(generators, coroutines, cheap threads); a compile-time transform (macros); and a notation for sequencing
values (`do`, `for`). The table at the end has them side by side.

## Scala

**Gives.** All six, and more than one of each: closures; GADT matching; higher-kinded types; implicit and
context parameters; union and intersection types; macros; `for`; and, on the JVM, virtual threads. Capture
checking is an experimental feature of Scala 3.

**Open.** Every family but the last. *Run here:* the tree (`scala/bank.scala`) and capabilities
(`scala/capabilities.scala`).

**Best fit.** There is no single one, which is the language's situation exactly: the community is divided
between monadic style (a tree or a runtime under `for`) and direct style (capabilities, with threads beneath).
For control and audit, a machine over data. For ordinary services where swapping and testing are the need,
context parameters.

**Painful.** `for` is heavier than direct style, and mixing the two styles in one codebase is costly. Rows in
types work and are hard on error messages.

**Others.** ZIO and Cats Effect are fibre runtimes whose programs are values run by an interpreter; ZIO's type
lists required services and errors. Kyo is an effect library whose computation type lists pending effects as
an intersection. atnos-eff is a freer-monad `Eff`. Ox is direct-style concurrency on virtual threads.
dotty-cps-async is a macro from direct style to monads. Gears is an experimental direct-style async library.

**Mine.** A machine with typed rows (`okay-scala`), and one interface with three implementations behind it — a
machine, a `Free` tree, tagless — so that they can be measured against each other (`okay-simple`; chapter 3b
quotes its table). What Scala cost: an `inline` method loses the type knowledge of the GADT match it is
expanded in, so some helpers could not be inlined; and a row can be written where a single effect is meant,
which then fails at run time (`okay-scala/docs/design.md`, `okay-effects/README.md`).

Two things about these libraries are stated where their subject is, in paragraphs headed or marked as mine,
and are collected here with the names, as read from the code on 2026-11-18. *Finalizers* (section 6.8): an
abort and an explicit unwind or cancel run them, with tests in `okay-core` (`TestGuard`) and `okay-scala`
(`EnsuringSuite`); a rest that is kept and never called releases nothing, which `okay-scala` pins with a test
and `okay-core` does not test in either direction; the machine of `okay-simple` has no finalizers.
*Transactions* (section 7.6): `okay-tx` flattens a bracket inside a bracket into the outer one and has neither
an independent transaction nor a savepoint; `okay-jdbc` refuses a nested transaction at run time; and a
bracket's body is opaque to an interceptor, by reading the kernel, with no test.

## Java

**Gives.** Lambdas; sealed interfaces, records and pattern `switch` that must cover a sealed type; intersection
bounds on type variables; virtual threads and scoped values. No higher-kinded types, no GADT refinement, no
notation for sequencing, no macros, no public continuation.

**Open.** The tree, with the answer type read off each operation and the row as an intersection bound: javac
checks the answers, the set of effects pending and a handler's completeness, and no handler has a cast
(okay-tiny, chapter 3j; not run here). `java/Tree.java` (*run here*) is the same tree at its shortest, with a
closed set of operations and one cast. Direct style on virtual threads (`java/Loom.java`, *run here*), or on
the tree by a javac plugin that rewrites a method into its chain before types are checked (chapter 3j).
Capabilities, as ordinary interfaces. State machines only by rewriting bytecode.

**Best fit.** Direct style on virtual threads where one resumption is enough and existing blocking code must
be kept; the tree with its row in the type where a handler must see everything, a rest must be resumed twice,
or the compiler should refuse an effect left unhandled. The tree is heavy to write in Java — chains of
`flatMap`, or the plugin — and it is not weaker for the programs than for the interpreter.

**Painful.** Writing, more than typing. Design choices decide most of it: where a clause is made by a function
of one operation the answer is typed; where the state is hidden as an existential it is not erased; where the
row is a set it is never moved by hand. My earlier libraries made the other choices and paid for them — "a
lambda can't implement a generic method", a handler that erased its state's type, rows positional and moved by
hand (`okay-java/README.md`, `okay-effects/README.md`). What Java costs whatever the design (section 3j.11): an
effect named twice on each operation, the part's type written where handlers are put, a row that is never a
type argument, one effect of a class in a row, and javac's messages about inference variables. Multi-shot in
direct style needs either a different JVM or bytecode rewriting; on the tree it needs nothing.

**Mine.** Four, which between them are a tour of the options: a `shift0` machine with a row notation
(`okay-java`); a bytecode transform that saves frames, multi-shot (`okay-java/resumable`); direct style on the
JDK's continuation, on threads, or on Espresso (`okay-direct`); and the tree in one file, with the row as a set
and direct style as a javac plugin (`okay-tiny`, chapter 3j). Chapters 3d and 3e quote the first three's one
shared benchmark table.

## Kotlin

**Gives.** `suspend`: a compiler transform to state machines whose continuation the standard library hands
out as an object. Context parameters. Lambdas with receivers. No higher-kinded types, no GADT refinement.

**Open.** Capabilities over `suspend` — the natural one. *Run here:* `kotlin/bank.kt`, on the standard library
alone. The tree is possible and unidiomatic.

**Best fit.** Effects as interfaces of `suspend` functions, passed as context or receiver; handlers that take
the continuation when they need the rest. The language was nearly designed for the one-shot case.

**Painful.** The second resumption. The rendering shows the raw failure. My port copies frames by reflection
on the JVM, cannot on native, and keeps a data-based machine beside it for programs that need many resumptions
on every platform (`okay-kotlin/docs/design.md`; chapter 3d has its two figures). Its handlers are found
lexically, which that document calls "the only one Kotlin can type": at run time `State<Int>` and
`State<String>` are one class.

**Others.** kotlinx.coroutines is the standard concurrency library on the same mechanism. Arrow provides typed
errors as a capability (`Raise<E>`) among much else.

## Rust

**Gives.** Enums and exhaustive `match`; ownership and destructors; closures (with lifetimes); `async`, a
state machine transform whose futures anyone may poll; procedural macros. No garbage collector, no GADTs, no
higher-kinded types, no guaranteed tail calls. Coroutines are unstable.

**Open.** State machines — by hand (`rust/bank.rs`, *run here*), by a macro, or by `async`. Capabilities as
traits and generics, which is ordinary Rust. A tree with boxed closures works and fights the language.

**Best fit.** `async` as the substrate for one-shot effects, with handlers found by type; an explicit `Clone`
state machine where the rest is needed more than once.

**Painful.** A value alive across a stop must be owned by the state. Without GADTs a machine typed by its
answers cannot be written, so a general interpreter erases answers and checks them inside
(`okay-rust/machine/README.md`). A program value is single-use; a closure resumed many times must be `Fn` and
`Clone`.

**Mine.** One API on `async` (one-shot), an attribute that generates `Clone` state machines (multi-shot, with
the restrictions chapter 3e lists), and a data machine as the specification (`okay-rust`). An earlier version
got multi-shot by replaying the body; it was removed.

## TypeScript and JavaScript

**Gives.** Closures; generators and `async`; in TypeScript, union types that make a usable row, and types that
are erased at run time. No higher-kinded types, no way to copy a generator.

**Open.** Generators (`typescript/bank.ts`, *run here*) and the tree. Capabilities as plain objects.

**Best fit.** Generators for programs, data for the parts that need many resumptions, and a type that tells
the two apart.

**Painful.** One type for everything sent into a generator (chapter 3d). Erased types: a cast passes anything.

**Others.** Effect is the major library: programs are values with their result, error and requirements in
the type, run by a fibre runtime, with a generator syntax. redux-saga interprets effects yielded by generators.

**Mine.** `okay-ts`: both representations, with the row as a union and the number of resumptions in the
type; a generator handed to a multi-shot handler "is a type error" (`okay-ts/README.md`).

## Python

**Gives.** Generators with `send`, `throw` and `close`; `async` on the same mechanism; closures; structural
`match`. No static guarantee of anything; no copy of a generator.

**Open.** Generators (`python/bank.py`, *run here*) and the tree.

**Best fit.** Generators. The row is whatever handlers are installed, found out at run time.

**Painful.** Nothing checks that a question has a handler, or that an answer has the right type, before the
line runs. Tests have to carry what types carry elsewhere.

## Go

**Gives.** Goroutines and channels, cheap and built in; interfaces; closures; `panic`/`recover`. Generics
without higher-kinded types, no sum types, no implicit parameters.

**Open.** Direct style on goroutines (`go/bank.go`, *run here*). Capabilities as interfaces passed
explicitly, which is what idiomatic Go already does with `context.Context` and small interfaces.

**Best fit.** Interfaces for the common case; a goroutine and two channels where a handler must hold the rest.

**Painful.** An answer's type cannot be tied to its question: the rendering asserts each one. No sum types, so
"an operation is one of these cases" is a type switch with a default. One resumption.

## C#

**Gives.** Iterators and `async`/`await`, both compiler-made state machines; `async` open to user-defined
awaitable types; LINQ query syntax, which is a notation for sequencing over any type with the right methods.
No higher-kinded types, no GADT refinement.

**Open.** State machines through the compiler (`csharp/bank.cs`, *run here*, on iterators). The tree, with
query syntax as its notation. Capabilities as interfaces — the mainstream dependency injection of the platform.

**Best fit.** `async` with a library as the scheduler for one-shot effects; interfaces for the rest.

**Painful.** The generated state machine cannot be copied. Iterators cannot receive an answer (chapter 3e).

**Others.** language-ext is a functional library for C# that uses query syntax for monadic code.

## Swift

**Gives.** Enums with associated values, `indirect` for recursion; closures; `async`/`await` with structured
concurrency; a continuation API for bridging callbacks, whose continuation must be resumed exactly once. No
higher-kinded types, no GADTs, no user-visible generators.

**Open.** Capabilities, and `async` as a one-shot substrate. The tree, with casts. *Nothing is run here*:
Swift is installed and no rendering was written.

**Best fit.** Protocols passed in, or looked up from task-local values.

**Others.** Point-Free's swift-dependencies supplies dependencies through task-local values, overridable in
tests; their Composable Architecture has reducers return effects as values for a store to run.

## Zig

**Gives.** `comptime`, where types are values and a missing declaration is a compile error; tagged unions;
explicit allocators. "No closures, no garbage collector and no custom operators" (`okay-zig/specs/design.md`).

**Open.** State machines by hand (`zig/bank.zig`, *run here*): the rest is a union, copied by assignment, and
handlers are a union too. Capabilities resolved at compile time.

**Best fit.** A context struct whose handlers are checked by `comptime` for ordinary effects, with no
continuation at all; a hand-written union for the few programs whose rest is needed.

**Painful.** `comptime` cannot rewrite a function body, so there is no macro to generate the states.

**Mine.** `okay-zig` has exactly those two layers and a data machine with its stack in an arena.

## Haskell

**Gives.** Everything the tree and the final encoding want: GADTs, higher-kinded types, type classes and type
families, `do`, laziness. Recent GHCs also expose delimited-continuation primitives.

**Open.** The tree (`haskell/Bank.hs`) and tagless final (`haskell/Tagless.hs`), both *run here*, both with no
cast and both printing line 6.

**Best fit.** The language has more answers than any other, and the choice among them is an old argument.

**Others.** `mtl`: effects as classes over a monad, with transformers beneath. `free` and `freer-simple`:
programs as data. `polysemy`: effects as data types with interpreters. `fused-effects`: effects interpreted by
type-class instances, called carriers. `effectful`: an effect monad built over `IO` with an environment of
handlers, aimed at speed and at fitting the existing `IO` ecosystem. `bluefin`: handles passed as arguments,
scoped by a type parameter. `eff`: a library designed on GHC's continuation primitives.

**Painful.** Choosing. And the lifting problem wherever transformers are underneath (chapter 3c).

## OCaml

**Gives.** Since 5.0, effect handlers in the language and runtime; GADTs; modules and functors.

**Open.** The built-in handlers (`ocaml/bank.ml`, *run here*). A tree, as in any ML. Final encodings through
modules.

**Best fit.** The built-in handlers, for anything one-shot. Chapter 3f.

**Painful.** No effect types: an unhandled effect is a run-time exception. One resumption.

**Others.** Eio, direct-style I/O on effects. Before version 5, Lwt and Async gave concurrency in monadic
style.

## Lean

**Gives.** Dependent types and indexed families; `do`; a checker for termination; proofs in the same language
as the program.

**Open.** The tree (`lean/Bank.lean`, *run here*), with every handler accepted as total.

**Best fit.** The tree — and it is the one language here in which the laws of chapter 4 can be theorems.
They are: `lean/Laws.lean` has twenty-four, *checked here* (section 4.9).

**Painful.** Universes: a program type that hides a type lives one level up, with consequences — "an effect
answering a program needs a universe more", and such a value "cannot live in an `IO.Ref`". And speed needs a
second definition: a fast interpreter that re-associates binds is not structurally recursive, so it is
`unsafe` and "unproved against the reference" (`okay-lean/AGENTS.md`).

**Mine.** `okay-lean` proves the monad laws and what each standard handler makes of its operations, about the
reference definitions (`okay-lean/README.md`, "The laws").

## The effect languages

Koka (`koka/bank.kk`, *run here*), Effekt, Eff, Unison, Frank: chapter 3f. In them the question of this
chapter does not arise; the question is whether you can use them.

## Side by side

What each language gives. "—" is absent; "lib" is available through a library or an encoding.

| | closures | typed answers | higher-kinded | suspension | transform | notation |
| --- | --- | --- | --- | --- | --- | --- |
| Scala | yes | yes (GADT match) | yes | virtual threads | macros | `for` |
| Java | yes | by witness (3j) | — | virtual threads | bytecode; javac plugin (3j) | — |
| Kotlin | yes | — | — | `suspend` | `suspend` | — |
| Rust | yes, owned | — | — | `async` | `async`, macros | — |
| TypeScript | yes | — | — | generators | — | — |
| Python | yes | untyped | untyped | generators | — | — |
| Go | yes | — | — | goroutines | — | — |
| C# | yes | — | — | iterators, `async` | iterators, `async` | LINQ |
| Swift | yes | — | — | `async` | `async` | — |
| Zig | — | — | `comptime` | — | — | — |
| Haskell | yes | yes (GADTs) | yes | primitives | — | `do` |
| OCaml | yes | yes (GADTs) | modules | fibres, handlers | — | binding operators |
| Lean | yes | yes (families) | yes | — | macros | `do` |

Koka is not in this table: what it gives is the handlers themselves.

And which family is therefore the natural one. **Run** marks a rendering under `code/`.

| | tree | final | capabilities | generators, threads | state machine | built in |
| --- | --- | --- | --- | --- | --- | --- |
| Scala | **run** | yes | **run** | yes (threads) | macro | — |
| Java | **run**; typed rows: 3j, not run here | — | yes | **run** | bytecode | — |
| Kotlin | possible | — | **run** (over `suspend`) | — | **run** (`suspend`) | — |
| Rust | fights ownership | — | yes (traits) | — | **run** | — |
| TypeScript | yes | encodings | yes | **run** | — | — |
| Python | yes | — | yes | **run** | — | — |
| Go | weak types | — | yes | **run** | — | — |
| C# | yes, with LINQ | — | yes | — | **run** | — |
| Swift | with casts | — | yes | — | `async` | — |
| Zig | arena machine | — | yes (`comptime`) | — | **run** | — |
| Haskell | **run** | **run** | yes | primitives | — | — |
| OCaml | yes | modules | yes | (built in) | — | **run** |
| Lean | **run** | yes | yes | — | — | — |
| Koka | — | — | — | — | — | **run** |

One pattern is visible in the second table and worth saying aloud. The languages with the richest types
(Scala, Haskell, Lean) take the tree and the final encoding naturally. The languages with the strongest
suspension mechanisms (Kotlin, Go, Java, C#, Python, TypeScript, OCaml) take direct style naturally, and
are one-shot. The languages without a collector (Rust, Zig) take the state machine. Java belongs to two rows
of this: with sealed types, exhaustive `switch` and intersection bounds it also takes the tree with a typed
row (chapter 3j), at the price of writing the chains. Very little of this is about taste. It is the
consequence of what each language already built for other reasons.
