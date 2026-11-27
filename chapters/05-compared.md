# 5 · The map: everything else, on the two halves

Effects did not appear in an empty field. Every problem of chapter 1 has well-known answers, some of them
excellent, and most engineers reading this already use several. So the useful question is not "are effects good"
but **"what does each approach see, what does it cost, and what can it not do"**.

This chapter takes the payment of chapter 2 through every neighbouring mechanism I know: sixteen sections. For
each I say first what it does well — people chose it for reasons — then where it ends, and then, under the
words **on the two halves**, why it ends there. Chapter 2b said that algebraic effects are operations as data
together with the rest of the program as a delimited continuation. Every mechanism below is that with one half
restricted, missing, fused into something else or hidden, and its limits are not accidents of its design: they
follow from which half, and how. At the close are two tables, one by halves and one by what an engineer wants.

**What this chapter is, among four that touch the same ground.** Chapter 2b *states* the idea and gives the map
in four lines. This chapter *is* the map: each mechanism as it is ordinarily used, placed on the halves.
Chapters 3a and 3h look the other way: given that you want the idea, the axes along which it can be built and
how to choose a construction. Chapter 15 is the same ground in time: who found which half, in what order, and
what each step gave up. Where one of those already says a thing, this chapter points to it and does not say it
again.

A word more on how this chapter stands to Part III, because five of the approaches appeared there too: free
monads, tagless final, capabilities, generators and `async`, and languages with effects built in. Part III
asked what each is *as a way to build* handlers. This chapter asks what each gives *as it is ordinarily used*,
without a handler library on top. The two answers differ, and the difference is the point: a generator is one
fixed effect as the language ships it (5.9) and a complete substrate for one-shot handlers once forty lines
drive it (chapter 3d). So "effects and handlers" in the last row of the table below is the idea of chapter 2,
by whichever construction; it is not one library, one language or one technique, and no snippet in this
chapter is in the notation of any implementation of mine. The snippets are pseudocode, or Scala where a type
signature is the point, and say which.

Throughout, hold the five things we want from the opening of the book:

1. the type says what the code reaches for;
2. the reach can be answered differently without changing the code;
3. every reach passes a point where it can be seen;
4. a reach can be refused, and then it does not happen;
5. the program can be stopped at a reach and continued later, or more than once.

## 5.0 The ruler

The five requirements are what an engineer wants. The two halves are why a mechanism can or cannot give them.
Requirements 1 to 4 come from the first half: when what a program may ask is a signature of operations that
exist as values, the type can list them (1), another handler can answer them (2), every one of them passes a
point that sees it (3), and one that is not answered has not happened (4). Requirement 5 is the second half:
it needs the rest of the program in somebody's hand.

So each section ends by asking seven things of its mechanism, and section 5.12 collects the answers:

- **Are the operations data?** Is a reach a value that can be looked at before anything is done about it?
- **Is the rest available?** Does anything outside the program hold what the program will do next?
- **Is it delimited?** Does that rest end at a known boundary, so that calling it returns?
- **How many resumptions?** None, one, or as many as wanted.
- **Is what may be asked typed?** Does a signature say what the code may reach for?
- **Can a run be recorded and replayed?** That needs the questions and the answers as values.
- **Can a question, or the rest, be stored or sent?** That needs them to be data all the way down.

## 5.1 Plain code and exceptions

```
receipt pay(from, to, amount) {
  have = db.balance(from)
  if (have < amount) throw new Refused(...)
  db.move(from, to, amount)
  return "moved ... at " + clock.now()
}
```

**What it does well.** It is the shortest thing that works, everyone can read it, and the tooling — debuggers,
profilers, stack traces — was built for exactly this shape. For a script, a prototype, or code with one
implementation for ever, it is the right choice, and chapter 14 says so again.

**Where it ends.** `db` and `clock` are whatever is in scope. The signature says nothing (1 fails). To answer
differently you must change what `db` refers to — a global, a field (2 by surgery). Nothing sees the calls unless
each is wrapped by hand (3 fails). A call, once made, is made (4 fails). The only "stop" the language offers is
the exception, which unwinds and cannot come back (5 fails, except for the one case of leaving).

The exception deserves a second look, because it is an effect that the language happens to have built in:
`throw` asks a question nobody will answer, and `catch` is its handler. It is the proof that languages already
accept the idea — for exactly one effect, with exactly one behaviour (do not continue), hard-wired.

**On the two halves.** An exception is the continuation half at one fixed setting. The thrown object is data,
and `try` is a real delimiter: the rest of the program between the `throw` and the `catch` is exactly what is
given up. But it is only ever given up. By the time a `catch` clause runs, that rest has been unwound and is
gone, so the one decision a handler could take about it — whether to continue it — was taken by the language.
Everything else in section 2b.2's table is out of reach for that reason and no other. Where a language checks
its exceptions, as Java does with `throws`, it even has a row — for this one effect. And section 15.3 tells of
the systems that did not unwind first: Lisp's conditions and Smalltalk's `resume:` kept the rest, and were
handlers in all but name.

## 5.2 Interfaces and dependency injection

```
class Payments(accounts: Accounts, clock: Clock) {
  def pay(from, to, amount): Receipt = ...
}
```

**What it does well.** This is the mainstream answer and a good one. The dependencies are named (a large part of
1). Any implementation of `Accounts` can be passed (2). It needs no special language features and every engineer
knows it. A container wires the graph.

**Where it ends.** Four places.

*It lists what was given, not what is done.* `pay` may still call `System.currentTimeMillis()` directly, read
an environment variable, start a thread. The constructor is a list of some reaches, with no claim to be all of
them.

*The call is a call.* `accounts.balance(from)` runs the implementation and returns. Something can wrap the
implementation — a decorator, a proxy — and see the call (3, for what you wrapped). It can decline to call
through (half of 4: it must still return *something* of the right type, or throw). It cannot hold the rest of
`pay`, so it cannot retry *the caller's* subsequent steps, cannot suspend the caller until tomorrow, cannot run
the caller down two alternatives (5 fails).

*The wiring is a second program.* The object graph — who gets which implementation, in what scope, with what
lifetime — is written in a container's configuration or annotations, resolved at start-up, and fails at
start-up or later. With effects the equivalent is the one line `run(program under b under a)`, which is
ordinary code, checked by the compiler, and visibly the place where the decisions are. Handlers nest around a
program instead of holding references to each other, so there is no graph that could be circular; and state that
several parts share is an effect with one handler that owns it, where a container leaves a singleton's state to
whoever reaches it.

*It decides who provides, and is silent about how the call is run.* Once the collaborator is in hand, calling it
is an ordinary method call. So control of execution — a transaction around this method, a retry, a cache, running
it on another thread — had to be added from outside the language. The container wraps the object in a proxy, and
an annotation tells the proxy what to do around the call. This works, and it is why a transaction in a mainstream
service is one word above a method.

It also has a failure that most engineers who have used it have explained to somebody at least once. A method
calls another method of the same object. The call goes through `this`, not through the proxy, and the annotation
on the second method does nothing: no transaction is opened, and nothing says so. The same answer explains why
the annotation is ignored on a private method. The proxy stands around the object, and the language has roads
that do not pass it. Containers offer a second mode that weaves the advice into the class itself and closes this
road; it needs weaving at build or load time, and the proxy remains the default.

With an operation as a value there is no proxy to walk past. A question asked from a private helper, from a
lambda, from a recursive call ten levels down reaches the handler in force around the program, because asking is
the only thing the code can do. Chapter 7 writes the transaction that way and runs it.

**The relation, exactly.** An interface is an effect's vocabulary. A method call on it is an operation *that has
already been answered by the time you can look at it*. Dependency injection is effects with the continuation
thrown away. That is why moving from one to the other is easy — the interface you have is the effect you need —
and why the move gains precisely the things that need the continuation.

Said as history: dependency injection was an effect system that stopped halfway. It took *who answers* out of
the code and gave it to something outside. It left *what happens around the answer* inside, and then reached
for proxies and aspects (section 5.10) to get at it.

**On the two halves.** An interface has the *vocabulary* of the first half and neither half as a value. The
methods are a signature. But a call is not a value: nothing can hold `accounts.balance(from)` and look at it
before it runs, so there is nothing to record, compare or send unless a wrapper turns each call into a record by
hand. And the rest of the caller is the host's stack, which nobody is given. One indirection — which
implementation the name refers to — is all that was made exchangeable. Every limit listed above is one of those
two absences: "it lists what was given" and "the wiring is a second program" are the missing data; "the call is
a call" is the missing rest.

## 5.2a Ports and adapters

Section 5.2 was about a mechanism. This one is about the architecture most often built on it, under the names
*ports and adapters* and *hexagonal architecture*, and with small differences *onion* and *clean*.

```
-- the application, which knows nothing outside itself
port Accounts { balance(account): Number ; move(from, to, amount) }      -- an interface it owns
class Payments(accounts: Accounts, clock: Clock) { def pay(from, to, amount): Receipt = ... }

-- the outside, which knows the application
class SqlAccounts(db) implements Accounts { ... }                         -- an adapter
main = new Payments(new SqlAccounts(db), new SystemClock)                 -- the composition root
```

The snippet is pseudocode. The application declares what it needs as interfaces of its own, the *ports*.
Everything that touches a database, a network or a clock is an *adapter* that implements a port, and the
dependencies point inward: an adapter knows the application, the application knows no adapter.

**What it does well.** It is the best-known answer to the problem of this book, and a team that follows it has
most of what chapter 7 asks for. The business rules can be read without the database. A test gives the
application adapters in memory. A second world — another database, a queue in place of a call — is a second
adapter. It is drawn on a whiteboard in a minute and needs nothing from the language. And it names the right
thing: that the code which decides and the code which reaches are two kinds of code. Chapter 7's sentence, "the
data model is the effects and every implementation is a handler", is this architecture with two words changed:
a port is an effect's vocabulary, an adapter is a handler, an application service is a program, and the
composition root is the one line.

**Where it ends.** It is section 5.2's mechanism used with discipline, so it ends where that does, and the
discipline adds two things of its own.

*The boundary is a rule about source files.* That the application calls no adapter, reads no clock and opens
no socket is checked by a reviewer, or by a tool that reads the import statements. Requirement 1 is met for
what passes a port and assumed for the rest.

*An adapter answers, and that is all it does.* It returns a value or throws. What happens *after* the answer
is in the application, so every policy about what happens next is written there. Take a use case that checks
five things and may refuse at each. Stopping at the first refusal, collecting all five to show beside a form,
and turning a refusal into "no result" are three policies. With a port they are three shapes of the use case:
an exception thrown through it, a list of errors carried through it, an optional returned at every step. With
the refusal as an operation they are three handlers of one program, because the handler has the rest: it drops
it, goes on with it, or drops it and gives a value of its own. Section 6.2 runs the first, a handler that leaves
from deep inside; chapter 8 has the failure and the state the other two are made of.

The other limits are section 5.2's, seen from the architecture. *What stands between a port and its adapter* —
a transaction, a retry, a cache, an audit record — is a decorator around the adapter or a proxy the container
makes, and has the proxy's roads around it. *Many small calls* stay many: a use case that asks a price for each
of forty items makes forty calls unless the port is given a second method and the use case is rewritten to call
it, where a handler that is given the forty questions before it answers any can ask once (chapter 9, the store).
*A use case that waits* — for a person, for tomorrow — has to give its thread back, and the rest of it becomes
a state machine written by hand (chapter 11).

**The relation, exactly.** Nothing in the design has to change, and that is the practical point. A team with
ports has already decided what its effects are. The move is to make the call a value and give the adapter the
rest: a port's methods become operations, the adapter becomes a handler, and the use case asks instead of
calling. What that buys is exactly the list above — the policies that were shapes of the use case, the
decorators, the batching, the waiting — and nothing else. A team that feels none of those has no reason to
move, and chapter 14 says so at length.

**In my library.** okay-tiny (github.com/okay-effects/okay-tiny, Java 21, one file of 290 lines of code with
no dependency; chapter 3j is about it) was written to put this to a code base built on ports. Its tests are
the claims of this section, run: one program under a handler that stops at the first refusal, one that
collects them, and one that gives an `Optional` (`ScenariosTest`: `theSameProgramTheFirstRefusalStopsIt`,
`theSameProgramAllRefusalsAreCollectedAndItGoesOn`, `aPartIsLeftWithAValueOfTheHandlers`); a handler that sees
every operation of an effect and passes it on, which is the decorator (`everyOperationOfAnEffectIsSeen`); one
operation that holds many items, answered with one query (`oneOperationThatHoldsManyItems`); a use case of
seven ports with its seven adapters as one function each and no container (`BookingTest`); and twenty-four
programs the compiler refuses, among them an effect left without a handler (`NegativeTest`). Since this
paragraph was first written it has also run in a small Spring Boot service with beans, a database, Spring's
transactions and a controller (`spring-sample`, six tests), with no change to the kernel; it has not been tried
on a large code base or by a team, and its `DESIGN.md` says so.

**On the two halves.** As section 5.2: a vocabulary, and neither half as a value. Ports and adapters is a rule
about which way dependencies point, not a mechanism, so it has no row of its own in the tables of section
5.12; its row is "interfaces, injection". What the rule gets right is the first half's *shape* — it finds the
signature. What it cannot give is what needs the operation as data, or the rest in the adapter's hand.

## 5.2b Command objects, and interpreters of plain data

The step that injection did not take, many designs do take: make the request a value.

```
commands = [ Debit("ann", 30), Credit("bob", 30), Stamp ]
execute(commands)
```

A command object with an `execute` method; a list of instructions handed to an interpreter; a query built as a
tree and given to a planner; an action dispatched to a store; an event appended to a log and folded into a
state. They are one design, and it is the first half of this book's idea on its own.

**What it does well.** Nearly everything chapter 10 will want. A command can be logged before it runs, refused,
queued, retried, batched, sent to another machine, replayed tomorrow. An interpreter can be exchanged: the same
commands against a real ledger or a test one. A query that is a tree can be rewritten before it is run, which is
what every database's planner does. Event sourcing is this design carried through a whole system, and its audit
trail is complete by construction. Requirements 2, 3 and 4 are met in full, for the same reason they are met by
handlers: the reach is data.

**Where it ends.** A command has no *rest*. The list above was written before any of it ran, so `Credit` cannot
depend on what `Debit` returned. Look at what `pay` needs: read the balance, *then decide* whether to move. That
is not a list. The usual ways out are three, and each is a familiar piece of machinery:

- the interpreter returns a result and ordinary code decides the next command — then the program is ordinary
  code again, the commands are calls with a record attached, and requirement 5 is where section 5.2 left it;
- the decision becomes a command too (`If`, `While`, variables), and the list grows into a small programming
  language with its own bugs;
- the result comes back as a new message and a hand-written state machine picks up the thread. The Elm
  architecture is the clean form: an update function returns a new model and a command, the runtime performs
  the command, and its result arrives later as another message to the same function. The program's
  continuation is the model and the cases of that function, written out by hand — chapter 1b's third program,
  as an architecture.

**On the two halves.** Data, and no continuation. Add to each command the one thing it lacks — a function from
its answer to the next command — and the list has become the tree of section 2b.1: `Debit("ann", 30) ; ok =>
…`. That single step is the whole distance from the command pattern to a free monad (section 5.5), and it is
why teams that already log their commands find effects easy to adopt: they have built half.

## 5.3 Mocks

**What they do well.** They let a test control a reach in code that was not designed for it.

**Where they end.** A mock replaces an implementation by *expectations*: this call, with these arguments, returns
that. The test now encodes the order and number of internal calls, so refactoring the code breaks tests that
assert nothing about behaviour. The mock is a second, partial implementation of the dependency, maintained by
hand, checked against nothing.

With effects there is no mocking, because there is nothing to replace. A test supplies a handler — usually a
real, small, in-memory implementation of the effect, written once and used by every test. It is an ordinary
implementation of the same vocabulary as production. `InMemory` in chapter 2 is six lines of the notation,
a short definition in each rendering under `code/`, and is the test double for every program that ever asks
accounts.

**On the two halves.** A mock is not a point on the map so much as a symptom of one: it exists because the
reach was not data, so a test has to reach *into* the code to replace it. Where the first half is present there
is nothing to reach into.

## 5.4 Monads, and their transformers

This is the tradition effects come from, so the comparison is close and needs care.

A **monad** is a type of computations with a way to put one after another. `Option` is computations that may
have no result; `Either[E, _]` those that may fail with an `E`; `State[S, _]` those that read and write an `S`;
`List` those with several results. Each is a small, exact model of one kind of effect, and writing code "in a
monad" makes that effect visible in the type. That is requirement 1, and functional programmers have had it
since the early 1990s.

**What it does well.** One effect, precisely, with laws. Nothing is hidden.

**Where it ends.** In the plural. Real code has several effects at once — it may fail, *and* reads
configuration, *and* keeps state, *and* logs. Monads do not combine by themselves: there is no general way to
make one monad out of two. The standard remedy is the **monad transformer** — `EitherT`, `StateT`, `ReaderT`,
`WriterT` — each of which wraps another monad and adds one effect.

So a program with four effects has a type like

```
EitherT[StateT[ReaderT[IO, Config, _], Cart, _], Refusal, Receipt]
```

and three consequences follow, all familiar to anyone who has worked this way.

- The effects are in a **fixed order**, chosen in the type, and the order changes the meaning (does state
  survive a failure or not?). A function written for one stack does not fit another.
- Using an inner effect from an outer layer requires **lifting** — explicit or by a family of type classes
  written for every pair of transformers, a number that grows with the square of the effects.
- Each layer is a real wrapper at run time, so cost grows with the depth of the stack.

Effects with rows are the answer to exactly this. `Receipt ! (Failure + State + Reader + Log)` is a *set* of
effects, in no order. A function asking only `State` is usable where the row has four effects, with no lifting.
The order is still a real choice — and it is made **where the handlers are applied**, at the edge, not in the
type of every function. The same program can be run "state survives failure" and "state is rolled back on
failure" by swapping two handlers. With transformers that is two different programs.

**On the two halves.** A monad has both halves, fused, and that is the most exact thing to be said about it.
Its second operation, the one that puts a computation before a function, *is* a decision about the rest of the
program: `Option`'s runs the rest or drops it, `List`'s runs it once for each element, `State`'s runs it once
with a value threaded through. Those are section 2b.2's rows — not at all, once, many times — each built into a
type. So a monad is one handler, applied in advance and named by a type; and a stack of transformers is a
nesting of handlers whose order was fixed when the type was written. What chapter 2 separated, the question
and the decision about the rest, the monad holds as one thing. The operations are there (`get`, `put`, "fail
with this") but as functions of that monad and not as values, so there is nothing for a second meaning to be
given to. Fixed order, lifting and a wrapper per layer are what fusing costs. Filinski's theorem (section 15.2)
is the same fact read the other way: because each monad is a way of using the delimited rest, a language with
that rest at the bottom needs no monad built in.

## 5.5 Free monads

**What they do well.** A free monad is "a program as data, interpreted later" — and that is half of this book's
idea, discovered in the same tradition. The operations are a data type; the program is a tree; an interpreter
gives it meaning. Requirements 2 and 3 are met in full: the interpreter sees every node.

**Where they end.** In two historical troubles, both since solved.

*Combination.* Several vocabularies had to be glued into one sum type by hand ("coproducts" and injection
machinery). Rows are the solution; it took about a decade to get them comfortable.

*Speed.* The classical encoding rebuilds the tree on every bind, and a left-nested chain becomes quadratic. The
remedies are known (reflection without remorse, the "freer" encoding, a machine instead of a tree). The
reputation outlived the problem: it is still common to be told that programs-as-data must be slow.

That claim can be tested, and I tested it. A mutual recursion of a million levels, each approach under its own
protocol in a JMH benchmark: the machine in my library took 2.26 ns and 40 bytes per level; ZIO took 4.17 ns and
56 bytes. The rounds are marked as partial in the document that reports them, and it says what was not measured
(chapter 13 gives the protocol and its limits). I do not offer this as "faster than ZIO" in general — one task
proves one task. I offer it against the word *impossible*.

Effects and handlers, as this book uses the term, are the free monad's idea with the two troubles removed and
one thing added: the handler is given the *rest of the program*, which a plain interpreter that folds a tree
does not expose as a value you can keep.

**On the two halves.** The free monad is the first half, complete: section 2b.1's data type is it. The second
half is in the tree too — it is each node's second component — and the question is only whether the interpreter
is allowed to touch it. In the classical use it is not. The interpreter is a translation of each operation
into some target monad, one operation at a time, and the rest is then sequenced by *that monad*: section 5.4's
fusing, moved to the edge. That is already a real gain, since the monad is chosen where the program is run and
not where it is written. Let the interpreter's clause receive the rest as a value instead, and it is a handler;
nothing else changes. So this row of the map is not a neighbour of the idea. It is the idea, by encoding, and
chapter 3 is forty lines of it. What it pays is what an encoding pays: an allocation for each step, a notation
in place of the language's statements, and stack traces of the interpreter (chapter 3b has the list, and what a
machine does about each).

## 5.6 Tagless final

In Haskell, from `code/haskell/Tagless.hs`, which runs (chapter 3c reads the file closely):

```haskell
pay :: (Bank m, Clock m, Failure m) => String -> String -> Integer -> m String
```

The listing is in a real language because its point is a feature of one: `m` is a type *constructor* that the
program abstracts over, and a notation that belongs to no language cannot show what that asks of a compiler.
Scala spells the same signature `def pay[F[_]: Monad: Accounts: Clock](…): F[Receipt]` (illustrative, not run
here), and the text below says `F` for the abstract monad, as that community does.

**What it does well.** The effects are type-class constraints on an abstract `F`. The signature lists them (1),
different instances can be supplied (2), and — its real advantage — there is no interpretive overhead: the
program is compiled directly against whichever `F` is chosen. For libraries that must be fast and generic, it is
a fine technique.

**Where it ends.** The program is not data. It is a polymorphic function, and once `F` is chosen it is ordinary
code in `F`. Nothing holds the tree, so nothing outside can walk it: to see every `Accounts` call you wrap the
`Accounts` instance (back to decorators). The continuation is whatever `F` provides; if `F` is a plain `IO`,
there is no multi-shot, no "keep the rest in a database". (If `F` is the list monad there is: the rendering
prints the line for a rest resumed twice.) And `F` must be one concrete monad at the end, so the transformer
question returns at the place where `F` is built.

Tagless final gives the first two requirements at the lowest cost. If those are all you need, it may be the
right choice.

**On the two halves.** Tagless final is the fold without the tree. Section 2b.1 said a handler is a fold over
the data type of programs; a program in this style is a function that takes the fold's cases — the instance —
and applies them directly. Nothing in between is ever built. That is where the speed comes from, and it is
exactly what is given up: with no tree there is no value to inspect before running, to store or to send; and
a question is a method being called, not a value passing a point. The second half is whatever `F` is:
section 5.4's fused rest, chosen once for the whole program.

## 5.7 Effect runtimes: ZIO, Cats Effect

These are the most widely used serious answers on the JVM, and they are good software.

**What they do well.** A value of type `ZIO[R, E, A]` or `IO[A]` is a description of a computation, run later by
a runtime that provides fibers, structured concurrency, cancellation, resource safety, scheduling, and a large
ecosystem. `R` in ZIO names the services required (a large part of 1); layers supply them (2). For concurrent
servers this is mature, fast, and well understood.

**Where they end.** At the question of what can *see inside*.

A ZIO or Cats Effect program is a plan built from the runtime's own primitives — `flatMap`, `async`, `fork` —
plus calls to your services. From outside, your `accounts.balance(from)` is an opaque step: a function that will
produce another plan. No single function can open someone else's `flatMap` and ask "what operation of the
business is this?". What the runtime offers instead is hooks at *its* level: supervisors see fibers, loggers see
log calls, aspects wrap what they are applied to. They are good tools, and they cover what the library exposes.

A handler covers every operation of the effect, because the operations are data and there is no other way for
the program to do anything. That is a difference of kind, not of degree, and it is the subject of Part VI.

There is a second difference, easiest to put as a question: can the meaning of an operation be changed from
outside? A layer swaps the *service* — another implementation of the same methods, each of which still runs and
returns one value of the declared type to its caller. That is requirement 2, and it is real. A handler gives the
*operation* its meaning, and the meaning includes what becomes of the caller. `Refusals` in chapter 2 turned the
whole computation into `paid | refused`; `AllChoices` ran the rest once for each option; neither is an implementation
of something the program was given. Handlers of different effects also know nothing of each other: the program
fixes no order among them, and one is exchanged without touching the others. Swapping a service is a good thing
to have. It is not this.

The same difference shows when two runtimes meet in one system: one part written against ZIO, another against
Cats Effect. Interop libraries exist and work at a boundary. The questions are how many boundaries a codebase
can carry before the adapters are the program, and who owns cancellation and errors where the two meet. Under
handlers a runtime is one more effect — "run this, and give me its result" — answered at the edge like any
other. I have written such bridges for my own libraries. I have not compared them with the interop libraries on
a real codebase, so this paragraph is argued.

The usual reply is that the price of this visibility is a catastrophic loss of speed. The measurement in 5.5 is
my answer: on the task I measured, the price was not there.

Fairness requires the other direction too. These runtimes have years of production hardening, a large ecosystem
of integrations, and many engineers who know them. An implementation that is right in principle has none of
that by virtue of being right.

**On the two halves.** An effect runtime has both halves, for a signature that is its own. Its programs are
data: a tree of the runtime's primitives, walked by an interpreter loop (chapter 3b). It holds the rest of each
program — that is what a fibre is — delimited by the fibre and resumed once, when an asynchronous result
arrives. Seen so, it is an implementation of this book's idea with the vocabulary fixed to concurrency, errors
and resources, with years of production use behind it. What it does not do is extend that
treatment to *your* operations. `accounts.balance(from)` is not a node of the tree; it is a function that
returns one. So the questions of the business are neither data nor a point where the rest is held, and what
section 5.2 said of an injected service applies to it unchanged. Effect, in TypeScript, has the same shape as
far as I can describe it: a program is a value with its result, error and requirements in the type, run by a
fibre runtime (chapter 3g). Kyo's computation type lists pending effects; I am not sure enough of how it treats
a user's operations to place it here, and the backlog says so.

## 5.8 Capabilities and direct style

A newer line: code is written in plain direct style (no `for`, no `flatMap`), and the right to perform an effect
is a **capability** — a value that must be in scope, passed implicitly. The listing is Scala, because the
point is a feature of that language, context parameters; it is the signature in `code/scala/capabilities.scala`,
which runs (Kotlin's form, over `suspend`, is in `code/kotlin/bank.kt`):

```scala
def pay(from: String, to: String, amount: Long)(using bank: Bank, clock: Clock, failure: Failure): String
```

**What it does well.** It reads like ordinary code and still lists its effects in the signature. It costs
nothing at run time. With a checker that tracks where capabilities may flow, it can also promise that a
capability does not escape. A row of effects and a set of capabilities say the same thing from two sides — what
a computation asks, what it holds — and chapter 10 returns to what such a checker can take over from a scan of
the compiled code.

**Where it ends.** A capability is a permission to *call*. The call is again a call: it runs and returns. To get
the continuation you need a runtime underneath that can capture the stack — threads, coroutines, or a compiler
transformation — and then direct style and effect handlers are the same thing with different surface syntax.
Several libraries (my own among them) offer both faces over one machine. Where there is no such runtime, direct
style gives requirements 1 and 2 and not 5.

**On the two halves.** Capability passing is neither half. It is a third thing that the idea also needs, done
well: *how a handler is found* (chapter 3a, axis c) and *what the types say* (axis d). A capability answers
"who answers, and may this code ask at all" — lexically, checked, at no cost. It says nothing about whether the
question is a value or whether anyone holds the rest; left alone, the question is a call and the rest is the
host's stack, as in section 5.2. That is why capabilities combine with every other row of the map instead of
competing with them, and why "capabilities or handlers" is not a real choice (chapter 3c).

## 5.9 async/await, generators, coroutines, threads

**What they do well.** They are continuations that the mainstream accepted. `await` stops a function and resumes
it when a result arrives. `yield` stops a producer and resumes it when the consumer wants more. Languages
compile these into state machines, efficiently.

A thread belongs in the same section, and so do a virtual thread and a goroutine. A thread blocked in a call is
a program stopped at a point, with the rest of it kept — by the scheduler — until there is something to continue
it with. What it does well is what made it the default for decades: the code needs no marking and no
rewriting, every library works inside it, and the debugger shows a real stack. Virtual threads and goroutines
keep all of that and make a stopped thread cheap enough to have very many of.

**Where they end.** Each is *one* effect with *one* fixed handler, built into the language. `await` can be
resumed once, by the scheduler. `yield` goes to the nearest enclosing generator. You cannot define a new one, and
you cannot change who answers. They also colour functions: an `async` function can only be awaited from another
`async` function, and the split runs through the whole codebase.

With handlers, "async" is an effect whose handler keeps the rest and resumes it when the answer arrives;
"generator" is an effect whose handler passes each value to a consumer. They are library code. A third one is
your code. (Sections 8.9 and 8.10 write both: a scheduler as a handler with a queue of rests, and a stream.)

The converse is also true, and it is how most languages get handlers at all: a generator, a coroutine, a
cheap thread or an `async` function, driven by a library instead of by the platform's scheduler, is a sound
substrate for every handler that resumes once. Six of the renderings under `code/` are built that way
(chapters 3d and 3e). What
this section describes is the feature as shipped; what those chapters describe is what it can carry.

**On the two halves.** All of these are the second half, restricted, with the first half absent until someone
adds it.

*The rest is real, and it is delimited.* A stopped generator is the rest of the producer up to the generator's
own boundary. A suspended `async` function is the rest of a task. A parked thread is the rest of that thread.
None of them is the rest of the whole program, which is why they compose where `call/cc` (section 5.9c) does
not.

*It is a piece of stack, or an object the compiler made, so it resumes once.* Resuming moves it on; there is
nothing left to resume a second time, and the platforms offer no copy (chapter 3d, which also prices the three
ways of buying a second resumption back). Storing it is out for the same reason.

*Who holds it is fixed.* For `await` and for a blocked thread, the scheduler holds the rest, and user code is
not offered it. A generator's rest is held by whoever drives the generator, which is why generators, of all
these, are the easiest to build handlers on.

*There are no operations.* What a generator yields, what a task awaits, what a thread blocks on is whatever the
code passed: a value of any shape, or nothing but the call itself. So there is nothing to list in a type —
"this function is `async`" is one bit, the colour — and nothing to intercept. Make the yielded thing a value of
a signature, and a generator has both halves; `code/typescript/bank.ts` and `code/python/bank.py` are forty
lines that do it, and `code/go/bank.go` does the same by sending the question down a channel.

## 5.9b Callbacks, and continuation-passing style

```
balance(from, have =>
  if have < amount then refused(…)
  else move(from, to, amount, _ =>
    now(at => receipt("moved … at {at}"))))
```

Instead of returning its result, each function is handed a function to call with it. That function is the rest
of the program, written out by the programmer.

**What it does well.** It needs nothing but functions, so it is available in every language and was, for
years, how a single-threaded server did input and output without blocking. The rest is an ordinary value: it
can be kept and called later, which is all of asynchronous programming; it can be called twice; it can be not
called. Everything in section 2b.2's table is reachable. Some compilers use this form internally for exactly
that reason, and a *promise* is a callback with two guards added: called at most once, and chainable.

**Where it ends.** In who writes it. The rest is available because the programmer turned the program inside out
to produce it: every step that follows a call is nested inside a function, loops become recursion, and a
failure must be passed along by hand, because the language's own `try` covers one callback and not the chain.
A function either takes a callback or it does not, and the two kinds do not mix: this is the first *colour*,
and `async`/`await` was invented to have a compiler do the inversion. And nothing delimits the rest except
convention. The callback is whatever function was passed; where it ends, and whether calling it returns
anything useful, is up to each author.

**On the two halves.** The second half, by hand, and none of the first. The question is the function that was
called — `balance` — which is code and not a value, so whoever provides `balance` can do what it likes with the
rest it was handed, and nothing outside can see that a balance was asked for. Put a tree where the functions
are, so that `balance(from, k)` *builds* the node `Balance(from) ; k`, and it is chapter 3. A tree of that kind
is sometimes built this way on purpose, for speed (chapter 3a, axis a: "a final encoding … over its own
continuation").

## 5.9c `call/cc`

Some languages offer the rest of the program without the inversion. `call/cc` ("call with current
continuation", in Scheme and its relatives) calls a function and hands it the continuation of that call, as a
first-class function the program may store, call later, or call more than once.

**What it does well.** It was the first general control operator, and it is a real one: exceptions,
generators, coroutines and backtracking can each be written with it as a library, and were (section 15.2).

**Where it ends.** In one missing thing, and the whole of this book's subject can be read as the repair of it.
The continuation `call/cc` captures is **undelimited**: it is the rest of the *entire* program — the caller, the
caller's caller, the loop that is serving requests, the exit of the process. Four consequences.

- *Calling it does not return.* The rest of the whole program has no "after". So a captured continuation is a
  jump, not a function: one cannot call it and then look at what came back, and chapter 6's handler that watches
  how the caller ends cannot be written directly.
- *It captures code it has never met.* A library that takes a continuation takes hold of its caller's caller
  too. Whatever that code holds open — a file, a lock, a transaction — is now inside a value that may be invoked
  again later, or never. Scheme has a construct, `dynamic-wind`, for guarding such regions for this reason.
- *It has no useful type.* The answer type of the rest of the whole program is "the program ends".
- *There is nowhere for a handler to stand.* A handler is a boundary: the stretch of program it governs. With
  no boundary there is no "this effect is handled here", and so no row.

**On the two halves.** The second half with its delimiter removed, and none of the first. The delimiter turns
out to be the important part. A continuation up to a boundary is a function, can be typed, and can be reasoned
about by someone who has read only what is inside the boundary; chapter 15's table of what each step in the
history gave up begins here. The trouble with `call/cc` is not that it captures too little. It captures too
much, and the part it should have left out is the part that makes a handler possible.

## 5.9d `shift` and `reset`, and prompts

The repair is a pair of operators. `reset` marks a boundary. `shift` captures the rest of the program *up to
the nearest boundary* and hands it to a function. With named boundaries the pair is usually called a *prompt*
and a capture up to it (section 15.2 has who introduced which).

**What it does well.** This is the second half, complete. The captured rest is a function that returns; it can
be called zero, one or many times; it has a type; and two uses of it compose. Filinski's theorem says how much
that is: every effect that can be written as a monad can be written with these two operators. Where a language
offers them — Racket does, natively, and recent versions of GHC expose primitives for them — a library author
has everything chapter 6 needs.

**Where it ends.** In what the two operators do not say.

- *No signature.* `shift` is one operator for every purpose. A function that uses it has, at best, a type that
  mentions answer types; it does not say "this asks for a balance". So nothing can be listed, and the row of
  section 2.6 has nothing to be a row *of*.
- *The decision is at the wrong end.* The function given to `shift` — the code that decides what happens to the
  rest — is written at the point of capture, inside the program. A handler is the same decision made at the
  boundary, by someone else, and exchangeable. With `shift` the program does not ask; it acts on its own rest.
- *One anonymous boundary.* The nearest `reset` wins. Two libraries that each use the pair for their own
  purpose capture up to each other's boundaries unless every boundary is given a name, which is what prompts
  are for.
- *Nothing is data.* There is no value that says what was wanted, so nothing to journal, replay or send.

**On the two halves.** The continuation without the signature. Give each boundary a set of named operations
it answers, and let the code inside *name an operation* instead of capturing — the capture then happens at the
boundary that answers that name, with clauses written there — and the pair has become a handler. Chapter 15
says it in one line: a handler is a delimiter that knows what it catches; a capture is a handler with no
signature. That the two can each be written with the other is the result section 2b.2 cited. As a way of
*building* handlers a prompt is a standard substrate; chapter 3b's machine, whose stack is cut into levels by
the handlers installed, is one seen from inside.

## 5.10 Aspects, middleware, interceptors

**What they do well.** They are the industry's answer to requirement 3: put a cross-cutting concern — logging,
authorisation, metering — in one place and apply it to many calls.

**Where they end.** At *which* calls. An aspect applies to the join points its language can express; a
middleware to the requests that pass its framework; an interceptor to the client it was registered on. A new
kind of call, or a library that talks to the network by itself, is outside. And each stands around one call, not
in front of the rest of the program.

The `Journal` of chapter 2 is the same idea with the gap closed: it sees every operation of its effect because
operations are the only thing the program can produce.

**On the two halves.** An aspect or a proxy is a point of interception with neither half behind it. What it
intercepts is a call, described by where it is — this method, that annotation — and not a value of a signature
the business chose; and it stands around that one call, so the rest of the caller is not its to hold (section
5.2's self-call is the proof that the calls themselves are not all its to see). Middleware written as a chain
deserves a fairer word. A middleware that is handed `next`, calls it, and then acts on what came back is
holding a delimited rest, and is doing chapter 6's "on the way back" correctly. But the rest it holds is the
rest *of the pipeline* — the other middleware and the endpoint — and not the rest of the business program that
runs at the end of it. It is the right mechanism at one fixed place.

## 5.11 Languages with effects built in

Koka, Eff, Effekt, Unison (its "abilities") and OCaml since version 5 have effect handlers in the language. They
are the cleanest expression of the idea, with syntax and — in several — inference of rows, and they are where
much of the theory was worked out. Where one of them is an option for your project, most of this book applies
directly and with less ceremony. The bank of chapter 2 runs in two of them, `code/ocaml/bank.ml` and
`code/koka/bank.kk`, and chapter 3f says how they differ from each other, which is more than one would guess.

Most teams cannot choose their language. The premise of this book, and of the libraries behind it, is that the
idea is a matter of data structures and can be had in the language you already use.

**On the two halves.** Both, by construction, and the differences among these languages are choices *within*
the halves. OCaml 5 is the instructive one. Its operations are data — constructors of one extensible type, each
naming its answer — and its handler holds a delimited rest. Its designers then made two restrictions on
purpose. The rest may be resumed once, because a rest that is a segment of stack can be moved instead of copied
and a program that holds a resource is not run twice by accident. And the types say nothing of what a function
may perform, so that existing code kept compiling. So "has handlers in the language" does not settle the two
later columns of the table below, and chapter 3f's own table shows six such languages choosing differently on
every axis. One more distinction that this row makes clear: here the *operation* is data and the *program* is
not. There is no tree to hold before running; the tree of section 2b.1 exists only as the questions the running
code performs, one at a time.

## 5.12 The two tables

**By halves.** One row for each section but 5.2a, which shares the row of 5.2, and the seven questions of
section 5.0.

| mechanism | operations are data | the rest is available | delimited | resumptions |
| --- | --- | --- | --- | --- |
| exceptions | the thrown object; one answer type, "never" | only to drop | by `try` | none |
| interfaces, injection | no: a call | no | — | one, in place |
| commands, plain interpreters | yes | no; or written by hand | — | one, in place |
| monads, transformers | no: functions of the monad | fused into the monad | by its run | fixed per monad |
| free and freer monads | yes | in the tree; given to a handler | by the handler | many |
| tagless final | no: methods | what `F` gives | what `F` gives | what `F` gives |
| effect runtimes | the runtime's own, not yours | to the runtime | by the fibre | one |
| capabilities | no: a call | no | — | one, in place |
| generators | what is yielded, if you make it so | to the driver | by the generator | one |
| `async`/`await` | no: one fixed question | to the scheduler | by the task | one |
| threads, goroutines | no, unless sent down a channel | to the scheduler | by the thread | one |
| callbacks, CPS | no: a function is called | yes, written by hand | no | any |
| `call/cc` | no | yes | no | many |
| `shift`/`reset`, prompts | no | yes | yes | many |
| aspects, proxies, middleware | no: a call at a place | of the pipeline only | by the pipeline | one |
| handlers in OCaml 5 | yes | yes | by the handler | one |
| handlers in Koka | yes | yes | by the handler | many |
| **effects and handlers** | **yes** | **yes** | **by the handler** | **none, one, many, later** |

| mechanism | what may be asked is typed | a run recorded and replayed | stored or sent |
| --- | --- | --- | --- |
| exceptions | where exceptions are checked | no | — |
| interfaces, injection | what was given | what was wrapped | no |
| commands, plain interpreters | the type of the commands | yes | the commands, yes |
| monads, transformers | yes, in a fixed order | no | no |
| free and freer monads | yes, with rows | yes | questions yes; the rest is a closure |
| tagless final | yes: constraints | what was wrapped | no |
| effect runtimes | services and errors, in some | by the runtime's hooks | no |
| capabilities | yes | what was wrapped | no |
| generators | weakly, or not | if the driver records what is yielded | no |
| `async`/`await` | the colour only | no | no |
| threads, goroutines | no | no | no |
| callbacks, CPS | no | no | no |
| `call/cc` | no | no | no |
| `shift`/`reset`, prompts | answer types, not questions | no | no |
| aspects, proxies, middleware | no | where applied | no |
| handlers in OCaml 5 | no | yes | questions yes; the rest is a fibre |
| handlers in Koka | yes: rows, inferred | yes | questions yes; the rest — |
| **effects and handlers** | **yes, where the host can** | **yes** | **questions yes; the rest where it is data** |

A dash is "does not arise", except in the Koka row, where it is a cell I have left empty rather than fill from
memory. Three readings of the pair of tables.

*The first column decides the last two.* Wherever "operations are data" says no, recording is "what was
wrapped" at best and sending is no. Control that depends on seeing the question — chapter 10 — is available
exactly in the rows that say yes.

*The second, third and fourth columns decide chapter 6.* Leaving needs a rest that can be dropped; waiting
needs one that somebody outside the program holds; "on the way back" needs it delimited, so that calling it
returns; retrying the caller and "what if" need more than one resumption.

*Only one kind of row says yes in both groups*, and it is the kind that has a handler: a boundary with a
signature. The last cell of the last row is the limit that chapter 11 is about: the rest can be stored only
where a construction makes it data. Of the sixteen renderings that is the two hand-written state machines, and
neither is written to a disk here.

**By what an engineer wants.** The same mechanisms against the five requirements this chapter opened with.

| approach | 1 type says the reach | 2 answered differently | 3 every reach seen | 4 refused | 5 stop, resume, repeat |
| --- | --- | --- | --- | --- | --- |
| plain code, exceptions | no | by surgery | no | no | leave only |
| interfaces + DI | what was given | yes | what was wrapped | by wrapper, must return | no |
| commands, interpreters | the commands | yes | yes | yes | no |
| mocks | — | in a test | in a test | in a test | no |
| monad transformers | yes, ordered | yes | by layer | yes | per monad |
| free monads | yes | yes | yes | yes | not as a value |
| tagless final | yes | yes | what was wrapped | by instance | what `F` gives |
| ZIO, Cats Effect | services, errors | yes (layers) | runtime hooks | by wrapper | fibers; once |
| capabilities, direct | yes | yes | what was wrapped | by capability | needs a runtime |
| async/await, generators | colour only | no | no | no | one fixed effect |
| callbacks, `call/cc`, `shift` | no | no | no | no | yes, by hand |
| aspects, middleware | no | no | where applicable | where applicable | no |
| **effects and handlers** | **yes (the row)** | **yes** | **yes, by construction** | **yes** | **yes** |

Three cautions about this table. It compares *mechanisms*, not ecosystems; on maturity, tooling and the number of
people who can maintain the code, several rows above the last one win easily. The last row's first cell
carries the footnote of chapter 1: what the row declares must be checked against what the code actually reaches.
And the last row is the idea, not an implementation. How much of it a given construction delivers is the table
of chapter 3h: every one delivers columns 2 to 4 for what passes through it; the type in column 1 depends on
the host language; and "repeat" in column 5 is delivered only where the rest of the program is a value that
survives being used — eight of the sixteen renderings.

## 5.13 What this chapter does not say

It does not say the other approaches are mistakes. Most of them are the same idea, arrived at from one side:
interfaces found the vocabulary, monads found the type, free monads found the program as data, async found the
continuation. Effects and handlers are what you get when you keep all four.

On the two halves the same sentence is shorter. Interfaces, commands and free monads are the road to the first
half; callbacks, `call/cc`, `shift` and `reset`, coroutines and threads are the road to the second; monads had
both, fused. The idea is the two kept separate and given to each other: a question that is a value, and a
handler that holds the rest.

It does not say how to build the idea, or which construction to choose: that is chapters 3a and 3h, and their
tables are about constructions, where these are about neighbours. And it does not say who found what; that is
chapter 15, whose table of what each step gave up is this chapter's second half read in order of time.

The next chapter is about the fifth column of the last table — the second half — because it is the one that
only the last row fills.
