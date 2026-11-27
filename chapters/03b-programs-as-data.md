# 3b · Programs as data, and an interpreter

The first family is the one chapter 3 built: a program is a data structure, and something walks it. It runs
here in four languages — `code/scala/bank.scala`, `code/java/Tree.java`, `code/haskell/Bank.hs`,
`code/lean/Bank.lean` — and all four print every line of the scenario, the rest resumed twice included.

## What it is

The whole structure, in the rendering that says it most exactly (`code/haskell/Bank.hs`):

```haskell
data Op x where                          -- an operation, indexed by the type of its answer
  Balance :: String -> Op Integer
  Move    :: String -> String -> Integer -> Op ()
  Now     :: Op Integer
  Refuse  :: String -> Op Void

data Prog a where                        -- a program: done, or a question with the rest
  Done :: a -> Prog a
  Ask  :: Op x -> (x -> Prog a) -> Prog a
```

In the literature this is the *freer monad*: the free monad with the functor requirement removed, so that the
operations can be any indexed type. A handler is a function from programs to programs, written by cases:

```haskell
refusals :: Prog a -> Prog (Either String a)
refusals (Done a)             = Done (Right a)
refusals (Ask (Refuse why) _) = Done (Left why)        -- the rest is not called
refusals (Ask op rest)        = Ask op (refusals . rest) -- not mine: still asked, the handled rest behind it
```

Three lines, three behaviours of chapter 2. The same three lines exist in each of the four files.

## What it gives

**Everything of chapter 2, from a language that has functions and a way to tell cases apart.** No generators,
no coroutines, no runtime support, no compiler plugin. This is why the idea can be had in a language that was
not designed for it, and why the first implementation in any new language is usually this one.

**The rest is a function, so it can be called twice.** All four renderings print line 6. In the Haskell one the
handler is seven lines and the only notable thing in it is `mapM` over the supposed answers.

**The program exists before it runs.** It can be passed to a handler chosen later, run twice under two sets of
handlers, sent to a test. Where the first question does not depend on anything, it can be looked at without
running anything.

**"It cannot go around" holds by construction.** The only thing a `Prog` can do is be `Done` or `Ask`. An
interceptor placed between the program and the handler sees every `Ask`, because there is no other road to the
handler. Chapter 2 claimed this; of the families in this part, this one and generators (chapter 3d) deliver it
without a qualification.

**Handlers compose in any order, decided at the edge.** `journal (refusals p)` and `refusals (journal p)` are
both well-typed and mean different things: in the first the journal's lines survive a refusal, in the second
they are lost with the refused branch. The Haskell and Lean renderings, having no mutable state to hide behind,
must choose, and choose the first. The choice is one line at the place the program is run.

## What it costs

**An allocation for every step.** Each `Ask` is a node and a closure; each bind wraps the chain again.

**A notation in place of statements.** Haskell has `do`, Scala `for`, Lean `do`. Java has neither, and
`code/java/Tree.java` shows what that means for the program itself:

```java
static Prog<String> pay(String from, String to, long amount) {
  return ask(new Balance(from)).then(have ->
      (have < amount ? ask(new Refuse(from + " has " + have + ", needs " + amount)) : new Done<Void>(null))
          .then(ok -> ask(new Move(from, to, amount)))
          .then(moved -> ask(new Now()))
          .then(at -> new Done<>("moved " + amount + " from " + from + " to " + to + " at " + at)));
}
```

It is correct, and nobody would choose to write a codebase in it. In a language without a notation for
sequencing values, this family is right for the *interpreter* and wrong for the *programs*; chapters 3d and 3e
are how such languages get the programs back.

**The stack.** `then` and `handle` recurse. A long program overflows unless the interpreter is a loop with its
own stack — the next section.

**Left-nested binds.** `((a >>= f) >>= g) >>= h` walks the chain again for each bind, and a program built by a
left fold is quadratic in its length. The remedies are known: re-associate while stepping, keep the pending
functions in a queue, or build with a continuation-passing encoding and convert once. Any library in this
family has chosen one.

**Stack traces show the interpreter.** A failure inside a program is reported from inside `handle`, under a
chain of anonymous functions.

## What the host language decides: the type of an answer

The four renderings differ in one line that is worth the reader's attention, because it is the best single
predictor of how pleasant this family is in a given language.

When a handler matches `Balance(account)`, it must answer with a number, and the rest it was handed expects
whatever the operation's answer type is. Does the compiler know, inside that case, that the two are the same?

- **Haskell and Lean: yes.** The operations are a GADT, an indexed family; matching on `Balance` refines `x` to
  `Integer`. `rest (Map.findWithDefault 0 account b)` type-checks and `rest "hello"` does not. There is no cast
  in either file. Lean adds that every handler is checked to terminate, since each is a structural recursion
  over the tree.
- **Scala 3: yes in a `match` on a GADT**, which is how the handlers of `code/scala/examples.scala` pass an
  answer to `rest` without a cast. `handle` in `kernel.scala` has none either: where the answer type is
  hidden inside the node, a type pattern, `case ask: Prog.Ask[x, a]`, gives it a name (section 3.4).
- **Java: no.** A `switch` over a sealed interface narrows the *value* to `Balance` and does not learn that the
  method's type parameter `X` is `Long`. `Tree.java` has one cast, in a helper named `answer`, and every
  handler goes through it. The designs I know for avoiding it use the operation as a witness of its own answer
  type; one of my libraries says so in so many words (`okay-direct/docs/design.md`: "Java, like Kotlin, does
  not refine a type parameter in a `switch` case"). Chapter 3j shows that design built: no handler of okay-tiny
  has a cast, because the answer type is read off the operation where the clause is made, not learned in the
  `case`.
- **Rust, Go, C#, Swift, TypeScript:** no GADTs in the sense needed. The usual way out is to erase the answer
  to one dynamic type and check it where it is used, or to give each operation a method that carries its own
  continuation. Of my Rust machine I wrote: "Rust has no GADTs, so [the] loop typed by the answers is not
  portable; the casts are three, inside" (`okay-rust/machine/README.md`).

The second thing the host decides is the *row*. All four renderings here have a closed set of operations and no
row in the type: `run` fails on a question nobody answers. Opening the set — letting separately written effects
combine, and having the type say which are pending — needs a type-level list or union and a way to compute
with it: type classes and type families in Haskell, implicits or match types in Scala, and in Java, less than I
had thought: an intersection bound on a type variable, `<R extends Stock & Refusal>`, is a set that javac
checks (chapter 3j). This is where libraries of this family spend most of their complexity, and where their
error messages come from.

## From a tree to a machine

A library of this family that is meant for production is not the two-case tree. It is a **machine**: a few
more kinds of node, and a loop.

The step is the one every compiler course makes from a recursive evaluator to an abstract machine (the name
*CEK* is from that tradition: Control, Environment, Kontinuation). Bind becomes a node instead of a function
that rebuilds the chain. "A program under a handler" becomes a node. The loop keeps an explicit stack of what
is waiting, cut into levels by the handlers that are installed, and the rest of the program at any moment is a
slice of that stack. The model of my reference implementation has eight nodes and states its continuation in
one line: "k = levels, the lowest first — immutable" (`okay-effects/SPEC.md`).

What the machine gives over the tree:

- **constant host stack**, so depth is bounded by memory. Before one of my Java libraries had an explicit
  stack, with each handler's body in a Java frame of its own, it "overflowed the stack at about 1 000 nested
  handlers" (`okay-java/docs/architecture.md`);
- **a rest that is data** — frames in a list — instead of a closure. Immutable frames are resumed twice by
  being put back twice, with no copy, and they are at least in principle something that could be written down;
- **a place to hang the things the tree lacks**: finalizers as frames, a handler's state as part of a level,
  an answer given in place without touching the stack.

What it costs is that it is a machine: several hundred lines that must be exactly right, a dispatch per node,
and one loop through which every program runs, which a JIT compiler profiles as a single piece of code.
Chapter 3i grows the other road in full — the tree kept, made stack-safe and given rows, in four stages that
run — so that the engineering this section summarises can be read line by line.

On my own libraries, for scale and nothing more: one interface with a machine and a plain `Free` tree behind
it, the same programs on both, JMH with two forks and five measured iterations in one session — a thousand
maps took 6.9 µs on the machine and 12.0 on the tree; a thousand asks across four handlers of other effects,
23.5 against 70.1 (`okay-simple/README.md`, "Measured"). The gap grows with the number of handlers crossed,
because the tree re-wraps the rest at every handler an operation passes and the machine does not.

## Where a handler's state lives

The Scala and Java renderings keep balances in a mutable field of the handler. The Haskell and Lean ones
cannot, and thread the state through: `accounts :: Balances -> Prog a -> Prog (a, Balances)`.

This looks like a matter of taste and is not. With a mutable handler, a rest resumed twice sees *one* state:
the second run starts from whatever the first run left. Chapter 6 runs that hazard on purpose (two futures, one
bank, two moves). With state passed as a value, each resumption starts from the state as it was at the
question. Neither is wrong. A design must say which it is, per handler, and a machine that keeps handler state
in its levels has to decide what "the state at the question" means when the rest is put back. Chapter 4
(section 4.6) states the law this is an instance of, and counts how often a handler with a mutable field
breaks it on generated programs.

## Who builds this way

Described from their own documentation, and only as far as I am sure.

- **Haskell.** The `free` package is the classical encoding. `freer-simple` is a library of extensible effects
  in this family: programs are data over an open union of effects, interpreted by handlers. `polysemy` has the
  same model for its user — effects are data types, interpreters give them meaning — and I do not describe its
  internal representation here.
- **Scala.** Cats provides `Free`. The `Eff` monad of atnos-eff follows the "freer monads, more extensible
  effects" design. ZIO and Cats Effect are also, underneath, values of a data type run by an interpreter loop;
  what they interpret is their own fixed set of primitives and not a user's operations, which is the
  distinction chapter 5 draws.
- **TypeScript.** Effect describes an `Effect` as a value that a runtime interprets.
- **Lean.** Monadic programs over inductive types are the language's ordinary style; a free-monad port of mine
  proves the monad laws and what each standard handler makes of its operations, with the caveat that the
  theorems are about a reference definition and what runs is a second, faster one "not proved equal to the
  reference" (`okay-lean/README.md`).
- **Mine**, as one set among these: a machine in Scala 3, the same model in Java 25 and Rust held to one
  conformance digest, and ports to TypeScript, Kotlin and Zig. They are described in chapter 3g, language by
  language, beside the others.

## On the axes

| axis | this family |
| --- | --- |
| b. the rest | a closure (tree) or frames as data (machine) |
| c. finding the handler | by matching, outward through the handlers; cached or indexed in a serious machine |
| d. the types | whatever the host can express: from nothing to full rows |
| e. resumptions | many |
| f. leaving early | nothing in the tree; finalizers as nodes in a machine; a kept rest releases nothing |
| g. storing the rest | no for closures; possible for frames that are data |
| h. cost | allocation per step; in-place answers must be added deliberately |
| i. the host | its exceptions bypass handlers unless converted; traces show the interpreter |

**Choose it when** you need the rest more than once, when control and audit are the point (the program must
not be able to go around), when the language gives nothing better, or when you are building the thing other
techniques will sit on. **Think twice when** the programs are long hot loops with nothing to intercept, or the
language has no notation for sequencing and the programs would be written by hand.
