# 3a · The axes of the design

Part III is about building the idea of chapter 2. There is no one way. There are about six families of
technique, dozens of libraries and half a dozen languages that a working engineer may meet, and they
differ along a small number of independent decisions. This chapter names the decisions. The next five chapters
take the families one by one, with code that runs; chapter 3g goes language by language; chapter 3h is how to
choose.

Nine axes. For each: the options, what each gives, what it costs, and which other choices it forces. An option
is described by its strength first. None of them is a mistake; each is the right answer to some problem.

They fall under the two halves of chapter 2b. Axis (a), how a program is represented, is how the *first* half
is made: whether the operations and the program are data, or code that behaves as if they were. Axes (b), (e),
(f) and (g) — what the rest is made of, how often it can be resumed, how it is left, whether it can be stored
— are the *second* half and its consequences. Axes (c) and (d), finding the handler and what the types say, are
the third purchase that section 2b.5 set apart. Cost (h) and the host (i) are what the choices come to. This
chapter and chapter 3h are about building the idea; the placing of *other* mechanisms on the same two halves is
chapter 5, and is not repeated here.

Where a sentence rests on something I ran, it names the file under `code/`. Where it rests on one of my own
libraries, it names the document; they are evidence from one implementer and are marked as such. Where it rests
on another project's documentation, it says only what I am sure of.

## (a) How a program is represented

This is the first decision and it forces more of the others than any.

**A tree that an interpreter walks.** The program is data: "done, or a question with the rest" (the free and
freer monads), or a larger set of nodes stepped by a loop with an explicit stack (a CEK-style machine). *It
gives* everything of chapter 2 with nothing asked of the host language but closures or objects: a handler is a
function over data, the rest is a value that can be called twice, the program can be inspected before it runs.
*It costs* an allocation for each step, a notation (`flatMap`, `for`, `do`) instead of the language's own
statements, and stack traces that show the interpreter. *It forces* nothing; it is the option that leaves every
other axis open, which is why chapter 3 used it.

**A final encoding.** The program is a function polymorphic in what interprets it: over an abstract monad with
type-class constraints (tagless final), or over its own continuation (Church and CPS encodings). *It gives* the
host compiler the whole program to optimise against one chosen interpreter, with no tree built. *It costs* the
tree: nothing outside can walk the program, and what "the rest" can do is whatever the chosen monad's bind does.
*It forces* a language with higher-kinded types, or a retreat to something weaker.

**Direct style on a continuation the runtime provides.** The program is ordinary code that stops at a question:
a generator at `yield`, a coroutine, a fibre, a virtual thread, or a native effect handler. *It gives* the
language's own control flow, loops and `try`, the debugger and the stack traces, and no allocation for a step
that does not stop. *It costs* whatever the runtime's continuation cannot do — usually the second resumption,
and storing. *It forces* axis (b): the rest is what the runtime made, and its properties are not yours to choose.

**A compiler transform to a state machine.** The program is written in direct style and compiled into a value
with one state per place it can stop: `async`/`await`, Kotlin's `suspend`, a macro or attribute that does the
same. *It gives* direct style and a rest that is plain data, with no runtime support. *It costs* a transform
somebody must write and maintain, restrictions on where a stop may appear, and "coloured" functions: code that
may stop is marked, and the mark spreads to its callers. *It forces* the question of who owns the transform:
the language, or you.

**Capability or context passing.** The program is ordinary code that receives the right to ask as a value —
an interface passed implicitly. *It gives* requirements 1 and 2 of chapter 5 (the signature says what is
reached for, the answer can be exchanged) at no run-time cost and with no new concept for the team. *It costs*
the rest of the program entirely: a method returns once. *It forces* a second mechanism underneath for anything
that needs the rest, and then it is the surface of one of the options above.

## (b) What the rest of the program is made of

| the rest is | called twice | stored or sent | cost of taking it |
| --- | --- | --- | --- |
| a closure | yes | no, in general | an allocation |
| frames as data (a machine's stack) | yes, if immutable | possible; rarely done | a slice or a copy of the frames |
| a stack segment, one-shot (generator, fibre, thread) | no | no | none, or a switch |
| a coroutine or frame that can be copied | yes, by copying | sometimes | a copy per extra resumption |
| a defunctionalised value (an enum of states) | yes, by copying | yes | none; it already exists |

*A closure* is the cheapest to write and available everywhere. It is opaque: it cannot be printed, compared or
written to a disk, and what it holds on to is not visible.

*Frames as data* are what a machine keeps instead of the host's stack. Because they are ordinary immutable
values, resuming twice needs no copy: the same frames are put back twice. One of my libraries states exactly
this of its machine (`okay-scala/docs/design.md`, "Multi-shot needs no copying").

*A one-shot stack segment* is the fastest thing there is for the ordinary case, because resuming is a jump. A
second resumption would continue from wherever the first one left the stack, so runtimes forbid it: OCaml 5
raises an exception on a continuation resumed twice; a Java continuation cannot be cloned; a generator object
in JavaScript or Python has no copy operation. `code/typescript/bank.ts` makes the attempt and prints why not.

*A copyable frame* buys multi-shot back in direct style, where the platform lets frames be read and written. It
copies the variables and shares what they point to, which is a trap in its own right: section (e).

*A defunctionalised value* is the rest written as data from the start: one named case per place the program can
stop, holding the variables alive there. `code/rust/bank.rs` is one, by hand. It is the only form here that is
trivially storable, and the only one a language without a garbage collector or closures can afford.

## (c) How a handler is found

**Dynamically, by tag, up a stack of handlers.** The operation carries its effect's identity; the nearest
enclosing handler of that effect takes it. *It gives* the full semantics of chapter 2, including handlers
installed and removed at run time and an interceptor that sees operations it was not written for. *It costs* a
search per operation unless it is cached, and a run-time error for a missing handler unless the types (d) rule
it out.

**Statically, by evidence.** The compiler passes each function a witness of where its handler is: a type-class
dictionary, an implicit or context parameter, an index into a row. *It gives* a direct call with no search,
which the optimiser can often inline. *It costs* flexibility: what is found is fixed where the code was
compiled, and "the nearest handler at run time" has to be reconstructed. Koka compiles its handlers this way,
by passing evidence; it is how that language makes common handlers cheap.

**Lexically, by capability.** The handler is a value in scope, and a function can ask only what it was handed.
*It gives* the simplest reasoning of the three: to know who answers, read the code. Effekt is built on this.
*It costs* a discipline, or a checker, to keep a capability from being stored and used after its handler has
gone.

The choice shows in one question: *if a function is passed as an argument, whose handlers do its operations
reach — those where it was written, or those where it is called?* Dynamic lookup says "where it is called".
Lexical says "where it was written". Both are defensible; a library should say which it is.

## (d) What the types say

- **Rows.** A type-level list or set of effects, with polymorphism over "the rest of the row". The most
  precise. It needs either a language built for it (Koka infers rows) or type-level programming in the host.
- **Union or intersection types.** The pending effects are a union (TypeScript) or an intersection (Kyo in
  Scala writes a computation's pending effects that way). Set-like for nothing; error messages are the host's.
- **Type-class constraints.** `(Bank m, Clock m) => …`: a set, unordered, checked. The row of tagless final.
- **Context parameters.** `(using Bank, Clock)`: the row as a list of things that must be in scope.
- **Capture sets.** The type records which capabilities a value may hold, so that one cannot leak. Scala 3's
  capture checking is an experimental feature of this kind.
- **Nothing.** A dynamic language, or a typed one where operations are erased to one type. The row exists only
  as the handlers installed, and a forgotten one is found when the question is asked.

What each *gives* is the same thing in different strengths: a forgotten handler is found earlier. What each
*costs* is error messages and inference. And there is a ceiling they share, said in chapter 1 and again in
chapter 10: on every mainstream platform a type lists what a function declares, not what its body can reach,
so the row needs a second witness whichever spelling is chosen.

OCaml 5 is worth naming here because it chose "nothing" deliberately: its effect handlers are in the language
and its type system does not track effects. An unhandled effect is a run-time exception. That was a decision
about compatibility with existing code, and it is a reminder that the mechanism and the types are separate
purchases.

## (e) One resumption or many

**One-shot** is enough for the large majority of what teams build: every interface call, every exception,
generators, async, state, readers, writers, resources, cancellation, interception, audit. It is what lets the
rest be a stack segment, and that is why it is fast.

**Multi-shot** is needed when a handler explores: non-determinism and backtracking, parsers, search,
probabilistic programming, "what would this program do if the answer were this or that" (chapter 6), and
retrying the *rest of the caller* with another answer. What it demands:

- a rest that can be used again — closure, immutable frames, or a copy (axis b);
- a decision about state: when the rest runs twice, does each run see the handler's state as it was at the
  question, or as the previous run left it? Both are useful and they are different programs;
- care with anything linear. A file opened before the question and closed after it is closed twice. A finalizer
  runs once per resumption. One of my libraries says of its resource scopes that they "are for programs that
  resume once" (`okay-simple/docs/modules/resource.md`), and that is the honest position;
- care with sharing. A frame copied for the second run copies the local variables and shares the objects they
  refer to. My Java module that re-enters saved frames has a test named for exactly this
  (`okay-java/resumable/README.md`, "what is shared and what is copied").

What multi-shot is *not* is replay. Running the body again from the start and feeding it recorded answers until
it reaches the same question gives a second resumption on any one-shot substrate. It costs time proportional to
the length of the prefix for every resumption, and it requires the body to be deterministic apart from its
effects — a clock read directly breaks it. I have used it and removed it; the rule I now hold is that a rest is
a value or the technique says it has none.

## (f) Leaving early

When a handler does not continue the rest, whatever the rest was holding must be released. Three mechanisms:

- **Unwinding.** The rest is resumed one last time with a signal — an exception thrown at the question it is
  stopped in — so the host's own `finally` blocks run. Direct-style techniques get this almost for nothing:
  closing a generator runs its `finally`; OCaml has `discontinue`; `code/java/Loom.java` throws at the parked
  call. It costs a walk down the stack.
- **Finalizers as data.** A tree or machine has no host `finally` to run, so "ensure this" is a node, and the
  interpreter runs the pending ones when it leaves. It costs a concept the user must learn and the interpreter
  must get right.
- **Destructors.** Where the language has ownership, dropping the rest drops what it owns. `code/rust/bank.rs`
  relies on it: the refused program's state is freed at the brace.

No mechanism covers the case that matters most to get straight: **a rest that is kept and never called
releases nothing**, because nothing can know it will not be called tomorrow. Chapter 6 (§6.8) says so of the
libraries I have read, mine included. A garbage collector will free the memory; it will not close the file.
Cancellation — leaving because someone *outside* asked — is the same mechanism invoked from elsewhere, and
needs the same answer.

## (g) Can the rest be stored or sent

Only if it is data all the way down. A closure is not. A stack segment is not, with rare exceptions (the
Espresso JVM can serialise its continuations, and one of my libraries uses that: `okay-direct/docs/
implementations.md`). Frames-as-data and state machines can be, and the price is then versioning: the stored
rest refers to code, and the code will change.

So most systems that must survive a restart do not store the rest at all. They use one of two alternatives.

*Explicit state and a step function.* The author writes the workflow as a value and a function from (state,
event) to (state, actions). It asks nothing of the language. It gives up writing the workflow as straight-line
code, which is the thing effects were for.

*A journal and replay.* The program is ordinary code; every answer is recorded; after a restart the program is
run again from the top and fed the recorded answers until it catches up. Workflow engines in wide use work
this way. It keeps straight-line code. It imposes determinism on everything that is not an effect, for ever,
and makes each resumption cost the length of the history.

Chapter 11 is this axis at length, with the code. The fact to carry from here: of my own ports, one
round-trips a captured rest through bytes, and inside one process (§11.4). "The rest is a value" and "the rest
can be saved" are different claims.

## (h) Cost

Three things decide it.

**Whether an operation allocates.** A tree allocates a node and a closure per step. A machine allocates
frames. Direct style and state machines allocate nothing for a step. For a rough sense of scale, from my own
measurements and no one else's: a state machine in Rust took every path of a choice among 1000 in 0.28 µs,
where a machine on the JVM took 13.24 µs for the same lane (`okay-rust/README.md`: three alternating rounds,
medians, 2026-10-28, measured under other load). That is two techniques in two languages, so it shows a
magnitude and proves no rule.

**Whether an answer needs the rest at all.** Most operations are answered and the program goes on: a counter
read, a configuration value, a log line. A design that captures the rest for those pays for a generality it
does not use. Every serious implementation separates a handler that answers *in place* (often called
tail-resumptive) from one that takes the rest. It is the single most valuable optimisation, and it is available
under every representation.

**What the host's optimiser can see.** A final encoding or a context parameter is a direct call the compiler
can inline. A loop over data is opaque to a JIT in a specific way: one loop serves every program, so its
branches are polluted by all of them. A state machine is straight-line code per state.

Chapter 13 has the numbers with their protocols. This axis is only the reasons.

## (i) Living with the host

**Exceptions.** A host exception thrown through an interpreter is not an operation, and no handler sees it
unless the library converts it. The opposite hazard: a library that implements leaving early *by* a host
exception can be defeated by a careless `catch` of everything in user code.

**Async runtimes.** If the platform already has a scheduler — an event loop, a fibre runtime, an executor —
the effect library must either be that runtime, or sit on it, or bridge to it. Bridging raises one question
that has no free answer: who owns cancellation where the two meet.

**Libraries.** Code that calls a database driver directly is outside every row. Every technique shares this,
and none solves it; chapter 10 is about checking it.

**Debugging.** Direct style keeps the host's stack traces, breakpoints and profiler. A tree or machine shows
its own loop in every trace. What the data-based techniques offer in return is a journal of operations, which
is a better record of *what the program did* and a worse record of *where in the source it was*.

## The axes, as one table

| axis | the options |
| --- | --- |
| a. the program | tree and interpreter · final encoding · direct style · state machine · capability passing |
| b. the rest | closure · frames as data · one-shot stack segment · copyable frame · defunctionalised value |
| c. finding the handler | by tag up a stack · by evidence · by lexical capability |
| d. the types | rows · union or intersection · constraints · context parameters · capture sets · nothing |
| e. resumptions | one · many (and then: state, linear resources, sharing) |
| f. leaving early | unwinding · finalizers as data · destructors — and the kept rest that nobody calls |
| g. storing the rest | data, with versioning · or explicit state · or a journal with replay |
| h. cost | allocation per step · answer in place or capture · what the optimiser sees |
| i. the host | exceptions · async runtime · libraries · stack traces |

The axes are not fully independent, and the dependencies are most of what there is to know:

- direct style on the runtime (a) *fixes* the rest (b) to what the runtime gives, and so usually fixes (e) at
  one and (g) at no;
- a tree with closures (a, b) gives many resumptions (e) and no storing (g);
- a state machine (a) gives (e) and (g) both, and takes away writing the program yourself unless a compiler or
  macro does the transform;
- capability passing (a) decides (c) and (d) well and has nothing to say on (b), (e), (f), (g);
- typed rows (d) are easiest with static evidence (c) and hardest in a language with neither higher-kinded
  types nor type-level computation.

The five chapters that follow are those five rows of (a), each with its consequences on the other eight.
