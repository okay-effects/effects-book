// THE PATH, STAGE 2 (chapter 3i): the handler receives the rest.
// The tree is stage 1's, unchanged. What changes is what a handler is given: the operation AND the rest of
// the program, as a function it may not call, call once, or call many times. That is the second half of
// chapter 2b, the delimited continuation, added to the first.
// `scala-cli run code/scala --main-class book.path.stage2.stage2`
package book.path.stage2

import scala.annotation.tailrec

/** a program over the operations `F`, ending in an `A`: stage 1's three nodes */
enum Free[F[_], +A]:
  case Return[F[_], A](a: A) extends Free[F, A]
  case Suspend[F[_], A](op: F[A]) extends Free[F, A]
  case Bind[F[_], X, A](m: Free[F, X], f: X => Free[F, A]) extends Free[F, A]

  def flatMap[B](f: A => Free[F, B]): Free[F, B] = Bind(this, f)
  def map[B](f: A => B): Free[F, B] = Bind(this, a => Return(f(a)))

import Free.{Bind, Return, Suspend}

/** stage 1's interpreter, kept: it is now what runs a program once its handlers have been applied */
@tailrec def run[F[_], A](p: Free[F, A])(answer: [X] => F[X] => X): A = p match
  case Return(a)   => a
  case Suspend(op) => answer(op)
  case b: Bind[f, x, a] => b.m match
      case Return(x)          => run(b.f(x))(answer)
      case Suspend(op)        => run(b.f(answer(op)))(answer)
      case m: Bind[f2, y, x2] => run(Bind(m.m, (y: y) => Bind(m.f(y), b.f)))(answer)

/** THE HANDLER: `ret` for a finished program, `eff` for an operation — given the operation and `k`, THE REST
 * OF THE PROGRAM UNDER THIS SAME HANDLER, from the operation's answer to what the handled program ends in.
 *
 * The loop is stage 1's: a value goes on, binds nested to the left are re-associated. The one new line is the
 * last. Where stage 1 computed `b.f(answer(op))` and went round, this hands the clause the operation and a
 * function that, GIVEN an answer, does the same. Whether there is an answer, and how many, is now the
 * clause's decision. A program is turned into a program: what `eff` returns may ask again */
def handle[F[_], A, B](p: Free[F, A])(ret: A => Free[F, B],
    eff: [X] => (F[X], X => Free[F, B]) => Free[F, B]): Free[F, B] = handling(p, ret, eff)

@tailrec private def handling[F[_], A, B](p: Free[F, A], ret: A => Free[F, B],
    eff: [X] => (F[X], X => Free[F, B]) => Free[F, B]): Free[F, B] = p match
  case Return(a)   => ret(a)
  case Suspend(op) => eff(op, ret)
  case b: Bind[f, x, a] => b.m match
      case Return(x)           => handling(b.f(x), ret, eff)
      case m: Bind[f2, y, x2]  => handling(Bind(m.m, (y: y) => Bind(m.f(y), b.f)), ret, eff)
      case s: Suspend[f2, x2]  => eff[x2](s.op, (x: x2) => handle(b.f(x))(ret, eff))

/** to pass an operation on to whoever is outside: ask it again, and go on with what they answer */
def passOn[F[_], X, B](op: F[X], k: X => Free[F, B]): Free[F, B] = Bind(Suspend(op), k)

/** the answer of a program that must hold no operation any more; the TYPE cannot say that it holds none */
def nobody[F[_]]: [X] => F[X] => X = [X] => (op: F[X]) => sys.error(s"nobody answers $op")

// ---- the bank, still ONE effect

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

type Outcome[A] = Either[String, A]
def show(r: Outcome[String]): String = r.fold("refused: " + _, "paid: " + _)

/** the bank in memory, a fixed clock and the refusal, in one handler, because there is one `F` */
final class InMemory(var balances: Map[String, Long], now: Long):
  def apply[A](p: Free[Bank, A]): Free[Bank, Outcome[A]] =
    handle[Bank, A, Outcome[A]](p)(a => Return(Right(a)),
      [X] =>
        (op: Bank[X], k: X => Free[Bank, Outcome[A]]) =>
          op match
            case Balance(a) => k(balances.getOrElse(a, 0L))               // ONCE
            case Move(f, t, n) =>
              balances = balances.updated(f, balances(f) - n).updated(t, balances.getOrElse(t, 0L) + n)
              k(())
            case Now         => k(now)
            case Refuse(why) => Return(Left(why)))                        // ZERO TIMES: `k` is not called
  def show = s"ann=${balances.getOrElse("ann", 0L)} bob=${balances.getOrElse("bob", 0L)}"

/** a bank that moves nothing, and leaves the balance to whoever is outside */
def dryRun[A](now: Long)(p: Free[Bank, A]): Free[Bank, Outcome[A]] =
  handle[Bank, A, Outcome[A]](p)(a => Return(Right(a)),
    [X] =>
      (op: Bank[X], k: X => Free[Bank, Outcome[A]]) =>
        op match
          case Move(_, _, _) => k(())
          case Now           => k(now)
          case Refuse(why)   => Return(Left(why))
          case _: Balance    => passOn(op, k))

/** each outcome of a program, with the balances that were supposed on the way to it */
type Paths[A] = List[(List[Long], A)]

/** what would the program do if the balance were each of `suppose`? MANY TIMES: `k` is called once for each */
def whatIf[A](suppose: List[Long])(p: Free[Bank, A]): Free[Bank, Paths[A]] =
  handle[Bank, A, Paths[A]](p)(a => Return(List((Nil, a))),
    [X] =>
      (op: Bank[X], k: X => Free[Bank, Paths[A]]) =>
        op match
          case Balance(_) =>
            suppose.foldLeft(Return(Nil): Free[Bank, Paths[A]])((all, s) =>
              all.flatMap(seen => k(s).map(more => seen ++ more.map((path, a) => (s :: path, a)))))
          case other => passOn(other, k))

/** a journal: each question of the accounts is asked again of whoever is outside, and written down with the
 * answer; the program does not know */
final class Journal(var lines: Vector[String] = Vector.empty):
  def apply[A](p: Free[Bank, A]): Free[Bank, A] =
    handle(p)(a => Return(a),
      [X] =>
        (op: Bank[X], k: X => Free[Bank, A]) =>
          op match
            case _: (Balance | Move) => passOn(op, (x: X) => { lines :+= s"$op -> $x"; k(x) })
            case _                   => passOn(op, k))

/** a hundred thousand operations, one after another */
def many(n: Int, done: Int = 0): Free[Bank, Int] =
  if n == 0 then Return(done) else ask(Now).flatMap(_ => many(n - 1, done + 1))

// ---- THE ATTEMPT: a second effect, written by someone who has not seen the bank

enum Log[A]:
  case Note(line: String) extends Log[Unit]

/** the usual answer: the sum of two effects, an operation of one OR of the other */
enum Sum[F[_], G[_], A]:
  case L[F[_], G[_], A](op: F[A]) extends Sum[F, G, A]
  case R[F[_], G[_], A](op: G[A]) extends Sum[F, G, A]

type Both[A] = Sum[Log, Bank, A]

/** 1. the PROGRAM says where in the sum each operation stands. `pay` itself cannot be used here: it is a
 * `Free[Bank, String]`, and making it a `Free[Both, String]` means walking its whole tree to wrap every
 * operation in `R` */
def noted: Free[Both, Long] =
  for
    _  <- Suspend[Both, Unit](Sum.L(Log.Note("asking the time")))
    at <- Suspend[Both, Long](Sum.R(Now))
  yield at

/** 2. the HANDLER of the log says it too: it is written against `Both`, knows that it is the left of two, and
 * would be written again for `Sum[Sum[Log, Bank], Other, _]`.
 * 3. And its result is still a `Free[Both, _]`: the type does not say that no `Note` is left */
def notes[A](p: Free[Both, A]): Free[Both, (Vector[String], A)] =
  handle[Both, A, (Vector[String], A)](p)(a => Return((Vector.empty, a)),
    [X] =>
      (op: Both[X], k: X => Free[Both, (Vector[String], A)]) =>
        op match
          case Sum.L(Log.Note(line)) => k(()).map((lines, a) => (line +: lines, a))
          case other                 => passOn(other, k))

@main def stage2(): Unit =
  def edge[A](p: Free[Bank, A]): A = run(p)(nobody[Bank])

  // ONCE: the payment
  val rich = InMemory(Map("ann" -> 100L), 42)
  val paid = edge(rich(pay("ann", "bob", 30)))
  println(s"once: ${show(paid)}; balances: ${rich.show}")
  assert(paid == Right("moved 30 from ann to bob at 42") && rich.balances == Map("ann" -> 70L, "bob" -> 30L))

  // ZERO TIMES: the refusal is a value, and nothing after it ran. No exception of the host is involved
  val poor = InMemory(Map("ann" -> 10L), 42)
  val refused = edge(poor(pay("ann", "bob", 30)))
  println(s"zero times: ${show(refused)}; balances: ${poor.show}")
  assert(refused == Left("ann has 10, needs 30") && poor.balances == Map("ann" -> 10L))

  // MANY TIMES: the rest resumed with a balance of 100 and again with 10
  val both = edge(whatIf(List(100, 10))(dryRun(42)(pay("ann", "bob", 30))))
  val twice = both.map((path, r) => s"${path.head} -> ${show(r).stripPrefix("paid: ")}").mkString(" | ")
  println(s"many times: $twice")
  assert(twice == "100 -> moved 30 from ann to bob at 42 | 10 -> refused: ann has 10, needs 30")

  // a handler that passes a question on: the journal, between the program and the bank
  val journal = Journal()
  edge(InMemory(Map("ann" -> 100L), 42)(journal(pay("ann", "bob", 30))))
  println(s"journal: ${journal.lines.mkString("; ")}")
  assert(journal.lines == Vector("Balance(ann) -> 100", "Move(ann,bob,30) -> ()"))

  // WHAT STILL FAILS

  // 1. the types do not say what is left unanswered. `journal(pay(…))` is a `Free[Bank, String]`, as
  // `pay(…)` is, and as a program under the bank is. A forgotten handler is found when the question is asked
  val forgotten: Free[Bank, String] = Journal()(pay("ann", "bob", 30))
  val found = scala.util.Try(edge(forgotten)).fold(_.getMessage, identity)
  println(s"a forgotten handler, found at run time: $found")
  assert(found == "nobody answers Balance(ann)")

  // … and handlers applied in the wrong order compile: a journal OUTSIDE the bank sees nothing
  val outside = Journal()
  edge(outside(InMemory(Map("ann" -> 100L), 42)(pay("ann", "bob", 30))))
  println(s"a journal outside the bank compiles, and has ${outside.lines.size} lines")
  assert(outside.lines.isEmpty)

  // 2. one `F`. Every handler above is handed every operation of the bank and must say what to do with each,
  // if only "pass it on". A second effect written apart needs the sum, and with the sum the program and
  // the handler both name their position in it (`noted`, `notes` above)
  val (lines, at) = run(notes(noted))([X] =>
    (op: Both[X]) =>
      op match
        case Sum.R(Now) => 42L
        case other      => sys.error(s"nobody answers $other"))
  println(s"two effects by hand: $lines, $at")
  assert(lines == Vector("asking the time") && at == 42L)

  // 3. the stack, which stage 1 had solved. `k` CALLS `handle`, which calls the clause, which calls `k`:
  // frames of the host for every operation answered, and they are not given back until the end
  val deep =
    try s"counted ${edge(InMemory(Map.empty, 42)(many(100_000)))}"
    catch case e: StackOverflowError => e.getClass.getName
  println(s"100000 operations under a handler that calls k: $deep")
  assert(deep == "java.lang.StackOverflowError")
  println("stage 2 holds")
