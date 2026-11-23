// THE PATH, STAGE 1 (chapter 3i): the simplest free monad, with a trampoline.
// A program is a tree of three nodes; one interpreter walks it in a loop and asks an `answer` function for
// each operation. This is the data half of chapter 2b alone: the handler answers, once, at once.
// `scala-cli run code/scala --main-class book.path.stage1.stage1`
package book.path.stage1

import scala.annotation.tailrec

/** a program over the operations `F`, ending in an `A` */
enum Free[F[_], +A]:
  /** finished, with a value */
  case Return[F[_], A](a: A) extends Free[F, A]
  /** one operation; the program ends in its answer */
  case Suspend[F[_], A](op: F[A]) extends Free[F, A]
  /** `m`, then `f` of what `m` ends in. A NODE, not a call: `flatMap` builds it and does nothing else, so
   * putting one program after another costs one allocation however long either is, and never recurses. The
   * work of walking moves to the interpreter, which is then the only code that needs a stack — and can do
   * without one */
  case Bind[F[_], X, A](m: Free[F, X], f: X => Free[F, A]) extends Free[F, A]

  def flatMap[B](f: A => Free[F, B]): Free[F, B] = Bind(this, f)
  def map[B](f: A => B): Free[F, B] = Bind(this, a => Return(f(a)))

import Free.{Bind, Return, Suspend}

/** THE INTERPRETER: a loop. `answer` says what each operation's answer is.
 *
 * Every case either finishes or goes round again with a smaller problem, so `@tailrec` compiles it to a jump
 * and the host stack does not grow. The third inner case is the one that needs thought: a bind whose left
 * side is itself a bind, `(m >>= g) >>= f`. Walking into `m >>= g` first would be a call that has to come
 * back to apply `f` — a stack frame per level of nesting. Instead the two binds are RE-ASSOCIATED to
 * `m >>= (y => g(y) >>= f)`: the same program by the monad's associativity law, with `m` now on top. One new
 * node and one closure per nested bind, on the heap; `f` waits inside the closure and not on the stack */
@tailrec def run[F[_], A](p: Free[F, A])(answer: [X] => F[X] => X): A = p match
  case Return(a)   => a
  case Suspend(op) => answer(op)
  case b: Bind[f, x, a] => b.m match
      case Return(x)          => run(b.f(x))(answer)
      case Suspend(op)        => run(b.f(answer(op)))(answer)
      case m: Bind[f2, y, x2] => run(Bind(m.m, (y: y) => Bind(m.f(y), b.f)))(answer)

/** THE SAME TREE, WALKED THE OBVIOUS WAY: to run a bind, run its left side, then the rest. The inner call is
 * not in tail position, so each bind nested to the left is a frame of the host stack */
def naive[F[_], A](p: Free[F, A])(answer: [X] => F[X] => X): A = p match
  case Return(a)        => a
  case Suspend(op)      => answer(op)
  case b: Bind[f, x, a] => naive(b.f(naive(b.m)(answer)))(answer)

// ---- the bank, as ONE effect: an enumeration of its operations, each naming the type of its answer

enum Bank[A]:
  case Balance(account: String) extends Bank[Long]
  case Move(from: String, to: String, amount: Long) extends Bank[Unit]
  case Now extends Bank[Long]
  case Refuse(why: String) extends Bank[Nothing]

import Bank.{Balance, Move, Now, Refuse}

def ask[A](op: Bank[A]): Free[Bank, A] = Suspend(op)

def pay(from: String, to: String, amount: Long): Free[Bank, String] =
  for
    have <- ask(Balance(from))
    _    <- if have < amount then ask(Refuse(s"$from has $have, needs $amount")) else Return[Bank, Unit](())
    _    <- ask(Move(from, to, amount))
    at   <- ask(Now)
  yield s"moved $amount from $from to $to at $at"

/** `Refuse` has no answer to give back, and an `answer` function must return one. The only way out of it
 * that does not continue the program is one the HOST provides: an exception */
final class Refused(val why: String) extends Exception(why)

/** a bank in memory and a fixed clock: every operation answered in place, from the handler's own state */
final class InMemory(var balances: Map[String, Long], now: Long):
  val answer: [X] => Bank[X] => X = [X] =>
    (op: Bank[X]) =>
      op match
        case Balance(a) => balances.getOrElse(a, 0L)
        case Move(f, t, n) =>
          balances = balances.updated(f, balances(f) - n).updated(t, balances.getOrElse(t, 0L) + n)
        case Now         => now
        case Refuse(why) => throw Refused(why)
  def show = s"ann=${balances.getOrElse("ann", 0L)} bob=${balances.getOrElse("bob", 0L)}"

/** what CAN be put around an answer: another answer. A wrapper sees the question and the answer, and can
 * refuse by throwing; it is handed nothing of the program */
final class Journal(inner: [X] => Bank[X] => X, var lines: Vector[String] = Vector.empty):
  val answer: [X] => Bank[X] => X = [X] =>
    (op: Bank[X]) =>
      op match
        case _: (Balance | Move) => val x = inner(op); lines :+= s"$op -> $x"; x
        case _                   => inner(op)

/** a wrapper that notes how deep the host stack is where every 100,000th operation is answered */
final class Depth(inner: [X] => Bank[X] => X, var asked: Int = 0, var seen: Set[Int] = Set.empty):
  val answer: [X] => Bank[X] => X = [X] =>
    (op: Bank[X]) =>
      if asked % 100_000 == 0 then seen += Thread.currentThread.getStackTrace.length
      asked += 1
      inner(op)

/** a million operations, each bound to the LEFT of the next: `((((0 >>= f) >>= f) >>= f) …)` */
def leftNested(n: Int): Free[Bank, Int] =
  (1 to n).foldLeft(Return(0): Free[Bank, Int])((p, _) => p.flatMap(i => ask(Now).map(_ => i + 1)))

/** a million operations, each bound to the RIGHT: `Now >>= (_ => Now >>= (_ => …))`, built as it is run */
def rightNested(n: Int, done: Int = 0): Free[Bank, Int] =
  if n == 0 then Return(done) else ask(Now).flatMap(_ => rightNested(n - 1, done + 1))

@main def stage1(): Unit =
  // the payment runs
  val rich = InMemory(Map("ann" -> 100L), 42)
  val paid = run(pay("ann", "bob", 30))(rich.answer)
  println(s"paid: $paid")
  println(s"balances: ${rich.show}")
  assert(paid == "moved 30 from ann to bob at 42" && rich.balances == Map("ann" -> 70L, "bob" -> 30L))

  // a million binds, nested either way, in constant stack
  val million = 1_000_000
  val clock = InMemory(Map.empty, 42).answer
  def constant(name: String, p: Free[Bank, Int]): Unit =
    val depth = Depth(clock)
    val counted = run(p)(depth.answer)
    println(s"$million binds nested to the $name: counted $counted; " +
      s"the stack was ${depth.seen.mkString(" or ")} frames deep at each of ${depth.asked / 100_000} samples")
    assert(counted == million && depth.seen.size == 1, depth.seen)
  constant("left", leftNested(million))
  constant("right", rightNested(million))

  // the naive interpreter, on the same two programs
  def attempt(name: String, p: Free[Bank, Int]): String =
    val outcome =
      try s"counted ${naive(p)(clock)}"
      catch case e: StackOverflowError => e.getClass.getName
    println(s"the naive interpreter, $name: $outcome")
    outcome
  assert(attempt("nested to the left", leftNested(million)) == "java.lang.StackOverflowError")
  // nested to the right the naive one survives, and for a reason that is the compiler's: its outer call IS
  // in tail position, and Scala turns a self-call in tail position into a jump with or without `@tailrec`
  assert(attempt("nested to the right", rightNested(million)) == s"counted $million")

  // WHAT THIS STAGE CANNOT DO. A handler here is a function from a question to its answer: it answers,
  // once, at once. It is never handed the rest of the program.

  // scenario lines 2 and 5, a refusal: only by an exception thrown THROUGH the interpreter. It works, and it
  // is the host's mechanism, not ours: nothing between the program and the edge can see it, record it or
  // turn it into a value except by `try`
  val poor = InMemory(Map("ann" -> 10L), 42)
  val refused =
    try run(pay("ann", "bob", 30))(poor.answer)
    catch case r: Refused => s"refused: ${r.why}"
  println(s"$refused -- by an exception of the host; balances: ${poor.show}")
  assert(refused == "refused: ann has 10, needs 30" && poor.balances == Map("ann" -> 10L))

  // scenario lines 3 and 4, a journal and a limit: possible, as a WRAPPER of the answer function. It sees
  // each question and its answer. It cannot ask a different question of "whoever is outside" as a program
  // would, because there is no program here to ask with: it can only call the function it wraps
  val journal = Journal(InMemory(Map("ann" -> 100L), 42).answer)
  run(pay("ann", "bob", 30))(journal.answer)
  println(s"journal, as a wrapper of the answer: ${journal.lines.mkString("; ")}")
  assert(journal.lines == Vector("Balance(ann) -> 100", "Move(ann,bob,30) -> ()"))

  // scenario line 6, the rest resumed twice: cannot be written. `answer` returns ONE `X` and the loop goes
  // on with it. To try a balance of 100 and then of 10 a handler needs "the program from here on" as
  // something it can call, and the type `F[X] => X` has no place for it. Stage 2.

  // a second effect, written by someone else, composed with this one: cannot be written either. `F` is one
  // closed enumeration, and one function answers all of it.
  println("stage 1 holds")
