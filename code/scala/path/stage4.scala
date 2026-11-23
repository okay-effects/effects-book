// THE PATH, STAGE 4 (chapter 3i): more constructors, each one forced.
// Stage 3 is correct and typed. This stage is engineering: two more nodes, two more kinds of handler, and
// places renamed so that an interceptor goes in front of a program already written; each added because
// stage 3 failed, wasted something, or could not be made to compile. Two mains:
//   `scala-cli run code/scala --main-class book.path.stage4.stage4`   the six lines of code/expected.txt
//   `scala-cli run code/scala --main-class book.path.stage4.forced`   the failing runs and the measurements
package book.path.stage4

import scala.annotation.tailrec
import book.path.stage3 as before

/** a program over the operations `F`, ending in an `A`: stage 1's three nodes, and two more */
enum Free[F[_], +A]:
  case Return[F[_], +A](a: A) extends Free[F, A]
  case Suspend[F[_], A](op: F[A]) extends Free[F, A]
  case Bind[F[_], X, A](m: Free[F, X], f: X => Free[F, A]) extends Free[F, A]
  /** AN OPERATION WITH ITS REST, one node: what `Bind(Suspend(op), k)` says in two. It is what a handler
   * passes an operation on as, and what an interpreter wants to find: the question, and what follows */
  case Inject[F[_], X, +A](op: F[X], k: X => Free[F, A]) extends Free[F, A]
  /** THE PROGRAM `p()`, NOT BUILT YET: it is built when an interpreter gets to this node. This is how a
   * function gives back "go on from here" WITHOUT going on itself */
  case Delay[F[_], A](p: () => Free[F, A]) extends Free[F, A]

  def flatMap[B](f: A => Free[F, B]): Free[F, B] = this match
    case Suspend(op) => Inject(op, f)
    case _           => Bind(this, f)
  def map[B](f: A => B): Free[F, B] = flatMap(a => Return(f(a)))

import Free.{Bind, Delay, Inject, Return, Suspend}

/** THE TRAMPOLINE, ONCE. Every interpreter below needs the same thing first: the program brought to its
 * HEAD — finished with a value, or an operation with its rest — in constant stack. Stage 3 wrote this loop
 * twice, inside `handle` and inside `run`; with four interpreters it is written here and used by each.
 * Stage 1's two rules are the first two inner cases. The others say that a delayed program is built now, and
 * that an operation under a bind takes the bind into its rest */
@tailrec def head[F[_], A](p: Free[F, A]): Return[F, A] | Inject[F, ?, A] = p match
  case r: Return[f, a]    => r
  case j: Inject[f, y, a] => j
  case s: Suspend[f, a]   => Inject(s.op, (a: a) => Return(a))
  case d: Delay[f, a]     => head(d.p())
  case b: Bind[f, x, a] => b.m match
      case Return(x)            => head(b.f(x))
      case m: Bind[f2, y, x2]   => head(Bind(m.m, (y: y) => Bind(m.f(y), b.f)))
      case d: Delay[f2, x2]     => head(Bind(d.p(), b.f))
      case j: Inject[f2, y, x2] => Inject(j.op, (y: y) => Bind(j.k(y), b.f))
      case s: Suspend[f2, x2]   => Inject(s.op, b.f)

// ---- ROWS: stage 3's, unchanged

final abstract class Sum[R[_], E[_], X]
infix type +[R[_], E[_]] = [X] =>> Sum[R, E, X]

sealed trait Empty[X]:
  def absurd: Nothing

enum Place[E[_], R[_], Rest[_]]:
  case Last[R[_], E[_]]() extends Place[E, R + E, R]
  case Init[E[_], R[_], G[_], Rest[_]](s: Place[E, R, Rest]) extends Place[E, R + G, Rest + G]
  case Nowhere extends Place[Empty, Empty, Empty]

type In[E[_], R[_]] = Place[E, R, ?]

trait Further:
  given init[E[_], R[_], G[_], Rest[_]](using s: Place[E, R, Rest]): Place[E, R + G, Rest + G] = Place.Init(s)
object Place extends Further:
  given last[R[_], E[_]]: Place[E, R + E, R] = Place.Last()

enum At[R[_], X]:
  case Placed[R[_], E[_], X](at: Place[E, R, ?], op: E[X]) extends At[R, X]

infix type ![+A, R[_]] = Free[[X] =>> At[R, X], A]

def ask[E[_], R[_], X](op: E[X])(using at: In[E, R]): X ! R = Suspend(At.Placed(at, op))

enum Taken[E[_], E2[_], Rest[_]]:
  case Mine[E[_], Rest[_]]() extends Taken[E, E, Rest]
  case Theirs[E[_], E2[_], Rest[_]](at: Place[E2, Rest, ?]) extends Taken[E, E2, Rest]

def take[E[_], R[_], E2[_]](at: Place[E2, R + E, ?]): Taken[E, E2, R] = at match
  case _: Place.Last[r, e]           => Taken.Mine()
  case i: Place.Init[e2, r, g, rest] => Taken.Theirs(i.s)

// ---- THE INTERPRETERS

/** running: the head of a program over the empty row is a value; an operation there is absurd */
def run[A](p: A ! Empty): A = head(p) match
  case Return(a) => a
  case j: Inject[f, y, a] => j.op match
      case pl: At.Placed[r, e, x] => absurd(pl.at, pl.op)

def absurd[E[_], X](at: Place[E, Empty, ?], op: E[X]): Nothing = at match
  case Place.Nowhere => op.absurd

/** THE HANDLER of the last effect, as in stage 3 but for one word: `k` is a TRAMPOLINE. Called, it does not
 * run the rest; it returns a `Delay` that says how to. The clause returns that node, or a program built
 * around it, to whoever called `handle` — an interpreter's loop — and THAT loop, from where it stands, goes
 * on. A frame of the host stack per operation has become a node on the heap per operation */
def handle[E[_], R[_], A, B](p: A ! (R + E))(ret: A => B ! R, eff: [X] => (E[X], X => B ! R) => B ! R): B ! R =
  head(p) match
    case Return(a) => ret(a)
    case j: Inject[f, y, a] => j.op match
        case pl: At.Placed[r, e, x] => take[E, R, e](pl.at) match
            case _: Taken.Mine[e2, r2] => eff[y](pl.op, (x: y) => Delay(() => handle(j.k(x))(ret, eff)))
            // passed on: the rest is run by the handler outside, when ITS `k` is resumed, so not delayed here
            case t: Taken.Theirs[e2, e3, r2] =>
              Inject(At.Placed(t.at, pl.op), (x: y) => handle(j.k(x))(ret, eff))

/** THE SAME WITH A STATE OF ITS OWN, from `s`. A clause is given the state of its time and resumes `k` with
 * the answer AND the state to go on from. The state is a value in the closure of each `k`, not a variable
 * of the handler: a `k` resumed twice starts twice from the same state */
def handle[E[_], R[_], A, B, S](p: A ! (R + E), s: S)(ret: (S, A) => B ! R,
    eff: [X] => (S, E[X], (X, S) => B ! R) => B ! R): B ! R =
  head(p) match
    case Return(a) => ret(s, a)
    case j: Inject[f, y, a] => j.op match
        case pl: At.Placed[r, e, x] => take[E, R, e](pl.at) match
            case _: Taken.Mine[e2, r2] =>
              eff[y](s, pl.op, (x: y, s1: S) => Delay(() => handle(j.k(x), s1)(ret, eff)))
            case t: Taken.Theirs[e2, e3, r2] =>
              Inject(At.Placed(t.at, pl.op), (x: y) => handle(j.k(x), s)(ret, eff))

/** A HANDLER THAT ANSWERS IN PLACE: stage 1's `answer`, come back as one layer among typed layers. The
 * clause is a function from the question to its answer. There is no `k` to allocate and no `Delay`: the
 * loop itself goes on with the answer. What it cannot do is everything stage 2 was for */
def answer[E[_], R[_], A](p: A ! (R + E))(ans: [X] => E[X] => X): A ! R = answering(p, ans)

@tailrec private def answering[E[_], R[_], A](p: A ! (R + E), ans: [X] => E[X] => X): A ! R = head(p) match
  case Return(a) => Return(a)
  case j: Inject[f, y, a] => j.op match
      case pl: At.Placed[r, e, x] => take[E, R, e](pl.at) match
          case _: Taken.Mine[e2, r2]       => answering(j.k(ans(pl.op)), ans)
          case t: Taken.Theirs[e2, e3, r2] => Inject(At.Placed(t.at, pl.op), (x: y) => answer(j.k(x))(ans))

// ---- PLACES RENAMED: a program built at one row, used at another

/** are two places of ONE row the same place? If they are, their effects are the same effect, and `Yes`
 * says so in its type. Two paths are compared step by step from the end of the row. `Yes` is a proof and
 * is given in the one case where the compiler has it, both places `Last`; `No` claims nothing, so the
 * fall-through cases, which Scala 3.3.8's checker asks for, cannot be wrong */
enum Same[E[_], E2[_]]:
  case Yes[E[_]]() extends Same[E, E]
  case No[E[_], E2[_]]() extends Same[E, E2]

def same[E[_], E2[_], R[_]](a: Place[E, R, ?], b: Place[E2, R, ?]): Same[E, E2] = a match
  case _: Place.Last[r, e] => b match
      case _: Place.Last[r2, e2] => Same.Yes()
      case _                     => Same.No()
  case i: Place.Init[e, r, g, rest] => b match
      case j: Place.Init[e2, r2, g2, rest2] => same(i.s, j.s)
      case _                                => Same.No()
  case _ => Same.No()

/** every place of the row `R1` given a place in the row `R2`, the effect unchanged: a function, typed */
trait Renaming[R1[_], R2[_]]:
  def apply[E[_]](at: Place[E, R1, ?]): Place[E, R2, ?]

/** the places of `R` renamed to places of `R + E`, where `E` already stands at `at` in `R`: `E`'s place
 * becomes the new last one, and every other place is one further in. The row gains a second `E`, and the
 * program's own `E` is now the one a handler of the last effect will take */
def interposing[E[_], R[_]](at: Place[E, R, ?]): Renaming[R, R + E] = new Renaming[R, R + E]:
  private val last = Place.Last[R, E]()
  def apply[E2[_]](p: Place[E2, R, ?]): Place[E2, R + E, ?] = same(at, p) match
    case _: Same.Yes[e]     => last
    case _: Same.No[e, e2]  => Place.Init(p)

/** THE PROGRAM `p` WITH ITS PLACES RENAMED. The tree is walked as it is run, one operation at a time: the
 * head is found, its operation given its new place, and the rest renamed when it is resumed. Nothing of
 * the program is run or changed but where each of its questions is addressed */
def remap[R1[_], R2[_], A](p: A ! R1, rename: Renaming[R1, R2]): A ! R2 = head(p) match
  case Return(a) => Return(a)
  case j: Inject[f, y, a] => j.op match
      case pl: At.Placed[r, e, x] => Inject(At.Placed(rename(pl.at), pl.op), (x: y) => remap(j.k(x), rename))

/** `p`, a program over `R`, as a program over `R + E` whose operations of `E` are at the end: ready for a
 * handler of the last effect that asks `E` again at its old place. An INTERCEPTOR is that handler */
def interposed[E[_], R[_], A](p: A ! R)(using at: In[E, R]): A ! (R + E) = Delay(() => remap(p, interposing(at)))

// ---- the bank: stage 3's three effects, and its program at ONE row, written once

enum Accounts[A]:
  case Balance(account: String) extends Accounts[Long]
  case Move(from: String, to: String, amount: Long) extends Accounts[Unit]

enum Clock[A]:
  case Now extends Clock[Long]

enum Refusal[A]:
  case Refuse(why: String) extends Refusal[Nothing]

import Accounts.{Balance, Move}, Clock.Now, Refusal.Refuse

type Bank[X] = (Empty + Clock + Accounts + Refusal)[X]

/** asking, at the bank's row */
def bankAsk[E[_], X](op: E[X])(using In[E, Bank]): X ! Bank = ask(op)

def pay(from: String, to: String, amount: Long): String ! Bank =
  for
    have <- bankAsk(Balance(from))
    _    <- if have < amount then bankAsk(Refuse(s"$from has $have, needs $amount")) else Return(())
    _    <- bankAsk(Move(from, to, amount))
    at   <- bankAsk(Now)
  yield s"moved $amount from $from to $to at $at"

// ---- handlers, each of the kind its job needs

type Balances = Map[String, Long]
def show(b: Balances): String = s"ann=${b.getOrElse("ann", 0L)} bob=${b.getOrElse("bob", 0L)}"

/** the accounts: A STATE OF ITS OWN. No variable anywhere; the balances at the end come back with the result */
def bank[R[_], A](balances: Balances)(p: A ! (R + Accounts)): (Balances, A) ! R =
  handle[Accounts, R, A, (Balances, A), Balances](p, balances)((s, a) => Return((s, a)),
    [X] =>
      (s: Balances, op: Accounts[X], k: (X, Balances) => (Balances, A) ! R) =>
        op match
          case Balance(a)    => k(s.getOrElse(a, 0L), s)
          case Move(f, t, n) => k((), s.updated(f, s(f) - n).updated(t, s.getOrElse(t, 0L) + n)))

/** the clock: IN PLACE. It never needed the rest of the program */
def fixedClock[R[_], A](now: Long)(p: A ! (R + Clock)): A ! R =
  answer[Clock, R, A](p)([X] =>
    (op: Clock[X]) =>
      op match
        case Now => now)

type Outcome[A] = Either[String, A]
def show(r: Outcome[String]): String = r.fold("refused: " + _, "paid: " + _)

/** the refusal: it NEEDS `k`, in order not to call it */
def refusals[R[_], A](p: A ! (R + Refusal)): Outcome[A] ! R =
  handle[Refusal, R, A, Outcome[A]](p)(a => Return(Right(a)),
    [X] =>
      (op: Refusal[X], k: X => Outcome[A] ! R) =>
        op match
          case Refuse(why) => Return(Left(why)))

/** the journal: AN INTERCEPTOR, with a state of its own, the lines so far. It takes a program over `R` and
 * gives back a program over `R`: the accounts are still there for whoever is further out */
def journal[R[_], A](p: A ! R)(using In[Accounts, R]): (Vector[String], A) ! R =
  handle[Accounts, R, A, (Vector[String], A), Vector[String]](interposed(p), Vector.empty)(
    (lines, a) => Return((lines, a)),
    [X] =>
      (lines: Vector[String], op: Accounts[X], k: (X, Vector[String]) => (Vector[String], A) ! R) =>
        ask[Accounts, R, X](op).flatMap(x => k(x, lines :+ s"$op -> $x")))

/** the limit: an interceptor that answers a large move with a refusal */
def limit[R[_], A](max: Long)(p: A ! R)(using In[Accounts, R], In[Refusal, R]): A ! R =
  handle[Accounts, R, A, A](interposed(p))(a => Return(a),
    [X] =>
      (op: Accounts[X], k: X => A ! R) =>
        op match
          case Move(_, _, n) if n > max => ask[Refusal, R, Nothing](Refuse(s"a move of $n is over the limit of $max"))
          case _                        => ask[Accounts, R, X](op).flatMap(k))

type Paths[A] = List[(List[Long], A)]

def whatIf[R[_], A](suppose: List[Long])(p: A ! (R + Accounts)): Paths[A] ! R =
  handle[Accounts, R, A, Paths[A]](p)(a => Return(List((Nil, a))),
    [X] =>
      (op: Accounts[X], k: X => Paths[A] ! R) =>
        op match
          case Balance(_) =>
            suppose.foldLeft(Return(Nil): Paths[A] ! R)((all, s) =>
              all.flatMap(seen => k(s).map(more => seen ++ more.map((path, a) => (s :: path, a)))))
          case Move(_, _, _) => k(()))

@main def stage4(): Unit =
  val rich = Map("ann" -> 100L)
  val poor = Map("ann" -> 10L)

  // 1. the program, run in one line. The balances are part of what the run returns
  val (after1, paid) = run(fixedClock(42)(bank(rich)(refusals(pay("ann", "bob", 30)))))
  println("1 " + show(paid))
  println("1 balances: " + show(after1))

  // 2. refused: nothing after the refusal ran
  val (after2, refused) = run(fixedClock(42)(bank(poor)(refusals(pay("ann", "bob", 30)))))
  println("2 " + show(refused))
  println("2 balances: " + show(after2))

  // 3. under a journal it knows nothing of: the same `pay`, at the same row, with the journal put in front
  val (_, (lines, _)) = run(fixedClock(42)(bank(rich)(journal(refusals(pay("ann", "bob", 30))))))
  lines.foreach(l => println("3 journal: " + l))

  // 4. under a limit, likewise
  val (after4, limited) = run(fixedClock(42)(bank(rich)(refusals(limit(50)(pay("ann", "bob", 80))))))
  println("4 " + show(limited))
  println("4 balances: " + show(after4))

  // 5. leaving early: the journal is outside the refusal, so its lines are kept when the program is left
  val (_, (seen, _)) = run(fixedClock(42)(bank(poor)(journal(refusals(pay("ann", "bob", 30))))))
  println(s"5 left early: the journal has ${seen.size} line, ${seen.mkString("; ")}")

  // 6. the rest resumed twice
  val outcomes = run(fixedClock(42)(whatIf(List(100, 10))(refusals(pay("ann", "bob", 30)))))
  println("6 twice: " + outcomes.map((path, r) => s"${path.head} -> ${show(r).stripPrefix("paid: ")}").mkString(" | "))

  assert(after1 == Map("ann" -> 70L, "bob" -> 30L) && after2 == poor && after4 == rich)
  assert(lines == Vector("Balance(ann) -> 100", "Move(ann,bob,30) -> ()") && seen == Vector("Balance(ann) -> 10"))

// ---- WHY EACH ADDITION: the run of stage 3 that fails or wastes, then the same run here

/** many operations, one after another */
def ticks[R[_]](n: Int, done: Int = 0)(using In[Clock, R]): Int ! R =
  if n == 0 then Return(done) else ask(Now).flatMap(_ => ticks(n - 1, done + 1))

/** as many, each bound to the LEFT of the next: `((((0 >>= f) >>= f) >>= f) …)`, every `f` asking once */
def leftTicks[R[_]](n: Int)(using In[Clock, R]): Int ! R =
  (1 to n).foldLeft(Return(0): Int ! R)((p, _) => p.flatMap(i => ask(Now).map(_ => i + 1)))

/** a clock that takes `k` and resumes it AT ONCE: what `fixedClock` was in stage 3 */
def resumedClock[R[_], A](now: Long)(p: A ! (R + Clock)): A ! R =
  handle[Clock, R, A, A](p)(a => Return(a),
    [X] =>
      (op: Clock[X], k: X => A ! R) =>
        op match
          case Now => k(now))

/** a clock that counts how often it was asked, on the way back: resumed, and then its result used */
def counted[R[_], A](now: Long)(p: A ! (R + Clock)): (Int, A) ! R =
  handle[Clock, R, A, (Int, A)](p)(a => Return((0, a)),
    [X] =>
      (op: Clock[X], k: X => (Int, A) ! R) =>
        op match
          case Now => k(now).map((n, a) => (n + 1, a)))

enum Idle[A]:
  case Pause extends Idle[Unit]

def idle[R[_], A](p: A ! (R + Idle)): A ! R =
  handle[Idle, R, A, A](p)(a => Return(a),
    [X] =>
      (op: Idle[X], k: X => A ! R) =>
        op match
          case Idle.Pause => k(()))

/** `n` operations of the clock, each passing through 0, 1 or 2 handlers of another effect on its way */
def passing(through: Int, n: Int): Int = through match
  case 0 => run(resumedClock(42)(ticks[Empty + Clock](n)))
  case 1 => run(resumedClock(42)(idle(ticks[Empty + Clock + Idle](n))))
  case _ => run(resumedClock(42)(idle(idle(ticks[Empty + Clock + Idle + Idle](n)))))

/** an interceptor of the clock that changes nothing: each question is asked again of the clock further in */
def watching[R[_], A](p: A ! (R + Clock))(using In[Clock, R]): A ! R =
  handle[Clock, R, A, A](p)(a => Return(a),
    [X] => (op: Clock[X], k: X => A ! R) => ask[Clock, R, X](op).flatMap(k))

/** an effect whose handler resumes more than once */
enum Choice[A]:
  case Choose[A](options: List[A]) extends Choice[A]

/** every option taken, one after another: `k` once for each */
def everyChoice[R[_], A](p: A ! (R + Choice)): List[A] ! R =
  handle[Choice, R, A, List[A]](p)(a => Return(List(a)),
    [X] =>
      (op: Choice[X], k: X => List[A] ! R) =>
        op match
          case Choice.Choose(options) =>
            options.foldLeft(Return(Nil): List[A] ! R)((all, o) => all.flatMap(as => k(o).map(as ++ _))))

/** the accounts as stage 3 kept them: in a variable of the handler */
final class InMemory(var balances: Balances):
  def apply[R[_], A](p: A ! (R + Accounts)): A ! R =
    handle[Accounts, R, A, A](p)(a => Return(a),
      [X] =>
        (op: Accounts[X], k: X => A ! R) =>
          op match
            case Balance(a) => k(balances.getOrElse(a, 0L))
            case Move(f, t, n) =>
              balances = balances.updated(f, balances(f) - n).updated(t, balances.getOrElse(t, 0L) + n)
              k(()))

/** move one of two amounts, and say what is left */
def spend[R[_]](using In[Choice, R], In[Accounts, R]): Long ! R =
  for
    amount <- ask(Choice.Choose(List(30L, 50L)))
    _      <- ask(Move("ann", "bob", amount))
    left   <- ask(Balance("ann"))
  yield left

/** bytes this thread allocated while `body` ran once, after it has run a few times: the JVM's own counter
 * for the thread, read before and after. Not a timing */
def allocated(body: => Any): Long =
  val threads = java.lang.management.ManagementFactory.getPlatformMXBean(classOf[com.sun.management.ThreadMXBean])
  for _ <- 1 to 300 do body
  val start = threads.getCurrentThreadAllocatedBytes
  body
  threads.getCurrentThreadAllocatedBytes - start

@main def forced(): Unit =
  def or(body: => Any): String =
    try body.toString
    catch case e: StackOverflowError => e.getClass.getName

  // 1. `Delay`, and `k` as a trampoline
  val n = 100_000
  println(s"$n operations, each resumed at once")
  val tail3 = or(before.resumedAtOnce(n))
  val tail4 = or(run(resumedClock(42)(ticks[Empty + Clock](n))))
  println(s"  stage 3: $tail3")
  println(s"  stage 4: $tail4")
  println(s"$n operations, each resumed and its result then used")
  val back3 = or(before.resumedThenUsed(n))
  val back4 = or(run(counted(42)(ticks[Empty + Clock](n))))
  println(s"  stage 3: $back3")
  println(s"  stage 4: $back4")
  assert(tail3 == "java.lang.StackOverflowError" && back3 == "java.lang.StackOverflowError")
  assert(tail4 == s"$n" && back4 == s"($n,$n)")

  // 2. `Inject`: bytes allocated for each operation, by how many handlers of OTHER effects it passes
  // through on its way to its own
  val ops = 1000
  val b3 = List(0, 1, 2).map(through => allocated(before.passing(through, ops)))
  val b4 = List(0, 1, 2).map(through => allocated(passing(through, ops)))
  println(s"bytes allocated for each operation, passing through 0, 1, 2 handlers of other effects")
  println(s"  stage 3: ${b3.map(_ / ops).mkString(", ")}")
  println(s"  stage 4: ${b4.map(_ / ops).mkString(", ")}")

  // 3. a state of its own: two futures, one bank
  type Futures[X] = (Empty + Choice + Accounts)[X]
  val shared = run(everyChoice(InMemory(Map("ann" -> 100L))(spend[Futures])))
  val own = run(everyChoice(bank(Map("ann" -> 100L))(spend[Futures]))).map((_, left) => left)
  println(s"ann has 100; move 30, or move 50; what is left in each future")
  println(s"  balances in a variable of the handler: ${shared.mkString(", ")}")
  println(s"  balances as the handler's state:       ${own.mkString(", ")}")
  assert(shared == List(70L, 20L) && own == List(70L, 50L))

  // 4. answering in place
  val inPlace = allocated(run(fixedClock(42)(ticks[Empty + Clock](ops))))
  println(s"bytes allocated for each operation of a clock: with k ${b4.head / ops}, in place ${inPlace / ops}")
  assert(b4(2) < b3(2) && inPlace < b4.head)
  val long = run(fixedClock(42)(ticks[Empty + Clock](1_000_000)))
  println(s"1000000 operations answered in place, no Delay: counted $long")

  // 5. what re-association costs: binds nested to the left, under a handler. If walking a chain of binds
  // were repeated for each operation, the bytes for each bind would grow with the length of the chain
  val perBind = List(1000, 2000, 4000).map(n => allocated(run(resumedClock(42)(leftTicks[Empty + Clock](n)))) / n)
  println(s"bytes allocated for each of 1000, 2000, 4000 binds nested to the left: ${perBind.mkString(", ")}")
  assert(perBind.max - perBind.min <= perBind.min / 10, perBind)

  // 6. `remap`: an interceptor in front of a program that is ALREADY WRITTEN, at a row that is fixed.
  // Stage 3's journal and limit worked because `pay` there was written for any row and the edge chose one
  // that names the accounts twice. A program that exists as a value has its row, and names them once:
  //   val written: String ! (Empty + Clock + Accounts + Refusal) = pay("ann", "bob", 30)
  //   Journal()(refusals(written))
  // is refused by Scala 3.3.8, in stage 3, with
  //   No given instance of type book.path.stage3.In[book.path.stage3.Accounts,
  //     [X] =>>
  //       book.path.stage3.Sum[book.path.stage3.Empty, book.path.stage3.Clock, X]
  //   ] was found for parameter x$2 of method apply in class Journal.
  // The journal asks the accounts again further in, and further in than this program's accounts there are
  // none. Here the same value is put under a limit and two journals, and its type never changes
  val written: String ! Bank = pay("ann", "bob", 30)
  val guarded: String ! Bank = limit(50)(written)
  val (_, (outer, (inner, outcome))) =
    run(fixedClock(42)(bank(Map("ann" -> 100L))(journal(journal(refusals(guarded))))))
  println(s"a program written once at its row, under a limit and two journals put in front of it")
  println(s"  ${show(outcome)}; the journals have ${outer.size} and ${inner.size} lines")
  assert(outcome == Right("moved 30 from ann to bob at 42") && outer == inner && outer.size == 2)

  // what it costs: the same interceptor over the same operations, reached the two ways
  val twiceNamed = allocated(run(resumedClock(42)(watching(ticks[Empty + Clock + Clock](ops)))))
  val renamed =
    allocated(run(resumedClock(42)(watching(interposed[Clock, Empty + Clock, Int](ticks[Empty + Clock](ops))))))
  println(s"bytes allocated for each operation under an interceptor: " +
    s"the row naming the effect twice ${twiceNamed / ops}, the places renamed ${renamed / ops}")
  assert(twiceNamed < renamed)
  println("stage 4 holds")

// WHAT THE REAL FILE STILL HAS THAT THIS ONE DOES NOT
// The end point of this path is okay-simple's `Free.scala` (282 lines) and `Places.scala`. Read from them:
//  - other names: this file's `Suspend` is `Perform` there; `Free` is covariant in `F` too, and `Return`
//    is a `Free[Nothing, A]`;
//  - `handleAt(p, in)`: an effect handled from ANY place of the row, not only the last. `take(in, at)`
//    there compares two paths step by step and has a case for each pair of constructors;
//  - a row of one effect that is the effect itself (`Place.Only`), the empty row as `Void`, and
//    `Place.Absurd`, the empty effect's place in every row. This file has `Nowhere` for the same job;
//  - more renamings than `interposing`: `toEnd`, by which `OnFree` moves an effect to the end of its row
//    (`split`), and `subPure`, `subOne`, `subMore`, by which it embeds a program in a larger row (`embed`).
//    `interposing` there is written with the general `take` and has a case for the empty effect's place;
//    this file's uses `same`, which answers only whether two places are one;
//  - `answer` with a state of its own, in a cell the clause reads and writes, and `answerAt`;
//  - the loop of `head` written out inside each interpreter, with a case for each node, instead of shared;
//    `flatMap`, `map` and the entry points `inline`;
//  - `@unchecked` on the patterns that take an operation out of its place. This file's patterns bind every
//    type argument as a variable (`At.Placed[r, e, x]`) and Scala 3.3.8 accepts them without;
//  - `OnFree`, the `Runner` interface: the same programs and handlers run on this tree and on a machine.
