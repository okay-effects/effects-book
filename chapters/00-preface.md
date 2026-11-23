# Preface

This book is about one idea. A program should not *do* things to the world; it should *ask* for them, and
something else should decide what the answer is.

Said like that it sounds like a slogan, and most of the book is the work of showing that it is not one: that the
idea can be stated exactly and without reference to any programming language, built in several quite different
ways — the shortest of them forty lines — compared honestly with everything else engineers use for the same
purpose, and carried from a single function up to the shape of a whole system.

The idea has a name in the research literature — *algebraic effects and handlers* — and a history of about
twenty-five years. Neither the name nor the history is needed to use it. They are in the last part of the book,
for the reader who wants to know who found what.

What is needed is to know what the idea is made of, and that is two things an engineer has met separately. The
requests a program makes are **data**: a named, typed vocabulary, values that can be looked at. And whoever
answers a request holds **the rest of the program**, up to itself, as something it may continue, drop, repeat
or keep. Chapter 2b says this exactly. Chapter 5 uses it as a ruler for everything else — exceptions,
dependency injection, callbacks, coroutines, monads, effect runtimes — each of which is one of the two halves,
or both with something fixed. The last chapter returns to it.

## Why I wrote it

I have written backend software for thirty years. The question that cost the most time over those years was
almost never "how do I compute this". It was one of these:

- What does this code actually touch?
- Can I run it without the database, the clock, the payment provider?
- Who called this, with what, and what came back — last Tuesday, in production?
- Can I stop it here, wait for a person, and go on tomorrow?
- Can I be sure it does not do anything else?

Each of those has its own industry of tools: dependency injection, mocking frameworks, tracing, workflow
engines, sandboxes. They do not share a model, so each team assembles them again, and the assembly is where the
defects live.

Effects answer all five with one mechanism. That is the claim of the book. It is a strong claim, and the way I
have tried to keep it honest is to write under rules.

## The rules

**The code compiles and runs.** Chapter 3 builds the whole mechanism from nothing, once. The same small program
is then written sixteen times in thirteen languages, by every technique Part III describes, and one script
checks that all of them print the same lines. Every example is that code, or is marked as an illustration that
was not run.

**The book belongs to no implementation.** The idea is stated in a notation of its own. No language and no
library is the reference, mine included. Part III lays out the ways the idea can be built, what each gives and
costs, and in which languages each is open, so that a reader can choose for their own language and problem.

**A number has a source.** Where the book says something is fast or slow, it names the benchmark and how it was
run. Where nothing was measured, it says "argued", not "shown".

**The alternative is described by its strengths first.** Dependency injection, monad transformers, ZIO, plain
code with discipline — people chose these for good reasons, and a comparison that hides those reasons teaches
nothing.

**The costs get their own part.** There are programs for which effects are the wrong tool, and a chapter says
which.

## Who it is for

Three readers, and I have tried not to write for only one of them.

*The engineer who has never met effects.* You need no functional programming background. If you have written a
function that takes a repository interface so it can be tested, you already know half of chapter 2.

*The engineer who knows monads, tagless final, ZIO or Cats Effect.* Part III and chapters 5 and 6 are for you:
where what you use sits among the ways of building this, what is the same idea under another name, and what
is different in kind. You may want to read chapter 3 first anyway; it is short.

*The engineer who writes Java, Kotlin, Go, Rust, TypeScript, Python, C# or Swift,* and expects a book on
effects to be about someone else's language. Chapter 3g has a section for yours, and `code/` most likely has
the bank in it.

*The architect, or whoever decides what a team builds.* Parts V, VI and VIII are written to be read without
writing code. They ask what the team stops building, what it can show an auditor, what adoption costs, and when
the answer is no.

## What it is not

It is not a manual for a library. I maintain a family of libraries built on this idea, in several languages, and
they appear as evidence and as worked cases: one implementer's measurements, tests and mistakes, named as such
beside other people's libraries. None of the code under `code/` uses them, and a reader who never touches my
code should still finish with something usable.

It is not a catalogue of libraries. Part III surveys techniques, and names libraries as examples of each,
described from their own documentation and only as far as I am sure of them.

It is not finished. `PLAN.md` beside this file lists every chapter, its thesis and its state.
