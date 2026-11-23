# 3 · One rendering, built in full: forty lines

Chapter 2 described five notions in a notation of its own. This chapter builds them once, in one language, by
one technique, so that every later claim in the book can be checked against code you have read in full. The
result is not a library. It is the smallest thing that is honestly "effects and handlers".

**It is one rendering among several, and it is stage 0 of one path.** As a rendering it is the first of
sixteen, and no more the reference than the other fifteen. As stage 0 it is where the one implementation that
this book grows in full begins: chapter 3i takes this tree through four more stages, to the shape of a
library. The language is Scala 3, chosen because the result is short. The technique is the most literal
one: a program is a tree of two cases, and the rest of the program is a function. Those are the two halves
of chapter 2b, each in its plainest material: an algebraic
data type, and a closure. Chapter 2's pair "a question ; the rest" is here a data structure you can hold. Section
3.9 lists the fifteen other renderings of the same bank under `code/`, in twelve more languages and by five
other techniques, and Part III (chapters 3a to 3h) is about what each technique gives and costs. A reader who does not
read Scala can follow the same construction in `code/java/Tree.java`, `code/haskell/Bank.hs` or
`code/lean/Bank.lean`, which are this chapter in those languages.

It is in `code/scala/kernel.scala`; the examples of chapter 2 are in `code/scala/examples.scala`, with
assertions. `scala-cli run code/scala --main-class book.examples` prints `all examples hold`.

## 3.1 The operation

```scala
trait Op[A]
```

One line. An operation is a value that names the type `A` of its answer. Every question in the book is a case
class extending this.

## 3.2 The program

```scala
enum Prog[+A]:
  case Done(a: A)
  case Ask[X, A](op: Op[X], rest: X => Prog[A]) extends Prog[A]
```

A program that ends with an `A` is either `Done(a)`, or `Ask(op, rest)`: a question whose answer has some type
`X`, and a function from that answer to the next program.

That is the entire data structure. There is no third case. Everything a program can ever be is a chain of `Ask`
ending in `Done`.

To write programs comfortably we need to put one after another:

```scala
  def flatMap[B](f: A => Prog[B]): Prog[B] = this match
    case Done(a)       => f(a)
    case Ask(op, rest) => Ask(op, x => rest(x).flatMap(f))
  def map[B](f: A => B): Prog[B] = flatMap(a => Done(f(a)))
```

`flatMap` says: when this program is done with `a`, go on with `f(a)`. If it is already done, that is `f(a)`. If
it is a question, it is the *same question*, and its rest is the old rest followed by `f`. Nothing is run; a
longer chain is built. (That second case is the property chapter 2b called *algebraic*, as one line of code:
whatever follows a question moves inside it.)

`flatMap` and `map` are what chapter 2's lines `x <- …` are written with. Scala has a notation for a chain of
them, `for x <- … yield …`, and the listings below use it; these two methods are what it means. (A rendering in
direct style needs neither: there the host language's own sequencing is the chain. Chapter 3d.)

Two small helpers:

```scala
def ask[X](op: Op[X]): Prog[X] = Prog.Ask(op, Prog.Done(_))
def done[A](a: A): Prog[A] = Prog.Done(a)
```

`ask(op)` is the shortest program that asks: one question, done with its answer.

## 3.3 The handler

```scala
trait Handler[A, B]:
  def done(a: A): Prog[B]
  def on[X](op: Op[X], rest: X => Prog[B]): Option[Prog[B]]
```

A handler turns a program ending in `A` into a program ending in `B`. It says what to do at the end (`done`) and
what to do with a question (`on`), given the question and the rest of the program. `None` means: not mine.

Note the type of `rest` in `on`: `X => Prog[B]`, not `X => Prog[A]`. The rest that the handler receives is
*already handled* — it is the remainder of the program with this same handler applied to it. That is what lets
`Refusals` return `Either` for the whole computation, and `AllChoices` return a `List`.

## 3.4 Applying a handler

```scala
def handle[A, B](h: Handler[A, B])(p: Prog[A]): Prog[B] = p match
  case Prog.Done(a) => h.done(a)
  case ask: Prog.Ask[x, a] =>
    val handled = (answer: x) => handle(h)(ask.rest(answer))
    h.on(ask.op, handled).getOrElse(Prog.Ask(ask.op, handled))
```

Walk the chain. At the end, call `done`. At a question, first build `handled`: the rest of the program with this
handler applied. Then offer the question to the handler. If it takes it, the result is whatever it decided. If it
does not (`None`), the question stays a question — now with the handled rest behind it — and goes on outward to
the next handler.

Three lines do all the work, and they are worth reading twice, because they contain the three behaviours of
chapter 2:

- the handler calls `handled(x)`: the program continues with answer `x`;
- the handler ignores `handled`: the program is abandoned at this point;
- the handler calls `handled` several times: the program is continued down several paths.

The pattern deserves a word. `Ask` hides the answer type `X` of its question: a `Prog[A]` does not say what
its first question is answered with. The pattern `ask: Prog.Ask[x, a]` gives that hidden type a name for the
length of the case. A lower-case `x` in a type pattern is a variable the match binds: "whatever this node's
question is answered with, call it `x`". From there the compiler knows that `ask.op` is an `Op[x]` and that
`ask.rest` takes an `x`, so `handled` is a function from `x`, and a line that passed the rest an answer of
another type would not compile. There is no cast in the kernel. The handlers of section 3.6 lean on the same
thing from the other side: inside `case Balance(a)` the compiler has learnt that this question's `X` is
`Long`, and `rest` takes a number. Without it one would say `Any` and cast, as the Java rendering does, once;
the Haskell and Lean renderings need none, because there too a match on an operation tells the compiler the
type of its answer (chapter 3b). This is the reader's first meeting with a mechanism the book uses again: in
chapter 3i a match on *where an effect stands in a row* tells the compiler which handler an operation
belongs to (§3i.4).

What these types still do not say is *whose* question it is. Every handler is offered every operation and
ends in `case _ => None`, and nothing stops a program from being run with a question nobody answers. That is
what a library adds with rows. Forty lines buy the idea, not that safety.

## 3.5 Running

```scala
def run[A](p: Prog[A]): A = p match
  case Prog.Done(a)    => a
  case Prog.Ask(op, _) => sys.error(s"nobody answers $op")
```

A program with no question left has a value. A program still asking something has none, and `run` says which
question nobody answered.

That is the whole kernel: one trait, one two-case type with two methods, two helpers, one trait, one function,
one function.

## 3.6 It runs

First the bank of chapter 2, written on these lines. The operations, each naming its answer:

```scala
case class Balance(account: String) extends Op[Long]
case class Move(from: String, to: String, amount: Long) extends Op[Unit]
case object Now extends Op[Long]
case class Refuse(why: String) extends Op[Nothing]
```

The program, which is chapter 2's `pay` line for line:

```scala
def pay(from: String, to: String, amount: Long): Prog[String] =
  for
    have <- ask(Balance(from))
    _    <- if have < amount then ask(Refuse(s"$from has $have, needs $amount")) else done(())
    _    <- ask(Move(from, to, amount))
    at   <- ask(Now)
  yield s"moved $amount from $from to $to at $at"
```

Three of its handlers: one that continues the rest once, one that does not continue it, one that asks the
question again and writes down the answer.

```scala
final class InMemory(var balances: Map[String, Long]) extends Handler[String, String]:
  def done(a: String) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[String]) = op match
    case Balance(a) => Some(rest(balances.getOrElse(a, 0L)))
    case Move(f, t, n) =>
      balances = balances.updated(f, balances(f) - n).updated(t, balances.getOrElse(t, 0L) + n)
      Some(rest(()))
    case _ => None

final class Refusals[A] extends Handler[A, Either[String, A]]:
  def done(a: A) = Prog.Done(Right(a))
  def on[X](op: Op[X], rest: X => Prog[Either[String, A]]) = op match
    case Refuse(why) => Some(Prog.Done(Left(why)))
    case _           => None

final class Journal[A](var lines: Vector[String] = Vector.empty) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case _: Balance | _: Move =>
      Some(Prog.Ask(op, (answer: X) => { lines :+= s"$op -> $answer"; rest(answer) }))
    case _ => None
```

Chapter 2's `paid(a) | refused(why)` is Scala's `Either`: `Right(a)` and `Left(why)`. `p under h` is
`handle(h)(p)`, so a stack of handlers reads from the inside out. `FixedClock`, `Choose` and `AllChoices` are
in the file and have the same shape.

From `code/scala/examples.scala`, the assertions that hold:

```scala
val bank = InMemory(Map("ann" -> 100L))
val ok = run(handle(Refusals[String]())(handle(FixedClock[String](42))(handle(bank)(pay("ann", "bob", 30)))))
assert(ok == Right("moved 30 from ann to bob at 42"))
assert(bank.balances == Map("ann" -> 70L, "bob" -> 30L))
```

The same program, an account too small:

```scala
val poor = InMemory(Map("ann" -> 10L))
val no = run(handle(Refusals[String]())(handle(FixedClock[String](42))(handle(poor)(pay("ann", "bob", 30)))))
assert(no == Left("ann has 10, needs 30"))
assert(poor.balances == Map("ann" -> 10L))        // no money moved
```

The second assertion is the one to look at. After the refusal, `Move` was never asked. Not "asked and rolled
back": the program stopped at `Refuse`, the handler did not continue it, and the lines after it did not exist as
far as the world is concerned.

The same program under a journal it knows nothing of:

```scala
run(handle(Refusals[String]())(handle(FixedClock[String](42))(handle(bank2)(handle(journal)(pay("ann", "bob", 30))))))
assert(journal.lines == Vector("Balance(ann) -> 100", "Move(ann,bob,30) -> ()"))
```

A program with a question nobody answers:

```scala
val forgotten = handle(FixedClock[String](42))(pay("ann", "bob", 30))
assert(scala.util.Try(run(forgotten)).isFailure)       // nobody answers Balance(ann)
```

And many resumptions:

```scala
val split = for a <- ask(Choose(List(10, 20))); b <- ask(Choose(List(1, 2))) yield (a, b)
assert(run(handle(AllChoices[(Int, Int)]())(split)) == List((10, 1), (10, 2), (20, 1), (20, 2)))
```

## 3.7 What forty lines do not give you

It would be dishonest to stop here and let the reader think a production library is this plus polish. Four
things are missing, and each is a real piece of engineering.

**The stack.** `flatMap` and `handle` are recursive functions. A program of a million steps builds a million
nested calls and the stack overflows. A library on this road is a **loop**: it keeps what is waiting — the frames, the
handlers, their state — in a data structure on the heap, and steps through it. This is the same move as
replacing recursion with an explicit stack, and it is what makes long programs, endless streams and mutual
recursion run in constant stack. The loop is usually called a *machine*; chapter 13 gives its measured cost.

**The types.** Our `run` discovers an unanswered question when it meets one. A library puts the row of
effects in the program's type, so the discovery happens at compile time, and so that each question reaches the
handler of *its* effect without a `match … case _ => None`. (The kernel has no cast to remove: section 3.4.)

**Speed.** Every step here allocates a closure and re-wraps the chain. An answer that could come straight from a
handler's own state — a counter, a configuration value — should not capture or rebuild anything. Libraries
distinguish a handler that answers *in place* from one that needs the rest of the program, and the first kind
costs a few nanoseconds. Chapter 13 has the numbers.

**Leaving properly.** When a handler does not continue the rest, anything the rest was holding — an open file, a
lock — must still be released. That needs a notion of a finalizer that runs when a program is left early.
Forty lines have none. Chapter 6 says exactly which ways of leaving the libraries I have read cover, and which
they do not.

None of these changes the idea. The data structure is still "done, or a question with the rest"; a handler still
receives the rest and decides. What changes is that the rest becomes an efficient, immutable, typed value instead
of a chain of closures.

These four are the debts of *this* technique. Another technique starts with other debts. Direct style on a
generator or a thread has no stack problem and no closure per step, because the host keeps the stack; it owes
the second resumption instead. A compiled state machine owes nothing at run time and a great deal to whoever
writes the compiler or the macro. Chapter 3a sets these out as axes, so that the list above is read as one
column of a table and not as the definition of the subject. Chapter 3i pays the first two of them for this
technique, a step at a time, with these lines as its stage 0: the same tree made stack-safe, then given rows.

## 3.8 Why build it at all

Because from here on the book makes claims — that this mechanism subsumes dependency injection, exceptions,
generators, middleware, workflow engines; that it gives control which wrappers cannot — and a reader who has
only been *told* what a handler is has to take those on trust.

You have now read every line of one. When a later chapter says "the handler simply does not continue the rest",
you know that this is a function not being called, and nothing more mysterious.

## 3.9 The same bank, fifteen more times

Under `code/` the bank of chapter 2 is written sixteen times. Each prints the same ten lines for the same scenario
(`code/expected.txt`): the payment; the refusal with no money moved; the journal; a limit that refuses a large
move before the bank sees it; the proof that nothing was asked after a refusal; and the rest of the program
resumed twice with two supposed balances. `sh code/run-all.sh` runs them and compares.

| rendering | a program is | the rest is | resumed twice |
| --- | --- | --- | --- |
| `scala/bank.scala` (this chapter) | a tree | a closure | yes |
| `java/Tree.java` | a tree | a closure | yes |
| `haskell/Bank.hs` | a tree, typed by a GADT | a closure | yes |
| `lean/Bank.lean` | a tree, typed and total | a closure | yes |
| `haskell/Tagless.hs` | a function over an abstract monad | what that monad's bind makes of it | yes: the list monad |
| `scala/capabilities.scala` | plain code with context parameters | the host's stack | no |
| `typescript/bank.ts` | a generator | the generator object | no |
| `python/bank.py` | a generator | the generator object | no |
| `java/Loom.java` | plain code on a virtual thread | a parked thread | no |
| `go/bank.go` | plain code on a goroutine | a blocked goroutine | no |
| `kotlin/bank.kt` | `suspend` functions | the compiler's continuation object | no |
| `csharp/bank.cs` | an iterator method | the compiler's state machine object | no |
| `ocaml/bank.ml` | plain code; handlers are in the language | a fibre | no |
| `rust/bank.rs` | a state machine, an enum, by hand | a value of that enum | yes, by `clone` |
| `zig/bank.zig` | a state machine, a union, by hand | a value of that union | yes, by copy |
| `koka/bank.kk` | plain code; handlers and rows are in the language | `resume`, given by the language | yes |

The last column is not a ranking. The eight that say no are renderings in which `pay` reads as ordinary code
of its language, running on a stack or a state machine that the runtime or the compiler owns; reading as
ordinary code is what they bought. Koka is the one that has both, and it has them because the language was
built for it. The next part is about that exchange and the others like it.

The first row is counted once, as a rendering. It is also stage 0 of the path of chapter 3i, whose stages 3
and 4 print the same ten lines and are run by the same script; they are later states of this one row, not
further renderings.
