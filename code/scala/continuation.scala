// The examples of chapter 6 (what a handler can do that a wrapper cannot), run and asserted:
// `scala-cli run code/scala --main-class book.continuation`. Nothing here but the forty lines of kernel.scala
// and the bank of examples.scala.
package book

// ---- 1. not continuing: leaving a deep walk with an answer is `Refuse` and `Refusals` of chapter 2

case class Alloc(centre: String, amount: Long)

/** two nested walks; the layers between the refusal and its handler do not mention it */
def check(lines: List[List[Alloc]], closed: Set[String]): Prog[Long] =
  def line(allocs: List[Alloc]): Prog[Long] = allocs match
    case Nil => done(0L)
    case a :: more =>
      (if closed(a.centre) then ask(Refuse(a.centre)) else done(())).flatMap(_ => line(more).map(_ + a.amount))
  lines.foldLeft(done(0L))((sum, l) => sum.flatMap(s => line(l).map(s + _)))

// ---- 2. continuing later: a producer that pushes, read as something that pulls

case class Emit(item: String) extends Op[Unit]

/** a producer: it emits every word, and knows nothing of who takes them. `produced` counts what it really did */
var produced = 0
def words(text: String): Prog[Unit] =
  text.split(' ').toList.foldLeft(done(()))((before, w) => before.flatMap(_ => { produced += 1; ask(Emit(w)) }))

/** an endless producer */
def numbers(from: Int): Prog[Nothing] = ask(Emit(from.toString)).flatMap(_ => numbers(from + 1))

/** push: every item collected, the producer run to its end */
final class Collected extends Handler[Unit, List[String]]:
  def done(u: Unit) = Prog.Done(Nil)
  def on[X](op: Op[X], rest: X => Prog[List[String]]) = op match
    case Emit(item) => Some(rest(()).map(item :: _))
    case _          => None

/** what a consumer holds between two items: nothing more to come, or an item and the REST OF THE PRODUCER */
enum Pull:
  case End
  case Item(item: String, next: () => Prog[Pull])

/** pull: at each item the producer is NOT continued; its rest is handed to the consumer, to continue when it likes */
final class Pulled[A] extends Handler[A, Pull]:
  def done(a: A) = Prog.Done(Pull.End)
  def on[X](op: Op[X], rest: X => Prog[Pull]) = op match
    case Emit(item) => Some(Prog.Done(Pull.Item(item, () => rest(()))))
    case _          => None

/** an ordinary consumer: a loop that can stop. `from` is by name: the producer is not continued unless asked */
def take(n: Int, from: => Prog[Pull]): List[String] =
  if n == 0 then Nil
  else
    run(from) match
      case Pull.End              => Nil
      case Pull.Item(item, next) => item :: take(n - 1, next())

/** two producers read in step, with no thread */
def zip(a: => Prog[Pull], b: => Prog[Pull], n: Int): List[(String, String)] =
  if n == 0 then Nil
  else
    (run(a), run(b)) match
      case (Pull.Item(x, nextA), Pull.Item(y, nextB)) => (x, y) :: zip(nextA(), nextB(), n - 1)
      case _                                          => Nil

/** chapter 2's bank in memory, for a program of any result */
final class Accounts[A](var balances: Map[String, Long]) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case Balance(a) => Some(rest(balances.getOrElse(a, 0L)))
    case Move(f, t, n) =>
      balances = balances.updated(f, balances(f) - n).updated(t, balances.getOrElse(t, 0L) + n)
      Some(rest(()))
    case _ => None

// ---- 3. continuing, and looking at what comes back: undo a move if THE CALLER later fails

/** a caller that moves money and may fail two lines later */
def payAndShip(from: String, to: String, amount: Long, inStock: Int): Prog[String] =
  for
    _ <- ask(Move(from, to, amount))
    _ <- if inStock == 0 then ask(Refuse("nothing left to ship")) else done(())
  yield s"shipped, paid $amount"

/** every Move is passed on to the bank; if the rest of the caller then ends refused, the money is moved back */
final class Compensated[A] extends Handler[Either[String, A], Either[String, A]]:
  def done(r: Either[String, A]) = Prog.Done(r)
  def on[X](op: Op[X], rest: X => Prog[Either[String, A]]) = op match
    case Move(from, to, n) =>
      Some(Prog.Ask(op, (moved: X) => rest(moved).flatMap {
        case Left(why) => ask(Move(to, from, n)).map(_ => Left(s"$why; moved $n back"))
        case ok        => Prog.Done(ok)
      }))
    case _ => None

// ---- 4. continuing again: retry the REST of the caller with another answer

case class Provider(candidates: List[String]) extends Op[String]
case class Quote(provider: String, amount: Long) extends Op[Long]
case class Charge(provider: String, amount: Long) extends Op[String]

/** the caller chooses nothing: it asks for a provider, then may refuse the one it was given, lines later */
def checkout(amount: Long): Prog[String] =
  for
    p   <- ask(Provider(List("fast", "steady")))
    fee <- ask(Quote(p, amount))
    _   <- if fee > 5 then ask(Refuse(s"$p asks a fee of $fee")) else done(())
    ref <- ask(Charge(p, amount + fee))
  yield ref

/** the rest of the caller is continued with the first candidate; if it ends refused, continued AGAIN with the next */
final class TryEach[A] extends Handler[Either[String, A], Either[String, A]]:
  def done(r: Either[String, A]) = Prog.Done(r)
  def on[X](op: Op[X], rest: X => Prog[Either[String, A]]) = op match
    case Provider(candidates) =>
      def from(left: List[String], last: Either[String, A]): Prog[Either[String, A]] = left match
        case Nil       => Prog.Done(last)
        case c :: more => rest(c).flatMap { case Left(why) => from(more, Left(why)); case ok => Prog.Done(ok) }
      Some(from(candidates, Left("no provider")))
    case _ => None

/** two providers with fixed fees; what was quoted and what was charged is kept, to see what ran twice */
final class Providers[A](fees: Map[String, Long]) extends Handler[A, A]:
  var quoted, charged = Vector.empty[String]
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case Quote(p, _)  => quoted :+= p; Some(rest(fees(p)))
    case Charge(p, n) => charged :+= p; Some(rest(s"$p-$n"))
    case _            => None

// ---- 5. continuing with an instruction: a decision that belongs to the caller, taken inside the callee

enum Verdict:
  case Skip
  case Use(value: Long)
case class Malformed(row: Int, text: String) extends Op[Verdict]

/** an importer with no policy parameter: at a row it cannot read, it asks */
def total(rows: List[String]): Prog[Long] =
  rows.zipWithIndex.foldLeft(done(0L)) { case (sum, (text, row)) =>
    sum.flatMap(s =>
      text.toLongOption match
        case Some(n) => done(s + n)
        case None =>
          ask(Malformed(row, text)).map {
            case Verdict.Skip   => s
            case Verdict.Use(n) => s + n
          })
  }

/** a policy: continue the importer with a verdict, or (None) do not continue it at all */
final class Policy[A](decide: Malformed => Option[Verdict]) extends Handler[A, Either[String, A]]:
  def done(a: A) = Prog.Done(Right(a))
  def on[X](op: Op[X], rest: X => Prog[Either[String, A]]) = op match
    case m: Malformed => Some(decide(m).fold(Prog.Done(Left(s"row ${m.row}: ${m.text}")))(rest(_)))
    case _            => None

// ---- 6. continuing twice: what would this program do, if the answer were this or that

/** at Balance the rest is continued once per supposed answer; every outcome is returned beside its suppositions */
final class WhatIf[A](suppose: List[Long]) extends Handler[A, List[(List[Long], A)]]:
  def done(a: A) = Prog.Done(List((Nil, a)))
  def on[X](op: Op[X], rest: X => Prog[List[(List[Long], A)]]) = op match
    case Balance(_) =>
      Some(suppose.foldLeft(Prog.Done(List.empty[(List[Long], A)]): Prog[List[(List[Long], A)]])((all, s) =>
        all.flatMap(seen => rest(s).map(outcomes => seen ++ outcomes.map((path, a) => (s :: path, a))))))
    case _ => None

/** a bank that moves nothing and says what it was asked to move */
final class DryRun[A](var would: Vector[String] = Vector.empty) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case m: Move => would :+= m.toString; Some(rest(()))
    case _       => None

@main def continuation(): Unit =
  // 1. zero times: the answer leaves two nested walks; the layers between never mention it
  val invoice = List(List(Alloc("north", 10), Alloc("south", 20)), List(Alloc("old", 5), Alloc("west", 1)))
  assert(run(handle(Refusals[Long]())(check(invoice, closed = Set("old")))) == Left("old"))
  assert(run(handle(Refusals[Long]())(check(invoice, closed = Set.empty))) == Right(36L))

  // 2. later: the same producer pushed to a list …
  assert(run(handle(Collected())(words("to be or not to be"))) == List("to", "be", "or", "not", "to", "be"))
  assert(produced == 6)
  // … and pulled: two items taken, and the producer never went further
  produced = 0
  assert(take(2, handle(Pulled[Unit]())(words("to be or not to be"))) == List("to", "be"))
  assert(produced == 2, "the third word was never produced")
  // an endless producer is no trouble to a consumer that stops
  assert(take(3, handle(Pulled[Nothing]())(numbers(1))) == List("1", "2", "3"))
  // two producers in step, no thread: both are values in the consumer's hand
  val pairs = zip(handle(Pulled[Unit]())(words("a b c")), handle(Pulled[Nothing]())(numbers(1)), 5)
  assert(pairs == List(("a", "1"), ("b", "2"), ("c", "3")), pairs)

  // 3. once, and on the way back: the move is undone because the CALLER failed after it
  val bank = Accounts[Either[String, String]](Map("ann" -> 100L))
  def shipped(inStock: Int) =
    run(handle(bank)(handle(Compensated[String]())(handle(Refusals[String]())(payAndShip("ann", "bob", 30, inStock)))))
  assert(shipped(inStock = 0) == Left("nothing left to ship; moved 30 back"))
  assert(bank.balances == Map("ann" -> 100L, "bob" -> 0L), bank.balances)
  assert(shipped(inStock = 1) == Right("shipped, paid 30"))
  assert(bank.balances == Map("ann" -> 70L, "bob" -> 30L))

  // 4. again: the first provider is refused by the caller three lines on; the rest is run again with the second
  val providers = Providers[Either[String, String]](Map("fast" -> 9L, "steady" -> 2L))
  val paid = run(handle(providers)(handle(TryEach[String]())(handle(Refusals[String]())(checkout(100)))))
  assert(paid == Right("steady-102"), paid)
  assert(providers.quoted == Vector("fast", "steady"), "what is after the question ran once per resumption")
  assert(providers.charged == Vector("steady"))
  // and when every candidate is refused, the last refusal is the answer
  val dear = Providers[Either[String, String]](Map("fast" -> 9L, "steady" -> 7L))
  val unpaid = run(handle(dear)(handle(TryEach[String]())(handle(Refusals[String]())(checkout(100)))))
  assert(unpaid == Left("steady asks a fee of 7") && dear.charged.isEmpty, unpaid)

  // 5. with an instruction: one importer, three callers, no policy parameter
  val rows = List("10", "20", "n/a", "30")
  assert(run(handle(Policy[Long](_ => Some(Verdict.Skip)))(total(rows))) == Right(60L))
  assert(run(handle(Policy[Long](_ => Some(Verdict.Use(1))))(total(rows))) == Right(61L))
  assert(run(handle(Policy[Long](_ => None))(total(rows))) == Left("row 2: n/a"))

  // 6. twice: one program, two supposed balances, both outcomes, and nothing moved anywhere
  val dry = DryRun[Either[String, String]]()
  val outcomes = run(handle(WhatIf[Either[String, String]](List(100, 10)))(handle(dry)(
    handle(FixedClock[Either[String, String]](42))(handle(Refusals[String]())(pay("ann", "bob", 30))))))
  assert(outcomes == List(
    (List(100L), Right("moved 30 from ann to bob at 42")),
    (List(10L), Left("ann has 10, needs 30"))), outcomes)
  assert(dry.would == Vector("Move(ann,bob,30)"))
  // the hazard: with a real bank outside, both futures act on ONE world — two moves
  val real = Accounts[List[(List[Long], Either[String, String])]](Map("ann" -> 100L))
  run(handle(real)(handle(WhatIf[Either[String, String]](List(100, 50)))(
    handle(FixedClock[Either[String, String]](42))(handle(Refusals[String]())(pay("ann", "bob", 30))))))
  assert(real.balances == Map("ann" -> 40L, "bob" -> 60L), real.balances)
  println("continuation examples hold")
