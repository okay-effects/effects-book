// The examples of chapters 2-6, run and asserted: `scala-cli run code/scala`
package book

// ---- the effects of a small bank: what business logic may ask

case class Balance(account: String) extends Op[Long]
case class Move(from: String, to: String, amount: Long) extends Op[Unit]
case object Now extends Op[Long]
case class Refuse(why: String) extends Op[Nothing]
case class Choose[A](options: List[A]) extends Op[A]

// ---- business logic: a program over those questions, and nothing else

def pay(from: String, to: String, amount: Long): Prog[String] =
  for
    have <- ask(Balance(from))
    _    <- if have < amount then ask(Refuse(s"$from has $have, needs $amount")) else done(())
    _    <- ask(Move(from, to, amount))
    at   <- ask(Now)
  yield s"moved $amount from $from to $to at $at"

// ---- handlers: each a way of answering

/** accounts kept in memory: the answer comes from the handler's own state, the program goes on once */
final class InMemory(var balances: Map[String, Long]) extends Handler[String, String]:
  def done(a: String) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[String]) = op match
    case Balance(a) => Some(rest(balances.getOrElse(a, 0L)))
    case Move(f, t, n) =>
      balances = balances.updated(f, balances(f) - n).updated(t, balances.getOrElse(t, 0L) + n)
      Some(rest(()))
    case _ => None

/** a clock that says what it is told: a test's clock */
final class FixedClock[A](now: Long) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case Now => Some(rest(now))
    case _   => None

/** a refusal: the rest of the program is NOT continued — zero resumptions */
final class Refusals[A] extends Handler[A, Either[String, A]]:
  def done(a: A) = Prog.Done(Right(a))
  def on[X](op: Op[X], rest: X => Prog[Either[String, A]]) = op match
    case Refuse(why) => Some(Prog.Done(Left(why)))
    case _           => None

/** a journal: every question of the bank is seen, written down, and ASKED AGAIN for whoever answers it —
 * the program does not know, and cannot go around */
final class Journal[A](var lines: Vector[String] = Vector.empty) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case _: Balance | _: Move =>
      Some(Prog.Ask(op, (answer: X) => { lines :+= s"$op -> $answer"; rest(answer) }))
    case _ => None

/** every choice taken: the rest of the program is continued once PER OPTION — many resumptions */
final class AllChoices[A] extends Handler[A, List[A]]:
  def done(a: A) = Prog.Done(List(a))
  def on[X](op: Op[X], rest: X => Prog[List[A]]) = op match
    case Choose(options) =>
      val none: Prog[List[A]] = Prog.Done(List.empty[A])
      Some(options.foldLeft(none)((all, o) => all.flatMap(as => rest(o).map(as ++ _))))
    case _ => None

@main def examples(): Unit =
  // one program, run in one line under handlers chosen at the edge
  val bank = InMemory(Map("ann" -> 100L))
  val ok = run(handle(Refusals[String]())(handle(FixedClock[String](42))(handle(bank)(pay("ann", "bob", 30)))))
  assert(ok == Right("moved 30 from ann to bob at 42"), ok)
  assert(bank.balances == Map("ann" -> 70L, "bob" -> 30L))

  // the same program, refused: nothing after the refusal ran
  val poor = InMemory(Map("ann" -> 10L))
  val no = run(handle(Refusals[String]())(handle(FixedClock[String](42))(handle(poor)(pay("ann", "bob", 30)))))
  assert(no == Left("ann has 10, needs 30"), no)
  assert(poor.balances == Map("ann" -> 10L), "no money moved")

  // the same program under a journal it knows nothing of
  val journal = Journal[String]()
  val bank2 = InMemory(Map("ann" -> 100L))
  run(handle(Refusals[String]())(handle(FixedClock[String](42))(handle(bank2)(handle(journal)(pay("ann", "bob", 30))))))
  assert(journal.lines == Vector("Balance(ann) -> 100", "Move(ann,bob,30) -> ()"), journal.lines)

  // a program with a question nobody answers does not run
  val forgotten = handle(FixedClock[String](42))(pay("ann", "bob", 30))
  assert(scala.util.Try(run(forgotten)).isFailure)

  // many resumptions: every way to split a bill
  val split: Prog[(Int, Int)] =
    for a <- ask(Choose(List(10, 20))); b <- ask(Choose(List(1, 2))) yield (a, b)
  assert(run(handle(AllChoices[(Int, Int)]())(split)) == List((10, 1), (10, 2), (20, 1), (20, 2)))
  println("all examples hold")
