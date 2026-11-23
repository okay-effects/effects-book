/-
The scenario of code/expected.txt in Lean 4, as PROGRAMS AS DATA (chapter 3b): the same tree as the Haskell
rendering, in a language that also checks that every function here terminates. The operations are a family
indexed by the type of the answer; matching on one tells the checker what its answer is; no cast exists.
`lean --run code/lean/Bank.lean`
-/

/-- an operation: a question as data, indexed by the type of its answer -/
inductive Op : Type → Type where
  | balance : String → Op Int
  | move : String → String → Int → Op Unit
  | now : Op Int
  | refuse : String → Op Empty

/-- a program: done, or a question with the rest. It lives one universe up, because it holds a type -/
inductive Prog (α : Type) : Type 1 where
  | done : α → Prog α
  | ask : {x : Type} → Op x → (x → Prog α) → Prog α

def Prog.bind : Prog α → (α → Prog β) → Prog β
  | .done a, f => f a
  | .ask op rest, f => .ask op (fun x => (rest x).bind f)

instance : Monad Prog where
  pure := .done
  bind := Prog.bind

def ask (op : Op x) : Prog x := .ask op .done

/-- the program -/
def pay (src dst : String) (amount : Int) : Prog String := do
  let held ← ask (.balance src)
  if held < amount then
    let never ← ask (.refuse s!"{src} has {held}, needs {amount}")
    nomatch never
  ask (.move src dst amount)
  let time ← ask .now
  return s!"moved {amount} from {src} to {dst} at {time}"

/-! handlers: each a structural recursion over the tree, which is why Lean accepts them as total -/

abbrev Balances := String → Int

def accounts (b : Balances) : Prog α → Prog (α × Balances)
  | .done a => .done (a, b)
  | .ask (.balance account) rest => accounts b (rest (b account))
  | .ask (.move src dst n) rest =>
    accounts (fun who => if who = src then b who - n else if who = dst then b who + n else b who) (rest ())
  | .ask op rest => .ask op (fun x => accounts b (rest x))

def fixedClock (time : Int) : Prog α → Prog α
  | .done a => .done a
  | .ask .now rest => fixedClock time (rest time)
  | .ask op rest => .ask op (fun x => fixedClock time (rest x))

/-- zero resumptions: the rest is not called -/
def refusals : Prog α → Prog (Except String α)
  | .done a => .done (.ok a)
  | .ask (.refuse why) _ => .done (.error why)
  | .ask op rest => .ask op (fun x => refusals (rest x))

def Op.shown : Op x → String
  | .balance account => s!"Balance({account})"
  | .move src dst n => s!"Move({src},{dst},{n})"
  | .now => "Now"
  | .refuse why => s!"Refuse({why})"

def noted (line : String) (p : Prog (α × List String)) : Prog (α × List String) :=
  p.bind fun (a, later) => .done (a, line :: later)

/-- an interceptor: the question is asked again for whoever is outside, and the answer written down -/
def journal : Prog α → Prog (α × List String)
  | .done a => .done (a, [])
  | .ask (.balance account) rest =>
    .ask (.balance account) (fun x => noted s!"Balance({account}) -> {x}" (journal (rest x)))
  | .ask (.move src dst n) rest =>
    .ask (.move src dst n) (fun x => noted s!"Move({src},{dst},{n}) -> ()" (journal (rest x)))
  | .ask op rest => .ask op (fun x => journal (rest x))

def limit (most : Int) : Prog α → Prog α
  | .done a => .done a
  | .ask (.move src dst n) rest =>
    if n > most then
      .ask (.refuse s!"a move of {n} is over the limit of {most}") (fun never => nomatch never)
    else .ask (.move src dst n) (fun x => limit most (rest x))
  | .ask op rest => .ask op (fun x => limit most (rest x))

/-- many resumptions: at a balance the rest is called once per supposed answer -/
def whatIf (suppose : List Int) : Prog α → Prog (List (Int × α))
  | .done a => .done [(0, a)]
  | .ask (.balance _) rest =>
    suppose.foldr
      (fun s later => (whatIf suppose (rest s)).bind fun here =>
        later.bind fun others => .done (here.map (fun (_, a) => (s, a)) ++ others))
      (.done [])
  | .ask op rest => .ask op (fun x => whatIf suppose (rest x))

/-- a bank that moves nothing -/
def dryRun : Prog α → Prog α
  | .done a => .done a
  | .ask (.move _ _ _) rest => dryRun (rest ())
  | .ask op rest => .ask op (fun x => dryRun (rest x))

def run : Prog α → Except String α
  | .done a => .ok a
  | .ask op _ => .error s!"nobody answers {op.shown}"

def outcome : Except String String → String
  | .ok receipt => s!"paid: {receipt}"
  | .error why => s!"refused: {why}"

def shownBalances (b : Balances) : String := s!"ann={b "ann"} bob={b "bob"}"

def edge (ann : Int) (p : Prog α) : Except String (α × Balances) :=
  run (accounts (fun who => if who = "ann" then ann else 0) (fixedClock 42 p))

def main : IO Unit := do
  let .ok (paid, rich) := edge 100 (refusals (pay "ann" "bob" 30)) | throw (IO.userError "1")
  IO.println s!"1 {outcome paid}"
  IO.println s!"1 balances: {shownBalances rich}"

  let .ok (refused, poor) := edge 10 (refusals (pay "ann" "bob" 30)) | throw (IO.userError "2")
  IO.println s!"2 {outcome refused}"
  IO.println s!"2 balances: {shownBalances poor}"

  let .ok ((_, written), _) := edge 100 (journal (refusals (pay "ann" "bob" 30))) | throw (IO.userError "3")
  for line in written do IO.println s!"3 journal: {line}"

  let .ok (over, kept) := edge 100 (refusals (limit 50 (pay "ann" "bob" 80))) | throw (IO.userError "4")
  IO.println s!"4 {outcome over}"
  IO.println s!"4 balances: {shownBalances kept}"

  let .ok ((_, seen), _) := edge 10 (journal (refusals (pay "ann" "bob" 30))) | throw (IO.userError "5")
  IO.println s!"5 left early: the journal has {seen.length} line, {"; ".intercalate seen}"

  let .ok outcomes := run (whatIf [100, 10] (dryRun (fixedClock 42 (refusals (pay "ann" "bob" 30)))))
    | throw (IO.userError "6")
  let lines := outcomes.map fun (s, r) =>
    match r with
    | .ok receipt => s!"{s} -> {receipt}"
    | .error why => s!"{s} -> refused: {why}"
  IO.println s!"6 twice: {" | ".intercalate lines}"
