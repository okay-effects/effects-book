// The examples of chapter 11 (stopping in the middle), run and asserted:
// `scala-cli run code/scala --main-class book.stopping`.
package book

case class Place(item: String, price: Long) extends Op[String] // answer: the order's reference
case class Approval(order: String) extends Op[Boolean]         // answer: a person's, some time this week
case class Ship(order: String) extends Op[Unit]
case class Refund(order: String) extends Op[Unit]

/** chapter 1b's third program, in a straight line */
def purchase(item: String, price: Long): Prog[String] =
  for
    order <- ask(Place(item, price))
    ok    <- if price > 10000 then ask(Approval(order)) else done(true)
    _     <- if ok then ask(Ship(order)) else ask(Refund(order))
    at    <- ask(Now)
  yield s"$order ${if ok then "shipped" else "refunded"} at $at"

/** a shop that keeps what it was really asked to do */
final class Shop[A](var did: Vector[String] = Vector.empty) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case p: Place  => did :+= p.toString; Some(rest(s"order-${did.size}"))
    case s: Ship   => did :+= s.toString; Some(rest(()))
    case r: Refund => did :+= r.toString; Some(rest(()))
    case _         => None

/** where a run stands: finished, or waiting for a person — with the REST OF THE PROGRAM in hand */
enum Stopped[+A]:
  case Finished(a: A)
  case Waiting(order: String, resume: Boolean => Prog[Stopped[A]])

/** an approval is not answered: the rest is kept, and the run returns */
final class Approvals[A] extends Handler[A, Stopped[A]]:
  def done(a: A) = Prog.Done(Stopped.Finished(a))
  def on[X](op: Op[X], rest: X => Prog[Stopped[A]]) = op match
    case Approval(order) => Some(Prog.Done(Stopped.Waiting(order, rest)))
    case _               => None

// ---- resuming by REPLAY: the answers are kept, the program is run again from its start over them

/** every question passed on, and its answer written down */
final class Journaling[A](var journal: Vector[Entry] = Vector.empty) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) =
    Some(Prog.Ask(op, (answer: X) => { journal :+= Entry(op.toString, answer.toString); rest(answer) }))

/** while the journal lasts, questions are answered from it and the world is not asked; after it, they pass on.
 * A recorded answer is text, read as what its question is answered with: the match on the question says which */
final class Replaying[A](var left: Vector[Entry], var replayed: Int = 0) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) =
    def next(read: String => X): Option[Prog[A]] = left.headOption match
      case None => None
      case Some(Entry(q, answer)) if q == op.toString =>
        left = left.tail; replayed += 1
        Some(rest(read(answer)))
      case Some(Entry(q, _)) => sys.error(s"diverged: the program asks $op, the journal has $q")
    op match
      case _: Place    => next(text => text)
      case _: Approval => next(_.toBoolean)
      case _: Ship     => next(_ => ())
      case _: Refund   => next(_ => ())
      case Now         => next(_.toLong)
      case _           => None

/** the next version of the program: one question more, before the others */
def purchase2(item: String, price: Long): Prog[String] =
  ask(Now).flatMap(seen => purchase(item, price).map(out => s"$out (seen at $seen)"))

// ---- resuming from the REST AS DATA: which stop, and the variables alive there

/** what a compiler makes of `purchase` when it compiles it to a state machine; here by hand, for its one stop */
enum PurchaseRest:
  case AfterApproval(order: String)

def resume(rest: PurchaseRest, ok: Boolean): Prog[String] = rest match
  case PurchaseRest.AfterApproval(order) =>
    for
      _  <- if ok then ask(Ship(order)) else ask(Refund(order))
      at <- ask(Now)
    yield s"$order ${if ok then "shipped" else "refunded"} at $at"

/** data can be written and read; this is the whole price of the second road, in miniature */
def write(rest: PurchaseRest): String = rest match
  case PurchaseRest.AfterApproval(order) => s"AfterApproval|$order"
def read(text: String): PurchaseRest = text.split('|') match
  case Array("AfterApproval", order) => PurchaseRest.AfterApproval(order)

/** the same stop, keeping data instead of a closure */
final class ApprovalsAsData[A] extends Handler[A, Either[String, A]]:
  def done(a: A) = Prog.Done(Right(a))
  def on[X](op: Op[X], rest: X => Prog[Either[String, A]]) = op match
    case Approval(order) => Some(Prog.Done(Left(write(PurchaseRest.AfterApproval(order)))))
    case _               => None

@main def stopping(): Unit =
  type S = Stopped[String]

  // Monday: the purchase runs to the approval and stops. Nothing is running; the run is a value
  val shop = Shop[S]()
  val monday = run(handle(shop)(handle(FixedClock[S](1))(handle(Approvals[String]())(purchase("a crane", 50000)))))
  val (order, rest) = monday match
    case Stopped.Waiting(order, rest) => (order, rest)
    case Stopped.Finished(a)          => sys.error(s"did not stop: $a")
  assert(order == "order-1" && shop.did == Vector("Place(a crane,50000)"))

  // Thursday, the same process: the rest is continued under Thursday's handlers. Nothing before the stop runs again
  val thursday = run(handle(shop)(handle(FixedClock[S](4))(rest(true))))
  assert(thursday == Stopped.Finished("order-1 shipped at 4"), thursday)
  assert(shop.did == Vector("Place(a crane,50000)", "Ship(order-1)"))

  // a small purchase never stops
  assert(run(handle(Shop[S]())(handle(FixedClock[S](1))(handle(Approvals[String]())(purchase("a pen", 3))))) ==
    Stopped.Finished("order-1 shipped at 1"))

  // but the rest is a closure: it cannot be written down for another process to read
  val bytes = java.io.ObjectOutputStream(java.io.ByteArrayOutputStream())
  assert(scala.util.Try(bytes.writeObject(rest)).failed.get.isInstanceOf[java.io.NotSerializableException])

  // ---- replay. Monday again, journaling the answers; the process then dies, and only the journal is left
  val shop1 = Shop[S]()
  val journaling = Journaling[String]()
  run(handle(shop1)(handle(FixedClock[S](1))(handle(Approvals[String]())(
    handle(journaling)(purchase("a crane", 50000))))))
  assert(journaling.journal == Vector(Entry("Place(a crane,50000)", "order-1")))

  // Thursday, another process: the director's answer is appended, and the program is run AGAIN from its start
  val told = journaling.journal :+ Entry("Approval(order-1)", "true")
  val shop2 = Shop[S]()
  val replaying = Replaying[String](told)
  val resumed = run(handle(shop2)(handle(FixedClock[S](4))(handle(Approvals[String]())(
    handle(replaying)(purchase("a crane", 50000))))))
  assert(resumed == Stopped.Finished("order-1 shipped at 4"), resumed)
  assert(shop2.did == Vector("Ship(order-1)"), "the order was not placed a second time")
  assert(replaying.replayed == told.size, "every recorded answer was passed through the program again")

  // the restriction: a new version that asks one more question cannot be resumed from the old journal
  val changed = scala.util.Try(run(handle(Shop[S]())(handle(FixedClock[S](4))(handle(Approvals[String]())(
    handle(Replaying[String](told))(purchase2("a crane", 50000)))))))
  assert(changed.failed.get.getMessage == "diverged: the program asks Now, the journal has Place(a crane,50000)")

  // ---- the rest as data. Monday: what is kept is a line of text
  type E = Either[String, String]
  val shop3 = Shop[E]()
  val kept = run(handle(shop3)(handle(FixedClock[E](1))(handle(ApprovalsAsData[String]())(purchase("a crane", 50000)))))
  assert(kept == Left("AfterApproval|order-1"))

  // Thursday, another process: the text is read, and the program goes on FROM THE STOP. Nothing is run again
  val shop4 = Shop[String]()
  val text = kept.fold(text => text, a => sys.error(s"did not stop: $a"))
  assert(run(handle(shop4)(handle(FixedClock[String](4))(resume(read(text), ok = true)))) == "order-1 shipped at 4")
  assert(shop4.did == Vector("Ship(order-1)"))
  println("stopping examples hold")
