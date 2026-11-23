// The scenario of code/expected.txt in Koka, a language BUILT AROUND EFFECT TYPES (chapter 3f). The row of
// effects is in every function's type and is inferred. An operation declared `fun` is answered in place; one
// declared `ctl` gives its handler the rest, as `resume`, to call zero, one or many times.
// `koka -e code/koka/bank.kk`

effect accounts
  ctl balance( account : string ) : int
  fun move( src : string, dst : string, amount : int ) : ()

effect clock
  fun now() : int

effect failure
  ctl refuse( why : string ) : a

// the program. Its type is its row: <accounts,clock,failure> string
fun pay( src : string, dst : string, amount : int ) : <accounts,clock,failure> string
  val have = balance(src)
  if have < amount then refuse(src ++ " has " ++ have.show ++ ", needs " ++ amount.show)
  move(src, dst, amount)
  val at = now()
  "moved " ++ amount.show ++ " from " ++ src ++ " to " ++ dst ++ " at " ++ at.show

// handlers. Each removes its effect from the row: <accounts|e> becomes e

fun in-memory( ann0 : int, action : () -> <accounts|e> a ) : e (a, string)
  var ann := ann0
  var bob := 0
  with handler
    return(x) (x, "ann=" ++ ann.show ++ " bob=" ++ bob.show)
    ctl balance(account) resume(if account == "ann" then ann else bob)
    fun move(src, dst, n)
      if src == "ann" then ann := ann - n else bob := bob - n
      if dst == "ann" then ann := ann + n else bob := bob + n
  action()

fun fixed-clock( at : int, action : () -> <clock|e> a ) : e a
  with fun now() at
  action()

// zero resumptions: `resume` is not called
fun refusals( action : () -> <failure|e> string ) : e string
  with handler
    return(receipt) "paid: " ++ receipt
    ctl refuse(why) "refused: " ++ why
  action()

// an interceptor: `override` lets a clause ask the same operation of whoever is outside
fun journal( action : () -> <accounts|e> a ) : <accounts|e> (a, list<string>)
  var lines := []
  with override handler
    return(x) (x, lines.reverse)
    ctl balance(account)
      val n = balance(account)
      lines := Cons("Balance(" ++ account ++ ") -> " ++ n.show, lines)
      resume(n)
    fun move(src, dst, n)
      move(src, dst, n)
      lines := Cons("Move(" ++ src ++ "," ++ dst ++ "," ++ n.show ++ ") -> ()", lines)
  action()

fun limit( most : int, action : () -> <accounts,failure|e> a ) : <accounts,failure|e> a
  with override handler
    ctl balance(account) resume(balance(account))
    fun move(src, dst, n)
      if n > most then refuse("a move of " ++ n.show ++ " is over the limit of " ++ most.show)
      else move(src, dst, n)
  action()

// many resumptions: at balance the rest is resumed once per supposed answer
fun what-if( suppose : list<int>, action : () -> <accounts|e> string ) : e list<string>
  with handler
    return(outcome) [outcome]
    ctl balance(account) suppose.flatmap( fn(s) resume(s).map( fn(o) s.show ++ " -> " ++ o.strip ) )
    fun move(src, dst, n) ()
  action()

fun strip( outcome : string ) : string
  match outcome.starts-with("paid: ")
    Just(rest) -> rest.string
    Nothing -> outcome

fun main()
  val (paid, rich) = in-memory(100) { fixed-clock(42) { refusals { pay("ann", "bob", 30) } } }
  println("1 " ++ paid)
  println("1 balances: " ++ rich)

  val (refused, poor) = in-memory(10) { fixed-clock(42) { refusals { pay("ann", "bob", 30) } } }
  println("2 " ++ refused)
  println("2 balances: " ++ poor)

  val ((_, written), _) = in-memory(100) { fixed-clock(42) { journal { refusals { pay("ann", "bob", 30) } } } }
  written.foreach( fn(l) println("3 journal: " ++ l) )

  val (over, kept) = in-memory(100) { fixed-clock(42) { refusals { limit(50) { pay("ann", "bob", 80) } } } }
  println("4 " ++ over)
  println("4 balances: " ++ kept)

  val ((_, seen), _) = in-memory(10) { fixed-clock(42) { journal { refusals { pay("ann", "bob", 30) } } } }
  println("5 left early: the journal has " ++ seen.length.show ++ " line, " ++ seen.join("; "))

  val outcomes = what-if([100, 10]) { fixed-clock(42) { refusals { pay("ann", "bob", 30) } } }
  println("6 twice: " ++ outcomes.join(" | "))
