// The laws of code/laws-expected.txt in Koka, a language BUILT AROUND EFFECT TYPES (chapter 4, by the technique
// of chapter 3f), as in bank.kk. A program is a plain function whose type carries its row; `p under h` is
// `h { p() }`. Sequencing is Koka's own `val`, so lines 1 to 3 are laws of `val`, seen through a handler.
// `koka -e code/koka/laws.kk`

// the vocabulary of the chapter: a state, a failure, a clock, a choice

effect state
  fun get() : int
  fun put( n : int ) : ()

effect failure
  ctl refuse( why : string ) : a

effect clock
  fun now() : int

effect choice
  ctl choose( options : list<int> ) : int

// handlers. Each removes its effect from the row: <state|e> becomes e

// the state: the cell is a local variable of the handler, and its content comes back beside the result
fun cell( s0 : int, action : () -> <state|e> a ) : e (a, int)
  var s := s0
  with handler
    return(x) (x, s)
    fun get() s
    fun put(n) s := n
  action()

// zero resumptions: `resume` is not called
fun refusals( action : () -> <failure|e> a ) : e either<string,a>
  with handler
    return(x) Right(x)
    ctl refuse(why) Left(why)
  action()

fun fixed-clock( at : int, action : () -> <clock|e> a ) : e a
  with fun now() at
  action()

// every option taken: the rest resumed once for each, in order
fun all-choices( action : () -> <choice|e> a ) : e list<a>
  with handler
    return(x) [x]
    ctl choose(options) options.flatmap( fn(o) resume(o) )
  action()

// the observer: `override` lets a clause ask the same operation of whoever is outside, and write it down
fun log( action : () -> <state|e> a ) : <state|e> (list<string>, a)
  var lines := []
  with override handler
    return(x) (lines.reverse, x)
    fun get()
      val x = get()
      lines := Cons("Get=" ++ x.show, lines)
      x
    fun put(n)
      put(n)
      lines := Cons("Put(" ++ n.show ++ ")", lines)
  action()

// the programs of the twelve lines; the type of each is its row

fun store( n : int ) : state int
  put(n)
  n

fun bump() : state int
  val x = get()
  put(x + 1)
  x

fun stopped() : <state,failure> int
  put(1)
  val x = get()
  refuse("stop at " ++ x.show)
  store(9)

fun lost() : <state,failure> int
  put(1)
  refuse("no")

fun timed() : <state,clock> int
  val t = now()
  put(t)
  val x = get()
  x + t

fun forked() : <state,choice> int
  put(1)
  val x = choose([10, 20])
  val y = get()
  put(y + x)
  get()

fun held( r : (int, int) ) : string
  r.fst.show ++ ", cell " ++ r.snd.show

fun outcome( r : either<string,string> ) : string
  match r
    Right(a) -> "paid(" ++ a ++ ")"
    Left(why) -> "refused(" ++ why ++ ")"

// what a program over the state is seen to do: its questions with their answers, its value, the cell after
fun seen( p : () -> <state|e> int ) : e string
  val ((lines, a), s) = cell(0) { log(p) }
  lines.join(" ") ++ " => " ++ held((a, s))

fun main()
  // 1-3. sequencing: a value bound and used, a program bound and returned, and grouping
  println("1 done then f: " ++ seen { val x = 5; store(x) } ++ " = " ++ seen { store(5) })
  println("2 p then done: " ++ seen { val x = bump(); x } ++ " = " ++ seen(bump))
  val f = fn(x) store(x + 1)
  val g = fn(y) { put(y * 2); y }
  val first-two = fn() { val x = get(); f(x) }
  val last-two = fn(x) { val y = f(x); g(y) }
  println("3 grouping: " ++ seen { val y = first-two(); g(y) } ++ " = " ++ seen { val x = get(); last-two(x) })

  // 4-5. what a handler makes of a program that only returns, and of one program after another
  println("4 handling done: " ++ held(cell(3) { 7 }) ++ " | " ++ outcome(refusals { 7 }.map(show)))
  val whole = cell(0) { val a = bump(); val b = bump(); a + b }
  val (a, s) = cell(0, bump)
  val (b, s2) = cell(s, bump)
  println("5 handling a sequence: " ++ held(whole) ++ " = " ++ held((a + b, s2)))

  // 6. a handler sees every operation of its effect, in order, and nothing of another's
  val ((lines, r), s6) = cell(0) { log { refusals(stopped) } }
  println("6 its own and no other's: " ++ lines.join(" ") ++ " => " ++ outcome(r.map(show)) ++ ", cell " ++ s6.show)

  // 7. a question nobody answers: `cell(0) { timed() }` has the row <clock>, and a main that is left with
  // that row is refused by the compiler. unanswered.kk is that program.
  println("7 nobody answers: does not compile: main is left with the effect clock, unhandled")

  // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
  val inside = outcome(refusals { cell(0, lost) }.map(held))
  val (refused, after) = cell(0) { refusals(lost) }
  println("8 state then failure: " ++ inside ++ " | failure then state: " ++ outcome(refused.map(show))
    ++ ", cell " ++ after.show)

  // 9. two handlers that only answer: either order
  println("9 two that only answer: " ++ held(cell(0) { fixed-clock(7, timed) }) ++ " = "
    ++ held(fixed-clock(7) { cell(0, timed) }))

  // 10-11. the rest resumed twice: a state handled inside the choice is each path's own, outside it is one
  println("10 twice, state inside: " ++ all-choices { cell(0, forked) }.map(held).join(" | "))
  val (paths, last) = cell(0) { all-choices(forked) }
  println("11 twice, state outside: " ++ paths.map(show).join(" | ") ++ ", cell " ++ last.show)

  // 12. what this handler owes its own operations: the four equations of a state
  val after-put = cell(0) { put(3); get() }.fst
  val last-put = cell(0) { put(3); put(4); get() }.fst
  val (x, y) = cell(0) { val x = get(); val y = get(); (x, y) }.fst
  val put-back = cell(5) { put(get()) }.snd
  println("12 the cell's own: " ++ after-put.show ++ " " ++ last-put.show ++ " " ++ x.show ++ "=" ++ y.show
    ++ " cell " ++ put-back.show)
