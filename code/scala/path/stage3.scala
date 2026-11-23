// THE PATH, STAGE 3 (chapter 3i): rows.
// The tree is stage 1's and the handler is stage 2's. What is new is in the TYPES: a program says which
// effects it may still ask, a handler takes one of them away, and only a program with none left can be run.
// This is the third thing of chapter 2b, the row. Prints the six lines of code/expected.txt.
// `scala-cli run code/scala --main-class book.path.stage3.stage3`
package book.path.stage3

import scala.annotation.tailrec

/** a program over the operations `F`, ending in an `A`: stage 1's three nodes */
enum Free[F[_], +A]:
  case Return[F[_], A](a: A) extends Free[F, A]
  case Suspend[F[_], A](op: F[A]) extends Free[F, A]
  case Bind[F[_], X, A](m: Free[F, X], f: X => Free[F, A]) extends Free[F, A]

  def flatMap[B](f: A => Free[F, B]): Free[F, B] = Bind(this, f)
  def map[B](f: A => B): Free[F, B] = Bind(this, a => Return(f(a)))

import Free.{Bind, Return, Suspend}

// ---- ROWS

/** the row `R + E`: the effects of `R`, then `E`. A NAME ONLY — no value of this class is ever made. Stage 2
 * built the sum of two effects as data, `L(op)` or `R(op)`, and every program and handler had to spell its
 * position. Here the row is a type that is only ever compared, and the position travels beside the
 * operation as a value: a `Place` */
final abstract class Sum[R[_], E[_], X]
infix type +[R[_], E[_]] = [X] =>> Sum[R, E, X]

/** the row of no effect — and, the same thing, the effect of no operation: a type with no value. If there
 * WERE a value it could produce anything, which is what `absurd` says */
sealed trait Empty[X]:
  def absurd: Nothing

/** WHERE the effect `E` stands in the row `R`, and what `R` is without it: `Rest`. A place is a PATH from
 * the end of the row: `Last` is the last effect, `Init(s)` is one further in than `s`. The three type
 * parameters are the point: a value `Last()` can only have the type `Place[E, R + E, R]`, so whoever
 * matches a place and finds `Last` has learnt, from the compiler, what `E`, the row and the rest are */
enum Place[E[_], R[_], Rest[_]]:
  case Last[R[_], E[_]]() extends Place[E, R + E, R]
  case Init[E[_], R[_], G[_], Rest[_]](s: Place[E, R, Rest]) extends Place[E, R + G, Rest + G]
  /** the one place of the empty row, and the effect standing at it is `Empty`, which has no operation */
  case Nowhere extends Place[Empty, Empty, Empty]

/** "`E` is somewhere in `R`", for a program that asks `E` and does not care what else `R` holds */
type In[E[_], R[_]] = Place[E, R, ?]

/** the compiler finds a place by itself: the last effect first, and failing that one further in. `init` is
 * in a parent so that when a row names an effect twice, the LAST of the two wins and nothing is ambiguous */
trait Further:
  given init[E[_], R[_], G[_], Rest[_]](using s: Place[E, R, Rest]): Place[E, R + G, Rest + G] = Place.Init(s)
object Place extends Further:
  given last[R[_], E[_]]: Place[E, R + E, R] = Place.Last()

/** an operation of the row `R`: an operation of some effect `E`, and `E`'s place in `R`. The row is a name
 * with no operations of its own, so each operation carries where in it it stands */
enum At[R[_], X]:
  case Placed[R[_], E[_], X](at: Place[E, R, ?], op: E[X]) extends At[R, X]

/** a program ending in `A` that may ask the effects of the row `R`: chapter 2's `A ! R` */
infix type ![+A, R[_]] = Free[[X] =>> At[R, X], A]

/** asking: one operation of `E`, at the place the compiler found for `E` in `R` */
def ask[E[_], R[_], X](op: E[X])(using at: In[E, R]): X ! R = Suspend(At.Placed(at, op))

/** an operation at `at` in `R + E`, met by the handler of the last effect `E`: `Mine`, and then the
 * operation's effect `E2` IS `E`; or `Theirs`, with its place in the row without `E` */
enum Taken[E[_], E2[_], Rest[_]]:
  case Mine[E[_], Rest[_]]() extends Taken[E, E, Rest]
  case Theirs[E[_], E2[_], Rest[_]](at: Place[E2, Rest, ?]) extends Taken[E, E2, Rest]

/** two cases, and the types do the work. `Last[r, e]` is a `Place[e, r + e, r]`; matched against a
 * `Place[E2, R + E, ?]` it tells the compiler `E2 = E`, so `Mine()`, which demands exactly that, is accepted.
 * `Init(s)` holds a place in `R`, which is what `Theirs` wants */
def take[E[_], R[_], E2[_]](at: Place[E2, R + E, ?]): Taken[E, E2, R] = at match
  case _: Place.Last[r, e]           => Taken.Mine()
  case i: Place.Init[e2, r, g, rest] => Taken.Theirs(i.s)

/** THE HANDLER of `E`, the last effect of `R + E`: a program over `R + E` becomes a program over `R`. The
 * loop is stage 2's. An operation is `take`n: the handler's own goes to its clause with `k`, the rest under
 * this same handler; any other is asked again one place nearer the end, with the same rest behind it.
 * Passing on is no longer the clause's business, and a clause is never shown an operation of another effect */
def handle[E[_], R[_], A, B](p: A ! (R + E))(ret: A => B ! R, eff: [X] => (E[X], X => B ! R) => B ! R): B ! R =
  handling(p, ret, eff)

@tailrec private def handling[E[_], R[_], A, B](p: A ! (R + E), ret: A => B ! R,
    eff: [X] => (E[X], X => B ! R) => B ! R): B ! R = p match
  case Return(a)        => ret(a)
  case s: Suspend[f, a] => handling(Bind(s, (a: A) => Return(a)), ret, eff)
  case b: Bind[f, x, a] => b.m match
      case Return(x)          => handling(b.f(x), ret, eff)
      case m: Bind[f2, y, x2] => handling(Bind(m.m, (y: y) => Bind(m.f(y), b.f)), ret, eff)
      case s: Suspend[f2, x2] => s.op match
          case pl: At.Placed[r, e, x3] => take[E, R, e](pl.at) match
              case _: Taken.Mine[e2, r2] => eff[x2](pl.op, (x: x2) => handle(b.f(x))(ret, eff))
              case t: Taken.Theirs[e2, e3, r2] =>
                Bind(Suspend(At.Placed(t.at, pl.op)), (x: x2) => handle(b.f(x))(ret, eff))

/** RUNNING exists for the empty row only. That is the whole check that nothing is left unanswered: a program
 * with a question still in its row is not an `A ! Empty`, and the call does not compile. Inside, an
 * operation would have to stand at a place of the empty row; there is one, `Nowhere`, and what stands there
 * is an operation of `Empty` — no value, `absurd`. So this function has no case that fails at run time */
@tailrec def run[A](p: A ! Empty): A = p match
  case Return(a) => a
  case s: Suspend[f, a] => s.op match
      case pl: At.Placed[r, e, x] => absurd(pl.at, pl.op)
  case b: Bind[f, x, a] => b.m match
      case Return(x)          => run(b.f(x))
      case m: Bind[f2, y, x2] => run(Bind(m.m, (y: y) => Bind(m.f(y), b.f)))
      case s: Suspend[f2, x2] => s.op match
          case pl: At.Placed[r, e, x3] => absurd(pl.at, pl.op)

def absurd[E[_], X](at: Place[E, Empty, ?], op: E[X]): Nothing = at match
  case Place.Nowhere => op.absurd

// ---- the bank, as THREE effects, each written without knowing the others

enum Accounts[A]:
  case Balance(account: String) extends Accounts[Long]
  case Move(from: String, to: String, amount: Long) extends Accounts[Unit]

enum Clock[A]:
  case Now extends Clock[Long]

enum Refusal[A]:
  case Refuse(why: String) extends Refusal[Nothing]

import Accounts.{Balance, Move}, Clock.Now, Refusal.Refuse

/** the program: over ANY row that has the three effects somewhere. It does not say where, or what else */
def pay[R[_]](from: String, to: String, amount: Long)(using In[Accounts, R], In[Clock, R], In[Refusal, R])
    : String ! R =
  for
    have <- ask(Balance(from))
    _    <- if have < amount then ask(Refuse(s"$from has $have, needs $amount")) else Return(())
    _    <- ask(Move(from, to, amount))
    at   <- ask(Now)
  yield s"moved $amount from $from to $to at $at"

// ---- handlers: each takes ONE effect off the end of the row

/** accounts in memory */
final class InMemory(var balances: Map[String, Long]):
  def apply[R[_], A](p: A ! (R + Accounts)): A ! R =
    handle[Accounts, R, A, A](p)(a => Return(a),
      [X] =>
        (op: Accounts[X], k: X => A ! R) =>
          op match
            case Balance(a) => k(balances.getOrElse(a, 0L))
            case Move(f, t, n) =>
              balances = balances.updated(f, balances(f) - n).updated(t, balances.getOrElse(t, 0L) + n)
              k(()))
  def show = s"ann=${balances.getOrElse("ann", 0L)} bob=${balances.getOrElse("bob", 0L)}"

/** a clock that says what it is told */
def fixedClock[R[_], A](now: Long)(p: A ! (R + Clock)): A ! R =
  handle[Clock, R, A, A](p)(a => Return(a),
    [X] =>
      (op: Clock[X], k: X => A ! R) =>
        op match
          case Now => k(now))

type Outcome[A] = Either[String, A]
def show(r: Outcome[String]): String = r.fold("refused: " + _, "paid: " + _)

/** a refusal: `k` is not called */
def refusals[R[_], A](p: A ! (R + Refusal)): Outcome[A] ! R =
  handle[Refusal, R, A, Outcome[A]](p)(a => Return(Right(a)),
    [X] =>
      (op: Refusal[X], k: X => Outcome[A] ! R) =>
        op match
          case Refuse(why) => Return(Left(why)))

/** a journal: it handles the accounts and ASKS THEM AGAIN, of the accounts that are further in. The `using`
 * is the whole of "there must be someone outside to answer": without an `Accounts` in `R` it does not compile */
final class Journal(var lines: Vector[String] = Vector.empty):
  def apply[R[_], A](p: A ! (R + Accounts))(using In[Accounts, R]): A ! R =
    handle[Accounts, R, A, A](p)(a => Return(a),
      [X] =>
        (op: Accounts[X], k: X => A ! R) => ask[Accounts, R, X](op).flatMap(x => { lines :+= s"$op -> $x"; k(x) }))

/** a limit: a large move is answered with ANOTHER question, a refusal; the bank further in never sees it */
def limit[R[_], A](max: Long)(p: A ! (R + Accounts))(using In[Accounts, R], In[Refusal, R]): A ! R =
  handle[Accounts, R, A, A](p)(a => Return(a),
    [X] =>
      (op: Accounts[X], k: X => A ! R) =>
        op match
          case Move(_, _, n) if n > max => ask[Refusal, R, Nothing](Refuse(s"a move of $n is over the limit of $max"))
          case _                        => ask[Accounts, R, X](op).flatMap(k))

/** each outcome of a program, with the balances that were supposed on the way to it */
type Paths[A] = List[(List[Long], A)]

/** what would the program do if the balance were each of `suppose`? `k` is called once for each; nothing is
 * moved */
def whatIf[R[_], A](suppose: List[Long])(p: A ! (R + Accounts)): Paths[A] ! R =
  handle[Accounts, R, A, Paths[A]](p)(a => Return(List((Nil, a))),
    [X] =>
      (op: Accounts[X], k: X => Paths[A] ! R) =>
        op match
          case Balance(_) =>
            suppose.foldLeft(Return(Nil): Paths[A] ! R)((all, s) =>
              all.flatMap(seen => k(s).map(more => seen ++ more.map((path, a) => (s :: path, a)))))
          case Move(_, _, _) => k(()))

// ---- not part of the scenario: what stage 4 runs on THIS stage to show what it does badly

/** many operations, one after another */
def ticks[R[_]](n: Int, done: Int = 0)(using In[Clock, R]): Int ! R =
  if n == 0 then Return(done) else ask(Now).flatMap(_ => ticks(n - 1, done + 1))

/** a clock that also counts how often it was asked, ON THE WAY BACK: `k` is resumed, and then its result is
 * used. The resumption is not the clause's last act */
def counted[R[_], A](now: Long)(p: A ! (R + Clock)): (Int, A) ! R =
  handle[Clock, R, A, (Int, A)](p)(a => Return((0, a)),
    [X] =>
      (op: Clock[X], k: X => (Int, A) ! R) =>
        op match
          case Now => k(now).map((n, a) => (n + 1, a)))

/** an effect nobody asks, and its handler: something for an operation of another effect to pass through */
enum Idle[A]:
  case Pause extends Idle[Unit]

def idle[R[_], A](p: A ! (R + Idle)): A ! R =
  handle[Idle, R, A, A](p)(a => Return(a),
    [X] =>
      (op: Idle[X], k: X => A ! R) =>
        op match
          case Idle.Pause => k(()))

def resumedAtOnce(n: Int): Int = run(fixedClock(42)(ticks[Empty + Clock](n)))
def resumedThenUsed(n: Int): (Int, Int) = run(counted(42)(ticks[Empty + Clock](n)))

/** `n` operations of the clock, each passing through 0, 1 or 2 handlers of another effect on its way */
def passing(through: Int, n: Int): Int = through match
  case 0 => run(fixedClock(42)(ticks[Empty + Clock](n)))
  case 1 => run(fixedClock(42)(idle(ticks[Empty + Clock + Idle](n))))
  case _ => run(fixedClock(42)(idle(idle(ticks[Empty + Clock + Idle + Idle](n)))))

@main def stage3(): Unit =
  // THE ROW IS CHOSEN AT THE EDGE, and the same `pay` is used at each
  type Plain[X] = (Empty + Clock + Accounts + Refusal)[X]
  type Watched[X] = (Empty + Clock + Accounts + Accounts + Refusal)[X]            // the accounts, twice
  type Limited[X] = (Empty + Clock + Accounts + Refusal + Accounts)[X]

  // 1. the program, run in one line: each handler takes the last effect, until the row is empty
  val rich = InMemory(Map("ann" -> 100L))
  println("1 " + show(run(fixedClock(42)(rich(refusals(pay[Plain]("ann", "bob", 30)))))))
  println("1 balances: " + rich.show)

  // A PROGRAM WITH A QUESTION NOBODY ANSWERS DOES NOT COMPILE. With the handlers of the clock and the
  // accounts left out, these two lines, uncommented,
  //   val unanswered: Outcome[String] ! (Empty + Clock + Accounts) = refusals(pay[Plain]("ann", "bob", 30))
  //   run(unanswered)
  // are refused by Scala 3.3.8 with
  //   Found:    (unanswered : book.path.stage3.Outcome[String] ! book.path.stage3.Empty +
  //     book.path.stage3.Clock + book.path.stage3.Accounts)
  //   Required: Any ! book.path.stage3.Empty
  // and a program asked for at a row that lacks one of its effects, `pay[Empty + Clock + Refusal](…)`, with
  //   No given instance of type book.path.stage3.In[book.path.stage3.Accounts, book.path.stage3.Empty +
  //     book.path.stage3.Clock + book.path.stage3.Refusal] was found for parameter x$4 of method pay

  // 2. the same program, refused: nothing after the refusal ran
  val poor = InMemory(Map("ann" -> 10L))
  println("2 " + show(run(fixedClock(42)(poor(refusals(pay[Plain]("ann", "bob", 30)))))))
  println("2 balances: " + poor.show)

  // 3. under a journal it knows nothing of. The row names the accounts twice; `pay` asks at the last of the
  // two, which is the journal's, and the journal asks at the one further in, which is the bank's
  val journal = Journal()
  run(fixedClock(42)(InMemory(Map("ann" -> 100L))(journal(refusals(pay[Watched]("ann", "bob", 30))))))
  journal.lines.foreach(l => println("3 journal: " + l))

  // 4. under a limit: the move is seen before the bank sees it, and refused
  val limited = InMemory(Map("ann" -> 100L))
  println("4 " + show(run(fixedClock(42)(limited(refusals(limit(50)(pay[Limited]("ann", "bob", 80))))))))
  println("4 balances: " + limited.show)

  // 5. leaving early: after the refusal nothing more was asked
  val seen = Journal()
  run(fixedClock(42)(InMemory(Map("ann" -> 10L))(seen(refusals(pay[Watched]("ann", "bob", 30))))))
  println(s"5 left early: the journal has ${seen.lines.size} line, ${seen.lines.mkString("; ")}")

  // 6. the rest resumed twice: what would the program do with a balance of 100, and of 10; nothing is moved
  type Supposed[X] = (Empty + Accounts + Clock + Refusal)[X]
  val outcomes = run(whatIf(List(100, 10))(fixedClock(42)(refusals(pay[Supposed]("ann", "bob", 30)))))
  println("6 twice: " + outcomes.map((path, r) => s"${path.head} -> ${show(r).stripPrefix("paid: ")}").mkString(" | "))

  assert(rich.balances == Map("ann" -> 70L, "bob" -> 30L) && poor.balances == Map("ann" -> 10L))
  assert(journal.lines == Vector("Balance(ann) -> 100", "Move(ann,bob,30) -> ()"))
  assert(limited.balances == Map("ann" -> 100L) && seen.lines == Vector("Balance(ann) -> 10"))
