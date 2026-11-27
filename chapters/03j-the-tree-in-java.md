# 3j · The same tree in Java, with the row in the type

Two passages earlier in this book said, as first written, what Java cannot do for this family. Section 3b: in
Java a `switch` "narrows the *value* … and does not learn that the method's type parameter `X` is `Long`", so a
handler needs a cast, and for the row there is "almost nothing usable in Java". Chapter 3g: "Java is the
clearest case of 'the tree is right for the interpreter and wrong for the programs'", and what is painful is
"everything about types". Both were true of what had been built when they were written, `code/java/Tree.java`
and my earlier Java libraries. This chapter is about a library written afterwards to test them, and what it
found: in Java 21, with no compiler plugin and no bytecode rewriting, the tree can say the type of every answer
and the set of effects a program may ask, the compiler refuses a program whose effects are not all handled and
a handler that leaves an operation out, and no user code has a cast. What is left of the two passages is
narrower and is stated in section 3j.11.

**Whose implementation this is.** okay-tiny (github.com/okay-effects/okay-tiny, Apache 2.0): one file,
`okay.tiny.Fx`, 290 lines of code, no dependency, no threads, no reflection; a second file, `Std`, 212 lines,
with the usual effects. It is mine, it is the smallest of my JVM kernels, and it is here because the question
it answers is about Java, not because it is better than the others. Chapter 3i grew one implementation in
Scala, where the type system does the work; this chapter shows the same tree where the type system does much
less, and what each missing feature costs in the shape of the code.

**What runs.** Nothing of this chapter is under `code/` or `run-all.sh`. Every listing is from okay-tiny at
commit `ece0f7e` (2026-11-27) and every claim names the test that asserts it. `./gradlew test` there runs 88
tests of the kernel (among them 24 programs that javac must refuse, each for the reason its first line
names), 19 of the direct-style plugin and 6 of a Spring Boot sample; all passed on 2026-11-27, on Java 21.
Figures in nanoseconds are from the library's `DESIGN.md` and were not re-run for this chapter (section 3j.9).

## 3j.1 What Java gives, and what it does not

For this family Java offers:

- **sealed interfaces and records**: an effect is a sealed interface, an operation a record of it, so the set
  of an effect's operations is closed and known to the compiler;
- **pattern `switch`** that must cover every case of a sealed type when it is an expression;
- **intersection bounds on a type variable**: `<R extends Stock & Refusal>` is a type variable that stands for
  any type having both;
- **generic methods with inference**, and lambdas.

It does not offer higher-kinded types, refinement of a type parameter inside a `case` (the GADT match of
section 3b), a notation for sequencing, or a public continuation. The design that follows is what fits in the
first list and goes around the second.

## 3j.2 The answer's type is on the operation

An effect and its operations:

```java
sealed interface Stock {
  record Reserve(String sku, int qty) implements Stock, Op<Boolean, Stock> {}
}
sealed interface Refusal {
  record Invalid(String field, String why) implements Refusal, Op<Void, Refusal> {}
}
```

`Op<A, F>` says two things of a record: the type of its answer, `A`, and the effect it belongs to, `F`. The
natural place to say the effect once is on the interface, `sealed interface Stock extends Op<Boolean, Stock>`.
That was tried and is refused as soon as two effects meet in one row: `<R extends Stock & Refusal>` would make
`R` a subtype of `Op<Boolean, Stock>` and of `Op<Void, Refusal>`, and javac says *Op cannot be inherited with
different arguments*. The other ways to name the effect once were tried (`DESIGN.md` §2): three fail for the
same reason, a generic supertype that two effects of one row give different arguments, and the fourth, a family
indexed by the answer alone, compiles but needs the family's class at every `perform` and still does not refine
the answer in a `case`. So the effect is named twice on each record, and that repetition is the first cost of
Java in this design.

Then the answer. Section 3b's problem is that inside `case Stock.Reserve r ->` the compiler knows `r` is a
`Reserve` and does not know that the answer the rest expects is `Boolean`. okay-tiny never asks it to learn
that inside the `case`. The clause is made by a function that reads the answer's type off the operation:

```java
static <B, R> Handler<Stock, B, B, R> stock(Map<String, Integer> left) {
  return handling(Stock.class, op -> switch (op) {
    case Stock.Reserve r -> answer(r, left.getOrDefault(r.sku(), 0) >= r.qty());
  });
}
```

`answer(Op<X, F> op, X value)` and its general form `on(Op<X, F> op, Clause<X, S, B, R> clause)` take the
operation as a witness of its own answer type: `X` is inferred from `r`, and the value, or the `k` the clause
is given, is checked against it: a `String` given as the answer of an operation whose answer is a `Long`
does not compile (`WrongAnswer`: *String cannot be converted to Long*). Section 3b said "the designs I know for avoiding
it use the operation as a witness of its own answer type"; this is that design, and it removes the cast from
every handler.

What the arms of the `switch` give is a `Case<F, S, B, R>`, a type in which `X` does not appear, so that arms of
different answer types are of one type. `Case` is sealed, over four private records that do have `X` (an
answer in place, a clause given the rest, a clause that keeps the rest, a clause that leaves): an existential
type made of a sealed interface. A clause of another answer type cannot be put there by a cast, because no
code outside the file can make a `Case` but through those four functions.

## 3j.3 The row is a set, and it is in the type

A program is `Comp<A, R>`: it gives `A` and may ask `R`. The row is a type variable of the method that makes
the program, bounded by every effect the program asks:

```java
static <R extends Stock & Refusal> Comp<Order, R> place(Cart cart) {
  return perform(new Stock.Reserve(cart.sku(), cart.qty())).flatMap(ok ->
      ok ? pure(new Order(cart))
          : perform(new Refusal.Invalid(cart.sku(), "out of stock")).map(x -> new Order(cart)));
}
```

Three signatures make this work.

- `perform` is `<A, F, R extends F> Comp<A, R> perform(Op<A, F> op)`: an operation of `F` can be asked in a row
  `R` only if `R` has `F`. Asking `Clock.Now` inside `place` is refused (`OpOutsideRow`, `OpOutsideRowInChain`).
- `flatMap` is `<B, Q extends R> Comp<B, Q> flatMap(Function<A, Comp<B, Q>> next)`: the row of the whole is a
  subtype of the rows of both parts, so a program of fewer effects is a program of more as it is. The union of
  rows is subtyping, and javac computes it.
- `run` takes `Comp<A, Object>`: only a program with nothing left to ask. A program that still asks an effect
  is refused there (`RunWithEffectsKept`: *no suitable method found for run*).

The order in which the row is an intersection does not matter: `Stock & Refusal` and `Refusal & Stock` are the
same bound. Chapter 3g's complaint about my other Java libraries — "rows are positional and moved by hand" — is
a complaint about a design that encoded the row as a list of places; a set needs no moving.

Handlers are put on a part by a builder whose order is forced by inference:

```java
Optional<Order> o = run(Fx.<Order>top().handler(trying()).handler(stock(left)).handle(place(cart)));
```

`row()` (or `top()`, the empty row) fixes the row that stays outside; each `.handler(h)` adds the effect of
`h` to the set the part may ask; `.handle(part)` checks the part against that set. The order matters: with the
handlers and the part in one call, javac infers the set from what the part asks and accepts a part that asks
more than is handled. Written this way, a part that asks an effect with no handler is refused
(`MissingHandler`: *incompatible types*), and so is a handler whose own clauses ask what does not stay outside
it (`HandlerAsksWhatDoesNotStay`, `HandlerAsksOutsideKeptRow`).

The twenty-four refusals, by kind (`src/test/negative`, checked by `NegativeTest`, each for its own message):

- **an operation the row does not have**: `OpOutsideRow`, `OpOutsideRowInChain`, `EffectTakenOutIsStillAsked`,
  `PartAsksMoreThanKept`, `SmallerOnPurpose`;
- **an effect left with no handler**: `MissingHandler`, `PartWithoutItsHandler`, `RunWithEffectsKept`,
  `StartWithEffectsKept`;
- **a handler that leaves an operation out**: `MissingCase`;
- **an answer or a state of another type**: `WrongAnswer`, `WrongAnswerOfOneKind`, `WrongNextState`,
  `ReturnOfAnotherState`, `ReturnOfAnotherType`;
- **a handler of another part or effect**: `HandlerOfAnotherPartValue`, `PartValueOfAnotherType`,
  `ClauseValueNotTheHandlers`, `ClauseOfAnotherEffect`, `OperationOfAnotherFamily`;
- **a handler that asks what does not stay**: `HandlerAsksWhatDoesNotStay`, `HandlerAsksOutsideKeptRow`,
  `ClauseAsksOutsideItsRow`, `PassWhereTheEffectDoesNotStay`.

What it costs. `R` and the part's value type `A` are written at `row()` (`Fx.<R, A>row()`), because handlers
that only answer are generic in the part's type and javac has nothing to infer it from. A row of several
effects cannot be written as a type argument — `Fx.<Stock & Clock, A>row()` is not Java — so it is always a
type variable of the enclosing method. Two effects of one class with different arguments (`State<Integer>` and
`State<String>`) cannot be in one row: javac refuses the bound, which matches the machine, which finds a
handler by the effect's class. And javac's messages name inference variables, not effects: *inference
variable R has incompatible bounds* is how "you asked an effect this program does not declare" is said. The
library's guide translates the three messages a user meets; nothing makes them shorter.

On the axes of chapter 3a: (c) a handler is found at run time by the effect's class, the nearest part first;
(d) the types say the answer of each operation, the set of effects pending, the completeness of a handler, and
what a handler's own clauses may ask. They do not say the laws (section 3j.11).

## 3j.4 Completeness is the compiler's

A handler's clauses are a `switch` expression over the sealed effect. A `switch` expression that leaves out a
record of a sealed interface does not compile (`MissingCase`: *the switch expression does not cover all
possible input values*). So "every operation of the effect has a clause" — the law of section 4.3 that a
handler is offered every question of its effect — is checked with no code of the library's at all. Adding an
operation to an effect breaks the build at every handler of it, which is what a team wants.

## 3j.5 A handler is a fold, and a monad

The general handler:

```java
static <A, R> Handler<Refusal, A, Optional<A>, R> trying() {
  return folding(Refusal.class, null, (s, a) -> Optional.of(a), op -> switch (op) {
    case Refusal.Invalid i -> on(i, (s, k) -> pure(Optional.empty()));       // the rest is left
  });
}
```

`Handler<F, A, B, R>`: of a part that gives `A`, a handler of `F` gives `B`, and its clauses may ask `R`. It
has a state (here none), `ret` — what it gives where the part ends, of its last state and the part's value —
and a clause for each operation, given the state and the rest `k`, a plain function of the operation's answer
and the next state. The state's type `S` is hidden the same way the answer's is: `Handler` is a sealed
interface without `S`, over one private record with it. What is around a handler does not see its state, and
cannot get it wrong.

This is chapter 2b's handler as a fold, and it is also a monad: `ret` is the unit, a clause with its `k` is the
bind of that operation, and `S` and `B` are its carrier (`DESIGN.md` §1, decision 3). The usual effects in
`Std` are each written as the handler that is its monad, using only what the kernel gives: `State<S>` gives
`Final<S, A>`, `Fail<E>` an `Optional<A>` or a `Result<E, A>`, `Emit<X>` a `Told<X, A>`, `Choice` a `List<A>`
of every way. And the order of two handlers is the order of two monads (`StdTest`,
`theOrderOfTwoHandlersIsTheOrderOfTwoMonads`): a failure inside a state keeps the state, `Final<S, Result<E,
A>>`; a state inside a failure is lost with the part, `Result<E, Final<S, A>>`. That is section 4.5's one
equation that the order of state and failure decides, shown in the types of the two results.

One effect, several handlers, the program unchanged: `ScenariosTest` runs the same `place` under a handler that
stops at the first refusal, one that collects all refusals and goes on, and one that gives an `Optional`
(`theSameProgramTheFirstRefusalStopsIt`, `theSameProgramAllRefusalsAreCollectedAndItGoesOn`,
`aPartIsLeftWithAValueOfTheHandlers`). Those are section 5.2a's policies that a port's adapter cannot be.

## 3j.6 The machine

`run` is one loop of about ninety lines. The rest of the program is a stack of parts, one for each handler in
force, and each part a stack of steps (a `flatMap`, a `map`, and two marks for closing). Both stacks are linked
lists whose steps are never changed after they are made; what is written is the state of the part being run,
a flag on a rest that it was gone on with, and, for a rest that is kept, which parts it holds.

- **An operation** finds the nearest part whose handler's effect it is of. A clause made by `answer` is
  answered where it is asked: nothing moves.
- **A clause that needs the rest** takes it off: the steps of the innermost part and the parts up to the
  handler's own, with their states. That is two pointers. The clause then runs as a program of what is
  around the handler's part — which is why it may ask the effects that stay outside, and why its own handler
  is not in force inside it.
- **`k.apply(answer, next)`** puts the parts back on top of what runs *now*, made anew of those taken off:
  those inside the handler's own with the states they had when the operation was asked, its own with `next`,
  those around it as they are now.

Because the parts put back are new and the parts a rest holds are never run, a rest gone on with twice is two
independent runs, each with its own states (`theRestIsGoneOnWithTwice`, `aStateIsAValueOfEachWay`,
`partsDeepOneInAnotherKeepTheirStatesOnEveryWay`). Nothing is replayed. This bears on section 4.6. There, a
handler that keeps its state in a variable is not a fold over the rest, and the Scala and Java trees of
`code/` are safe only because their law files pass the state along by hand. In okay-tiny the state is in the
part's record, not in a variable the author of the handler chooses: every handler's state is part of the rest,
by construction, and the author cannot make the other choice by accident. The guide still warns that mutable
state *captured* by a handler, outside its state, is shared by every way — the language cannot stop that.

**No stack.** No call of the loop is nested in another; the depth of a program is in the two lists. The tests
run with `-Xss256k -Xmx64m`: a million operations in a loop, a million `flatMap` nested to the left, a
million `map` (`aLongProgramTakesNoStack`). A call that is the last thing a `flatMap`'s function does is a tail
call and leaves nothing behind: two functions that call each other 10^8 times run in that stack and heap
(`DESIGN.md` §5; the test, `aTailCallBetweenTwoFunctionsTakesNeitherStackNorHeap`, runs 5·10^6). For a call with
nothing asked before it there is `defer(() -> other(n - 1))`, a node of its own. What is not a tail call is
said too: a call with a `map` after it grows the heap by a step a level, and a handler put anew inside a loop is
a part a level — the guide's rule is that a handler goes around a loop, not inside it.

## 3j.7 Each node forced, and what it saved

Like stage 4 of chapter 3i, the kernel began smaller and each addition was made after a measurement or a
program that could not be written. The figures are `DESIGN.md`'s, JMH, ns per operation (section 3j.9 for the
protocol).

| change | before | after |
| --- | ---: | ---: |
| an operation and what follows it as one node, `Asking(op, next)`; an answer, innermost of seven | 10.1 | 6.9 |
| a clause that only goes on with `k` answered in place; a counter | 18.9 | 9.0 |
| a handler's state in its part's record; a counter, outermost of seven | 19.4 | 16.4 |
| `defer` as a node, not `pure(null).flatMap(..)` | 5.9 | 2.2 |

What forced each. The first: `perform(op).flatMap(next)` was a `FlatMap` of a `Perform`, so an answer in place
put a step on the stack and took it off at once; as one node — the node a free monad is named for — the answer
goes straight to `next`, and one object less is made. The second: the commonest stateful clause,
`k.apply(answer, next)`, took the rest off and put it back; the machine now sees that a clause gave nothing but
its own rest gone on with, and sets the state where it is (`answer` turned out to be one case of this, and no
word of its own was needed). The third: the states were in an array by depth, copied before a write once a
rest held them. The fourth: as `pure(null).flatMap(..)` a put-off call was two turns and four objects. And one
more, without a row of its own: clauses and steps became sealed types of records, each with what it needs and
the hidden type an existential, where they had been one record with flags and `Object` fields — fewer casts,
and a step 2 to 6 ns faster.

And one change that was about depth, not speed: when the states were in an array that every new part copied,
parts nested in parts cost the square of their depth — 10^5 levels, 0.6 s; with the states in the parts,
0.05 s (`aPartMoreCostsTheSameHoweverDeep`).

What was tried and left is recorded beside it (`DESIGN.md` §4): a second kind of handler for answers in
place (one kind and `answer` keep the speed), sets of operations in the kernel (an operation that holds a list
does it — `oneOperationThatHoldsManyItems`), one clause for the whole effect with `k.resume(op, answer)` (`k`
stops being a function), reading the effect's class from an empty varargs array (a hidden parameter, and a
pitfall for a handler generic in its effect). Of the alternatives the most instructive is the last but one:
`leave(value)`, a clause for any operation of an effect, is what was kept of it. It has no operation to read
an answer's type from, so it cannot go on with the rest; the types say so.

## 3j.8 Leaving, closing, waiting, watching

Four words beside the tree, each small.

- **`leave(value)`** leaves the part with the handler's value, for any operation of the effect: the handler
  that turns every operation into one result is written with no `switch`.
- **`ensuring(part, after)`** runs `after` when the part ends with a value, when it is left by a clause that
  did not go on with the rest, and under an exception (`whatIsToBeClosedIsClosedWhenThePartEnds`, `…IsLeft`,
  `…UnderAnException`). A rest gone on with twice ends twice and is closed twice; one gone on with and then
  left, once. This is axis (f) of chapter 3a, done with a mark among the steps and a mark under each clause
  given the rest: when the clause ends and the rest was never gone on with, the rest was left. The cost is one
  to two nanoseconds an operation. `after` is a `Runnable` and asks nothing; finalizers that ask are not here.
- **`keep(op, clause)`** is `on` without that mark: the rest is the clause's to go on with later, in a run of
  its own. It is all the kernel has for asynchrony. `Std.Async` is written with it and nothing else: one
  program, run under `blocking()`, where the thread waits, or by `start`, where the rest goes on from the
  callback of a `CompletableFuture` and no thread waits (`AsyncTest`: `theThreadThatRunsTheProgramWaits`,
  `orNoThreadWaits`, `aStateGoesOnAcrossAWait`). Chapter 11's question — can the rest be stored — is answered
  "in memory only": the kept rest is a Java object.
- **`run(program, see)`** gives an observer every operation of every effect, as data, before it is answered;
  what it throws stops the program there, so an operation it refuses is not answered
  (`anObserverIsGivenEveryOperation`, `anObserverRefusesAnOperation`). It is the one thing a handler cannot do
  alone, see the operations of every effect, and it is an argument of `run`, not a part of the program: an
  application has one. This is chapter 10 in one parameter. A failure is thrown on as it is, with the
  operation asked last and the handlers in force added to it as a suppressed exception; there is no stack
  trace of the program, and the library says so.

## 3j.9 What it costs

`DESIGN.md` §5: JMH 1.37, Temurin 21, Apple silicon, ns per operation, each ± under 0.5, a machine with nothing
else running.

| | ns |
| --- | ---: |
| `answer`, the handler the innermost of seven | 5.0 |
| `answer`, the handler the outermost of seven | 11.6 |
| an answer and two `map` | 17 or 22, from one run to the next |
| a counter (a state that moves on at every operation), alone | 9.0 |
| the counter the innermost of seven | 9.6 |
| the counter the outermost of three | 11.4 |
| the counter the outermost of seven | 16.4 |

The same document gives the limit of these figures: with another process busy, the outermost counter gave 33
to 41 ns from one run to the next, for this kernel and the one before it alike, so two kernels are compared by
runs taken in turn and by their least. A free monad makes five or six small objects an operation; that is the
floor of this family on the JVM, and it is the order of chapter 13's reference kernels (section 13.6), not a
competitor to them: different lanes, different days, and the figures were not taken together.

## 3j.10 Where it has been, and against the family's tests

**The TCK.** `TckTest` runs the family's conformance test: a thousand random programs of logs, states,
failures and choices one in another, the digest of their outcomes compared with the reference's. It gives
`-8623788747993822161`, the number the Scala, Java 25 and Rust kernels of okay-effects give
(`theDigestIsTheFamilys`). A different representation, in a weaker type system, computes the same answers.

**A real flow.** `Booking`: a booking of seven effects from okay-shop, already written with effects in another
library, moved with its seven handlers as one function each and no container (`BookingTest`).

**Ports and adapters.** `docs/ports-and-adapters.md` and `PortsTest` move a service built on interfaces and
injection in four steps, its callers unchanged, and run the service before and after on the same scenarios.
Section 5.2a cites it.

**Spring.** `spring-sample/`: a Spring Boot service with beans, a database, Spring's transactions and a
controller (six tests). It found nothing to change in the kernel: a program runs on the thread that called
`run`, so what Spring binds to a thread is where the handlers expect it, and `ensuring` was enough for a
transaction around a part.

**Direct style, beside the kernel.** `okay-tiny-direct` is a plugin of javac (369 lines) that rewrites a method
marked `@Direct` into the chain it stands for, after parsing and before types are attributed: `T x = $(p);
rest` becomes `bind(p, (T x) -> { rest })`, an `if`, a for-each and `try`-`finally` (as `ensuring`) are
rewritten, and the whole body is put off with `defer`. Because javac attributes the chain, the row, the
inference and the error messages are javac's, and the rest after a `$` is a value like any other — a handler
may leave it or go on with it twice (`DirectTest.everyWay`). Where it cannot rewrite a `$` — inside a lambda,
a `while`, a branch of `?:`, a `try` with a `catch` — it is an error at that line, never a failure at run time
(ten programs under `direct/src/test/negative`). It depends on javac's internal classes, as Lombok does. This
adds a road to chapter 3g's table: in Java a transformation need not rewrite bytecode, it can rewrite the
source tree, and then the types are checked after it.

## 3j.11 What this corrects, and what Java still costs

What the earlier chapters said, as first written, against what okay-tiny shows:

- **"A Java handler needs a cast"** to give the rest its answer (§3b). No handler of okay-tiny has one: the
  operation is the witness (`on`, `answer`). The refinement is still absent; the cast is no longer needed.
- **For rows, "almost nothing usable in Java"** (§3b). A row is an intersection bound, a set, checked by javac,
  together with a handler's completeness and what its clauses may ask: 24 refusals.
- **Rows "positional and moved by hand"; "a handler erases its state's type"** (§3g, *Painful*). True of a row
  of places; a set needs no moving. The state's type is hidden as an existential, not erased.
- **The tree "wrong for the programs"** (§3g). Heavy to write — chains of `flatMap`, or a javac plugin for
  direct style — not wrong.
- **Transformation "bytecode only"** (§3g, first table). Also the source tree, by a javac plugin.

Sections 3b and 3g now say so and point here.

What Java still costs, and the library lists in its own pitfalls:

- the effect is named twice on each operation's record;
- the part's value type is written at `row()`, and a generic effect's types are said at the start of a chain
  (`Std.<Integer, R>update(..)`);
- a row of several effects is never a type argument, only a method's type variable;
- one effect of a class in a row; two `State`s are told apart by where their handlers are put, not by type;
- javac's messages speak of inference variables, far from the cause;
- the machine's loop is untyped inside: seven places in `Fx.java` are marked unchecked — the phantom row in
  `flatMap` and `map`, the builder's two, the loop, and the two that open the hidden state and answer types.
  What guards them is that `Case` and `Handler` can only be made by the library's functions and that the loop
  checks a clause was made for the operation asked.

And what no type here says: that `ret` and the clauses make a lawful monad. A clause that applies `k` twice and
keeps one result passes the types. Chapter 4's laws are the author's to keep, here as everywhere but Lean.

## 3j.12 What was not shown

- Nothing of okay-tiny is in this book's `code/`: no rendering of the bank, no law file, nothing under
  `run-all.sh`. The chapter cites tests by name at one commit.
- It has not been tried on a large code base, with JPA, or by a team other than me and my agents.
- The figures are of one machine, from the library's own record, not re-run here.
- No benchmark compares it with `Tree.java`, with okay-java, or with the reference kernels in one session.

## What was run

In okay-tiny at `ece0f7e`, Java 21: `./gradlew test` — 88 tests of the kernel (`ScenariosTest`, `StdTest`,
`AsyncTest`, `TckTest`, `BookingTest`, `GuideTest`, `PortsTest`, `ReadmeTest`, and `NegativeTest` over the 24
programs of `src/test/negative`), 19 of `direct/`, 6 of `spring-sample/`; all passed, 2026-11-27.
