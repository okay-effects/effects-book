// MEANT NOT TO COMPILE (chapter 4, line 7 of code/laws-expected.txt for laws.kk): the program `timed` under
// cell(0) and no handler for the clock. Its row, <clock>, is inferred and is a legal type; what the compiler
// refuses is a main that still has it:
//   (1, 0): build error: there are unhandled effects for the main expression
//      unhandled effect: code/koka/unanswered/clock
// `koka -v0 -c code/koka/unanswered.kk` must fail (with --builddir=<tmp> to keep the build out of the tree).
// With `fixed-clock(7) { ... }` around `timed()`, as on line 9 of laws.kk, it compiles.

effect state
  fun get() : int
  fun put( n : int ) : ()

effect clock
  fun now() : int

fun cell( s0 : int, action : () -> <state|e> a ) : e (a, int)
  var s := s0
  with handler
    return(x) (x, s)
    fun get() s
    fun put(n) s := n
  action()

fun timed() : <state,clock> int
  val t = now()
  put(t)
  val x = get()
  x + t

fun main()
  println(cell(0, timed).fst)
