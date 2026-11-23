// PROGRAMS TO GENERATE (chapter 4). A law is about every program; one example is one program. This is a
// small language of programs over the chapter's vocabulary, a generator of them written by hand (no
// library), and a REFERENCE: what a term means, computed directly, with no tree, no handler and no kernel.
// The property mains (`generated`, here and under path/) build each term on a kernel and compare.
package book.laws

enum Term:
  case Lit(n: Long)
  /** the value bound by the nearest `Then` around it; 0 where there is none */
  case Var
  case Get, Now
  /** the value of `t`, put into the cell, and returned */
  case Put(t: Term)
  /** refuse, naming the value of `t` */
  case Refuse(t: Term)
  case Choose(a: Long, b: Long)
  /** `a`, then `b`, their sum: two programs in sequence */
  case Add(a: Term, b: Term)
  /** `x <- a ; b`, where `b` says `Var` for `x` */
  case Then(a: Term, b: Term)
  /** what follows depends on an answer */
  case IfOdd(c: Term, a: Term, b: Term)

  def has(wanted: Term => Boolean): Boolean = wanted(this) || (this match
    case Put(t)         => t.has(wanted)
    case Refuse(t)      => t.has(wanted)
    case Add(a, b)      => a.has(wanted) || b.has(wanted)
    case Then(a, b)     => a.has(wanted) || b.has(wanted)
    case IfOdd(c, a, b) => c.has(wanted) || a.has(wanted) || b.has(wanted)
    case _              => false)

/** a generator with a seed: the same thousand terms on every run and on every kernel */
final class Terms(seed: Long):
  private var state = seed
  def below(n: Int): Int =
    state = state * 6364136223846793005L + 1442695040888963407L
    ((state >>> 33) % n).toInt
  def term(depth: Int): Term =
    import Term.*
    if depth == 0 then below(5) match
      case 0 => Lit(below(5).toLong)
      case 1 => Var
      case 2 => Get
      case 3 => Now
      case _ => Choose(below(3).toLong, 3L + below(3))
    else below(8) match
      case 0 => Put(term(depth - 1))
      case 1 => Refuse(term(depth - 1))
      case 2 | 3 => Add(term(depth - 1), term(depth - 1))
      case 4 | 5 => Then(term(depth - 1), term(depth - 1))
      case 6 => IfOdd(term(depth - 1), term(depth - 1), term(depth - 1))
      case _ => term(0)

/** THE REFERENCE. One path of a term, computed by plain recursion: the cell is passed along, a refusal is a
 * `Left`, the clock says `now` or is missing (`None`: the run stops there, as `Left("nobody answers Now")`),
 * and a choice takes its first option. `trace` is every question of the state in the order asked */
object Reference:
  import Term.*
  final case class Ran(trace: Vector[String], cell: Long, result: Either[String, Long])

  def eval(t: Term, x: Long, cell: Long, now: Option[Long]): Ran =
    def after(first: Ran)(next: Long => Ran): Ran = first.result match
      case Left(_) => first
      case Right(a) =>
        val second = next(a)
        Ran(first.trace ++ second.trace, second.cell, second.result)
    def at(cell: Long)(t: Term, x: Long): Ran = eval(t, x, cell, now)
    t match
      case Lit(n) => Ran(Vector.empty, cell, Right(n))
      case Var    => Ran(Vector.empty, cell, Right(x))
      case Get    => Ran(Vector(s"Get=$cell"), cell, Right(cell))
      case Now    => Ran(Vector.empty, cell, now.toRight("nobody answers Now"))
      case Put(t) => after(at(cell)(t, x))(n => Ran(Vector(s"Put($n)"), n, Right(n)))
      case Refuse(t) =>
        val first = at(cell)(t, x)
        after(first)(n => Ran(Vector.empty, first.cell, Left(s"at $n")))
      case Choose(a, _) => Ran(Vector.empty, cell, Right(a))
      case Add(a, b) =>
        val first = at(cell)(a, x)
        after(first)(n => { val r = at(first.cell)(b, x); r.copy(result = r.result.map(n + _)) })
      case Then(a, b) =>
        val first = at(cell)(a, x)
        after(first)(n => at(first.cell)(b, n))
      case IfOdd(c, a, b) =>
        val first = at(cell)(c, x)
        after(first)(n => at(first.cell)(if n % 2 != 0 then a else b, x))
