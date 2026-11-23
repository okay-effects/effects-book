# 16 · The picture, again

Chapter 2b stated the idea in one paragraph, before any of it had been built, compared or used. This chapter
states it again, with what the chapters between have added. It is short, and it introduces nothing.

## 16.1 What you now know

**An effect is two things together.** Its operations are data: a signature, and a program is the tree those
operations freely generate. Its handler is a fold over that tree which, at each operation, holds the rest of
the program up to itself — a delimited continuation — as a value it may drop, call once, call many times, or
keep.

**Each half, alone, is something you already used.** Data without the rest is a command object, an event log,
an interface with a recording wrapper: everything can be seen and nothing can be held. The rest without the
data is a callback, a coroutine, a thread, `shift` and `reset`: anything can be held and nothing can be named.
A monad is both, fused in a type chosen in advance. Chapter 5 placed seventeen mechanisms that way, and for
each of them the limit that its users know by experience turned out to be the half it lacks.

**"Algebraic" is a rule about what may be an operation.** An operation is something whose only held program is
its own continuation, so that whatever follows it can be moved inside it. What acts on a stretch of a program —
catching, a changed setting, a transaction — is not that. It is a handler around the stretch, or a node that
holds a sub-program and hides it from whoever stands between (chapter 7). In the theory the operations also
satisfy equations; in every implementation I know they do not, the tree is free, and the laws are tests.

**The halves are bought separately when the idea is built.** Part III was that shopping list. How the program
is represented decides whether the first half is real: a tree and a generator can only ask; plain code holding
an interface can do anything. What the rest is made of decides the second: a closure can be called twice and
cannot be stored; a piece of stack is the cheapest thing there is and resumes once; a state machine can be
copied and written down and must be produced by somebody. Rows, the types that list what may be asked, are a
third purchase.

## 16.2 Where each half did the work

| what the book claimed | first half: the question is data | second half: the rest is held |
| --- | --- | --- |
| the type says what the code reaches for (1, 2) | the signature is the vocabulary | — |
| tests, platforms, languages exchanged (7) | another fold over the same tree | — |
| a journal, a limit, replay, an audit (10) | every question passes as a value | to refuse, the rest is dropped |
| leave, wait, look back, retry, decide, fork (6) | the handler knows which question | all six |
| approval that waits; a workflow (11) | replay needs only this | keeping the rest needs it as data |
| an agent that can be supervised (12) | the tool call | the stopped session |
| what it costs (13) | an answer in place: nanoseconds | taking the rest: tens of them, or more |

The table reads two ways. Down the first column, most of what makes this an *architecture* and a means of
*control* is the first half, and a team can have a great deal of it with command objects and discipline; that
was chapter 14's advice for a team that will not learn the rest. Down the second, everything that makes it a
difference *in kind* from a wrapper is the second half, and there is no partial version of it: either
something outside the program holds what the program will do next, or nothing does.

## 16.3 What stays open

The book has said where it is unfinished, and the list belongs beside the picture.

- **The laws.** Section 2b.3 said practice keeps the free tree and checks laws by tests. Chapter 4 writes
  them down, runs twelve lines of them in each of the sixteen renderings and proves what can be proved of a
  tree. Still open there: generated programs in a language other than Scala, a proof about any rendering but
  the Lean one, and the general statement of when two handlers commute.
- **The rest as data, across a process.** Chapter 11's second road is run here by hand, for one stop. Of my own
  ports one round-trips a captured rest through bytes, in one process; none restores one in another.
- **"On the way back" in five renderings.** Chapter 6 states what their reply-returning handlers give up; no
  line of the scenario shows it.
- **Scoped operations.** Chapter 7 found a bracket's body opaque to an interceptor, in forty lines and, by
  reading, in my kernel. Whether an audit should see inside is a design question that the book raises and does
  not settle.
- **Agents.** That an agent writes better code under these rules is argued and observed, and not measured.
- **Other people's libraries.** They are described from their documentation, and nothing here runs the bank on
  them.

## 16.4 The sentence

Chapter 2 ended on a sentence: *a program is a value that asks; a handler holds the rest of it and decides.*
The first clause is the first half and the second clause is the second. Everything in between was those two
clauses, taken apart to see what each is made of, and put back together.
