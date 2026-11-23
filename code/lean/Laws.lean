/-
THE LAWS (chapter 4) in Lean 4, on the tree of chapter 3b, twice over. First as THEOREMS: what holds of every
program and every handler, checked by Lean when it reads this file; no `sorry`, no axiom of my own. Then as the
twelve lines of code/laws-expected.txt, which every rendering under code/ prints.
`lean --run code/lean/Laws.lean`
-/

/-- a program over ANY signature `F`: done, or a question with the rest. The theorems of the first part are
about this type and so about every vocabulary, not about the one the lines use -/
inductive Prog (F : Type → Type) (α : Type) : Type 1 where
  | done : α → Prog F α
  | ask : {x : Type} → F x → (x → Prog F α) → Prog F α

namespace Prog
universe u
variable {F : Type → Type} {α β γ : Type} {δ : Type u}

def bind : Prog F α → (α → Prog F β) → Prog F β
  | .done a, f => f a
  | .ask op rest, f => .ask op (fun x => (rest x).bind f)

/-! ## Sequencing -/

/-- `x <- done a ; f(x)` is `f(a)` -/
theorem done_bind (a : α) (f : α → Prog F β) : (done a).bind f = f a := rfl

/-- `x <- p ; done x` is `p` -/
theorem bind_done (p : Prog F α) : p.bind done = p := by
  induction p with
  | done a => rfl
  | ask op rest ih => simp [bind, ih]

/-- grouping does not matter -/
theorem bind_assoc (p : Prog F α) (f : α → Prog F β) (g : β → Prog F γ) :
    (p.bind f).bind g = p.bind (fun a => (f a).bind g) := by
  induction p with
  | done a => rfl
  | ask op rest ih => simp [bind, ih]

/-- an operation is ALGEBRAIC: whatever follows it moves inside it. Here it is the definition of `bind` -/
theorem ask_bind {x : Type} (op : F x) (rest : x → Prog F α) (f : α → Prog F β) :
    (ask op rest).bind f = ask op (fun a => (rest a).bind f) := rfl

/-! ## A handler is a fold -/

/-- the fold: what to make of `done`, and what to make of a question given what was made of its rest -/
def fold (ret : α → δ) (on : {x : Type} → F x → (x → δ) → δ) : Prog F α → δ
  | .done a => ret a
  | .ask op rest => on op (fun x => fold ret on (rest x))

theorem fold_done (ret : α → δ) (on : {x : Type} → F x → (x → δ) → δ) (a : α) :
    fold ret on (done a) = ret a := rfl

theorem fold_ask (ret : α → δ) (on : {x : Type} → F x → (x → δ) → δ) {x : Type} (op : F x)
    (rest : x → Prog F α) : fold ret on (ask op rest) = on op (fun a => fold ret on (rest a)) := rfl

/-- handling one program after another: handle the first, and where it is done, handle what follows -/
theorem fold_bind (ret : β → δ) (on : {x : Type} → F x → (x → δ) → δ) (p : Prog F α) (f : α → Prog F β) :
    fold ret on (p.bind f) = fold (fun a => fold ret on (f a)) on p := by
  induction p with
  | done a => rfl
  | ask op rest ih => simp [bind, fold, ih]

/-- THE fold: anything that treats `done` and a question as the two clauses say is this fold. A handler has
no freedom beyond its clauses -/
theorem fold_unique (ret : α → δ) (on : {x : Type} → F x → (x → δ) → δ) (h : Prog F α → δ)
    (atDone : ∀ a, h (done a) = ret a)
    (atAsk : ∀ {x : Type} (op : F x) (rest : x → Prog F α), h (ask op rest) = on op (fun a => h (rest a))) :
    ∀ p, h p = fold ret on p := by
  intro p
  induction p with
  | done a => exact atDone a
  | ask op rest ih => simp [atAsk, fold, ih]

/-- sequencing is itself a fold: the handler that answers nothing and changes the end -/
theorem bind_fold (p : Prog F α) (f : α → Prog F β) : p.bind f = fold f (fun op rest => ask op rest) p := by
  induction p with
  | done a => rfl
  | ask op rest ih => simp [bind, fold, ih]

/-! ## A handler of some of the questions: its own, and no other's -/

/-- a handler in the shape of chapter 3: a clause for the questions it takes, `none` for the others -/
def handle (ret : α → Prog F β) (clause : {x : Type} → F x → Option ((x → Prog F β) → Prog F β)) :
    Prog F α → Prog F β :=
  fold ret (fun op rest => match clause op with
    | some mine => mine rest
    | none => ask op rest)

/-- its own question: the clause, given the rest ALREADY HANDLED -/
theorem handle_mine (ret : α → Prog F β) (clause : {x : Type} → F x → Option ((x → Prog F β) → Prog F β))
    {x : Type} (op : F x) (rest : x → Prog F α) (mine : (x → Prog F β) → Prog F β) (h : clause op = some mine) :
    handle ret clause (ask op rest) = mine (fun a => handle ret clause (rest a)) := by
  simp [handle, fold, h]

/-- another's question: it passes, still asked, and the handler goes on behind it -/
theorem handle_other (ret : α → Prog F β) (clause : {x : Type} → F x → Option ((x → Prog F β) → Prog F β))
    {x : Type} (op : F x) (rest : x → Prog F α) (h : clause op = none) :
    handle ret clause (ask op rest) = ask op (fun a => handle ret clause (rest a)) := by
  simp [handle, fold, h]

/-- a handler that takes nothing and ends in `done` is no handler -/
theorem handle_nothing (p : Prog F α) : handle done (fun _ => none) p = p := by
  induction p with
  | done a => rfl
  | ask op rest ih =>
    simp only [handle, fold]
    congr 1
    funext a
    exact ih a

/-! ## Running -/

/-- only a program with no question left has a value -/
def run : Prog F α → Option α
  | .done a => some a
  | .ask _ _ => none

/-- a question nobody answered: no value, whatever the rest was -/
theorem run_ask {x : Type} (op : F x) (rest : x → Prog F α) : run (ask op rest) = none := rfl

end Prog

instance {F : Type → Type} : Monad (Prog F) where
  pure := .done
  bind := Prog.bind

/-! ## The vocabulary of the twelve lines, and what its handlers owe -/

inductive Op : Type → Type where
  | get : Op Int
  | put : Int → Op Unit
  | refuse : String → Op Empty
  | now : Op Int
  | choose : List Int → Op Int

abbrev Program := Prog Op
open Prog (done ask)

def perform (op : Op x) : Program x := .ask op .done

/-- the state: a handled program is a function from the cell's content -/
def cell : Program α → Int → Program (α × Int)
  | .done a, s => .done (a, s)
  | .ask .get rest, s => cell (rest s) s
  | .ask (.put n) rest, _ => cell (rest ()) n
  | .ask (.refuse why) rest, s => .ask (.refuse why) (fun x => cell (rest x) s)
  | .ask .now rest, s => .ask .now (fun x => cell (rest x) s)
  | .ask (.choose options) rest, s => .ask (.choose options) (fun x => cell (rest x) s)

def refusals : Program α → Program (Except String α)
  | .done a => .done (.ok a)
  | .ask (.refuse why) _ => .done (.error why)
  | .ask .get rest => .ask .get (fun x => refusals (rest x))
  | .ask (.put n) rest => .ask (.put n) (fun x => refusals (rest x))
  | .ask .now rest => .ask .now (fun x => refusals (rest x))
  | .ask (.choose options) rest => .ask (.choose options) (fun x => refusals (rest x))

def fixedClock (time : Int) : Program α → Program α
  | .done a => .done a
  | .ask .now rest => fixedClock time (rest time)
  | .ask .get rest => .ask .get (fun x => fixedClock time (rest x))
  | .ask (.put n) rest => .ask (.put n) (fun x => fixedClock time (rest x))
  | .ask (.refuse why) rest => .ask (.refuse why) (fun x => fixedClock time (rest x))
  | .ask (.choose options) rest => .ask (.choose options) (fun x => fixedClock time (rest x))

/-- every option taken: the rest once for each -/
def allChoices : Program α → Program (List α)
  | .done a => .done [a]
  | .ask (.choose options) rest =>
    options.foldr
      (fun o later => (allChoices (rest o)).bind fun here => later.bind fun others => .done (here ++ others))
      (.done [])
  | .ask op rest => .ask op (fun x => allChoices (rest x))

def noted (line : String) (p : Program (List String × α)) : Program (List String × α) :=
  p.bind fun (later, a) => .done (line :: later, a)

/-- the observer: every question of the state asked again, and written down with its answer -/
def log : Program α → Program (List String × α)
  | .done a => .done ([], a)
  | .ask .get rest => .ask .get (fun x => noted s!"Get={x}" (log (rest x)))
  | .ask (.put n) rest => .ask (.put n) (fun x => noted s!"Put({n})" (log (rest x)))
  | .ask op rest => .ask op (fun x => log (rest x))

/-! What `cell` owes `Get` and `Put`: the four equations of a state, for every rest and every content. These
are laws of THIS handler, not of handlers -/

theorem get_after_put (n s : Int) (rest : Int → Program α) :
    cell (ask (.put n) fun _ => ask .get rest) s = cell (ask (.put n) fun _ => rest n) s := rfl

theorem put_after_put (n m s : Int) (rest : Unit → Program α) :
    cell (ask (.put n) fun _ => ask (.put m) rest) s = cell (ask (.put m) rest) s := rfl

theorem get_after_get (s : Int) (rest : Int → Int → Program α) :
    cell (ask .get fun x => ask .get fun y => rest x y) s = cell (ask .get fun x => rest x x) s := rfl

theorem put_what_was_got (s : Int) (rest : Unit → Program α) :
    cell (ask .get fun x => ask (.put x) rest) s = cell (rest ()) s := rfl

/-! The order of state and failure. With the cell handled first, inside, a `Put` before a refusal is not
seen; with the cell outside it is. One equation, true in one order -/

theorem put_then_refuse_inside (n s : Int) (why : String) (rest : Empty → Program α) :
    refusals (cell (ask (.put n) fun _ => ask (.refuse why) rest) s)
      = refusals (cell (ask (.refuse why) rest) s) := rfl

theorem put_then_refuse_outside (n s : Int) (why : String) (rest : Empty → Program α) :
    cell (refusals (ask (.put n) fun _ => ask (.refuse why) rest)) s = done (.error why, n) := rfl

theorem refuse_outside (s : Int) (why : String) (rest : Empty → Program α) :
    cell (refusals (ask (.refuse why) rest)) s = done (.error why, s) := rfl

/-- two handlers that only answer, each once and at once: either order, for every program -/
theorem cell_fixedClock (time : Int) (p : Program α) :
    ∀ s, cell (fixedClock time p) s = fixedClock time (cell p s) := by
  induction p with
  | done a => intro s; rfl
  | ask op rest ih =>
    intro s
    cases op <;> simp [fixedClock, cell, ih]

/-- handling a sequence under the state: handle the first part, and what follows from the content it left -/
theorem cell_bind (p : Program α) (f : α → Program β) :
    ∀ s, cell (p.bind f) s = (cell p s).bind (fun r => cell (f r.1) r.2) := by
  induction p with
  | done a => intro s; rfl
  | ask op rest ih =>
    intro s
    cases op <;> simp [Prog.bind, cell, ih]

/-- the observer changes nothing: with its lines thrown away, a logged program is the program -/
theorem log_unseen (p : Program α) :
    ∀ s, (cell (log p) s).bind (fun r => done (r.1.2, r.2)) = cell p s := by
  induction p with
  | done a => intro s; rfl
  | ask op rest ih =>
    intro s
    cases op <;> simp [log, noted, cell, cell_bind, Prog.bind, Prog.bind_assoc, ih]

/-- the two orders of state and failure, for every program: the cell handled inside is the cell handled
outside with its content forgotten where the program was refused -/
theorem refusals_cell (p : Program α) :
    ∀ s, refusals (cell p s) = (cell (refusals p) s).bind (fun r => done (r.1.map (fun a => (a, r.2)))) := by
  induction p with
  | done a => intro s; rfl
  | ask op rest ih =>
    intro s
    cases op <;> simp [refusals, cell, Prog.bind, ih]
    all_goals rfl

/-! ## The twelve lines -/

def put (n : Int) : Program Int := do
  perform (.put n)
  return n

def bump : Program Int := do
  let x ← perform .get
  perform (.put (x + 1))
  return x

def stopped : Program Int := do
  perform (.put 1)
  let x ← perform .get
  let _ ← perform (.refuse s!"stop at {x}")
  put 9

def lost : Program Int := do
  perform (.put 1)
  let _ ← perform (.refuse "no")
  return 0

def timed : Program Int := do
  let t ← perform .now
  perform (.put t)
  let x ← perform .get
  return x + t

def forked : Program Int := do
  perform (.put 1)
  let x ← perform (.choose [10, 20])
  let y ← perform .get
  perform (.put (y + x))
  perform .get

def Op.shown : Op x → String
  | .get => "Get"
  | .put n => s!"Put({n})"
  | .refuse why => s!"Refuse({why})"
  | .now => "Now"
  | .choose _ => "Choose"

/-- running, for the lines: a value, or the name of the question nobody answered -/
def ran : Program α → Except String α
  | .done a => .ok a
  | .ask op _ => .error op.shown

def held (r : Int × Int) : String := s!"{r.1}, cell {r.2}"

def outcome : Except String Int → String
  | .ok a => s!"paid({a})"
  | .error why => s!"refused({why})"

def seen (p : Program Int) : String :=
  match ran (cell (log p) 0) with
  | .ok ((lines, a), s) => s!"{" ".intercalate lines} => {a}, cell {s}"
  | .error op => s!"nobody answers {op}"

def shown (r : Except String String) : String :=
  match r with
  | .ok line => line
  | .error op => s!"nobody answers {op}"

def main : IO Unit := do
  IO.println s!"1 done then f: {seen ((Prog.done 5).bind put)} = {seen (put 5)}"
  IO.println s!"2 p then done: {seen (bump.bind .done)} = {seen bump}"
  let f := fun (x : Int) => put (x + 1)
  let g := fun (y : Int) => (put (y * 2)).bind fun _ => .done y
  IO.println s!"3 grouping: {seen (((perform .get).bind f).bind g)} = {
    seen ((perform .get).bind fun x => (f x).bind g)}"

  IO.println s!"4 handling done: {shown ((ran (cell (.done 7) 3)).map held)} | {
    shown ((ran (refusals (.done 7))).map outcome)}"
  let whole := cell (bump.bind fun a => bump.bind fun b => .done (a + b)) 0
  let pieces := (cell bump 0).bind fun (a, s) => (cell bump s).bind fun (b, s2) => .done (a + b, s2)
  IO.println s!"5 handling a sequence: {shown ((ran whole).map held)} = {shown ((ran pieces).map held)}"

  IO.println s!"6 its own and no other's: {shown ((ran (cell (log (refusals stopped)) 0)).map
    fun ((lines, r), s) => s!"{" ".intercalate lines} => {outcome r}, cell {s}")}"

  IO.println s!"7 nobody answers: {match ran (cell timed 0) with
    | .ok _ => "it ran"
    | .error op => op}"

  let inside := match ran (refusals (cell lost 0)) with
    | .ok (.ok r) => s!"paid({held r})"
    | .ok (.error why) => s!"refused({why})"
    | .error op => s!"nobody answers {op}"
  IO.println s!"8 state then failure: {inside} | failure then state: {
    shown ((ran (cell (refusals lost) 0)).map fun (r, s) => s!"{outcome r}, cell {s}")}"

  IO.println s!"9 two that only answer: {shown ((ran (cell (fixedClock 7 timed) 0)).map held)} = {
    shown ((ran (fixedClock 7 (cell timed 0))).map held)}"

  IO.println s!"10 twice, state inside: {
    shown ((ran (allChoices (cell forked 0))).map fun paths => " | ".intercalate (paths.map held))}"
  IO.println s!"11 twice, state outside: {shown ((ran (cell (allChoices forked) 0)).map
    fun (paths, s) => s!"{" | ".intercalate (paths.map toString)}, cell {s}")}"

  let first := fun (p : Program Int) (s : Int) => shown ((ran (cell p s)).map fun (a, _) => toString a)
  let twice := shown ((ran (cell (do let x ← perform .get; let y ← perform .get; return (x, y)) 0)).map
    fun ((x, y), _) => s!"{x}={y}")
  let back := shown ((ran (cell (do let x ← perform .get; perform (.put x)) 5)).map fun (_, s) => toString s)
  IO.println s!"12 the cell's own: {first (do perform (.put 3); perform .get) 0} {
    first (do perform (.put 3); perform (.put 4); perform .get) 0} {twice} cell {back}"
