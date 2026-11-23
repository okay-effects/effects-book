# 2b · The idea, exactly: algebraic data and delimited continuations

Chapter 2 gave five notions and a bank. This chapter says what they amount to, once, in one place, because
every comparison in the rest of the book is made against it.

> **Algebraic effects are two things together. The operations are algebraic data: a signature, and a program is
> the tree those operations freely generate. A handler is a fold over that tree which, at each operation, holds
> the rest of the program up to itself — a delimited continuation — as a value it may drop, call once, call many
> times, or keep.**

Engineers who know the subject often compress it to two words, *algebraic data types and delimited
continuations*. As an engineering statement that is right, and it is the spine of this book. It needs its exact
content, and three qualifications, which are in section 2b.5. Nothing below uses category theory, and nothing
below is a simplification that a later chapter has to take back.

## 2b.1 The first half: the operations are data, and a program is their tree

A **signature** is a list of operations, each with what it carries and the type of its answer. Section 2.1 wrote
three: `Accounts`, `Clock`, `Failure`.

Given a signature, there is exactly one most general thing a program over it can be. It is either finished with
a value, or it is one operation together with what to do for each possible answer:

```
Program(A)  =  done(A)
            |  Balance(account) ; Number => Program(A)
            |  Move(from, to, n) ; Unit  => Program(A)
            |  Now ; Time                => Program(A)
            |  Refuse(why)                                 -- no answer, so nothing follows
```

That is an ordinary recursive data type — an *algebraic data type* in the everyday sense, a sum of cases built
from products — with one case for each operation and one for `done`. It is section 2.2's "done, or a question
with the rest", written out for the bank. It is called the **free** structure over the signature because
nothing has been added to it and nothing has been identified in it: two programs are equal only if they are the
same tree. `pay("ann", "bob", 30)` is one such tree:

```
Balance("ann") ; have =>
  if have < 30:  Refuse("ann has {have}, needs 30")
  otherwise:     Move("ann", "bob", 30) ; _ =>  Now ; at =>  done "moved 30 from ann to bob at {at}"
```

(The tree has one branch for every possible answer, so it is as wide as the answer's type; nobody builds it
whole. A rendering either builds it lazily, a node at a time, or never builds it and runs code that *would
unfold* to it. Chapters 3 and 3a to 3f are about that difference. The idea does not depend on it.)

A **handler** is a fold over this type. A fold over a list says what to do with the empty list and what to do
with an element, given the result for the rest of the list. A fold over a program says what to do with `done`,
and what to do with each operation, given the result for the rest of the program. Section 2.3's handler has
exactly that shape: a `done` line, and a line `Op(…), rest => …` for each operation it answers.

What the first half pays for, alone:

- the questions can be **listed** — the signature is the complete vocabulary — and so **typed**: the row of
  section 2.6 is the set of signatures a program is a tree over;
- each question can be **seen** as a value with a meaning, **recorded** with its answer, compared with a
  recording and so **replayed**, and **sent** to another process;
- the same tree can be folded by **different handlers**, which is all of testing, porting and dry runs;
- a program **cannot go around**: a tree over a signature has no nodes but that signature's.

## 2b.2 The second half: the rest of the program, up to the handler

Look again at one node, `Balance("ann") ; have => …`. Its second component is the rest of the program as a
function of the answer. When the fold reaches this node, the handler's line is given that component — already
folded, which is why section 3.3 could say that the rest a handler receives is "already handled".

Two words describe what the handler is holding.

It is a **continuation**: what the program will do next, as something that can be called with the answer.

It is **delimited**: it is the rest of the program *up to this handler* and no further. It does not include
whoever applied the handler, the code that will use the handler's result, or the server loop around all of it.
So calling `rest(v)` is an ordinary call. It returns, with the value the handled remainder ended in, and the
handler's line goes on after it. Chapter 6's handler that moves money back if the caller later fails depends on
exactly that: it calls `rest`, and then looks at what came back.

Because it is a value in the handler's hand, there are four things to do with it, and section 2.4 showed each:

| the handler | what that is called elsewhere |
| --- | --- |
| does not call it | an exception, an early exit, a refusal, cancellation |
| calls it once, now | an ordinary call: a service answers and the caller goes on |
| calls it once, later | a generator, `await`, a callback, an approval that waits |
| calls it more than once | backtracking, search, "what if", retrying the caller |

What the second half pays for, alone: control. Every control structure in that table, as code in a library and
not as a feature of a language.

The two halves are one object seen twice. In the tree, the delimited continuation *is* the second component of
a node; nothing has to be captured, because it was never hidden. In a program that runs as ordinary code, the
continuation is the piece of stack between the place that asked and the handler, and capturing it is how the
node comes to exist. That each view can define the other has been shown, and is not only an analogy (Kammar,
Lindley and Oury; Forster, Kammar, Lindley and Pretnar; chapter 15 has the references, which carry the fine
print about types).

## 2b.3 What "algebraic" means, exactly

Section 2.7 said the word means, in practice, that the operations are the cases of a data type. That is the
part an engineer uses. The name of the subject comes from somewhere a little different, and the difference
decides what may be an operation at all.

In the work of Plotkin and Power an effect is an **algebraic theory**: a set of operations, and **equations**
between programs built from them. An operation of such a theory has one defining property, and it can be said
with the bank. Whatever follows an operation can be moved inside it:

```
( have <- Balance("ann") ; P(have) ) then Q        is the same program as
  have <- Balance("ann") ; ( P(have) then Q )
```

The question does not care what comes after it. It sits at the head of the program, and "then Q" only makes its
rest longer. This is called *commuting with sequencing*, and an operation that has the property is an
**algebraic operation**. In the tree it is not a law to be checked but the definition of putting one program
after another: chapter 3's `flatMap` at a question is "the same question, and its rest is the old rest followed
by `f`". An operation is algebraic exactly when the only program it holds is its own continuation.

Three consequences, each of which an engineer meets.

**Some things are not operations, and cannot be made into them.** Take anything that acts on a *stretch* of a
program and not at a point: catching a failure, running a part with a changed setting, a transaction bracket.
Suppose "catch" were an operation taking the guarded part `p` and the recovery `h`. Commuting with sequencing
would say

```
catch(p, h) then Q        is the same as        catch(p then Q,  h then Q)
```

and it is not: on the right a failure inside `Q` is caught, on the left it is not. The bracket of section 7.6
fails the same test. `Atomic(body) then Insert(x)` and `Atomic(body then Insert(x))` differ in whether the
insert is inside the transaction. The scope of such a construct is the point of it, and sequencing must not move
that scope.

So the theory files these under another heading, and there are two.

- They are **handlers**. *Raising* a failure is an algebraic operation (`Refuse`); *catching* is the handler
  `Refusals` applied to a part of the program, `p under Refusals`. This is the reading in the title of Plotkin
  and Pretnar's paper, "Handlers of algebraic effects": exception handlers were the thing that would not fit as
  an operation, and became the general notion. A setting changed for a part of a program is likewise a handler
  around that part.
- Or they are **scoped operations**: a node that holds a sub-program, admitted as a second kind of node with
  rules of its own (Wu, Schrijvers and Hinze, "Effect handlers in scope", is where this was set out). Section
  7.6 writes a transaction this way, as `Atomic(body)`, and meets the known price of it at once: a handler
  standing between the program and the bracket sees the bracket go by and nothing inside it.

Knowing which of the two a design uses is practical. When a construct "holds a block", it is one of these, and
the questions to ask are the ones section 7.6 asks: who walks the block, and who can see into it.

**A handler is a way of giving the operations a meaning that the tree respects.** In Plotkin and Pretnar a
handler supplies, for each operation, an interpretation on some result type. Together those interpretations
form a *model* of the theory, and handling a program is the one structure-preserving map from the free model —
the tree — into that model. For a data type, "the one structure-preserving map out of the free thing" is a
fold. That is the precise sense in which a handler is a fold, and why a handler need say only what to do with
`done` and with each operation: everything else is determined.

**In the theory there are equations; in practice there are tests.** The theory of state is not `get` and `put`
alone. It is `get` and `put` with equations such as "a `get` after a `put` returns what was put", and a handler
is correct only if its model satisfies them. Every library and language I know of in this family drops the
equations: the tree is the free one, any fold over it is accepted, and what a handler promises about its
operations is a property of that handler, checked by tests, or proved where the language can (chapter 3g: my
Lean port proves what each standard handler makes of its operations).

The bank shows why practice chose that, and it is not laziness. One would like `Move(a, b, 10)` followed by
`Move(a, b, 20)` to equal `Move(a, b, 30)`. Under `InMemory` it does: the balances cannot tell them apart.
Under `Journal` it does not: there are two lines or one. An audit is precisely a handler that refuses an
equation the business handler satisfies. A tree with no equations built in is what lets both handlers fold the
same program, and chapter 10 is built on that freedom. The price is that the laws live in tests; chapter 4
states them, runs them in every rendering, and proves in Lean the ones that can be proved of a tree.

## 2b.4 Why it takes both

Each half alone is something every engineer has already used, and each alone runs out at a known place.

| | what is missing | so it cannot |
| --- | --- | --- |
| data without the continuation | the rest: the answer comes back once, in place | wait, retry the caller, run twice |
| the continuation without the data | the signature | be listed, typed, intercepted, recorded, sent |
| both, fused into fixed types | the choice at the edge | reorder or exchange a meaning without rewriting |
| both, by encoding | nothing in principle | — it pays in notation, in cost, or by hiding a half |

A command object, an interpreter of a list of instructions and a method call through an injected interface are
the first row. A callback, `call/cc`, `shift` and `reset`, a thread, a coroutine, a generator and `async` are
the second. A monad and a stack of monad transformers are the third. Free and freer monads, tagless final and
the effect runtimes are the fourth.

That table is the whole map in four lines, and it is all this chapter gives of it. **Chapter 5 is the map in
full**: every one of those mechanisms and a dozen more, each with what it does well first, then which half it
restricts, and why its limits follow from that. It comes after Part III because several of the mechanisms are
also *ways of building* the idea, and are easier to place once they have been seen built.

What having both gives is not the sum of the two lists. It is that the second list applies to everything the
first one names: any question in the vocabulary can be refused, delayed, retried or forked, by a handler the
program has never heard of, and every such decision is taken at a point that sees the question as a value. The
five programs of chapter 1b needed the continuation. The five questions of the preface needed the data. One
mechanism answers both sets because it is both things.

## 2b.5 The two-word summary, with its qualifications

"Algebraic data types and delimited continuations" — keep it, with three notes.

1. **"Algebraic" names the theory, not the data type.** The effects are called algebraic because their
   operations are those of an algebraic theory: they commute with sequencing, and in the theory they come with
   equations. The data type enters because the free model of a signature *is* an algebraic data type. For an
   engineer the two readings coincide, and the first one adds the rule of section 2b.3: what acts on a scope is
   a handler or a scoped operation, never a plain operation.
2. **The continuation is delimited by the handler, and that is what a handler is.** A handler is not a callback
   that happens to be given a continuation. It is a boundary together with the clauses for what crosses it:
   the fold's cases, applied at the edge of the stretch of program they govern. Chapter 15 puts the history in
   one line: a handler is a delimiter that knows what it catches.
3. **An implementation may restrict the continuation, and most do.** The idea has the rest as a value that can
   be used any number of times. A rest that is a piece of stack can be resumed once (chapter 3d); OCaml 5 chose
   that on purpose (chapter 3f). Such an implementation is still this idea, with the last line of section
   2b.2's table taken out, and the book says so wherever it matters.

And one thing the summary leaves out, correctly. **Rows are a third thing.** That a program's type lists what
it may ask is a consequence of the first half that a language may or may not check. OCaml 5 has both halves and
no rows. Capability passing has the rows and neither half as a value. The mechanism and the types are separate
purchases, and chapter 3a prices them separately.

## 2b.6 How the rest of the book uses this

| where | which half |
| --- | --- |
| chapter 3 | both, built once: the tree is a data type of two cases, the rest is a function |
| chapters 3a to 3h | the ways to build each half, and what follows from each choice |
| chapter 5 | the map: every neighbouring mechanism, by which half it restricts |
| chapter 6 | the continuation half: the six things a handler can do with the rest |
| chapters 7 and 10 | the data half: the vocabulary as the architecture; the question as the point of control |
| chapter 11 | the continuation as a value that outlives a process, which needs it to be data too |
| chapter 12 | both: an agent's tool call is data, and a supervisor holds the rest of the session |
| chapter 13 | what each half costs: an answer in place, and taking the rest |
| chapter 15 | where each half came from, and who joined them |
| chapter 16 | the picture again, with what the chapters between have added |
