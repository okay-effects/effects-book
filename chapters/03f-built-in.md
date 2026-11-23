# 3f · Languages with handlers built in

In the sixth family nothing is encoded. The language has a construct for asking and a construct for handling,
and its compiler and runtime know what they mean.

Two of these languages run here: OCaml 5 in `code/ocaml/bank.ml` and Koka in `code/koka/bank.kk`. They were
chosen because they sit at opposite corners of the design: one untyped and one-shot, one typed by rows and
multi-shot. Effekt, Eff, Unison and Frank are not installed on the machine this book's code runs on. They are
described from their own documentation, briefly, and **the one line of Effekt below is illustrative and was not
run**; I have left out anything whose syntax I was not sure of.

## What a language can do that a library cannot

**Give it syntax.** The program is direct style with no `yield`, no `for`, no marker — and the handler receives
the rest as a named thing it can call.

**Infer the row.** Where effects are in the type system, the compiler works out what a function may ask, and
the signature can be left unwritten.

**Optimise by kind of handler.** A compiler that sees a handler can tell whether it resumes at the end of its
clause (answer in place, no capture), never resumes (an exception), or keeps the rest. A library has to ask
its user to say which.

**Make the rest cheap in the runtime.** A runtime built for it can make the stack a chain of segments, so
that taking the rest is detaching a segment and resuming is attaching it.

The cost is the same in every case and is not technical: it is a choice of language, with that language's
ecosystem, hiring pool and tools.

## OCaml 5

OCaml has had effect handlers in the language and runtime since version 5.0. It is the first widely used
general-purpose language to have them, and the motive was concrete: to express concurrency — lightweight
threads, async I/O, generators — as libraries in direct style, without colouring functions.

From `code/ocaml/bank.ml`. The operations extend a type the standard library provides, each naming its answer;
the program is plain OCaml:

```ocaml
type _ Effect.t +=
  | Balance : string -> int Effect.t
  | Move : string * string * int -> unit Effect.t
  | Now : int Effect.t
  | Refuse : string -> 'a Effect.t

let pay src dst amount () =
  let have = perform (Balance src) in
  if have < amount then perform (Refuse (Printf.sprintf "%s has %d, needs %d" src have amount));
  perform (Move (src, dst, amount));
  let at = perform Now in
  Printf.sprintf "moved %d from %s to %s at %d" amount src dst at
```

A handler is a `match` on the body with `effect` cases; `k` is the rest:

```ocaml
let accounts (type a) (balances : (string, int) Hashtbl.t) (body : unit -> a) : a =
  match body () with
  | result -> result
  | effect Balance account, k -> continue k (of_ balances account)
  | effect Move (src, dst, n), k ->
    Hashtbl.replace balances src (of_ balances src - n);
    Hashtbl.replace balances dst (of_ balances dst + n);
    continue k ()
```

An interceptor performs the effect again inside its clause, where the enclosing handler is the next one out.
A question with no case passes outward by itself. It is chapter 2's notation with different punctuation.

Three decisions are worth knowing, on the axes of chapter 3a.

- **(b, e) The rest is a fibre and is resumed once.** A continuation is a stack segment allocated on the heap.
  `continue` resumes it. The rendering attempts a second `continue` on the same `k` and catches the exception
  the runtime raises, `Continuation_already_resumed`; that is its line 6. One-shot was chosen for speed and
  for safety with resources.
- **(f) Dropping the rest has a construct.** `discontinue k e` resumes the rest by raising `e` inside it, so
  that its clean-up runs. The handler for refusals in the rendering does this instead of forgetting `k`:

  ```ocaml
  let drop k = try ignore (discontinue k Left_early) with Left_early -> ()

  let refusals (type a) (body : unit -> a) : (a, string) result =
    match body () with
    | receipt -> Ok receipt
    | effect Refuse why, k -> drop k; Error why
  ```

  A `k` that is neither continued nor discontinued is simply garbage, and what it held is not released: the
  rule of §6.8 holds in a language with handlers built in as it does in a library.
- **(d) Effects are not in the types.** The type of `pay` is `string -> string -> int -> unit -> string`. It
  says nothing of what `pay` performs, and an effect nobody handles is an exception at run time. That kept
  existing code compiling. What the compiler does check is each answer: inside the case for `Balance`, `k`
  expects an `int`. The price showed while writing the rendering: each handler needs its result type written
  out (`(type a) … : a`), or the checker reports that a type would escape its scope.

What OCaml gives is the family of chapter 3d — direct style, the host's own stack traces, no colour — with the
handler as a language construct and no cost for code that does not use it. Eio, an I/O library for OCaml 5, is
built on these handlers. Jane Street maintains an extended OCaml with a system of modes; I am not sure enough
of the state of its effect-related work to describe it.

## Koka

Koka is a research language from Microsoft Research built around effect types. Every function's type carries
a row of effects, the row is inferred, and it is polymorphic: `map` has the effects of the function it is
given. From `code/koka/bank.kk`:

```koka
effect accounts
  ctl balance( account : string ) : int
  fun move( src : string, dst : string, amount : int ) : ()

effect failure
  ctl refuse( why : string ) : a

fun pay( src : string, dst : string, amount : int ) : <accounts,clock,failure> string
  val have = balance(src)
  if have < amount then refuse(src ++ " has " ++ have.show ++ ", needs " ++ amount.show)
  move(src, dst, amount)
  val at = now()
  "moved " ++ amount.show ++ " from " ++ src ++ " to " ++ dst ++ " at " ++ at.show
```

`<accounts,clock,failure> string` is chapter 2's `Text ! (Accounts + Clock + Failure)`, checked. A handler
removes its effect from the row, and says so in its type:

```koka
fun refusals( action : () -> <failure|e> string ) : e string
  with handler
    return(receipt) "paid: " ++ receipt
    ctl refuse(why) "refused: " ++ why
  action()
```

The two keywords in the declarations are axis (h) made visible. An operation declared `fun` is answered in
place: its handler returns a value and the program goes on, with no rest captured. One declared `ctl` gives its
handler the rest, as `resume`, to call zero, one or several times. So the common case is cheap by declaration,
and the general case is available. In the rendering `move` is a `fun`, and `balance` is a `ctl` for one reason:
line 6 resumes the rest of the program once for each supposed balance.

```koka
fun what-if( suppose : list<int>, action : () -> <accounts|e> string ) : e list<string>
  with handler
    return(outcome) [outcome]
    ctl balance(account) suppose.flatmap( fn(s) resume(s).map( fn(o) s.show ++ " -> " ++ o.strip ) )
    fun move(src, dst, n) ()
  action()
```

`resume` is called twice and the rendering prints line 6 as the tree renderings do — from a program written in
direct style. It is the only one of the sixteen that has both without a hand-written state machine.

An interceptor is a handler marked `override`: its clauses may ask the same operation of whoever is outside,
and its type keeps the effect in the row (`<accounts|e>` on both sides).

Koka compiles handlers by passing evidence — each function receives where its handlers are — and compiles to C
with reference counting in place of a tracing collector. On the axes: rows, inferred (d); evidence (c); many
resumptions for `ctl`, in place for `fun` (e, h). It is the most complete answer to "what would the notation of
chapter 2 look like as a real language". The whole rendering compiled at the first attempt from my reading of
the documentation, which is a small piece of evidence about how directly the model maps onto the language.

## Effekt

Effekt is a research language in which effects are capabilities and handlers are *lexical*: a handler brings a
capability into scope, functions that need it receive it, and the type system tracks which capabilities a
function value holds, so that one cannot be used after its handler has returned. A function type reads as a
result and the effects it needs:

```
// illustrative, not run here
def pay(src: String, dst: String, amount: Int): Unit / { Accounts, Failure }
```

It is chapter 3c's capability passing with the escape problem solved in the types and a real continuation
underneath, and it is the clearest demonstration that "capabilities" and "handlers" are one design seen from
two sides (Brachthäuser, Schuster, Ostermann, "Effects as Capabilities", OOPSLA 2020).

## Eff, Unison, Frank

**Eff** (Bauer and Pretnar) is the language in which handlers were first a programming construct and not only
a piece of semantics. It is small, and its continuations may be called more than once; most textbook examples
of handlers — choice, backtracking, state by passing — were first written in it.

**Unison** calls effects *abilities*. They appear in function types (a function from `Text` to `Nat` that may
ask `Accounts` is written `Text ->{Accounts} Nat`), and a handler pattern-matches on a request together with
its continuation. Unison is the one language here aimed squarely at industrial use beside OCaml, with its own
platform; abilities are how it expresses I/O, exceptions and remote computation.

**Frank** (Lindley, McBride, McLaughlin, "Do be do be do", POPL 2017) removes the handler as a separate
construct. A function may handle the effects of its *arguments*: ordinary application is the special case in
which it handles none. It is a research language and the reason to know it is the idea, which shows that the
split between "function" and "handler" is itself a design choice.

## What they have in common, and what they do not

| language | effects in types | handler found | resumptions | notable for |
| --- | --- | --- | --- | --- |
| OCaml 5 (run) | no | dynamically | one | a mainstream runtime; fibres; direct-style I/O |
| Koka (run) | rows, inferred | by evidence | many (`ctl`), in place (`fun`) | the types; compilation by evidence |
| Effekt | capabilities, tracked | lexically | — | capabilities that cannot escape |
| Eff | yes | dynamically | many | the original |
| Unison | abilities | by `handle` | — | effects for a distributed platform |
| Frank | abilities | by application | — | no separate handler construct |

A dash is a cell I have left empty rather than fill from memory.

The table makes one point that the rest of this part depends on. These languages were designed by people who
agree on what an effect handler is, and they still chose differently on every axis: typed or not, dynamic or
lexical, one resumption or many. The axes of chapter 3a are not artefacts of encoding the idea in a language
that lacks it. They are the design space itself.

## On the axes

**Choose one of these languages when** the choice of language is open, and the problem is one where effects
are the main structure of the program — a concurrency library, a language runtime, a research system, a new
service with no legacy. Then most of this book applies with less ceremony than any library can offer.
**Otherwise** — and this is most readers — the chapters before this one are how to have the idea in the
language you already use, and the next one says how well each language takes it.
