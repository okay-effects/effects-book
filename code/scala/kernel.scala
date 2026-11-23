// THE WHOLE IDEA IN ONE FILE (chapter 3). Not a library: the smallest thing that is honestly "effects and
// handlers", so that every later claim of the book can be checked against forty lines.
package book

/** an operation: a question as data, naming the type of its answer */
trait Op[A]

/** a program: it is done, or it asks an operation and holds the rest of itself */
enum Prog[+A]:
  case Done(a: A)
  case Ask[X, A](op: Op[X], rest: X => Prog[A]) extends Prog[A]

  def flatMap[B](f: A => Prog[B]): Prog[B] = this match
    case Done(a)       => f(a)
    case Ask(op, rest) => Ask(op, x => rest(x).flatMap(f))
  def map[B](f: A => B): Prog[B] = flatMap(a => Done(f(a)))

/** asking: the program that asks one question and is done with its answer */
def ask[X](op: Op[X]): Prog[X] = Prog.Ask(op, Prog.Done(_))
def done[A](a: A): Prog[A] = Prog.Done(a)

/** a handler: what to do when the program is done, and what to do with a question — given the question and the
 * rest of the program. None: not my question, let it pass to whoever is outside */
trait Handler[A, B]:
  def done(a: A): Prog[B]
  def on[X](op: Op[X], rest: X => Prog[B]): Option[Prog[B]]

/** a program under a handler is a program again: with the handler's questions answered, the others still asked */
def handle[A, B](h: Handler[A, B])(p: Prog[A]): Prog[B] = p match
  case Prog.Done(a) => h.done(a)
  case ask: Prog.Ask[x, a] =>
    val handled = (answer: x) => handle(h)(ask.rest(answer))
    h.on(ask.op, handled).getOrElse(Prog.Ask(ask.op, handled))

/** running: only a program with no question left has a value */
def run[A](p: Prog[A]): A = p match
  case Prog.Done(a)    => a
  case Prog.Ask(op, _) => sys.error(s"nobody answers $op")
