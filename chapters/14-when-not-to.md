# 14 · When not to

A book that cannot say when its subject is the wrong tool is advertising. This chapter is the list of cases
where I do not use effects, or use fewer of them than I could, and a short test for telling which case you are
in.

Most of the list is not mine alone. The rules I give coding agents (chapter 12) end with a section called "when
not to use effects" and a list of common mistakes, and the agents report back where the approach was friction.
What follows is that section, expanded, with the reasons. The snippets are pseudocode.

## 14.1 Pure computation

Parsing, validation, arithmetic, formatting, turning one data structure into another. A function from values to
a value reaches for nothing, so there is nothing to ask and nobody to answer.

```
total(lines)            = sum(line.price * line.quantity for line in lines)     // a function
total(lines) ! Pricing  = for each line: ask Pricing.multiply(...)              // ceremony
```

The second form buys nothing that the first lacks. A pure function is already everything this book wants of a
program: its type says what it does, it can be run anywhere, it needs no mock, it gives the same answer twice.
Effects exist to make the *other* kind of code more like this kind. Lifting pure steps into operations makes
them slower (chapter 13: several nanoseconds each, against less than one), harder to read, and no safer.

The working rule: programs call pure functions freely. An operation is for the moment the code needs something
it does not have.

## 14.2 One implementation, for ever

Chapter 5 said plain code is the right choice for code with one implementation for ever, and I meant it. If a
function writes one file in one format on one machine, and no test, replay, audit or second platform will ever
want that write answered differently, then an interface with one implementation is a formality and an effect
with one handler is a larger formality.

Be honest about "for ever", though, in both directions.

It is usually false for anything a test must run without. An in-memory handler *is* a second implementation, and
the moment you want the logic tested without the real thing, you have two. It is also false for anything that
must be seen or limited: the journal and the limit of chapter 10 are handlers too.

It is quite often true for the leaves: the code *inside* a handler that speaks to one driver. That code is
plain code. Nobody should build an effect system inside the Postgres handler.

## 14.3 A script

A throwaway script, a migration run once, a prototype to learn whether an idea works. The shortest thing that
works is correct here, and every tool — the debugger, the stack trace, the reader's intuition — was built for
it.

The qualification in my own rules is "without IO worth isolating". A script that moves money, deletes records
or acts with production credentials is short, and it is still exactly the code whose every reach I would want
to see, dry-run and limit. Length is not the criterion. What it touches is.

Such a script is also where a vocabulary pays with the least ceremony. One that copies a shop's data from
production to a test server has, as its type, "a count, asking `Source` and `Target`" (illustrative; it is one
of my own tools). If `Source` has only reading operations, and its handler only sends reads, the script cannot
write to production: there is no question it could ask to do so. The same script runs against memory in a test,
and it parses the data with the application's own model, not with a second copy of that model which drifts. The
edge of chapter 10 applies here as everywhere: this holds for what the script asks, and a scan must say that it
does nothing else.

## 14.4 A team that will not read it

This is the case I would weigh first, and it has nothing to do with the mechanism.

Code in this style is only safer if the people who maintain it know what a handler is, why the order of handlers
matters, and why a line after a question may run twice. Chapter 13 listed what stands in the way: unfamiliar
type errors, stack traces of the machine, tools built for another shape, few people to hire who have done it.

If the team does not want this, or cannot spare the weeks to learn it, or will lose the one person who
understands it, then the result is the worst of both: a codebase in an idiom nobody on it reads fluently. A
well-kept codebase of interfaces, injection and discipline — the first rows of chapter 5's table — is better
than a neglected one of effects. I would rather a team adopted the *boundary* idea (name what the logic asks;
keep the world in a few listed places; check it at build time) in the idiom it already knows than adopt my
libraries and resent them.

That is argued from the costs listed in chapter 13. It is not an observation of a team that adopted this and
gave it up.

[AUTHOR: has a team other than you and your agents adopted or dropped the approach? If so the real story
belongs here and is worth more than the argument.]

## 14.5 A hot inner loop

Chapter 13's figures: an operation answered in place costs single nanoseconds; a plain loop step costs less
than one. For a question followed by a network call the difference vanishes. For a loop that asks a million
times a second it is the entire cost.

The rule in my notes for agents is one line: *compute purely and ask one effect with the result.*

```
// a question per element: a million operations
for each x in items:  ask Metrics.count(x.kind)

// pure work, then one question
counts = countByKind(items)          // a plain loop
ask Metrics.record(counts)
```

The second is faster and is also a better vocabulary — "record these counts" is what the business meant. Where
a loop really must ask per element, a library can batch (one operation per chunk of elements), or compile the
handler into the loop when the handler is known where the program is written. Both exist in my JVM library;
both are optimisations you should need a measurement to reach for.

Do not avoid effects for speed by guess. Write the lane that has your shape, as chapter 13 says, and see.

## 14.6 An effect for something used once, inside one handler

A common over-reach: every helper becomes an effect.

The handler of `Mail` builds a MIME message, opens a connection, retries twice. None of those is something
*business logic* asks. They are how one handler does its job. Giving each its own effect — `Mime`, `Smtp`,
`Retry` — produces a vocabulary of the implementation, three more handlers to wire, and a row that tells the
reader about plumbing.

An effect earns its place when one of these holds:

- business logic needs to say it;
- it will have more than one answerer (a test counts);
- it must be seen, limited, recorded or stopped.

A handler may itself be written against lower effects, and often should be — `Bookings` over `Store` and `Clock`
— when those are vocabularies someone else also uses. A detail with one user stays a private function.

The opposite mistake is in the same list: one effect called `App` with forty operations. That is a module with
a new name. Rows compose; split by what the operations are about.

## 14.7 A vocabulary at the level of the transport

Chapter 7 called this the most expensive mistake in the style, and it belongs here because it is a way of
using effects where they do no good.

```
effect Sql    query(text): Rows
effect Http   post(url, body): Response
```

Every reach now goes through a handler, and almost nothing has been gained. The programs still know table names
and URLs. A test must still fake a database, only now through a handler. The journal says "a query happened";
it cannot say that money moved. A limit on `Http.post` cannot tell a refund from a transfer. You have paid the
costs of chapter 13 for requirements 2 and 3 of chapter 5 at the level of bytes, which a proxy would have given
you.

The same goes for an operation that hands the program a live object — a connection, a stream handle, a client.
The program then acts on the object directly, past every handler, and the row has become a formality.

If the first vocabulary you can think of is the transport's, that is a sign the domain has not been named yet.
It may be the moment to stop and name it. It may also mean that this code *is* transport — a driver, a codec,
a proxy — and is properly the inside of a handler, to be written as plain code.

## 14.8 Too small to matter

Each handler of chapter 6 replaces something conventional, and at a small size the conventional thing is the
right call. The list is worth having in one place, because it is where enthusiasm over-reaches:

- one or two layers between a failure and whoever handles it: return the failure;
- one consumer that wants every item: collect them;
- exactly two exits from a short function: write the line twice;
- one or two policies that any caller would state the same way: make them parameters;
- a process whose states are themselves the specification: write the state machine.

Each of chapter 6's handlers is more machinery than the thing it replaces at this size. The mechanism pays
when the shape recurs, or is deep, or must be controlled.

## 14.9 A short test

Four questions, in order. Stop at the first that says no.

1. **Does this code reach for the world?** If it only computes, it is a function. Stop.
2. **Does anyone need that reach answered differently, or seen, or refused, or stopped?** A test without the
   real thing, a second platform, an audit, a limit, an approval, a replay — any one is a yes. If the honest
   answer is none, now or foreseeably, write plain code behind a plain boundary. Stop.
3. **Can the vocabulary be said in the business's words?** If you can only name the transport, either the
   domain is not understood yet or this is the inside of a handler. Stop, and find out which.
4. **Will the people who keep this code read it?** If not, take the idea — a named boundary, the world in few
   places, a check at build time — in the idiom they already use.

Four times yes: effects, at the grain of what the business asks, with the pure work left as functions and the
inner loops left as loops.

And one question for afterwards, which the rules I give agents make them answer and which I recommend to people:
*where would plain code have been simpler?* A team that can list three such places in its own codebase is using
the tool with judgement. A team that can list none has stopped asking.
