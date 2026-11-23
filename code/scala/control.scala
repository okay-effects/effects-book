// The examples of chapter 10 (effects as control), run and asserted.
package book

/** a limit: every Move is seen BEFORE it is answered; one above the limit becomes a refusal, and the move is
 * never asked of the bank. The program is not told and cannot go around */
final class Limit[A](max: Long) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case Move(_, _, n) if n > max => Some(ask(Refuse(s"a move of $n is over the limit of $max")))
    case _                        => None

/** what a journal kept: each question with its answer, in order — both as text, which is what a journal that
 * outlives the process can hold. The type of an answer is not kept; it is found again from the question */
final case class Entry(question: String, answer: String)

/** a recording journal: as chapter 2's, keeping the answers themselves */
final class Recording[A](var entries: Vector[Entry] = Vector.empty) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case _: Balance | _: Move | Now =>
      Some(Prog.Ask(op, (answer: X) => { entries :+= Entry(op.toString, answer.toString); rest(answer) }))
    case _ => None

/** REPLAY: the questions answered from a journal alone — no bank, no clock. A question the journal does not have
 * next is a divergence: the handler itself ASKS — a refusal, for whoever handles refusals outside. The recorded
 * answer is text; matching the question tells the compiler what it must be read as, so there is no cast */
final class Replay[A](var left: Vector[Entry]) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) =
    def next(read: String => X): Option[Prog[A]] = left.headOption match
      case Some(Entry(q, answer)) if q == op.toString =>
        left = left.tail
        Some(rest(read(answer)))
      case other => Some(ask(Refuse(s"diverged at $op: the journal has ${other.map(_.question)}")))
    op match
      case _: Balance => next(_.toLong)
      case _: Move    => next(_ => ())
      case Now        => next(_.toLong)
      case _          => None

@main def control(): Unit =
  // a limit the program knows nothing of: the large move never reaches the bank
  val bank = InMemory(Map("ann" -> 5000L))
  def guarded(amount: Long) = run(handle(Refusals[String]())(handle(FixedClock[String](42))(handle(bank)(
    handle(Limit[String](1000))(pay("ann", "bob", amount))))))
  assert(guarded(300) == Right("moved 300 from ann to bob at 42"))
  assert(guarded(3000) == Left("a move of 3000 is over the limit of 1000"))
  assert(bank.balances == Map("ann" -> 4700L, "bob" -> 300L), "only the small move happened")

  // record a run against the real handlers …
  val recording = Recording[String]()
  val live = InMemory(Map("ann" -> 100L))
  val first = run(handle(Refusals[String]())(handle(FixedClock[String](1700000000L))(handle(live)(
    handle(recording)(pay("ann", "bob", 30))))))
  assert(recording.entries.map(_.question) == Vector("Balance(ann)", "Move(ann,bob,30)", "Now"))

  // … and replay it with NO bank and NO clock: the same result, from the journal alone
  val again = run(handle(Refusals[String]())(handle(Replay[String](recording.entries))(pay("ann", "bob", 30))))
  assert(again == first, again)

  // a changed program does not replay silently: the divergence is named
  val changed = run(handle(Refusals[String]())(handle(Replay[String](recording.entries))(pay("ann", "bob", 31))))
  assert(changed == Left("diverged at Move(ann,bob,31): the journal has Some(Move(ann,bob,30))"), changed)

  // the hazard of §10.6: a program that PICKS ITS OWN HANDLER. The limit and the journal stand around it, and
  // see nothing: the move over the limit happened
  val backdoor = InMemory(Map("ann" -> 5000L))
  val around = Journal[String]()
  val sneaky: Prog[String] = done(()).map(_ =>
    run(handle(Refusals[String]())(handle(FixedClock[String](42))(handle(backdoor)(pay("ann", "bob", 3000))))).toString)
  val got = run(handle(Limit[String](1000))(handle(around)(sneaky)))
  assert(got == "Right(moved 3000 from ann to bob at 42)" && around.lines.isEmpty, got)
  assert(backdoor.balances == Map("ann" -> 2000L, "bob" -> 3000L), "over the limit, and it happened")
  println("control examples hold")
