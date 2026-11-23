# 15 · A short history

Nothing in this book was invented in it. The idea is the meeting of four lines of work, the oldest of them about
sixty years old, and this chapter says who found what, in order.

It is last on purpose. Read first, a history is a list of names. Read after the problems, it is the reason the
mechanism has the shape it has: almost every step was a *restriction* that made an earlier, more powerful idea
safe to use.

A word on how it is written. I am an engineer, not a historian of programming languages. Every author, title,
venue and year below is as the reference lists of my libraries' two companion texts give them — "The theory of
Okay" and the continuations book — and where those lists are silent I have left the fact out rather than supply
it from memory. So the gaps here are gaps in my notes, not judgements about who mattered.

## 15.1 A computation is not a value

The first line starts with types. A pure function returns a value. A program does more: it consults state,
fails, writes output, waits. Eugenio Moggi's insight was that "a computation of an `A`" and "a value of type
`A`" are different types, and that the difference has structure: for each *notion of computation* there is a
type constructor with two operations, one to return a value and one to put one computation after another,
obeying three laws. That structure is a monad, and Moggi showed that state, exceptions, non-determinism,
continuations and input-output are all instances.

Philip Wadler carried the idea into programming practice: monads are how a pure language *expresses* effects,
and the laws are what make such programs safe to refactor.

This is requirement 1 of chapter 5 — the type says what the code does — and it is where the tradition begins.
Its limit, also from chapter 5, is the plural: two monads do not combine by themselves.

## 15.2 The rest of the program, and its boundary

The second line is about control, and it is the older one.

Before the continuation was a feature it was a description. To say what evaluating an expression means, you
say: here is the value, and here is the rest of the computation waiting for it. Writing programs so that the
rest is always explicit turns `goto`, exceptions, loops and coroutines into one vocabulary.

Then it became a feature: `call/cc` hands a program its continuation as a function to store, call later, or
call twice. Generators, backtracking and exceptions can be built from it. And it has the defect that the rest
of this line exists to repair: the captured continuation is the rest of the *whole* program — the caller, the
caller's caller, the server's request loop. A library that captures one takes hold of code it has never met.
There is no boundary to reason about, because there is no boundary.

**Matthias Felleisen** introduced the **prompt**: mark a place, and capture only up to the mark. The captured
piece is now finite. It can be given a type, it returns like an ordinary function, and it can be invoked more
than once without the rest of the world being re-entered.

**Olivier Danvy and Andrzej Filinski** gave delimited control its standard pair, `shift` and `reset`, and its
typing discipline. `reset` marks the boundary; `shift` captures the rest up to the nearest one and hands it to
a function.

Then the result that connects this line to the first. **Filinski's** theorem: `shift` and `reset` can express
any monadic effect. State, exceptions, non-determinism are each a particular way of invoking, not invoking, or
twice invoking the captured continuation. A language that has delimited control at the bottom need not build in
any other effect. Chapter 2 of this book, with its "zero times, once, many times", is that theorem told with a
bank.

**R. Kent Dybvig, Simon Peyton Jones and Amr Sabry** later made the prompt a first-class value with an
identity, so that an inner boundary can be crossed on purpose to reach a named outer one.

## 15.3 A system that got there first

One step is routinely left out of this lineage, and it came decades before the theory tidied it.

The Lisp family had exception systems in which the handler runs *before* the stack is unwound, and may choose
to resume. Smalltalk-80's exceptions could answer `resume:`. Common Lisp's condition system separated three
roles that one `catch` conflates: signalling a problem, deciding what to do about it, and the named restarts
offered by the code that knows the situation to the code that knows the policy.

That is chapter 6's importer and its malformed row, in production, long before anyone called it a handler. The
lesson is not about Lisp. "Unwind first, then decide" was a choice the mainstream languages made, and it
discards what the decision often needs.

## 15.4 Programs as data

The third line is the one a working engineer meets first, usually without the name.

A free monad turns "a program using these operations" into a data structure: leaves are values, nodes are
operations, and sequencing only grows the tree. An interpreter is then an ordinary function over the tree, and
changing the interpretation needs no change to the program. **Wouter Swierstra's** "Data types à la carte" made
this the standard recipe for extensible interpreters.

It had the two troubles chapter 5 named. Left-nested sequencing made naive implementations quadratic; **Janis
Voigtländer** diagnosed it and proposed a transformation, and **Atze van der Ploeg and Oleg Kiselyov** gave
another answer in "Reflection without remorse". And the classical construction demanded that every vocabulary
of operations be a functor, shaped to carry its own continuation; **Oleg Kiselyov and Hiromi Ishii** removed
that demand with the *freer* monad, which stores the continuation beside the operation. That is exactly the
`Ask(op, rest)` of chapter 3.

## 15.5 Operations first, and then handlers

The fourth line is where the name of this book's subject comes from.

**Gordon Plotkin and John Power** reframed Moggi's monads from the inside. An effect is not first a type
constructor but a set of **operations** with equations: non-determinism *is* `choose`, state *is* `get` and
`put`, and the monad arises from that algebraic theory. Thinking operation-first separates two things: a
program performs operations; what they mean is decided elsewhere.

"Elsewhere" got its name from **Plotkin and Matija Pretnar**: a **handler** gives each operation an
interpretation that receives the operation's payload *and the continuation of the program at that point*.
Exceptions discard the continuation; state threads a value through it; non-determinism calls it several times.
Handlers generalise `try`/`catch` from "catch a throw" to "catch any operation", and an operation not handled
passes outward to the next handler.

Here the lines meet, and they are the two halves of chapter 2b arriving from different decades: the operations
as data from sections 15.4 and 15.5, the delimited rest from section 15.2. A handler is a delimiter that knows
what it catches; a capture is a handler with no signature. The restriction that effects add to delimited
control is a good one: an operation has a type, so a reader knows what may happen without reading the
handler. That handlers and delimited control can each define the other is due to **Ohad Kammar, Sam Lindley
and Nicolas Oury**; the comparison of the three ways to give a programmer user-defined effects — handlers,
monadic reflection, delimited control — is **Yannick Forster, Kammar, Lindley and Pretnar's**.

One refinement belongs here because chapters 2b and 7 lean on it. Plotkin and Power's operations are
*algebraic*: what follows one can be moved inside it. Catching a failure is not, and neither is anything else
that acts on a stretch of a program; that is why Plotkin and Pretnar made handling a separate notion. **Nicolas
Wu, Tom Schrijvers and Ralf Hinze** later gave the other reading: operations that hold a sub-program, *scoped*
operations, as a kind of their own. Section 7.6 of this book writes a transaction that way, and finds what it
costs.

The last ingredient made the *set* of pending effects open. **Kiselyov, Amr Sabry and Cameron Swords'**
"extensible effects" replaced stacks of monad transformers with a single monad over an open union of
signatures. With it the plural problem of section 15.1 has its answer: the row of chapter 2.

## 15.6 The languages

Where handlers are built into a language, the sources I am working from mention these.

- **Koka**: Daan Leijen's language of row-polymorphic effect types. My notes name its row types as one of the
  two ancestors of the rows in my own libraries, the other being Kiselyov and Ishii's open unions.
- **Frank**: Sam Lindley, Conor McBride and Craig McLaughlin, whose paper's title is "Do be do be do".
- **Effekt**: Jonathan Immanuel Brachthäuser, Philipp Schuster and Klaus Ostermann — effect handlers by
  *capability passing*, where a permission is a value with a scope. Chapter 5's section on capabilities is this
  line.
- **OCaml**: effect handlers retrofitted onto the runtime by K.C. Sivaramakrishnan and co-authors. Their
  continuations are **one-shot** — resumable at most once — because a stack segment cannot be re-run without
  being copied.

The last point is the newest turn of the oldest lesson. The JVM's virtual threads make the same choice. It is
not a weakness of implementation: a one-shot continuation can be a piece of stack that is moved rather than
copied, which is what makes very many concurrent threads affordable. Keeping the rest of a program as an
immutable value instead, as chapters 6 and 11 need for running it twice, is a different trade with a different
cost. Neither is the right one in general.

Two more belong on the list, and my companion texts do not cover them, so I say only what their own authors
state.

- **Eff**: Andrej Bauer and Matija Pretnar's language, built to be programmed with the handlers of Plotkin and
  Pretnar's paper and nothing else. It is the first language designed around them.
- **Unison**: its *abilities* are effect handlers under another name; its documentation says the design follows
  Frank.

## 15.7 What the order tells you

Read as a sequence, the history is an argument.

| step | what it gave up | what that bought |
| --- | --- | --- |
| the continuation as a description | — | a way to say what control flow means |
| `call/cc` | — | first-class control, and a problem |
| prompts and delimiters | the whole rest of the program | a finite captured piece |
| `shift` and `reset` | free choice of what is re-delimited | composition, and types |
| first-class prompts | one anonymous boundary | boundaries that can be named |
| handlers | arbitrary points of capture | operations with a signature |
| one-shot runtimes | resuming twice | cheap threads on real hardware |

Unrestricted power was found first and set aside, not because it was hard to build but because nobody could
reason about a boundary that did not exist. Every later row gave something up and got a guarantee for it. That
is also the design advice of this book in one line: when a handler in your code can do *anything* with the rest
of a program, ask which restriction would let a reader know what it does.

Chapter 5 has the same rows as mechanisms an engineer can use today, with what each does well (sections 5.9b
to 5.9d are callbacks, `call/cc`, and `shift` with `reset`). This table is their order in time; that one is
their place on the two halves.

## 15.8 The papers

In order of year, with Moggi's two papers kept together at the head. All but five are as my companion texts
give them; the five added for this book are the two "Coeffects" and "Effects as capabilities" papers that
chapters 10 and 12 use, Bauer and Pretnar's on Eff, Unison's documentation, and Wu, Schrijvers and Hinze's on
scoped operations, which chapters 2b and 7 use.

- Eugenio Moggi. *Computational lambda-calculus and monads.* LICS 1989.
- Eugenio Moggi. *Notions of computation and monads.* Information and Computation 93(1):55–92, 1991.
- Adele Goldberg, David Robson. *Smalltalk-80: The Language and its Implementation.* Addison-Wesley, 1983.
- Matthias Felleisen. *The theory and practice of first-class prompts.* POPL 1988.
- Olivier Danvy, Andrzej Filinski. *A functional abstraction of typed contexts.* DIKU report 89/12, 1989.
- Olivier Danvy, Andrzej Filinski. *Abstracting control.* LISP and Functional Programming 1990.
- Guy L. Steele Jr. *Common Lisp the Language*, 2nd edition. Digital Press, 1990.
- Philip Wadler. *The essence of functional programming.* POPL 1992.
- Andrzej Filinski. *Representing monads.* POPL 1994.
- Philip Wadler. *Monads for functional programming.* In Advanced Functional Programming, LNCS 925, Springer,
  1995.
- Kent M. Pitman. *Condition handling in the Lisp language family.* In *Advances in Exception Handling
  Techniques*, LNCS 2022, 2001.
- Gordon Plotkin, John Power. *Notions of computation determine monads.* FoSSaCS 2002.
- Gordon Plotkin, John Power. *Algebraic operations and generic effects.* Applied Categorical Structures
  11(1):69–94, 2003.
- R. Kent Dybvig, Simon Peyton Jones, Amr Sabry. *A monadic framework for delimited continuations.* JFP
  17(6):687–730, 2007.
- Wouter Swierstra. *Data types à la carte.* JFP 18(4):423–436, 2008.
- Janis Voigtländer. *Asymptotic improvement of computations over free monads.* MPC 2008.
- Gordon Plotkin, Matija Pretnar. *Handlers of algebraic effects.* ESOP 2009.
- Oleg Kiselyov, Amr Sabry, Cameron Swords. *Extensible effects: an alternative to monad transformers.* Haskell
  Symposium 2013.
- Ohad Kammar, Sam Lindley, Nicolas Oury. *Handlers in action.* ICFP 2013.
- Atze van der Ploeg, Oleg Kiselyov. *Reflection without remorse.* Haskell Symposium 2014.
- Daan Leijen. *Koka: Programming with row polymorphic effect types.* MSFP 2014.
- Tomas Petricek, Dominic Orchard, Alan Mycroft. *Coeffects: a calculus of context-dependent computation.*
  ICFP 2014.
- Nicolas Wu, Tom Schrijvers, Ralf Hinze. *Effect handlers in scope.* Haskell Symposium 2014.
- Andrej Bauer, Matija Pretnar. *Programming with algebraic effects and handlers.* Journal of Logical and
  Algebraic Methods in Programming 84(1):108–123, 2015.
- Oleg Kiselyov, Hiromi Ishii. *Freer monads, more extensible effects.* Haskell Symposium 2015.
- Yannick Forster, Ohad Kammar, Sam Lindley, Matija Pretnar. *On the expressive power of user-defined effects:
  effect handlers, monadic reflection, delimited control.* ICFP 2017.
- Sam Lindley, Conor McBride, Craig McLaughlin. *Do be do be do.* POPL 2017.
- Daan Leijen. *Type directed compilation of row-typed algebraic effects.* POPL 2017.
- Jonathan Immanuel Brachthäuser, Philipp Schuster, Klaus Ostermann. *Effekt: capability-passing style for
  type- and effect-safe, extensible effect handlers in Scala.* Journal of Functional Programming 30, 2020.
- Jonathan Immanuel Brachthäuser, Philipp Schuster, Klaus Ostermann. *Effects as capabilities: effect handlers
  and lightweight effect polymorphism.* OOPSLA 2020.
- K.C. Sivaramakrishnan, Stephen Dolan, Leo White, Tom Kelly, Sadiq Jaffer, Anil Madhavapeddy. *Retrofitting
  effect handlers onto OCaml.* PLDI 2021.
- The Unison language documentation, "Abilities and ability handlers" (unison-lang.org).
