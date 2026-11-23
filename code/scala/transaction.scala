// The examples of chapter 7 §7.6 (a transaction as a node with a subtree), run and asserted.
package book

/** a bracket that is DATA: the operation holds its body, a program. There is no commit to ask for */
case class Atomic[A](body: Prog[A]) extends Op[A]
case class Rollback(why: String) extends Op[Nothing]
case class Insert(row: String) extends Op[Unit]

/** written on its own: it does not know whether it will run inside another bracket */
def charge(order: String, funds: Long, price: Long): Prog[Unit] = ask(Atomic(
  if funds < price then ask(Rollback(s"no funds for $order")) else ask(Insert(s"payment $order $price"))))

def place(order: String, funds: Long, price: Long): Prog[String] = ask(Atomic(
  for
    _ <- ask(Insert(s"order $order"))
    _ <- charge(order, funds, price)
  yield order))

/** what the handler owns: one connection, one bit (open or not), what is committed and what is not yet.
 * `conflicts` commits are refused first, as a database refuses one on a serialisation failure */
final class Connection(var conflicts: Int = 0):
  var open = false
  var said = Vector.empty[String]
  var rows = Vector.empty[String]
  var pending = Vector.empty[String]
  def begin(): Unit = { open = true; said :+= "BEGIN" }
  def rollback(): Unit = { pending = Vector.empty; open = false; said :+= "ROLLBACK" }
  def commit(): Boolean =
    if conflicts > 0 then { conflicts -= 1; rollback(); false }
    else { rows ++= pending; pending = Vector.empty; open = false; said :+= "COMMIT"; true }

/** the handler walks the tree. The first bracket it meets: BEGIN, walk the body, COMMIT. A bracket inside a
 * bracket: walk on. A rollback anywhere inside: the rest of the body is dropped. A refused commit: the body is a
 * value in the handler's hand, so it is walked again */
final class Transactions[A](c: Connection) extends Handler[A, Either[String, A]]:
  def done(a: A) = Prog.Done(Right(a))
  def on[X](op: Op[X], rest: X => Prog[Either[String, A]]) = op match
    case Insert(row) =>
      if c.open then c.pending :+= row else c.rows :+= row
      c.said :+= s"INSERT $row"
      Some(rest(()))
    case Rollback(why) => Some(Prog.Done(Left(why)))
    case Atomic(body) if c.open =>
      Some(handle(Transactions[X](c))(body).flatMap {
        case Right(x)  => rest(x)
        case Left(why) => Prog.Done(Left(why))
      })
    case Atomic(body) =>
      def walk(): Prog[Either[String, A]] =
        c.begin()
        handle(Transactions[X](c))(body).flatMap {
          case Right(x)  => if c.commit() then rest(x) else walk()
          case Left(why) => c.rollback(); Prog.Done(Left(why))
        }
      Some(walk())
    case _ => None

/** an interceptor that answers nothing: it notes which operations pass it */
final class Watching[A] extends Handler[A, A]:
  var saw = Vector.empty[String]
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) =
    saw :+= op.getClass.getSimpleName
    None

@main def transaction(): Unit =
  // two brackets in the code, one in the other: one BEGIN and one COMMIT
  val c1 = Connection()
  assert(run(handle(Transactions[String](c1))(place("o-1", funds = 100, price = 30))) == Right("o-1"))
  assert(c1.said == Vector("BEGIN", "INSERT order o-1", "INSERT payment o-1 30", "COMMIT"), c1.said)
  assert(c1.rows == Vector("order o-1", "payment o-1 30"))

  // the inner function alone: its bracket is now the first, so it is the transaction
  val c2 = Connection()
  assert(run(handle(Transactions[Unit](c2))(charge("o-2", funds = 100, price = 30))) == Right(()))
  assert(c2.said == Vector("BEGIN", "INSERT payment o-2 30", "COMMIT"), c2.said)

  // a rollback from the inner bracket ends the outer one: the order row is not kept
  val c3 = Connection()
  assert(run(handle(Transactions[String](c3))(place("o-3", funds = 10, price = 30))) == Left("no funds for o-3"))
  assert(c3.said == Vector("BEGIN", "INSERT order o-3", "ROLLBACK"), c3.said)
  assert(c3.rows.isEmpty)

  // a commit refused once: the handler holds the body, and walks it again. The program has no retry in it
  val c4 = Connection(conflicts = 1)
  assert(run(handle(Transactions[String](c4))(place("o-4", funds = 100, price = 30))) == Right("o-4"))
  assert(c4.said == Vector(
    "BEGIN", "INSERT order o-4", "INSERT payment o-4 30", "ROLLBACK",
    "BEGIN", "INSERT order o-4", "INSERT payment o-4 30", "COMMIT"), c4.said)
  assert(c4.rows == Vector("order o-4", "payment o-4 30"), "kept once")

  // a limit of these forty lines: a handler standing between the program and the transaction sees the bracket
  // go by, and not what is inside it. The body is handed whole to the handler of the bracket
  val c5 = Connection()
  val between = Watching[String]()
  assert(run(handle(Transactions[String](c5))(handle(between)(place("o-5", funds = 100, price = 30)))) == Right("o-5"))
  assert(between.saw == Vector("Atomic"), between.saw)
  println("transaction examples hold")
