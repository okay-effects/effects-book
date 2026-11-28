# 3j · The same tree in Java, with the row in the type

Java has no higher-kinded types, does not refine a type parameter in a `case`, and has no notation for
sequencing: of the languages in chapter 3g it is the one whose types seem to give this family least. This
chapter shows how much they give all the same. In Java, with no compiler plugin and no bytecode rewriting,
the tree can say the type of every answer and the set of effects a program may ask; the compiler refuses a
program whose effects are not all handled, a handler that leaves an operation out, and a handler that asks
what is not around it; and no handler has a cast. What Java costs is real, and is mostly in the writing, not
in what can be checked (section 3j.11).

**What runs.** `code/java/rows/`: a kernel, `Fx.java`, of 79 lines of code; the bank of chapter 2 on it,
`Bank.java`, which prints the ten lines of `code/expected.txt`; the twelve laws of chapter 4, `Laws.java`; and
eight programs under `refused/` that javac must refuse, each for the words on its first line. `run-all.sh`
runs all of it. Like the path of chapter 3i it is one implementation, not one of the sixteen renderings. It is
made as chapter 3's forty lines are made, for reading and not for speed: it is recursive, and a node is made
anew for every handler it passes. Every listing in sections 3j.2 to 3j.6 is from these files.

**Where it comes from.** It is distilled from okay-tiny (github.com/okay-effects/okay-tiny, Apache 2.0), my
smallest JVM library: one file of 398 lines of code for Java 21, a loop of typed moves in place of the
recursion, and what a library needs beside the kernel — leaving, closing, asynchrony, an observer, the usual
effects, direct style as a plugin of javac. Sections 3j.7 to 3j.10 are about the library and cite its tests
by name at commit `8962bdf` (2026-11-28): 100 tests of the kernel, 22 of the plugin and 10 of a Spring Boot
sample, all passing that day. The rule of section 3j.3 is the library's too.

## 3j.1 What Java gives, and what it does not

For this family Java offers:

- **sealed interfaces and records**: an effect is a sealed interface, an operation a record of it, so the set
  of an effect's operations is closed and known to the compiler;
- **pattern `switch`** that must cover every case of a sealed type when it is an expression;
- **intersection bounds on a type variable**: `<R extends Accounts & Clock>` is a type variable that stands for
  any type having both;
- **generic methods with inference**, and lambdas.

It does not offer higher-kinded types, refinement of a type parameter inside a `case` (the GADT match of
section 3b), a notation for sequencing, or a public continuation. The design that follows is what fits in the
first list and goes around the second.

## 3j.2 The answer's type is on the operation

The bank's effects, from `Bank.java` (its `toString` methods left out):

```java
sealed interface Accounts {
  record Balance(String account) implements Accounts, Op<Long, Accounts> {}
  record Move(String from, String to, long amount) implements Accounts, Op<Void, Accounts> {}
}
sealed interface Clock {
  record Now() implements Clock, Op<Long, Clock> {}
}
sealed interface Refusal {
  record Refuse(String why) implements Refusal, Op<Void, Refusal> {}
}
```

`Op<X, F>` says two things of a record: the type of its answer, `X`, and the effect it belongs to, `F`. The
natural place to say the effect once is on the interface, `sealed interface Clock extends Op<Long, Clock>`.
That is refused as soon as two effects meet in one row: `<R extends Accounts & Clock>` would make `R` a
subtype of two parameterizations of `Op`, and javac says *Op cannot be inherited with different arguments*.
okay-tiny's `DESIGN.md` (§2) records the other ways to name the effect once that were tried: three fail for
the same reason, and the fourth, a family indexed by the answer alone, compiles but needs the family's class at
every `ask` and still does not refine the answer in a `case`. So the effect is named twice on each record, and
that repetition is the first cost of Java in this design.

Then the answer. Section 3b's problem is that inside `case Accounts.Balance b ->` the compiler knows `b` is a
`Balance` and does not know that the answer the rest expects is a `Long`. Here it never has to learn it there.
The clause is made by a function that reads the answer's type off the operation:

```java
static <A, R> Handler<Accounts, Void, A, A, R> inMemory(Map<String, Long> balances) {
  return handling(Accounts.class, op -> switch (op) {
    case Accounts.Balance b -> answer(b, balances.getOrDefault(b.account(), 0L));
    case Accounts.Move m -> {
      balances.merge(m.from(), -m.amount(), Long::sum);
      balances.merge(m.to(), m.amount(), Long::sum);
      yield answer(m, null);
    }
  });
}
```

`answer(Op<X, F> op, X x)` and its general form `on(op, (state, k) -> …)` take the operation as a witness of
its own answer type: `X` is inferred from `b`, and the value, or what is given to `k`, is checked against it.
`answer(b, "a hundred")` does not compile (`refused/WrongAnswer.java`). This is the road without a cast that
section 3b names: no handler has to learn the answer's type in a `case`, so the refinement Java lacks is
never needed by a handler.

It is still needed once, inside the kernel, and the kernel says so. A clause is a record that keeps the
operation it was made for, `Clause<X, F, S, B, R>`; the arms of a `switch` give clauses of different `X`, so a
handler's clauses are `Clause<?, F, S, B, R>`, and when the machine gives a clause the rest of the part it has
to say that the clause's `X` is the rest's. It tests that the clause was made for the very operation asked,
and casts:

```java
@SuppressWarnings("unchecked")
private static <Y, X, F, S, B, R> Prog<B, R> given(Clause<Y, F, S, B, R> c, S s, Op<X, ?> asked,
                                                   BiFunction<X, S, Prog<B, R>> k) {
  if (c.op() != asked) throw new IllegalStateException("a clause for " + c.op() + " was given " + asked);
  return c.body().apply(s, (BiFunction<Y, S, Prog<B, R>>) (BiFunction<?, S, Prog<B, R>>) k);
}
```

That is the one cast of `Fx.java`, and with `-Xlint:all` the file has no other warning. It is the price of the
missing refinement, paid in one place instead of in every handler.

## 3j.3 The row is a set, and it is in the type

A program is `Prog<A, R>`: it gives `A` and may ask `R`. The row is a type variable of the method that makes
the program, bounded by every effect the program asks:

```java
static <R extends Accounts & Clock & Refusal> Prog<String, R> pay(String from, String to, long amount) {
  return ask(new Accounts.Balance(from)).then(have ->
      (have < amount ? Fx.<Void, Refusal, R>ask(new Refusal.Refuse(from + " has " + have + ", needs " + amount))
          : Fx.<Void, R>done(null))
          .then(ok -> ask(new Accounts.Move(from, to, amount)))
          .then(moved -> ask(new Clock.Now()))
          .then(at -> done("moved " + amount + " from " + from + " to " + to + " at " + at)));
}
```

Three signatures of `Fx.java` make this work.

- `ask` is `<X, F, R extends F> Prog<X, R> ask(Op<X, F> op)`: an operation of `F` can be asked in a row `R`
  only if `R` has `F`. A program of row `Accounts` that asks the clock is refused (`refused/OutsideTheRow.java`).
- `then` is `<B, Q extends R> Prog<B, Q> then(Function<A, Prog<B, Q>> next)`: the row of what follows may have
  more effects than the row of what went before, so a program of fewer effects is one of more as it is. The
  union of rows is subtyping, and javac computes it. (The one explicit row in `pay` is in the branches of `?:`,
  where javac has no target to infer it from.)
- `run` takes `Prog<A, Object>`: only a program with nothing left to ask (`refused/RunWithEffects.java`).

The order of an intersection does not matter: `Accounts & Clock` and `Clock & Accounts` are the same bound.
A row of places — a list of effects in order — has to be moved by hand when a handler is put in the middle of
it, and my earlier Java libraries paid that (`okay-java/README.md`); a set needs no moving. And the row is in
the types only: `Prog`'s two records hold an operation and a function, and no value holds a row. Where the
kernel has to change a program's row it makes the program anew (`rowed`), rather than cast it.

**Handlers are put outermost first, in two steps.**

```java
var paid = run(Fx.<Result>top().handler(inMemory(rich)).handler(fixedClock(42)).handler(refusals())
    .handle(pay("ann", "bob", 30)));
```

`top()` (or `row()`, for a part with effects left outside it) fixes the row that stays outside and the type
of the result. Each `.handler(h)` adds the effect of `h` to the set inside it; `.handle(part)` checks the part
against the last set. The signature is the whole of it:

```java
public <V extends W, P> Handlers<R, V, P, B> handler(Handler<? super V, ?, P, A, W> h)
public Prog<B, R> handle(Prog<A, ? super W> part)
```

`W` is what is inside the handlers put so far; `V`, inferred as `W` and the new handler's effect, is what is
inside this one; the handler's own row is exactly `W`, what is around it. Two things in this follow from
Java's inference. The steps are separate calls because javac must fix the set before it sees the part: with
the handlers and the part in one call it would infer the set from what the part asks, and accept a part that
asks more than is handled. And the result type is written at `top()`, because a handler that only answers is
generic in what the part gives and javac has nothing to infer it from. A part that asks an effect nobody
handles is refused (`refused/Unhandled.java`: *inference variable R has incompatible bounds*), and so is
line 7 of the laws (`refused/NobodyAnswers.java`).

**Why outermost first, and why exactly `W`.** A handler's clauses are programs of what is around it, and so is
the rest `k` they are given: once its own operations are answered, the rest of the part still asks whatever
the handlers *around* this one answer. The handler's row must say exactly that. Put outermost first, each
handler's row is known when it is put, and is `W`. The journal of line 3 asks `Accounts` of the bank around it,
and can be put only inside a handler of `Accounts` (`refused/JournalOutsideTheBank.java`):

```java
static <A, R extends Accounts> Handler<Accounts, Void, A, A, R> journal(List<String> lines) {
  return handling(Accounts.class, op -> switch (op) {
    case Accounts.Balance b -> on(b, (none, k) -> Fx.<Long, Accounts, R>ask(b).then(x -> {
      lines.add(b + " -> " + x);
      return k.apply(x, none);
    }));
    case Accounts.Move m -> on(m, (none, k) -> Fx.<Void, Accounts, R>ask(m).then(x -> {
      lines.add(m + " -> ()");
      return k.apply(x, none);
    }));
  });
}
```

Both halves of the rule are needed, and a program that compiles and fails shows each. Put innermost first,
as okay-tiny first put them, every handler's row is typed as the row outside all of them. A clause there that runs
its rest by itself — `Fx.run(k.apply(x, s))`, in a handler declared with an empty row — compiles, and fails
when run with *no handler for Now*, because the rest still asks the clock handled around it (a program run
against okay-tiny at `ece0f7e`; it is not in the library's tests). Put outermost first but typed `? super W`,
a handler could still say its row was empty inside a handler of the clock, and run its rest the same way.
With the row exactly `W` that program is refused (`refused/RunsTheRest.java`; in okay-tiny,
`RestRunByItself` under `src/test/negative`).

## 3j.4 Completeness is the compiler's

A handler's clauses are a `switch` expression over the sealed effect. A `switch` expression that leaves out a
record of a sealed interface does not compile (`refused/MissingCase.java`: *the switch expression does not
cover all possible input values*). So "every operation of the effect has a clause" — the law of section 4.3
that a handler is offered every question of its effect — is checked with no code of the kernel's at all.
Adding an operation to an effect breaks the build at every handler of it, which is what a team wants.

It also changes what a handler may be. In `code/java/Tree.java` the dry run of line 6 answers `Move` and lets
`Balance` pass, and a handler that answers some operations of an effect is ordinary. Here a handler of
`Accounts` answers both or does not compile: line 6's `whatIf` goes on with the rest once for each supposed
balance and answers `Move` with nothing moved, in one handler.

## 3j.5 A handler is a fold, and a monad

```java
public record Handler<F, S, A, B, R>(Class<F> effect, S state, BiFunction<S, A, Prog<B, R>> done,
                                     Function<F, Clause<?, F, S, B, R>> clauses) {}
```

A handler of `F` has a state of type `S`, what it gives where the part ends with an `A` (of its last state and
that value), and a clause for each operation, given the state and the rest `k`, a function of the operation's
answer and the next state. This is chapter 2b's handler as a fold, and it is also a monad: `done` is the unit,
a clause with its `k` is the bind of that operation, and `S` and `B` are its carrier. The cell of the laws:

```java
static <A, R> Handler<State, Long, A, Held<A>, R> cell(long initial) {
  return new Handler<>(State.class, initial, (s, a) -> done(new Held<>(a, s)), op -> switch (op) {
    case State.Get g -> on(g, (s, k) -> k.apply(s, s));
    case State.Put p -> on(p, (s, k) -> k.apply(null, p.n()));
  });
}
```

The order of two handlers is the order of two monads. Line 8 of the laws puts the refusals around the cell
and then the cell around the refusals, and the types of the two results are the meanings:
`Outcome<Held<Long>>` loses the cell with the part, `Held<Outcome<Long>>` keeps it —
`8 state then failure: refused(no) | failure then state: refused(no), cell 1`. That is section 4.5's one
equation that the order of state and failure decides.

One effect, several handlers, the program unchanged: lines 1 to 6 of the scenario run the same `pay` under a
bank, a journal, a limit and a supposition, and line 2's refusal and line 4's are one effect handled the same
way, asked by different code. Those are section 5.2a's policies that a port's adapter cannot be.

## 3j.6 The machine, and the rest as a value

`run` of the forty lines, with a row:

```java
private static <F, S, A, B, R> Prog<B, R> under(Handler<F, S, A, B, R> h, S s, Prog<A, ?> p) {
  return switch (p) {
    case Done<A, ?> d -> h.done().apply(s, d.value());
    case Ask<?, A, ?> a -> step(h, s, a);
  };
}
private static <X, F, S, A, B, R> Prog<B, R> step(Handler<F, S, A, B, R> h, S s, Ask<X, A, ?> a) {
  if (!h.effect().isInstance(a.op())) return new Ask<>(a.op(), x -> under(h, s, a.rest().apply(x)));
  BiFunction<X, S, Prog<B, R>> k = (x, next) -> under(h, next, a.rest().apply(x));
  return given(h.clauses().apply(h.effect().cast(a.op())), s, a.op(), k);
}
```

An operation of another effect is asked again further out, with the rest still under this handler; one of its
own is given to its clause with `k`, the rest of the part under this handler from the next state on. `k` is a
closure: it may be called once, twice or never, and is never replayed. The state is passed to it as a value,
so a rest gone on with twice goes on with two states, and every handler's state is part of the rest by
construction. That is section 4.6's law, and lines 10 and 11 show both sides of it:

```
10 twice, state inside: 11, cell 11 | 21, cell 21
11 twice, state outside: 11 | 31, cell 31
```

The Scala and Java trees of the sixteen renderings are safe there only because their law files pass the
state along by hand, which their bank files do not; here there is no other way to write a handler's state.
What the language cannot stop is state *captured* from outside, like the bank's map: it is shared by every way.
`whatIf` of line 6 is safe with the bank's map only because it moves nothing.

The kernel is recursive, as the forty lines are: a long program overflows the stack, and every handler a node
passes makes the node anew. okay-tiny is the same model as a loop, and the next four sections are about it.

**The library's machine.** In okay-tiny `run` is a loop of one line, `while (!(at instanceof Done<Z> done)) at =
at.next(this)`, over moves of three kinds: a program to run, a value to give, the end. A move holds the steps
that take its value, `Rest<A, Y>`, and the parts those steps end in, `Around<Y, Z>`, and javac checks that
the three fit: no register of the machine is an `Object`. The rest of the program is a
stack of parts, one for each handler in force, each a stack of steps; taking the rest is two pointers, and
putting it back makes the parts anew, so a rest gone on with twice is two independent runs
(`theRestIsGoneOnWithTwice`, `aStateIsAValueOfEachWay`, `partsDeepOneInAnotherKeepTheirStatesOnEveryWay`). No
call of the loop is nested in another. Its tests run with `-Xss256k -Xmx64m`: a million operations in a loop,
a million `flatMap` nested to the left, a million `map` (`aLongProgramTakesNoStack`). A call that is the last
thing a `flatMap`'s function does is a tail call and leaves nothing behind: two functions that call each other
10^8 times run in that stack and heap (`DESIGN.md` §5; the test,
`aTailCallBetweenTwoFunctionsTakesNeitherStackNorHeap`, runs 5·10^6). What is not a tail call is said too: a
call with a `map` after it grows the heap by a step a level, and a handler put anew inside a loop is a part a
level.

## 3j.7 In the library: each node forced, and what it saved

Like stage 4 of chapter 3i, okay-tiny's kernel began smaller and each addition was made after a measurement or a
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

The machine itself went the same way, and that one cost. As one loop over registers of type `Object`, with a
cast wherever a hidden type was opened, it answered in place in 5.0 ns; as moves typed throughout, in 5.7,
with the same 64 bytes made an operation, while a state costs less (9.0 ns then, 7.3 now). Of the nanosecond, a third
was the way back to the loop after each answer and went when an answer was let go on within its move; 0.3 is
the operation kept for the failure's note; the rest was not found (`DESIGN.md` §5). What it bought is that
a move which gives the wrong thing to the wrong step does not compile.

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

## 3j.8 In the library: leaving, closing, waiting, watching

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

## 3j.9 In the library: what it costs

`DESIGN.md` §5: JMH 1.37, Temurin 21, Apple silicon, ns per operation, each ± under 0.5, a machine with nothing
else running.

| | ns |
| --- | ---: |
| `answer`, the handler the innermost of seven | 5.7 |
| `answer`, the handler the outermost of seven | 14.5 |
| an answer and two `map` | 17 or 22, from one run to the next |
| a counter (a state that moves on at every operation), alone | 7.3 |
| the counter the innermost of seven | 7.6 |
| the counter the outermost of three | 10.0 |
| the counter the outermost of seven | 15.6 |
| a state said of a template, outside four other effects said of templates | 15.5 |

At a depth of seven the same code gives one figure or twice it from one start of the JVM to the next — the
counter 16 ns or 33 — as the JIT happens to compile it; the table has the lesser. The same document gives the
other limit of these figures: with another process busy, the outermost counter gave 33
to 41 ns from one run to the next, for this kernel and the one before it alike, so two kernels are compared by
runs taken in turn and by their least. A free monad makes five or six small objects an operation; that is the
floor of this family on the JVM, and it is the order of chapter 13's reference kernels (section 13.6), not a
competitor to them: different lanes, different days, and the figures were not taken together.

## 3j.10 In the library: where it has been, and the family's tests

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
controller, the accounts answered once through `JdbcTemplate` and once through JPA (ten tests). It found
nothing to change in the kernel: a program runs on the thread that called `run`, so what Spring binds to a
thread — a transaction, and JPA's persistence context with it — is where the handlers expect it, and
`ensuring` was enough for a transaction around a part. A handler changes two entities and saves nothing; the
commit writes them after the program has ended.

**Direct style, beside the kernel.** `okay-tiny-direct` is a plugin of javac (418 lines) that rewrites a method
marked `@Direct` into the chain it stands for, after parsing and before types are attributed: `T x = $(p);
rest` becomes `bind(p, (T x) -> { rest })`, an `if`, a for-each and `try`-`finally` (as `ensuring`) are
rewritten, and the whole body is put off with `defer`. Because javac attributes the chain, the row, the
inference and the error messages are javac's (every step is given the method's row as a type, so an effect
the row has not is refused at its own `$`), and the rest after a `$` is a value like any other — a handler
may leave it or go on with it twice (`DirectTest.everyWay`). Where it cannot rewrite a `$` — inside a lambda,
a `while`, a branch of `?:`, a `try` with a `catch` — it is an error at that line, never a failure at run time
(twelve programs under `direct/src/test/negative`). It depends on javac's internal classes, as Lombok does. In
Java, then, a transformation need not rewrite bytecode: it can rewrite the source tree, and the types are
checked after it (chapters 3e and 3g).


## 3j.11 What Java gives this family, and what it costs

What the types check, in `code/java/rows/` and in okay-tiny alike, with no plugin and no cast in any handler:

- **the answer of every operation**: the operation is the witness of its own answer type (`on`, `answer`);
- **the row**: an intersection bound, a set, never moved by hand; an operation outside it, an effect left with
  no handler, and a program run with effects still pending are refused;
- **a handler's completeness**, by the exhaustive `switch` over a sealed effect;
- **what a handler's own clauses may ask**: exactly what is around it (section 3j.3).

The evidence is programs javac must refuse: eight under `code/java/rows/refused/`, run by `run-all.sh`, and
twenty-five in okay-tiny's `src/test/negative`, each for its own reason.

What Java costs whatever the design:

- the effect is named twice on each operation's record;
- the result's type is written where the handlers are put, and a row is said by hand where javac has no target
  for it (the branches of `?:`, the start of a chain of a generic effect);
- a row of several effects is never a type argument, only a method's type variable;
- a generic effect is not specialized by `extends`: a type variable cannot have `State<Integer>` and
  `State<String>` among its bounds. In okay-tiny the usual effects are templates, and an effect is said of one
  in a line — `interface Count { State<Integer, Count> state = State.of(Count.class); }` — a type of its own
  in the row, so two states are two types (`StdTest`);
- javac's messages speak of inference variables, far from the cause;
- the refinement is paid once inside the kernel: one cast in `Fx.java`, guarded by the test that a clause was
  made for the operation asked. okay-tiny has five places marked unchecked — the row, a phantom, said anew
  where handlers begin to be put; a hidden type opened by an identity, twice; a part made anew as the part it
  replaces; an operation of a template as what the template made it — and none in the moves of its machine;
  they are guarded the same way and by the rule that its clauses and handlers can be made only through its
  functions.

And what no type here says: that `done` and the clauses make a lawful monad. A clause that calls `k` twice and
keeps one result passes the types. Chapter 4's laws are the author's to keep, here as everywhere but Lean; for
this kernel they are the twelve lines of `Laws.java`.

**What its tests missed.** Three faults were found in the library after every test had passed, and each is
a fault of this kind of kernel, not of Java. A rest was, for a few hours, given its steps after its clause had
returned — an optimization — and a clause that ran its rest inside itself found it without them: a callback
given to work already done is called there and then. The same callback, where it did run the rest, ran it as
a call inside the clause, and with work that ends quickly the Java stack only grew: 27 frames at a wait became
86 to 888 over twenty thousand waits. And where a part was left, a closing that threw kept the other
closings of that rest from being run. The first was seen by another program using the library, the second by
a test of twenty thousand waits written after the first, the third by reading the code. None is in the
family's TCK, which has no closing, no rest kept and no work that ends on another thread. What was added is
`WaysTest`: three thousand random programs of states, failures, choices, closings and exceptions, each
written nine ways that mean the same and take the machine by different roads — answered in place, the rest
taken off and put back, the rest kept — which must give the same value or exception and the same notes in
the same order; and whatever was opened closed once, the last opened first. Run against the kernel as it
was, it finds the third fault within its first six hundred programs. The fast roads of a machine are where
its faults are, and a law that says two ways of writing are one is a test of them that needs no second
implementation.

The plugin had its own: a name declared in a block with a `$`, or in a branch that may end the method, was
in what followed that block's — a field of the same name was read as the local, with no word from javac.
It is refused now, at the block.

## 3j.12 What was not shown

- The book's kernel is recursive and makes a node anew for every handler it passes; nothing of it was measured.
  The library's figures (3j.9) are of one machine, from its own record, not re-run here.
- Neither has been tried on a large code base, or by a team other than me and my agents.
- No benchmark compares okay-tiny with `Tree.java`, with okay-java, or with the reference kernels in one session.

## What was run

`sh code/run-all.sh`: `java code/java/rows/Bank.java` prints `code/expected.txt`; `java code/java/rows/Laws.java`
prints `code/laws-expected.txt` with line 7 as *does not compile*; the eight programs of
`code/java/rows/refused/` are refused by javac for the words on their first lines. `javac -Xlint:all` on the
three files gives no warning. Java 26, 2026-11-27.

In okay-tiny at `8962bdf`, Java 21: `./gradlew test` — 100 tests of the kernel (`ScenariosTest`, `StdTest`,
`AsyncTest`, `TckTest`, `WaysTest`, `BookingTest`, `GuideTest`, `PortsTest`, `ReadmeTest`, and
`NegativeTest` over the 25 programs of `src/test/negative`), 22 of `direct/`, 10 of `spring-sample/`; all
passed, 2026-11-28. The program
of section 3j.3 that compiled and failed was run against `ece0f7e`, where handlers were put innermost first.
