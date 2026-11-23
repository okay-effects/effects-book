# 2 · Asking

This chapter states the idea exactly. It has five notions — operation, program, handler, the rest of the
program, row — and each is small. I will go slowly, because everything later is these five used in different
places, and a reader who has them firmly will find the rest of the book easy.

The examples use a small bank. They are written in a notation that belongs to this book and to no programming
language, because the idea belongs to none. Section 2.0 is the whole of the notation. Every example of this
chapter also exists as code that runs, sixteen times over in thirteen languages, under `code/`; chapter 3 builds one of
those renderings line by line, and chapters 3a to 3h are about how the others differ and why.

## 2.0 The notation

| written | read |
| --- | --- |
| `Balance(account) -> Number` | an operation, and the type of its answer |
| `-> never` | an operation that is not answered |
| `x <- Balance("ann")` | ask, and call the answer `x` |
| `Move("ann", "bob", 30)` on a line of its own | ask, and do not name the answer |
| `done v` | finish with the value `v` |
| `Balance("ann") ; have => …` | a program stopped at a question: the question, then the rest, given the answer |
| `handler H : A ! (E + R) => B ! R` | `H` answers the effect `E`; a program ending in `A` becomes one ending in `B` |
| `Op(…), rest => …` | inside a handler: what to do with this question, given the rest of the program |
| `rest(v)` | continue the rest of the program with the answer `v` |
| `p under h` | the program `p` with the handler `h` applied; a program again |
| `A ! (E1 + E2)` | a program that ends in `A` and may ask the effects `E1` and `E2`: its row |
| `x <- p`, where `p` is a program | run `p` here, as part of this program, and call what it ends in `x` |
| `back <- rest(v)` | inside a handler: continue the rest, and call what it ends in `back` |
| `if r is refused(why) then …` | tell the cases of a result apart, naming what a case holds |
| `run(p)  =  v` | a result: running `p` gives `v`; the section says which file under `code/` asserts it |

Nothing else is used. Assignment (`:=`), `if`, `for each`, strings and lists mean what they mean everywhere.
The last four rows are not needed in this chapter; chapters 6 to 12 use them.

## 2.1 An operation is a question, as data

Start with what the business logic needs from outside. Not *how* it gets it — what it needs.

```
effect Accounts
  Balance(account)        -> Number
  Move(from, to, amount)  -> Unit         -- nothing comes back but "it is done"

effect Clock
  Now                     -> Time

effect Failure
  Refuse(why)             -> never        -- nothing comes back at all
```

`Balance("ann")` is an ordinary value. It does not look anything up. It can be printed, compared, stored in a
list, sent over a wire, written to a file. It is a *description of a request*.

Each operation names the type of its answer. `-> Number` reads: a question whose answer is a number. `-> never`
reads: a question that is never answered — asking it is the end of the road. That one line will turn out to be
all of exception handling.

A set of operations that belong together is an **effect**. `Balance` and `Move` are the effect "accounts". `Now`
is the effect "clock". `Refuse` is "failure". An effect is a vocabulary: the words a program may use when it
speaks to the outside.

How a language writes this differs, and the differences are small: a family of case classes or records, the
cases of one enumeration, the methods of an interface, the constructors of an indexed data type. What differs
more is whether the language can keep the link between an operation and the type of its answer without help;
chapter 3b shows a language that can and one that needs a cast.

**What to notice.** Nothing has been said about databases. The effect is designed from the side of the program:
what does business logic need to *say*? This is the first design decision in any system built this way, and
chapter 7 argues it is the most important one — the effects are the data model of the architecture.

## 2.2 A program is a value that asks

Now the logic.

```
pay(from, to, amount) : Text ! (Accounts + Clock + Failure) =
  have <- Balance(from)
  if have < amount then Refuse("{from} has {have}, needs {amount}")
  Move(from, to, amount)
  at <- Now
  done "moved {amount} from {from} to {to} at {at}"
```

Read it as it looks: ask for the balance; if it is too small, refuse; otherwise ask for the move; ask the time;
produce a receipt.

The important fact is what `pay("ann", "bob", 30)` is. It is **not an action**. No balance has been read. It is
a value: a program that, if run, will end with a text.

What is that value, concretely? It is one of two things:

- **done**, with a result; or
- **a question**, together with *what to do with the answer*.

`pay("ann", "bob", 30)` is the second kind. It is, literally, the pair

```
Balance("ann") ; have => … everything after the first line …
```

The first component is the question the program is asking right now. The second is a function from the answer
to the *next* program — which will again be either done or another question.

So a program is a chain: question, then (given an answer) question, then (given an answer) … then done. It is
built without anything happening, the way a recipe is written without anything being cooked.

Languages differ in how literally this pair exists. In some renderings it is a data structure you can print: a
node holding a question and a function. In others the program is ordinary code that *stops* at each question —
a generator at its `yield`, a thread parked in a call, a compiled state machine in one of its states — and the
pair is what you hold while it is stopped. The difference matters a great deal for what can be done with the
second component, and Part III is about it. For this chapter it is enough that the pair can always be had.

**What to notice.** `pay` contains no reference to any implementation. There is no `accounts` parameter that
was constructed somewhere, no import of a database driver. There is nothing to mock, because there is nothing
there. The function is complete with the whole outside world undefined.

## 2.3 A handler answers

A **handler** takes a program and answers the questions of one effect.

Here is one for accounts, with the balances held in memory:

```
handler InMemory(balances) : A ! (Accounts + R)  =>  A ! R
  done(a)                    => done a
  Balance(account), rest     => rest(balances[account])
  Move(from, to, n), rest    => balances[from] := balances[from] - n
                                balances[to]   := balances[to] + n
                                rest(unit)
```

A handler has two parts.

The `done` line says what to do when the program finishes. Here: nothing special, pass the result on.

Each other line is called with a question, **and with the rest of the program**. Look at the line for
`Balance`. The handler looks the number up and calls `rest` with it — it hands the answer to the remainder of
the program, which goes on. A question that has no line is not this handler's: it passes through, still asked.

Applying a handler to a program gives **a program again** — one in which the handler's questions have been
answered and the others are still being asked:

```
pay("ann", "bob", 30) under InMemory(…)      -- still asks Now and Refuse; no longer asks Balance or Move
```

So handlers stack. Each takes away one effect:

```
run( pay("ann", "bob", 30) under InMemory({ann: 100}) under FixedClock(42) under Refusals )
```

Read from the left: the program; then accounts answered; then the clock answered; then refusals answered.
Nothing is left to ask, so `run` can produce the value: `paid("moved 30 from ann to bob at 42")`.

That one line is the whole application. Which database, which clock, how failure is reported — every decision
about the outside world is in that line and nowhere else. Chapter 7 is about what it means to build systems
whose every such decision is in one place.

**What to notice.** Change the line, not the program:

```
… under Postgres(pool)          -- production
… under InMemory({…})           -- a test
… under Recorded(journal)       -- last Tuesday, replayed
… under Remote(socket)          -- another process answers
```

`pay` is the same bytes in all four.

## 2.4 The rest of the program is a value

This is the notion that separates effects from everything that looks like them, so it gets the most space.

When a handler's line is called, the program has **stopped** at a question. It is not running. What is left of
it — every line after the question — is in the handler's hand, as `rest`. The handler now decides what happens
to it. There are exactly three things it can do.

**Continue it once.** `rest(answer)`. This is the ordinary case, and it is all that an interface call can
express: you asked, you got an answer, you went on.

**Not continue it.** Here is the handler for refusals:

```
handler Refusals : A ! (Failure + R)  =>  (paid(A) | refused(Text)) ! R
  done(a)             => done paid(a)
  Refuse(why), rest   => done refused(why)        -- rest is not called
```

On `Refuse(why)` it does not call `rest` at all. It answers the *whole computation* with `refused(why)`. The
rest of `pay` — the move, the clock, the receipt — is dropped. It never runs, because nobody ran it.

That is an exception: thrown at the question, caught at the handler. But there is no `throw` and no `try` in
the notation. Exceptions were not added; they fell out. And note that the handler also changed the *type* of
the result — a program that ended in `A` now ends in `paid(A) | refused(Text)`. The program did not have to say
it might fail in that way; the handler decided how failure is represented.

**Continue it more than once.** Take a question with several possible answers:

```
effect Choice
  Choose(options) -> one of the options

split : (Number, Number) ! Choice =
  a <- Choose([10, 20])
  b <- Choose([1, 2])
  done (a, b)
```

and a handler that continues the rest once for *each* option and collects what comes back:

```
handler AllChoices : A ! (Choice + R)  =>  List(A) ! R
  done(a)                 => done [a]
  Choose(options), rest   => all := []
                             for each o in options:
                               all := all ++ rest(o)      -- the same rest, called again
                             done all
```

`run(split under AllChoices)` is `[(10,1), (10,2), (20,1), (20,2)]`. The program was written as if it chose
once. The handler ran it down every path. This is backtracking search, non-determinism, "try all the
alternatives" — again with nothing added to the notation.

A different handler for the same `Choose` could take the first option only; or pick at random; or ask a person;
or ask a language model and keep the alternatives for later. The program `split` is the same in all of them.

So: zero times, once, many times. And there is a fourth possibility hidden in "once" — **once, but later**. The
handler can put `rest` somewhere — a table, a queue, a database row — and return. Whoever has the answer next
week calls it. The program has been suspended across time without being written as a state machine. Chapter 11
is about that.

Here the renderings part company, and it is the first thing to know about any implementation you meet. Zero
times and once are available in every one. *Many times* needs a rest that can be used again, and not every
technique has one: a rest that is a function or a piece of data can be called twice, a rest that is a stopped
generator or a parked thread cannot. *Later, in another process* needs a rest that can be written down, which
is rarer still. Of the sixteen renderings under `code/`, eight print the line for "the rest resumed twice" and
eight print the reason they cannot. Chapter 3a makes this an axis of the design, and chapter 3h says how to choose.

**The name.** In the literature, `rest` is called the *continuation*: the continuation of the program from this
point. The word has a reputation for difficulty that it does not deserve. It means "everything after here, as a
function of the answer". You have been looking at one for three pages.

**What to notice.** A handler is not a callback and not a wrapper. A wrapper stands *around* a call: it runs
before, calls through, runs after. It can skip the call. It cannot skip *the rest of the caller*, cannot run the
rest of the caller twice, cannot put the rest of the caller in a database. A handler holds the caller's future.
That is the difference in kind, and chapter 6 returns to it.

## 2.5 A handler can pass a question on

One more capability, used constantly in real systems. A handler may see a question, do something, and then **ask
it again** for whoever is further out. A handler's line is itself a program, so it may ask:

```
handler Journal(lines) : A ! (Accounts + R)  =>  A ! (Accounts + R)
  done(a)                 => done a
  op of Accounts, rest    => answer <- op                    -- asked again, of whoever is outside
                             lines := lines ++ ["{op} -> {answer}"]
                             rest(answer)
```

It does not answer `Balance`. It re-asks it — so the in-memory bank, or the real database, still answers — and
when the answer comes back it writes a line and continues the program. Its type says as much: `Accounts` is on
both sides of the arrow.

Put it anywhere inside the handler that really answers:

```
pay("ann", "bob", 30) under Journal(lines) under InMemory({ann: 100})
```

and afterwards `lines` is

```
Balance(ann) -> 100
Move(ann,bob,30) -> ()
```

`pay` was not modified. It was not compiled against a logging library. It cannot tell that it is being
journaled, and — this is the property that matters for control — **it cannot go around the journal**, because the
only thing it is able to do is ask, and every ask passes through.

This is an *interceptor*. Audit, metering, rate limits, authorisation, caching, fault injection, "ask a person
before any transfer above a thousand" are all this shape. Chapter 10 builds on it.

The sentence "it cannot go around" is as strong as the rendering makes it. Where the program is data, or a
generator that can only yield, it holds by construction. Where the program is plain code holding an interface,
an interceptor is a wrapper around the implementation and sees what was routed through it; chapter 3c runs
that case and says what is lost.

## 2.6 The row: what may still be asked

The last notion is about types, and it is what makes the scheme safe rather than merely flexible.

A program can only be *run* when no question is left unanswered. The smallest renderings find that out when
they run: `run` fails with "nobody answers Now". That is honest but late.

The check can move into the type. The type of a program carries, besides its result, the **row** of effects it
may still ask:

```
pay : Text ! (Accounts + Clock + Failure)
```

Read: a text, asking accounts, the clock and failure. Each handler removes its effect from the row:

```
InMemory    :  A ! (Accounts + R)   =>   A ! R
FixedClock  :  A ! (Clock + R)      =>   A ! R
Refusals    :  A ! (Failure + R)    =>   (paid(A) | refused(Text)) ! R
```

and `run` accepts only the empty row. Forget a handler and the program does not compile. Add a new question to
`pay` and every place that runs it stops compiling until someone decides who answers.

How a language spells the row is one of the axes of chapter 3a: a type-level list or set, a union type, a list
of type-class constraints, a list of context parameters, a set of capabilities the compiler tracks — or nothing
at all, in a dynamic language, where the row exists only as the handlers that happen to be installed. Three of
the renderings under `code/` get the check from their language for nothing (constraints in Haskell, context
parameters in Scala, an inferred row in Koka), and there a forgotten handler is a compile error today.

The row is the answer to this book's opening question. **It is the part of a function's type that says what the
function reaches for.** `Text ! (Accounts + Clock + Failure)` reads the balance, moves money, reads the clock,
may refuse — and, on the evidence of its type, does nothing else. (On the evidence of its type: chapter 1 already
said why that evidence needs a second witness, and chapter 10 provides it.)

Rows also compose without ceremony. A program that asks accounts, calling one that asks the clock, simply has
both in its row. There is no "lifting" from one into the other, no fixed order in which they must be stacked.
Chapter 5 shows why that sentence, which sounds like nothing, is the end of a twenty-year-old problem.

## 2.7 The five notions, together

| notion | what it is | what it replaces |
| --- | --- | --- |
| operation | a question as data, naming its answer's type | a method call on a service |
| effect | a vocabulary of operations | an interface |
| program | done, or a question with the rest | a function body that acts |
| handler | answers one effect, given the rest of the program | an implementation, a wrapper, a mock, a catch |
| row | the effects still to be answered, in the type | the documentation nobody keeps current |

And one sentence to carry forward: **a program is a value that asks; a handler holds the rest of it and
decides.**

And a second one, for the colleague who asks in a corridor what all this is: **an effect is an exception that
can be resumed at the place where it was thrown.** The word *algebraic* means, in practice, no more than section
2.1 showed: the operations of an effect are the cases of an ordinary data type, and a handler says what each
case means. Neither sentence asks for a new language. Most explanations I have read bury those two sentences
under detail and end by concluding that one is needed.

"In practice" is doing some work in that paragraph. The next chapter, 2b, is short and says what the five
notions amount to, exactly: two things together, operations as algebraic data and the rest of the program as a
delimited continuation; what the word *algebraic* means in the theory it comes from; and which familiar
constructs are therefore not operations at all. Every comparison later in the book is placed on those two
halves.

Chapter 3 then builds exactly this once, in forty lines of one language, and runs every example above. The
part after it lays out the other ways to build it.
