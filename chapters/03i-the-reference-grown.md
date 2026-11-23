# 3i · One implementation, grown in four steps

Part III laid out the families and how to choose among them. This chapter makes one choice — a tree, an
interpreter, rows in the types, on the JVM — and builds it from nothing to the shape a library has. It starts
from stage 0, the forty lines of chapter 3, and goes on in four steps, stages 1 to 4. Each begins with what
the stage before could not do, shown by a program that fails or cannot be
written; makes the smallest change that fixes it; and says what the change costs. The aim is that a reader
who finishes can say of every constructor and every type parameter of the last file why it is there.

**Whose implementation this is.** The end point is a file of mine: `Free.scala` in okay-simple, 282 lines,
with `Places.scala` beside it for the rows. It is one implementation among those Part III surveyed, and it is
here because I can account for every line of it, not because the others are lesser. The notation of chapter 2
stays the language of the idea; the fifteen other renderings stay as the comparisons. What this chapter adds
is one path walked in full, so that the engineering inside a library is seen being forced, a step at a time.

**What runs.** Stage 0 is `code/scala/kernel.scala`, which chapter 3 built; it is also the first of the
sixteen renderings, and is counted once. Stages 1 to 4 are four files under `code/scala/path/`, each
self-contained, each its own package, each with a main that asserts what it claims:

| stage | file | adds | of chapter 2b | axes of 3a |
| --- | --- | --- | --- | --- |
| 0 | `kernel.scala` (chapter 3) | two nodes; a handler given the rest; recursion | both halves | a, b |
| 1 | `stage1.scala` | a tree of three nodes; an interpreter that is a loop | the data half | a, h |
| 2 | `stage2.scala` | a handler that is given the rest of the program | the continuation half | b, e |
| 3 | `stage3.scala` | rows: which effects may still be asked, in the type | the third thing, rows | c, d |
| 4 | `stage4.scala` | two more nodes, a handler's state, answers in place, places renamed | engineering | e, h |

Stages 1 and 2 take stage 0 apart again on purpose: stage 1 keeps only its data half, to solve the stack
where nothing else is in the way, and stage 2 puts the rest of the program back. `sh code/run-all.sh` runs
all five. Stages 3 and 4 print the ten lines of `code/expected.txt`, as the sixteen
renderings do. Every output quoted below is from these mains, on Scala 3.3.8 and Java 26.

## 3i.1 Stage 0: the forty lines of chapter 3

Chapter 3's forty lines are the idea at its smallest, and the path starts there: a program is `Done(a)` or
`Ask(op, rest)`, and a handler is given `rest`. They have no cast: `handle` names the answer type hidden in a
node with a type pattern, `case ask: Prog.Ask[x, a]`, and the compiler checks that the rest is given an answer
of its own question's type (§3.4). Section 3.7 listed what they lack. Two of those debts are what this
chapter pays:

- **the stack.** `flatMap` and `handle` there are recursive functions, and a long program overflows;
- **the types.** They say what a question is answered with, and not whose question it is: `run` finds an
  unanswered question when it meets one, and every handler is offered every operation and ends in
  `case _ => None`.

The other two — speed, and leaving properly — are touched in stage 4 and not at all, in that order. No stage
here has finalizers.

The four stages that follow are the same tree as stage 0's, made stack-safe and then given rows. Section 3i.6 shows how
literally that is meant: the last stage has a function whose result type is chapter 3's two nodes.

## 3i.2 Stage 1: a tree of three nodes, and a loop

From `code/scala/path/stage1.scala`:

```scala
enum Free[F[_], +A]:
  case Return[F[_], A](a: A) extends Free[F, A]
  case Suspend[F[_], A](op: F[A]) extends Free[F, A]
  case Bind[F[_], X, A](m: Free[F, X], f: X => Free[F, A]) extends Free[F, A]

  def flatMap[B](f: A => Free[F, B]): Free[F, B] = Bind(this, f)
  def map[B](f: A => B): Free[F, B] = Bind(this, a => Return(f(a)))
```

Two type parameters. `F` is the vocabulary: a type constructor whose values are operations, indexed by the
type of their answer, so that `F[Long]` is "an operation of `F` answered with a number". `A` is what the
program ends in. It is covariant so that a program ending in `Nothing` — a refusal — can stand wherever a
program is expected; that is what lets the two branches of the `if` in `pay`, a refusal and a unit, be one
type.

**Why `Bind` is a node and not a function call.** Chapter 3's `flatMap` did work: on a question it wrapped the
rest in a closure that would call `flatMap` again when answered. So a chain of binds was a chain of pending
calls, paid for on the host stack when the program ran, and paid again at every question for every bind
still waiting outside it (chapter 3b, "Left-nested binds"). Here `flatMap` allocates one object and
returns. Sequencing two programs costs the same whatever their length and never recurses. The price is that
the tree no longer has its first question on top: someone has to walk the binds to find it. That someone is
the interpreter, and because it is now the only code that walks, it is the only code that needs a stack — and
it can be written not to.

```scala
@tailrec def run[F[_], A](p: Free[F, A])(answer: [X] => F[X] => X): A = p match
  case Return(a)   => a
  case Suspend(op) => answer(op)
  case b: Bind[f, x, a] => b.m match
      case Return(x)          => run(b.f(x))(answer)
      case Suspend(op)        => run(b.f(answer(op)))(answer)
      case m: Bind[f2, y, x2] => run(Bind(m.m, (y: y) => Bind(m.f(y), b.f)))(answer)
```

Every case finishes or goes round again, so `@tailrec` compiles the function to a jump. The first two inner
cases are plain: a value is given to the rest; an operation is answered and the answer given to the rest. The
third is the one that needs a reason.

**Why re-association.** A bind whose left side is a bind, `(m >>= g) >>= f`, cannot be stepped by looking at
its left side alone, because the left side is not a value or an operation. The direct way is to run `m >>= g`
first and come back to apply `f`; "come back" is a stack frame, one for each level of nesting. The loop
instead rewrites the program to `m >>= (y => g(y) >>= f)`. By the associativity law of sequencing it is the
same program; `m` is now on top, where the loop can look at it; and `f` waits inside a closure on the heap.
The cost is one `Bind` and one closure for each bind that was nested, once.

The file has both interpreters, and the run says what the difference is:

```
1000000 binds nested to the left: counted 1000000; the stack was 6 frames deep at each of 10 samples
1000000 binds nested to the right: counted 1000000; the stack was 6 frames deep at each of 10 samples
the naive interpreter, nested to the left: java.lang.StackOverflowError
the naive interpreter, nested to the right: counted 1000000
```

The naive interpreter is three lines, `naive(b.f(naive(b.m)(answer)))(answer)` being the third. It survives
the right-nested program for a reason that is the compiler's: its outer call is in tail position, and Scala
turns a self-call in tail position into a jump whether or not `@tailrec` is written. The inner call is the
frame per nesting.

**What is known about left-nested binds.** That sequencing by substitution makes a left-nested program
quadratic is an old observation about free monads, and chapter 15 (§15.4) names who diagnosed it and the two
well-known answers: Voigtländer's transformation, and van der Ploeg and Kiselyov's "Reflection without
remorse", which keeps the pending functions in a queue built for cheap appending; Kiselyov and Ishii's freer
monad uses such a queue. The answer taken here is the third that chapter 3b listed, "re-associate while
stepping", and as far as I know it is the usual one on the JVM, where it was described for Scala by Rúnar
Bjarnason ("Stackless Scala with Free Monads"). What it gives up against a queue is that the pending rest is
a chain of closures nobody can inspect. What I can show for this implementation is a measurement, in stage
4: bytes allocated for each bind stay level as a left-nested chain grows from 1,000 to 4,000 (§3i.5). I have
not proved that no program is quadratic on it.

**What stage 1 is, on the two halves.** The data half and nothing else. A program is a value; the questions
are values; the interpreter can be exchanged. And a handler is a function `[X] => F[X] => X`: from a question
to its answer. It answers, once, at once, and is never given the rest of the program. Chapter 2b said what
follows from having this half alone — "the answer comes back once, in place" — and the file shows each
consequence against the scenario:

- **Lines 2 and 5, a refusal.** `Refuse` has no answer and `answer` must return one. The only exit that does
  not continue the program is the host's: `throw`. The file does that and prints
  `refused: ann has 10, needs 30 -- by an exception of the host`. It works. It is also not ours: nothing
  between the program and the edge can turn it into a value except with `try`.
- **Lines 3 and 4, a journal and a limit.** Possible, as a *wrapper* of the answer function: the file's
  `Journal` wraps `answer`, sees each question with its answer, and prints the two journal lines. This is
  chapter 6's wrapper, and it is all a wrapper is: it can call the function it wraps, before or after doing
  something of its own. It has no program in its hand with which to ask a different question of whoever is
  outside.
- **Line 6, the rest resumed twice.** Cannot be written. To try a balance of 100 and then of 10 a handler
  needs "the program from here on" as something to call, and the type `F[X] => X` has no place for it.
- **A second effect, written apart.** Cannot be written. `F` is one closed enumeration.

On the axes: (a) is settled, a tree that an interpreter walks; (b) has no entry, since there is no rest to
be made of anything. This is where command objects and interpreters of plain data live (§5.2b), and where
capability passing stops by design (chapter 3c).

## 3i.3 Stage 2: the handler is given the rest

The tree does not change. What a handler is changes, from `code/scala/path/stage2.scala`:

```scala
def handle[F[_], A, B](p: Free[F, A])(ret: A => Free[F, B],
    eff: [X] => (F[X], X => Free[F, B]) => Free[F, B]): Free[F, B] = handling(p, ret, eff)
```

Two more type parameters and two clauses. `ret` says what a finished program becomes. `eff` is given an
operation and `k`, a function from the operation's answer to *the handled rest*: what the remainder of the
program, under this same handler, ends in. `B` is separate from `A` because a handler may change what a
program ends in: a refusal handler turns an `A` into an `Either[String, A]`. And the result is a program
again, a `Free[F, B]`: what `eff` returns may itself ask.

The loop is stage 1's, with one line different. Where stage 1 had

```scala
      case Suspend(op)        => run(b.f(answer(op)))(answer)
```

stage 2 has

```scala
      case s: Suspend[f2, x2]  => eff[x2](s.op, (x: x2) => handle(b.f(x))(ret, eff))
```

Stage 1 computed the answer and went round. Stage 2 hands the clause the operation and a function that,
*given* an answer, does what stage 1 did. Whether there is an answer, and how many, has moved from the
interpreter to the clause. That is the whole of the continuation half: `k` is the rest of the program up to
this handler, as a value. It is delimited because `handle(b.f(x))` wraps the rest in this handler and stops
there; whoever called `handle` is not part of it.

The file's bank is one handler for the whole vocabulary, and three of its lines are the three behaviours:

```scala
            case Balance(a) => k(balances.getOrElse(a, 0L))               // ONCE
```

```scala
            case Refuse(why) => Return(Left(why)))                        // ZERO TIMES: `k` is not called
```

and, in `whatIf`, `k(s)` inside a fold over the supposed balances. The run:

```
once: paid: moved 30 from ann to bob at 42; balances: ann=70 bob=30
zero times: refused: ann has 10, needs 30; balances: ann=10 bob=0
many times: 100 -> moved 30 from ann to bob at 42 | 10 -> refused: ann has 10, needs 30
journal: Balance(ann) -> 100; Move(ann,bob,30) -> ()
```

The refusal is now a value, `Left(why)`, and no exception of the host is involved. The journal is now a
handler and not a wrapper: it is given each question of the accounts, asks it *again* as a program,

```scala
def passOn[F[_], X, B](op: F[X], k: X => Free[F, B]): Free[F, B] = Bind(Suspend(op), k)
```

and continues with what whoever is outside answered. It can do that because a handler returns a program.

On the axes this is (b), the rest is a closure, and so (e), many resumptions, for nothing; and (g), no
storing, for the same reason. Nothing is done about (f): a `k` that is dropped releases nothing.

**What stage 2 costs, and what still fails.** Three things, each run in the file.

*The stack is lost again.* Stage 1 ran a million operations in six frames. Stage 2:

```
100000 operations under a handler that calls k: java.lang.StackOverflowError
```

`k` calls `handle`, which calls the clause, which calls `k`. Those are host frames for each operation
answered, and none is given back until the program ends. It happens even when resuming is the clause's very
last act, as it is the bank's. Giving the handler the rest as an ordinary function undid what the loop had bought.
Stage 4 repairs it.

*The types do not say what is left.* `journal(pay(…))` is a `Free[Bank, String]`. So is `pay(…)`, and so is a
program under the bank. The compiler cannot tell a program that still asks from one that does not:

```
a forgotten handler, found at run time: nobody answers Balance(ann)
a journal outside the bank compiles, and has 0 lines
```

*There is one `F`.* Every handler is given every operation and must say what to do with each, if only
`case other => passOn(other, k)`. And a second effect, written by someone who has not seen the bank, does
not fit. The usual answer is the sum of two effects as data:

```scala
enum Sum[F[_], G[_], A]:
  case L[F[_], G[_], A](op: F[A]) extends Sum[F, G, A]
  case R[F[_], G[_], A](op: G[A]) extends Sum[F, G, A]
```

The file tries it with a small `Log` effect beside the bank, and it runs
(`two effects by hand: Vector(asking the time), 42`). What it shows is who has to know the shape of the sum.
The program does: it writes `Suspend[Both, Long](Sum.R(Now))`, and `pay`, a `Free[Bank, String]`, cannot be
used in it without walking its whole tree to wrap every operation. The handler of the log does: it matches
`Sum.L(Log.Note(line))`, so it is written against one particular sum and would be written again for a sum of
three. And its result is still a `Free[Both, _]`; the type does not say that no `Note` is left.

Each of the three is the absence of the same thing: a way to say, in a type, *which effects, and where each
one stands*.

## 3i.4 Stage 3: rows

Section 2b.5 set rows apart as a third thing, not part of the definition. Stage 2 is the evidence: it has
both halves and no rows, and it runs the scenario's hard lines. Rows are bought separately, and this is the
purchase. Everything new in `code/scala/path/stage3.scala` is in the types; at run time it adds one small
object for each operation.

**A row is a name.** Stage 2's sum was data: an operation was wrapped in `L` or `R`. Here the sum is a type
with no values:

```scala
final abstract class Sum[R[_], E[_], X]
infix type +[R[_], E[_]] = [X] =>> Sum[R, E, X]
```

`Empty + Clock + Accounts + Refusal` is only ever written in a signature and compared by the compiler. What
an operation needs, to be told from the operations of other effects, travels beside it as a value:

```scala
enum Place[E[_], R[_], Rest[_]]:
  case Last[R[_], E[_]]() extends Place[E, R + E, R]
  case Init[E[_], R[_], G[_], Rest[_]](s: Place[E, R, Rest]) extends Place[E, R + G, Rest + G]
```

**Why a place is a path.** A row is built by adding effects at the end, so every effect in it is either the
last or somewhere in the row without the last. `Last` says the first; `Init(s)` says the second, and `s` says
where. A place is a number counted from the end, written in unary. The three type parameters are what make
it more than a number: the effect `E`, the row `R` it is a place in, and `Rest`, the row without `E`. A value
`Last()` can have no type but `Place[E, R + E, R]`. So when code *matches* a place and finds `Last`, the
compiler learns what the effect and the row are. It is the mechanism of stage 0's `case ask: Prog.Ask[x, a]`
(§3.4), where a match named an answer's type; here a match on a place names an effect.

An operation of a row is an operation of some effect together with that effect's place, and a program over
a row is a `Free` of those:

```scala
enum At[R[_], X]:
  case Placed[R[_], E[_], X](at: Place[E, R, ?], op: E[X]) extends At[R, X]
```

```scala
infix type ![+A, R[_]] = Free[[X] =>> At[R, X], A]
```

`String ! (Empty + Clock + Accounts)` is chapter 2's `Text ! (Clock + Accounts)`. Nobody writes a place by
hand. `ask` takes it as a `using` parameter, and two `given` definitions let the compiler build it: the last
effect first, and failing that one further in.

**How matching tells the compiler the type.** The handler of the last effect meets an operation at some place
in `R + E`, and asks one question of it:

```scala
def take[E[_], R[_], E2[_]](at: Place[E2, R + E, ?]): Taken[E, E2, R] = at match
  case _: Place.Last[r, e]           => Taken.Mine()
  case i: Place.Init[e2, r, g, rest] => Taken.Theirs(i.s)
```

`Taken.Mine()` has the type `Taken[E, E, R]`: it can only be returned where the operation's effect `E2` *is*
`E`. In the first case the compiler accepts it, because a `Last[r, e]` that is also a `Place[E2, R + E, ?]`
forces `E2 = E`. In the second, `Init` holds a place in `R`, the row without `E`, and that is what `Theirs`
carries. Two lines, and neither has a cast or a test of a class at run time beyond the match itself. The
handler then uses the answer:

```scala
          case pl: At.Placed[r, e, x3] => take[E, R, e](pl.at) match
              case _: Taken.Mine[e2, r2] => eff[x2](pl.op, (x: x2) => handle(b.f(x))(ret, eff))
              case t: Taken.Theirs[e2, e3, r2] =>
                Bind(Suspend(At.Placed(t.at, pl.op)), (x: x2) => handle(b.f(x))(ret, eff))
```

Under `Mine`, `pl.op` is an `E[x2]` and goes to the clause. Under `Theirs`, the operation is asked again at
its place in the smaller row, with the handled rest behind it. Passing on is stage 2's `passOn`, done once in
the interpreter and no longer in every clause, and a clause is never shown an operation of another effect.

`handle`'s type is the sentence of §2.0, `handler H : A ! (E + R) => B ! R`:

```scala
def handle[E[_], R[_], A, B](p: A ! (R + E))(ret: A => B ! R, eff: [X] => (E[X], X => B ! R) => B ! R): B ! R =
```

**Why `run` on the empty row is the check.** `run` has one signature, `def run[A](p: A ! Empty): A`. A program
with an effect still in its row is not an `A ! Empty`, so the call is refused. That is all of "what is not
answered does not happen" that a type can carry. Inside `run` there must still be a case for an operation,
because the tree has a `Suspend` node whatever the row. The file closes it without a failure at run time. A
third place,

```scala
  case Nowhere extends Place[Empty, Empty, Empty]
```

is the only one the compiler will accept at the empty row, and what stands there is an operation of `Empty`,
a type with no values and a method `absurd: Nothing`. So the case is `op.absurd`, which type-checks and can
never be reached. The stage-2 failure, repeated here, does not compile. With two handlers left out, the file
quotes what Scala 3.3.8 says of `run(unanswered)`:

```
Found:    (unanswered : book.path.stage3.Outcome[String] ! book.path.stage3.Empty +
  book.path.stage3.Clock + book.path.stage3.Accounts)
Required: Any ! book.path.stage3.Empty
```

and of `pay` asked for at a row that lacks the accounts:

```
No given instance of type book.path.stage3.In[book.path.stage3.Accounts, book.path.stage3.Empty +
  book.path.stage3.Clock + book.path.stage3.Refusal] was found for parameter x$4 of method pay
```

**The program, and the edge.** The bank is now three effects, each an enumeration that does not mention the
others. `pay` is written for any row that has them somewhere:

```scala
def pay[R[_]](from: String, to: String, amount: Long)(using In[Accounts, R], In[Clock, R], In[Refusal, R])
    : String ! R =
```

Its body is stage 1's, line for line. The row is chosen where the program is run, and each handler takes the
last effect off:

```scala
  type Plain[X] = (Empty + Clock + Accounts + Refusal)[X]
```

```scala
  println("1 " + show(run(fixedClock(42)(rich(refusals(pay[Plain]("ann", "bob", 30)))))))
```

**The journal and the limit.** Both must handle the accounts and ask the accounts again. With a handler that
takes the last effect and gives back a program over the rest, the least that does it is a row that names the
effect twice:

```scala
  type Watched[X] = (Empty + Clock + Accounts + Accounts + Refusal)[X]            // the accounts, twice
```

`pay` asks at the place the compiler finds first, the later of the two; that one is the journal's. The
journal's clause asks at the `Accounts` of the row it returns into, and that one is the bank's. Its signature
says so: `(p: A ! (R + Accounts))(using In[Accounts, R]): A ! R`, and without an `Accounts` further in it
does not compile. Nothing was added to the machinery for this. What it asks of the program is that it be
written for any row, as `pay` is, and of the edge that it choose a row with the accounts twice, in the right
position for each interceptor. A program that already exists as a value, at a row of its own, cannot be
journaled this way at all. Stage 4 takes that up (§3i.5, "Places renamed").

Stage 3 prints the ten lines of the scenario (`same     scala, the path, stage 3: rows`, from `run-all.sh`).

**No cast, and no `@unchecked`.** Stage 0 and the four files contain neither. The library this path ends at marks its
patterns `@unchecked` where an operation is taken out of its place, with a comment on why that is sound.
The stage files write those patterns with every type argument as a variable (`At.Placed[r, e, x3]`), and
Scala 3.3.8 accepts them without the annotation and without a warning.

**What rows cost.** One `Placed` object for each operation. A search by the compiler at each `ask`. Error
messages like the two above, which are exact and not short. Type arguments written out at every handler in
these files (`handle[Accounts, R, A, A](p)(…)`): left to infer them in stage 2, Scala 3.3.8 fixed `B` from
the first clause before it had seen the second, and refused the second. And an order: this `handle` takes
only the last effect, so the handlers at the edge are applied from the end of the row inward.

On the axes: (d), rows as a type-level list in the host language; and (c), a handler found by evidence the
compiler builds, then reached at run time by passing outward one handler at a time, each deciding in constant
time by `take`.

## 3i.5 Stage 4: more nodes, each one forced

Stage 3 is correct and typed. What follows is engineering, and it would be padding if each piece could not
show the run that asked for it. `code/scala/path/stage4.scala` has two mains: `stage4` prints the scenario,
and `forced` runs stage 3's code and its own on the same programs. All the figures below are `forced`'s.

### `Delay`, and `k` as a trampoline

The failing run is stage 2's, which stage 3 inherited:

```
100000 operations, each resumed at once
  stage 3: java.lang.StackOverflowError
  stage 4: 100000
100000 operations, each resumed and its result then used
  stage 3: java.lang.StackOverflowError
  stage 4: (100000,100000)
```

The second pair is a clock that counts on the way back, `k(now).map((n, a) => (n + 1, a))`: the resumption
is not the clause's last act. Both overflow in stage 3, because `k` *runs* the rest. The change is a node and
one word:

```scala
  case Delay[F[_], A](p: () => Free[F, A]) extends Free[F, A]
```

```scala
            case _: Taken.Mine[e2, r2] => eff[y](pl.op, (x: y) => Delay(() => handle(j.k(x))(ret, eff)))
```

`k` no longer runs anything. It returns a node that says how to go on. The clause returns that node — or a
program built around it, `Bind(Delay(…), f)` for the counting clock — to whoever called `handle`. That caller
is an interpreter's loop, and the loop has a case for `Delay`: build `d.p()` and go round. So the rest
is run from the loop's own frame, not from inside the clause. A frame of the host stack for each operation
has become a node on the heap for each operation; the stack no longer grows with the program, and
the length of the program is paid for in garbage. That is all "trampoline" means.

It is not free:

```
bytes allocated for each operation, passing through 0, 1, 2 handlers of other effects
  stage 3: 88, 192, 296
  stage 4: 120, 192, 264
```

The first column is an operation answered by the nearest handler: 32 bytes more with the trampoline. (How
these are taken: the JVM's own count of bytes allocated by the thread, read before and after one run of
1,000 operations, after 300 runs to let the compiler settle. It is not a timing, and it is what was
allocated after the compiler's own removals, not a count of `new` in the source.)

### `Inject`: an operation with its rest, one node

The other columns of that table are the reason for the second node. An operation that passes through a
handler of another effect is re-made on the other side: in stage 3 as `Bind(Suspend(placed), k)`, three
objects and a closure. Every interpreter then takes that pair apart again to find the operation. One node
says the same:

```scala
  case Inject[F[_], X, +A](op: F[X], k: X => Free[F, A]) extends Free[F, A]
```

and `flatMap` makes it directly when it is given an operation:

```scala
  def flatMap[B](f: A => Free[F, B]): Free[F, B] = this match
    case Suspend(op) => Inject(op, f)
    case _           => Bind(this, f)
```

Measured: each handler an operation passes through costs 104 bytes in stage 3 (192 − 88, 296 − 192) and 72
in stage 4. With one handler on the way the two stages are level, the saving having paid for the trampoline;
with two, stage 4 is ahead. I measured allocation only. That it is also one test fewer for each interpreter
is read from the code.

`Inject` is chapter 3's `Ask(op, rest)`. With it, stage 4 can say what every interpreter needs first:

```scala
@tailrec def head[F[_], A](p: Free[F, A]): Return[F, A] | Inject[F, ?, A] = p match
```

*The program brought to its head: finished with a value, or an operation with its rest.* Its body is stage
1's loop with a case for each new node. Its result type is the two nodes of chapter 3. So the five-node tree
is chapter 3's two-node tree, built lazily: `Bind`, `Suspend` and `Delay` exist so that building a program
and resuming one cost a node and not a call, and `head` is where they are worked off, in constant stack. The
handlers are then short, because none of them loops over binds any more. (My library writes this loop out
inside each of its interpreters. The stage shares it, which reads better and is not measured against the
other.)

### A handler with a state of its own

Stage 3's bank kept its balances in a variable of the handler. A rest resumed twice then sees one bank:

```
ann has 100; move 30, or move 50; what is left in each future
  balances in a variable of the handler: 70, 20
  balances as the handler's state:       70, 50
```

The second future started from what the first had left. Chapter 3b ("Where a handler's state lives") said a
design must choose; this is the run that makes the choice visible. The second line is a handler whose clause
is given the state and resumes with the answer *and the state to go on from*:

```scala
def handle[E[_], R[_], A, B, S](p: A ! (R + E), s: S)(ret: (S, A) => B ! R,
    eff: [X] => (S, E[X], (X, S) => B ! R) => B ! R): B ! R =
```

```scala
              eff[y](s, pl.op, (x: y, s1: S) => Delay(() => handle(j.k(x), s1)(ret, eff)))
```

The state is a value in the closure of each `k`. A `k` called twice starts twice from the same `s`. The
fifth type parameter, `S`, is the only new thing. The bank becomes

```scala
          case Balance(a)    => k(s.getOrElse(a, 0L), s)
          case Move(f, t, n) => k((), s.updated(f, s(f) - n).updated(t, s.getOrElse(t, 0L) + n)))
```

and returns the balances with the result, so stage 4's scenario has no variable in it anywhere. The journal
is the same kind, its state the lines so far. This is axis (e)'s question — does each run see the state as it
was at the question, or as the previous run left it — answered "as it was" for this kind of handler. When
the other answer is wanted, it is had by the order of the handlers, not by a variable.

### A handler that answers in place

The clock never needed the rest of the program. Neither does any handler whose clause would be "work out a
value, resume once with it, and do nothing after". For those, stage 1's handler comes back, as one layer
among typed layers:

```scala
def answer[E[_], R[_], A](p: A ! (R + E))(ans: [X] => E[X] => X): A ! R = answering(p, ans)
```

```scala
          case _: Taken.Mine[e2, r2]       => answering(j.k(ans(pl.op)), ans)
```

No `k` is made and no `Delay`; the loop goes on with the answer itself.

```
bytes allocated for each operation of a clock: with k 120, in place 88
1000000 operations answered in place, no Delay: counted 1000000
```

The 32 bytes the trampoline cost are back. Chapter 3a (axis h) called this the single most valuable
optimisation and said it is open under every representation; here it is, seven lines. What such a handler
cannot do is everything stage 2 was for: it cannot refuse, resume twice, ask another question, or look at
what the rest ended in. The refusal *needs* `k`, in order not to call it. So a library offers both, and the
author of a handler picks the weakest that does the job.

### Places renamed: an interceptor in front of a program already written

Stage 3 got its journal and its limit from two things together: a `pay` written for any row, and a row at
the edge that names the accounts twice. Take away the first — a program that exists as a value, with its
row — and the journal cannot be applied. The file quotes what Scala 3.3.8 says, in stage 3, of
`Journal()(refusals(written))` where `written` is a `String ! (Empty + Clock + Accounts + Refusal)`:

```
No given instance of type book.path.stage3.In[book.path.stage3.Accounts,
  [X] =>>
    book.path.stage3.Sum[book.path.stage3.Empty, book.path.stage3.Clock, X]
] was found for parameter x$2 of method apply in class Journal.
```

The journal handles the last `Accounts` and asks the accounts further in, and further in than this program's
accounts there are none. That is a real restriction and not a matter of style. A program handed over by other
code has its row. So has the rest `k` of a program, and so has anything stored. And the capability at stake
is the one chapter 10 is built on: putting an observer or a policy in front of an effect *without the
program's type, or the program, changing*.

What is needed is the program's operations of `Accounts` moved to a second, later `Accounts` in a longer
row, and every other operation left at its effect. That is a renaming of places:

```scala
trait Renaming[R1[_], R2[_]]:
  def apply[E[_]](at: Place[E, R1, ?]): Place[E, R2, ?]
```

and the one renaming an interceptor wants sends `E`'s place to the new end and every other place one
further in:

```scala
def interposing[E[_], R[_]](at: Place[E, R, ?]): Renaming[R, R + E] = new Renaming[R, R + E]:
  private val last = Place.Last[R, E]()
  def apply[E2[_]](p: Place[E2, R, ?]): Place[E2, R + E, ?] = same(at, p) match
    case _: Same.Yes[e]     => last
    case _: Same.No[e, e2]  => Place.Init(p)
```

`same` compares two places of one row, a step at a time from the end, and is the third use of the mechanism
of §3i.4: its answer `Yes` has the type `Same[E, E]`, so returning `last`, a place of `E`, where a place of
`E2` is wanted is accepted only under it. `remap` applies a renaming to a program:

```scala
def remap[R1[_], R2[_], A](p: A ! R1, rename: Renaming[R1, R2]): A ! R2 = head(p) match
  case Return(a) => Return(a)
  case j: Inject[f, y, a] => j.op match
      case pl: At.Placed[r, e, x] => Inject(At.Placed(rename(pl.at), pl.op), (x: y) => remap(j.k(x), rename))
```

It walks the tree as the tree is run: the head is found, its operation given its new place, and the rest
renamed when it is resumed. Nothing of the program runs or changes but where each question is addressed. An
interceptor is then a handler of the last effect applied to `interposed(p)`, which is `remap` with
`interposing` behind a `Delay`. The journal's type becomes

```scala
def journal[R[_], A](p: A ! R)(using In[Accounts, R]): (Vector[String], A) ! R =
```

a program over `R` in, a program over `R` out. Stage 4's `pay` is written once, at one row,

```scala
def pay(from: String, to: String, amount: Long): String ! Bank =
```

and lines 3, 4 and 5 of the scenario are that `pay` with the journal or the limit put in front. `forced` puts
one value under a limit and two journals:

```
a program written once at its row, under a limit and two journals put in front of it
  paid: moved 30 from ann to bob at 42; the journals have 2 and 2 lines
```

The cost is one more walk. The same interceptor over the same 1,000 operations, reached the two ways:

```
bytes allocated for each operation under an interceptor: the row naming the effect twice 264, the places renamed 336
```

72 bytes for each operation, which is what stage 4 paid for an operation passing a handler of another
effect: `remap` makes a new `Inject`, a new `Placed` and a closure for the rest, each time. Stage 3's way
pays nothing at run time, because the compiler placed the program's operations where the interceptor wanted
them. A library that has both lets a program written for any row take the cheap road.

`same` has two fall-through cases, `case _ => Same.No()`, that Scala 3.3.8's checker asks for and that can
never be wrong: `No` claims nothing. No `@unchecked` was needed.

### What re-association costs, measured

```
bytes allocated for each of 1000, 2000, 4000 binds nested to the left: 365, 366, 367
```

Under a handler, with every bind asking once. The cost of a bind does not grow with the length of the chain.

## 3i.6 Why each part is there

| part of the last file | why it exists | forced in |
| --- | --- | --- |
| a value, or a question with its rest | the idea: stage 0's `Done` and `Ask`, here `Return` and `Inject` | stage 0 |
| a type pattern that names a hidden type | the rest is given an answer of its question's type, uncast | stage 0 |
| `Free[F[_], +A]` | a program over a vocabulary; a refusal fits anywhere | stage 1 |
| `Return`, `Suspend` | a value; one question | stage 1 |
| `Bind` | sequencing costs a node, so only the interpreter needs a stack | stage 1 |
| re-association | a bind on the left of a bind is brought on top without a frame | stage 1 |
| `eff`'s second argument, `k` | the handler decides whether and how often the rest runs | stage 2 |
| `B` beside `A` | a handler may change what a program ends in | stage 2 |
| `R + E`, a type with no values | the effects still askable, compared by the compiler | stage 3 |
| `Place[E, R, Rest]` | where an effect stands; matching it gives the compiler the types | stage 3 |
| `At.Placed` | the row has no operations, so each carries its place | stage 3 |
| `take`, `Taken` | mine or theirs, decided once, in the interpreter | stage 3 |
| `run` on `A ! Empty` only; `Nowhere` | an unanswered question does not compile | stage 3 |
| `Delay` | `k` returns "go on from here" and the loop goes on | stage 4 |
| `Inject` | a passed-on operation is one node; a program has a head | stage 4 |
| `S` in `handle(p, s)` | each resumption has the state of its time | stage 4 |
| `answer` | a handler that does not need `k` does not pay for one | stage 4 |
| `Renaming`, `remap`, `same` | a program already written is re-addressed, not rewritten | stage 4 |
| `interposing`, `interposed` | an interceptor in front of an effect that stays; the type unchanged | stage 4 |

## 3i.7 What the other families do at each step

A sentence each; the chapters named have the rest.

- **The stack (stage 1).** Direct style has no interpreter and so no such problem: the host's stack is the
  program's (chapter 3d). A state machine has no stack to grow (3e). A final encoding inherits whatever its
  chosen monad's bind does (3c). A machine keeps the binds as frames in a list of its own instead of
  re-associating them (3b, "From a tree to a machine").
- **The rest (stage 2).** Direct style is given it by the runtime, as a suspended generator, thread or fibre,
  and for that reason has one resumption (3d). A state machine has it as a value that can be copied (3e). A
  language with handlers hands the clause `resume` (3f). Capability passing has none and stays at stage 1 on
  purpose (3c).
- **Rows (stage 3).** Haskell and Lean get the match that refines a type from the language, and build rows
  with type classes or not at all (3b). Tagless final writes the row as constraints, and context parameters
  as a list of things in scope (3c). Koka infers it (3f). OCaml 5 has none, deliberately (3a, axis d).
- **The engineering (stage 4).** Answering in place is every family's first optimisation (3a, axis h). The
  trampoline and `Inject` are this family's own debts; a machine pays the first with an explicit stack, and
  direct style and state machines never incur either. A handler's own state as a value is what the pure
  renderings were made to do from the start (3b). An interceptor needs no renaming where a handler is found
  at run time by looking outward from where the question is asked: a handler that asks its own effect again
  reaches the next one out (3a, axis c). Renaming is the price of having found the handler in the types.

## 3i.8 The end point

The path began at stage 0 with 38 lines. Stage 4 is 487 with its bank, its failing runs and its
measurements. `Free.scala` in okay-simple is
282 lines of the interpreter alone, and has what stage 4 leaves out: an effect handled from any place in the
row and not only the last; a row of one effect, and the empty effect placed in every row; two more renamings
than the interceptor's, by which it moves an effect to the end of its row and embeds a program in a larger
row; an in-place handler with a state of its own; and an interface, shared with a
machine, through which the same programs run on either. The comment that closes `stage4.scala` lists them by
name, so that a reader can open the library and find the stage in it. Its names differ in one place: this
chapter's `Suspend` is `Perform` there.

None of it changes what stage 2 established. A program is data; a handler is given the rest. Everything
after that was either telling the compiler what stage 2 already did, or paying less for it.

## What was run

| claim | file, main | prints |
| --- | --- | --- |
| §3i.2 | `scala/path/stage1.scala`, `book.path.stage1.stage1` | ends `stage 1 holds` |
| §3i.3 | `scala/path/stage2.scala`, `book.path.stage2.stage2` | ends `stage 2 holds` |
| §3i.4 | `scala/path/stage3.scala`, `book.path.stage3.stage3` | `code/expected.txt` |
| §3i.5, the scenario | `scala/path/stage4.scala`, `book.path.stage4.stage4` | `code/expected.txt` |
| §3i.5, every failing run and figure | `scala/path/stage4.scala`, `book.path.stage4.forced` | ends `stage 4 holds` |

Stage 0 is `scala/kernel.scala`, checked by `book.bank` (the scenario) and `book.examples`, as chapter 3 says.
The two compiler messages of §3i.4 and the one of §3i.5 were produced by compiling the lines the files quote
in comments; they are not checked by `run-all.sh`. The figure for `remap` was the same in three runs.
The figures in bytes are from one machine (an Apple arm64 laptop, Temurin 26.0.2)
and were the same in each of the runs I made (five of the first table, three of the last line); `forced`
asserts only their order, not their values. What rests on memory and not on a run: the attributions in
"What is known about left-nested binds", and the sentences of §3i.7 about other families, which repeat what
chapters 3a to 3f say. What rests on reading and not on a run: what is said of `Free.scala` in §3i.4 and
§3i.8, and that chapter 3's `flatMap` pays again at every question.
