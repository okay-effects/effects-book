# 12 · Agents

An AI agent now has two roles in software. It **writes** code, and it **acts** — it calls tools, runs commands,
sends messages, moves money. This chapter argues that effects are the right shape for both roles, and for the
same reason.

It is also the chapter where I have the least measurement. So it is written in three registers, and each section
says which it is in: **argued** (reasoning from the mechanism), **observed** (what I have seen in my own work,
which is not an experiment), and **measured** (very little, and said plainly).

## Part one: the agent as a writer of code

### 12.1 What an agent finds hard (argued)

A coding agent does not have the project in its head. It has a window of text, a set of tools for reading files
and running commands, and no memory of yesterday unless someone wrote it down. From that position, the costly
things about a codebase are these.

- **Hidden context.** To change a function safely the agent must know what it touches. If the answer is spread
  over a dependency graph, a container configuration and conventions, the agent must read a great deal — or
  guess.
- **Slow or unavailable feedback.** If verifying a change needs a database, credentials and a running stack, the
  agent cannot verify it, and writes plausible code instead of checked code.
- **Unbounded action.** Any line the agent writes may open a socket or read a clock. Nothing tells it that this
  module should not, and nothing tells the reviewer that it did.
- **Convention without enforcement.** "The domain layer must not import the HTTP client" lives in a wiki the
  agent may never see.

None of this is specific to machines. It is what makes a codebase hard for a new human colleague, compressed
into minutes instead of months.

In one line: an agent writes the way a capable newcomer does — fast, locally correct, globally unaware. I do
not think the remedy is a cleverer agent. It is a system legible enough that an author who reasons locally
cannot get the global picture wrong.

### 12.2 What effects change for it (argued)

Take those four in turn.

**The context is the vocabulary.** To write business logic the agent needs the list of operations: a page. It
does not need to know which database exists. The row in a function's type tells it what that function may ask —
and therefore what the agent may use there.

**Feedback is immediate.** A test is the program under in-memory handlers. It needs no infrastructure, runs in
milliseconds, and is deterministic because time and identifiers are answered by the test. The agent's loop —
write, run, read the failure, fix — closes without a human and without an environment.

**The compiler says what is missing.** Add a question to a program and every place that runs it fails to compile
until someone decides who answers. An agent is very good at following a compiler error to its cause, and very
bad at noticing an omission that produces no error at all. Rows turn the second kind into the first. And the
check is local: a change is judged against the row of the place it was made in, without the agent having read
the rest of the system. It gets the text of an error, not an incident in production.

**The boundary is checked by a tool.** If generated business code reaches for the network or the clock, the
build-time check of chapter 10 fails and names the class and the reach. The rule is not in a wiki; it is in the
build, and its message is one the agent can act on. Taken to its end this is a gate: code an agent wrote is
compiled, checked against the boundary, and only then run, under a policy that is a handler. I have such a gate
as an experiment (chapter 10, section 10.7), not as a product.

There is a fifth point, less obvious. **The task decomposes along a line the agent can see.** "Write the handler
of `Mail` for this provider" and "write the program that books a slot" are separate jobs with a typed interface
between them. Each can be given to an agent, or to several at once, with little risk that they collide.

### 12.3 What I have observed

Most of the code behind this book — the libraries in several languages, the applications built on them — was
written with coding agents working under exactly these rules, with me deciding what to build and checking what
came back. That is one person's practice, not a controlled comparison, and I report it as such.

What I have seen, repeatedly:

- An agent given the vocabulary and an in-memory handler writes a new program and its tests in one pass, and the
  tests mean something, because they run the real program.
- When an agent takes a shortcut that reaches past the boundary, the audit finds it at the next build. Without
  the audit I would have found some of those in review and missed others.
- Rules stated as "the data model is effects, implementations are handlers, run in one line" are followed far
  more reliably than rules stated as a list of prohibitions, because the first kind tells the agent what to *do*.
- The failures are different in kind. In conventional code an agent's typical defect is a plausible call to
  something that does not behave as assumed. Under effects it is more often a badly chosen vocabulary — an
  operation at the wrong level — which is a design error, visible in review of one small file.
- Where several implementations must agree — the same kernel in Scala, in Java, in Rust — a conformance suite
  and a reference machine say which one is wrong. Agents wrote most of those kernels, and could, because most
  of their mistakes came back as a type error or a failed check. Not all. One conversion error left every test
  green and made one operation far slower; a ported benchmark found it. A check catches what it was written to
  catch.

I publish the rules the agents work under as a skill — a few pages of plain text that any agent can be pointed
at: the model in six lines, the rules for code, which library for which language, how to check the boundary,
when *not* to use effects. It ends by asking the agent, after it has written the code, for a candid engineering
opinion of the approach: what was easier, what was friction, where plain code would have been simpler. I read
those answers. Type errors in rows are the commonest complaint.

A reader can repeat this in an afternoon, and I would rather they did than took my word. Point your own agent
at the rules, give it a real task, and then ask it plainly: was this easier or harder than usual, would you
trust code written this way more or less, where would you not use it? An agent now reads and writes more code
than any of us, so its view of a way of writing code is evidence of a kind. It is not a measurement.

### 12.4 What has not been measured

The claim "agents write better code with effects" would be settled by an experiment I have not run: the same set
of tasks, the same model, with and without the rules; each result checked by hidden tests and by the boundary
audit; enough tasks and enough repetitions that the difference is larger than the noise.

Until that exists, this part of the chapter is an argument from mechanism plus one practitioner's experience.
I think the argument is strong. I would rather say that than dress it as a result.

### 12.5 What the agent did not change (observed)

For some months I have built my projects with several agents at once, on the same code. More than I expected,
it has been like working with a team of people, and I mean that plainly.

They need time to think. Pressed with a short budget, an agent gives a quick, confident, shallow answer. They
need to be told precisely what is wanted, and what is not, and told again: an instruction to commit after every
change was followed, and some tasks later had to be given a second time. They misunderstand as people do. One
agent wrote a careful plan and found, while delivering it, that another had tried that path the night before
and measured it to be worse. Each was right in its own context. Neither knew what the other knew — the "no
memory unless someone wrote it down" of 12.1, between two authors instead of two days.

Most of what I first counted as an agent's mistake was an answer to a question I had not asked clearly,
sometimes not even of myself. When I wrote down the result I wanted to see, the work changed direction within
the hour. The question that mattered was not about agents: do we know what result we need?

It also settled, for me, where the work is. Take the steps of delivering software from easy to hard.

1. Write the code.
2. Make sure it works.
3. Make sure it does nothing wrong.
4. Deliver it to the people who need it.
5. Find those people, and understand what they need.

Agents have made the first nearly free. They help with the second, and somewhat with the third and fourth. The
fifth is ours. Writing the code was never the hard part; it was the visible one.

For this book the step that matters is the third. "It works" covers what you looked at. "It never does the
wrong thing" covers everything else: the side effect nobody asked for, the command an agent decided to run by
itself. Tests do not reach that. It needs a boundary, a check of the boundary, and a record of what actually
happened, which is chapter 10 and the second part of this one.

None of this arrived with agents. Describing what you want and letting a machine produce it is what a compiler,
a build tool and a description of infrastructure already do. The questions were the same for each of them: how
do I check the result, and what do I do if it is wrong? Both begin with a statement of what is wanted and how
we would know we had it. The practices built around those questions — a test written first, a second pair of
eyes — hold with an agent as they did without.

This section is one person's observation, and it is about agents more than about effects. It is here because it
sets the size of the claim. Effects do not tell an agent, or me, what to build. They make a part of the third
step something a build can check and a journal can show.

## Part two: the agent as an actor

Here the ground is firmer, because nothing needs to be argued about quality. An agent that acts is a program
that reaches for the world, and this whole book is about such programs.

### 12.6 A tool call is an operation

When an agent decides to run a command, edit a file, call an API or send a message, it emits a description of
what it wants: a name and arguments. The harness executes it and returns the result. The agent continues.

That is, exactly, chapter 3's `Ask(op, rest)`. The agent is a program that can only ask; the harness is a
handler. The industry arrived at the structure of effects for agents without calling it that, because there was
no other way to connect a language model to the world.

Both halves of chapter 2b are there, and for once neither had to be argued for. The tool call is the first
half: a name and arguments, as data, from a published signature — the list of tools. And the harness holds the
second: between the call and its result the whole session is stopped, in the harness's hand, to be continued
with whatever result the harness chooses to give.

What it has mostly *not* done is use the structure. The typical harness executes the call, or shows a
permission dialog. Everything in chapter 10 is available and mostly unused.

### 12.7 What a handler between an agent and the world can do

Put a handler between the agent and its tools, and you have, with no change to the agent:

- **a policy**: deny, ask a person, or allow, by tool and arguments — *any write outside the project directory:
  ask; any payment over a limit: deny; reading files: allow*;
- **a refusal with a reason**: the agent is told why, as the answer to its question, and goes on;
- **a journal**: every call, the decision, the rule that decided, the result, under a hash chain;
- **replay**: a recorded session re-run without the tools, to debug the agent or to test a new policy against
  real past behaviour;
- **a dry run**: answer without executing, to see what the agent would do;
- **approval that waits**: the rest of the session kept while a person decides — on their phone, tomorrow.

I have built this in two forms: a guard that stands in front of any agent speaking the tool protocol, with the
policy as a text of rules and the journal exportable and verifiable; and a small daemon that lets me run coding
agents on my own machine from a phone, in which every tool call passes a hook decided by such a policy, and the
hook fails closed.

One observation from testing the second is worth recording because it surprised me. A local model was asked to
write a file and wrote it to the wrong directory. The policy asked; the call was refused, with the reason. The
model read the reason and corrected itself. A refusal that arrives *as an answer*, in the place where the agent
expected a result, is something the agent can reason about. A permission error from the operating system three
layers down usually is not.

### 12.8 Why this is the right place for control

There are other places to put controls on an agent: in its instructions, in the model, in a sandbox around the
process, at the network.

- **Instructions** are advice. They are followed most of the time, which is not a property to rest a payment on.
- **The model** cannot be inspected for what it will do.
- **A sandbox** and **network rules** are real and worth having, and they see bytes: an allowed host, a blocked
  system call. They cannot tell a refund from a transfer.

The handler is the one place that is both *unavoidable* — the agent cannot act except by asking — and at the
level of *meaning*. It sees `Transfer(account, amount)`, decides, and records why. The other controls remain
useful as the second witness of chapter 10: they catch what goes around.

There is a regulatory side, which I mention without claiming expertise. Rules now arriving for automated
systems ask that their actions be logged, attributable, and subject to human oversight. A journal of every
operation with the decision and its reason, and the ability to stop at any operation for a person, is that
requirement met by the structure rather than by a reporting project.

### 12.9 A supervisor is one more handler (runs)

The usual way to give an agent oversight is a framework: a runtime for agents, with its own notions of step,
tool, guard and callback, into which the agent is written. Frameworks bring real things — connectors, tracing
screens, people who know them.

The structure of 12.6 makes something smaller sufficient. A supervisor is a handler. It stops at an operation,
asks a policy — or a person, or another agent — and continues or does not. It needs no instrumentation of the
agent and no patching of its tools, and control stops being a review after the fact. If I had to compress this
into advice for someone designing a system that agents will write and operate: **give agents effects, not
frameworks.** That sentence is argued. I have not built the same agent on a framework and compared.

What follows runs. The listings are in the notation of section 2.0 and are checked by
`code/scala/agents.scala`, on the tree of chapter 3: `scala-cli run code/scala --main-class book.agents` prints
`agent examples hold`, and the results below are its assertions. Nothing in this section resumes a rest more
than once, so every technique of Part III could carry it; what the supervisor does need is section 6.4's
handler that is still standing when an answer comes back, to record how each call ended. An agent's one
operation, and an agent that does not learn:

```
effect Tools
  Tool(name, arg)        -> Text       -- what came back: an output, or "error: …"
effect Asking
  Confirm(call, because) -> yes or no  -- a question to a person

stubborn(tries) : Text ! Tools =
  if tries = 0 then done "gave up"
  out <- Tool("sh", "make test")
  if out starts with "error" then stubborn(tries - 1) else done out
```

**What a decision needs to know.** The policies of chapter 10 judged one call alone: this move is over the
limit. Better rules need what came before. *Ask before a push, unless the tests passed after the last edit.*
*Three commands failed in a row: stop and ask, even for a harmless one.*

The research literature has a name for this side. An effect is what a computation does to the world; a
*coeffect* is what it needs from its context (Petricek, Orchard and Mycroft, "Coeffects: a calculus of
context-dependent computation", ICFP 2014). The agent is effects. Its policy has a coeffect: the
calls before this one.

A need that is one value — a setting, the current user — is already an effect: the decision asks and is
answered. The need that differs in kind is the past:

```
Past  =  a list of (call, how it ended)                 -- the calls so far, oldest first

failedInRow(past)  =  how many entries at the end of past ended in "error…"

stuck(past, call)  =                                    -- the rule: go, or askFirst
  if there is no past to read then askFirst             -- an unmet need does not allow
  else if failedInRow(past) >= 3 and call.name is not "read" then askFirst
  else go
```

The supervisor keeps each call as it went, with how it ended, and decides the next from that:

```
handler Supervisor(rule) : A ! (Tools + Asking + R)  =>  A ! (Tools + Asking + R)
  done(a)                   => done a
  Tool(name, arg), rest     => call     := Tool(name, arg)
                               decision := rule(past, call)
                               decided  := decided ++ [decision]
                               why      := "{failedInRow(past)} calls failed in a row"
                               yes      := true
                               if decision is askFirst then yes <- Confirm(call, why)    -- a person, asked
                               if yes then
                                 out  <- call                              -- passed on to the world, outside
                                 past := past ++ [(call, out)]             -- on the way back: how it ended
                                 rest(out)
                               else
                                 past := past ++ [(call, "refused: {why}")]     -- recorded; not a failure
                                 rest("refused: {why}")                    -- the refusal is the answer
```

The decision is itself a program: it goes on, or it answers in place, or it asks a person first, and asking a
person is an operation like the rest. (`past` and `decided` are the supervisor's own, and start empty.)

```
run( stubborn(10) under Supervisor(stuck) under world under person )  =  "refused: 3 calls failed in a row"
  -- world.ran    = 3                                   the fourth call never reached the world
  -- person.asked = ["sh make test: 3 calls failed in a row"]
  -- decided      = [go, go, go, askFirst]
```

(`world` answers every `Tool` with "error: 2 tests fail" and counts them; `person` answers `Confirm` with no
and keeps what was asked. In the file the two decisions are `Decision.Go` and `Decision.AskFirst`, and the
supervisor keeps `past` and `decided` as fields.)

Three calls reached the world. The fourth waited for a person, who said no, and the refusal came back to the
agent as the answer to its call. `stubborn` has no supervisor in it.

One detail is in the code because the real guard got it wrong first. A person's refusal is recorded, and it is
not a failure. In the first live run a person's own refusals were counted as the agent's failures.

**The audit is the same function.** What the supervisor kept is a journal. Apply the same rule at every step of
it, each step with the past up to that step:

```
audit(journal, rule)  =  for each position i in journal:
                           rule(the entries of journal before i, the call at i)

audit(past, stuck)  =  decided                          -- past and decided as the supervisor left them
```

The audit gives exactly the decisions taken live. Here that is one assertion on two runs. In my guard it is a
law, held by a property test over 300 random sessions (notes 26 and 27; the notes state no more
of the protocol than that). An auditor's question — why was this call let through? — is answered by running
the policy again over the record, not by reading what somebody logged about the decision.

The shape used here, a point in focus with what led to it and one function applied at every such point, is what
the literature calls a comonad, and there are designs that model the whole agent as one: a state with a focus.
That view describes the answering side well — a window of conversation, a journal. I put effects at the centre
for one reason. An agent is dangerous through what it does, not through its state. To stop a command that runs
inside a step function, the step must be split into deciding and doing, and the doing, as data, is an operation
again. This is argued.

**An unmet need fails closed.**

```
stuck(no past, Tool("read", "README.md"))  =  askFirst
```

Where the guard stands somewhere that has no history, such as a proxy that sees single calls, a rule that needs
the past does not allow.

**The same agent, another world.**

```
run( stubborn(10) under Supervisor(stuck) under a world whose tests pass from the third run under nobody )
  =  "ok: all pass"                                     -- nobody.asked = []
```

(In the file the world is `Tests(passFrom = 3)`, and the audit of this second run is asserted equal to its
decisions as well.)

In the agent I use, the worlds are these: the project itself; a client in which my phone approves each edit; a
scripted model, in tests; a journal, with no model at all; and a forecast, in which the real model is given
*imagined* results of its tools, to see what it would do next before a risky step is allowed. Each is a choice
of handlers, and the agent's program is the same in all five.

That is also what makes an agent's laws testable. "A tool that was not offered never runs." "A replay performs
the same acts." "The forecast touches nothing." Each is a statement about a sequence of operations, and with
the operations explicit each is a property test: in my agent, over 200 to 300 runs against a model scripted to
misbehave (note 25, which gives no further protocol).

### 12.10 The model is an effect too

One step further. The *call to the language model* is itself a reach: it costs money, it is slow, it is not
deterministic, and it depends on a provider. So it is an operation — "complete this", "score these options" —
and the model is a handler.

Then everything applies in the other direction as well. Tests run with a recorded or a scripted model. A
session is replayed from its journal with the model's answers included, so a failure that happened once becomes
reproducible. The provider is swapped — a hosted model, a local one — by changing a handler. And a program can
be written so that the model is only ever asked to *choose among options the program offers*, never to produce a
free answer: its outputs are then typed by construction, and what it cannot be offered it cannot do.

Nor need the model be the only one that answers. Where a program asks "weigh these candidates", the same
operation can be answered by the data itself when the question names a thing literally, by an exact procedure
where one exists, by the model otherwise, and by a person when no source is sure. From outside these are
indistinguishable: each answers one operation. Inside, one is statistical and the others can be read and
checked, and the line between them is the handler. A part proposed by a model can then be admitted on a law —
it must agree with a reference on every input tried — and not on trust. I state the principle only; the work
behind it is outside this book.

## 12.11 The same reason, twice

An agent writes better code when what code may do is small, typed and checked. An agent acts safely when what it
may do is small, typed and checked. Effects are a way of making what a program may do into a list — and a list
is something both a compiler and a person can read.

The first half of that sentence is, for now, an argument. The second half is how the thing is built.

And the two halves of the idea divide between the two roles much as one would guess. What helps an agent
*write* is mostly the first: a vocabulary it can be given on a page, a row a compiler can check, handlers that
make a test cheap. What makes an agent that *acts* governable needs both: the call as a value, so that a policy
can read it, and the rest of the session in the supervisor's hand, so that the policy can refuse it, wait for a
person, or — where the rest can be used twice — ask what the agent would have done.
