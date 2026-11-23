# 9 · Screens, servers and stores as effects

Chapter 7 made a claim about whole systems and showed its shape in two lines that were not run. Chapter 8 went
through the effects every system has under its own. This chapter takes three things an application is made of
and that nobody calls standard effects — a screen in front of a person, a store behind, a server around both —
builds each as an effect small enough to read, and then runs one application under three stacks of handlers.

Each of the three sections has the same three parts, and they are kept apart.

1. **The idea, in miniature.** In the notation of section 2.0, on the forty lines of chapter 3, with every
   result an assertion. All of it is `code/scala/applications.scala`: `scala-cli run code/scala --main-class
   book.applications` prints `application examples hold`, and `sh code/run-all.sh` runs it. There is no
   network, no database and no browser in that file. A "request" is a value and so is a "page".
2. **What my own library does** for the same subject, read from its files and cited by path. These are one
   implementer's record. They show what the miniature leaves out, and they are where I report what was hard.
   They are not the way to do it, and I ran none of them for this chapter.
3. **What the usual tool does well**, and then where it ends.

One question runs through all three, the one chapter 8 asked of each standard effect: *would the first half do?*
Operations as data, answered once and in place, or the rest of the program as well? The answers are less even
than I expected. A store needs the first half alone until two things are asked of it, and both are in section
9.2. A screen needs the rest the moment its handler cannot wait where it stands. A server is two things: a
platform, which is first half throughout, and a host, whose whole job is keeping rests.

The store comes second and the server third, against the title, because the server of 9.3 hosts a program that
saves.

## 9.1 A screen

```
effect Screen
  Show(text)        -> Unit
  Enter(label)      -> Text                    -- what the person typed under that label
  Press(buttons)    -> one of the buttons

entered(field) : Text ! Screen =               -- a field is a label and what it accepts
  text <- Enter(field.label)
  if field accepts text then done text
  Show(the problem with text)
  entered(field)                               -- asked again

byQuestions : Order ! Screen =
  Show("New order")
  customer <- entered(customer) ; item <- entered(item) ; quantity <- entered(quantity)
  done {customer, item, quantity}
```

`byQuestions` is a dialogue written as a straight line. It asks the person as `pay` asked the bank, and like
`pay` it is a value: nothing has been shown to anybody.

**Answered where the handler stands.** A terminal can wait. Its handler prints, reads a line and calls the
rest:

```
handler Console(say, hear) : A ! (Screen + R)  =>  A ! R        -- a terminal: a real shared world
  done(a)                 => done a
  Show(text), rest        => say(text) ; rest(unit)
  Enter(label), rest      => say("{label}:") ; rest(hear())
  Press(buttons), rest    => say the buttons ; rest(a line that is one of them, asked until it is)

handler Scripted(acts, shown) : A ! (Screen + R)  =>  (shown, A) ! (Failure + R)
  done(a)                 => done (shown, a)
  Show(text), rest        => rest(unit)                              -- carrying shown ++ [text]
  Enter(label), rest      => if acts starts with (label, value) then rest(value)     -- carrying the other acts
                             else Refuse("the script has …, the screen asks {label}")
  Press(buttons), rest    => the same, for an act ("press", one of buttons)
```

```
run( byQuestions under Console(say, hear) )                    -- typed: ann, tea, two, 2
  =  {customer: ann, item: tea, quantity: 2}
  -- said: New order, customer:, item:, quantity:, quantity must be a number above zero, quantity:

run( byQuestions under Scripted([customer: ann, item: tea, quantity: two, quantity: 2]) )
  =  ([New order, quantity must be a number above zero], {customer: ann, item: tea, quantity: 2})

run( byQuestions under Scripted([customer: ann, quantity: 2]) under Refusals )
  =  refused("the script has quantity = 2, the screen asks item")
```

Both are *in place*, in chapter 8's word: the rest is called once, at once, as the last thing the handler
does. `Console` keeps no state because a terminal is a world (in the file it is two functions; the lines
"typed" above are a list). `Scripted` carries its acts and what was shown, and it names fields by their labels,
so a script that no longer fits the program fails with the place where it stopped fitting. The first half of
the idea is enough for both. A screen at a terminal is a command and its reply.

**Answered by somebody who must return.** A web server cannot wait where it stands. It is called with a
request, it must return a response, and the person's answer arrives in *another* call. So its handler does the
one thing `Console` could not do: at a question it does not call the rest. It hands the rest out.

```
handler Paged(shown) : A ! (Screen + R)  =>  (ended(shown, A) | waiting(shown, question, rest)) ! R
  done(a)                 => done ended(shown, a)
  Show(text), rest        => rest(unit)                              -- carrying shown ++ [text]
  Enter(label), rest      => done waiting(shown, entering(label), rest)       -- rest is not called
  Press(buttons), rest    => done waiting(shown, pressing(buttons), rest)
```

```
page1  =  run( byQuestions under Paged )           =  waiting([New order], entering(customer), rest1)
page2  =  run( rest1({customer: ann}) )            =  waiting([], entering(item), rest2)
page3  =  run( rest2({item: tea}) )                =  waiting([], entering(quantity), rest3)
page4  =  run( rest3({quantity: two}) )            =  waiting([quantity must be …], entering(quantity), rest4)
          run( rest4({quantity: 2}) )              =  ended([], {customer: ann, item: tea, quantity: 2})
```

Five runs, each of which ends. Between two of them nothing is running, and what the server holds is `rest2`:
the program from the second question on, with `ann` inside it. This is chapter 11's first section with a person
in the place of a director, and it is the sentence people use for the mechanism without the word: *the session
is the rest of the program, kept between requests*. `Paged` is `Approvals` with three kinds of question. This
handler needs the second half, and "later" is the line of chapter 2b's table it needs.

What `rest2` is made of decides what the server can do with it, exactly as in section 11.2. Here it is a
closure, in one process. Section 9.3 keeps a table of them, and then replaces them twice with something that
can be written down.

**Back is the rest called again.** `rest1` is still in hand after `page2` was made from it:

```
run( rest1({customer: bob}) ) … answered tea and 2   =  ended([], {customer: bob, item: tea, quantity: 2})
run( rest4({quantity: 3}) )                          =  ended([], {customer: ann, item: tea, quantity: 3})
```

A browser's back button followed by a different answer is a rest resumed a second time. On the forty lines it
works and gives two independent futures, because `Paged` carries what it shows and keeps nothing in a variable
(section 4.6). On the eight renderings whose rest can be resumed once it cannot be done this way at all, and
the screen must be rebuilt by other means. And what the first future did to the world stays done: a save
behind the first `quantity` is not taken back by going back.

**A screen as data.** The other way to ask is one question that holds a whole form:

```
orderForm  =  form("New order", [field(customer), field(item), field(quantity, a number above zero)])

effect Screen, one more
  Fill(form)   -> what was entered, by label          -- every field accepted

handler FieldByField : A ! (Screen + R)  =>  A ! (Screen + R)        -- a form, lowered into questions
  Fill(form), rest   => Show(form.title)
                        each field's text <- entered(field)
                        rest(them, by label)
```

`orderForm` is a value. What a field accepts is in it as data, a kind, not a function. Two handlers answer
`Fill` differently. `FieldByField` turns it into the sequence above, for a handler that can ask one thing at a
time: under it and the same script, `Fill(orderForm)` gives exactly what `byQuestions` gave. `Paged` gets one
more clause and answers the form in one page, checked from the data, with every problem at once:

```
form1  =  run( Fill(orderForm) under Paged )            =  waiting([], filling(orderForm), rest)
form2  =  run( rest({customer: ann, quantity: two}) )
       =  waiting([item is needed, quantity must be a number above zero], filling(orderForm), rest)
          run( the rest of form2, given all three )     =  ended([], {customer: ann, item: tea, quantity: 2})
```

Three round trips became one, and no line of the program says so.

When each? A form is right when the questions do not depend on each other's answers. Then the program has
nothing to decide between them, so it should not be the one sequencing them: a handler that has the whole form
can lay it out, check it, let the person move between fields, and send it once. A sequence is right when the
next question depends on an answer: a confirmation that shows what was entered, a branch, an approval. The
application of 9.4 uses both, a form and then a confirmation. The trap is the middle: a program that asks ten
independent fields one by one has promised an order nobody needed, and no handler may undo the promise. The
same trap, with keys for fields, is the N+1 query of the next section.

**In my library.** okay-ui (`okay-ui/README.md`, `specs/business.md`) is the second way taken as the rule. Its
effect `Ui` has one operation that waits, `Show(screen)`, answered with the result of the button pressed; a
screen is a value made of `Field`, `Form`, `Table`, `Button` and `Part`, and the spec says of fields what I
said of `orderForm`: what one accepts is decided in the model, once, for every toolkit. Sequence exists only
*between* screens. There is one standard handler, `steps`
(`core/src/main/scala/okay/business/Business.scala`), and it is `Paged`: it makes a `Step` that is `Done` or
`Waiting(screen, resume)`, and everything that shows a screen is a loop over steps. A terminal, a test script
(`Play`, which presses buttons and fills fields by their labels) and a page are three such loops. So where my
miniature has two handlers in place and one that stops, the library has only the one that stops, and the
terminal is a driver of it. I think the library is right and the miniature is the better teaching: it shows
which of the three *needs* the rest.

What the library has that the miniature lacks: tables and selection; buttons that carry values; a second
operation for a screen shown while the program goes on; a block of work a person can leave, which keeps a rest
and resumes it a second time; screens answered by an event; three kernels behind one import. The comment on
`steps` says the rest is kept in each `Waiting` "so a toolkit may resume an older one again: back", and the
spec adds the condition, a kernel whose rest can be resumed more than once. The underlying effect is in
okay-faces (`okay-faces/README.md`): `Face`, with `Offer(view, actions)` and `Show(view)`, the same step
machine, and a face for a terminal, a window, pages from a server, a JSON API, tools for an agent, a phone.

What was hard, from `okay-ui/specs/reference.md`. The reference application's page tests ran under a DOM
library that does not validate inputs, and they passed. A number field drawn without a `step` attribute was
then refused by a real browser's own validation for a value such as `12.5`, before the form reached the
program. It was found by running the application in a headless browser, and fixed in the face. The lesson is
about this section's `Scripted`: a script tests the program against the vocabulary. It says nothing about a
handler it replaced.

**What a component framework does well, and where it ends.** A component framework treats a screen as a
function of state, redrawn when the state changes. That is the right model for what happens *inside* a
screen: a keystroke, a list that filters as one types, a drag, an animation, layout. It has an ecosystem of
components no library of mine will have, and for an application that is one rich screen it is the tool.

It ends where a screen is a step in something longer. A flow of five screens is, in such a framework, a state
variable saying which screen is current and event handlers that change it: the hand-written state machine of
chapter 1b, with the sequence that the business cares about spread over callbacks. It is tested by driving a
renderer. And a second front for the same flow — a terminal, a set of tools for an agent — is a second
program. What this section adds is narrow: the *flow* as a straight line that can be run under a script. In my
own pages the screens of that flow are still drawn by ordinary page code, inside a handler.

The idea of keeping the rest of a page between requests is also not new: web frameworks built on
continuations did it long before the libraries of this book. I name none here; the backlog has the ones I
remember, to be read again before they are described.

## 9.2 A store

```
effect Store
  Find(table, key)             -> a row, or nothing
  Save(table, row)             -> Unit                 -- a row has a key; a second save of it replaces
  Query(table, column, value)  -> the rows with that value in that column, by key
```

Chapter 8's `Store` held a number under a key, as the lower vocabulary of a state. This one has rows, and it
is at the level section 7.1 asked for only by courtesy: a real application would say `Orders.of(customer)`
and handle that into this.

Three handlers.

```
handler InTables(tables) : A ! (Store + R)  =>  (A, tables) ! R             -- in memory, carried
handler InJournal(lines) : A ! (Store + R)  =>  (A, lines) ! R              -- a save is a line; nothing is overwritten

effect Database
  Sql(text, values)   -> rows

handler AsSql : A ! (Store + R)  =>  A ! (Database + R)                     -- SQL as text
  Find(table, key), rest          => rows <- Sql("SELECT * FROM {table} WHERE key = ?", [key])
                                     rest(the first of rows, or nothing)
  Save(table, row), rest          => Sql("INSERT INTO {table} (…) VALUES (…) ON CONFLICT (key) DO UPDATE SET …",
                                         [the row's values])
                                     rest(unit)
  Query(table, column, v), rest   => rows <- Sql("SELECT * FROM {table} WHERE {column} = ? ORDER BY key", [v])
                                     rest(rows)
```

All three are in place. `AsSql` is section 7.2's layering again: it answers one effect by asking a lower one,
and what it asks is *data*. That is the whole of this handler's interest. A statement is a text with its values
beside it, never inside it, so it can be asserted, logged, counted, and sent by whoever has a connection (the
file's run has a fourth statement, quoted at the end of this section):

```
run( (Save(products, tea) ; Find(products, tea) ; Query(orders, customer, ann))  under AsSql under NoDatabase )
  -- said:
  -- INSERT INTO products (key, maker, name) VALUES (?, ?, ?)
  --   ON CONFLICT (key) DO UPDATE SET maker = excluded.maker, name = excluded.name       [tea, m1, green tea]
  -- SELECT * FROM products WHERE key = ?                                                 [tea]
  -- SELECT * FROM orders WHERE customer = ? ORDER BY key                                 [ann]

run( Find(orders, "x'; DROP TABLE orders; --") under AsSql under NoDatabase )
  -- said: SELECT * FROM orders WHERE key = ?        [x'; DROP TABLE orders; --]
run( Query(orders, "x'; DROP TABLE orders; --", ann) under AsSql under NoDatabase under Refusals )
  =  refused("not a name: x'; DROP TABLE orders; --")
```

A value cannot reach the text because there is no line that puts one there. A name of a table or a column
does go into the text, and the handler checks names in one function. Injection is a property of one handler
that can be read in a minute, not of every place that builds a query.

`NoDatabase` is the honest limit of this file. It keeps what was said and answers every statement with no
rows. No engine runs these statements here, because the book's code takes no dependency, so nothing below is
evidence that a database accepts them or answers as `InTables` does.

**What a store owes**, as chapter 8 owed a list for each effect: you find what was saved; the last save of a
key wins; a query finds what was saved; several keys at once are each of them (for the operation that the end
of this section adds). The file runs both sides of each under anything that claims to be a store.

```
storeOwes(InTables)                  =  [holds, holds, holds, holds]
storeOwes(InJournal)                 =  [holds, holds, holds, holds]
storeOwes(AsSql under NoDatabase)    =  [fails, holds, fails, holds]
```

The third line is section 8.1's stub. SQL over no database finds nothing it saved. It "holds" twice only
because both sides of those equations are equally empty. So the stack with SQL text in it, in section 9.4,
is a test of what the program *says* to a database and of nothing it is told back. Text asserted is not
agreement, and agreement between two handlers of one effect is the thing a store most needs tested.

**A transaction, around a part.** Section 7.6 built a transaction as a node holding its body. Chapter 8 said
that five of its six constructs over a stretch were handlers around a part. Here is the transaction that way:

```
handler Atomically(log) : A ! (Store + R)  =>  A ! (Store + R)
  done(a)               => Save each line of log, of whoever is outside          -- at the end
                           done a
  Save(table, row), rest => rest(unit)                                           -- carrying log ++ [it]; not asked
  Find(table, key), rest => if log has that key then rest(its last row)          -- its own writes
                            else found <- Find(table, key) ; rest(found)
  Query(…), rest         => the outside's rows with the log laid over them

both(fails)  =  ( Save(products, tea)
                  mine <- Find(products, tea)
                  if fails then Refuse("no cups")
                  Save(products, cup)
                  done mine )  under Atomically

run( both(no) under InTables({}) )                    =  (tea, {products: {tea, cup}})
run( both(yes) under Refusals under InTables({}) )    =  (refused("no cups"), {})          -- nothing was written
run( both(no) under AsSql under NoDatabase )          -- said: INSERT, INSERT; the read never reached it
```

This is section 8.6 turned over. There, a scope written as a handler released on its `done` line, and a rest
dropped from outside left the file open: the trap. Here the `done` line commits, and a rest dropped from
outside is exactly a rollback. The same mechanism, and it is a leak for a resource and the right behaviour
for a write that has not happened yet. The handler needs nothing of the rest; it relies on the refusal
handler outside to drop it. One limit: under `AsSql` the log goes out as two plain statements, with no
`BEGIN` and no `COMMIT` said. Nothing is written unless the part ends; that the two are then written as one
is not shown.

The other half of 8.6's finding holds too, and here it is the trap:

```
forked  =  ( Save(products, tea) ; k <- Choose([cup, mug]) ; Save(products, k) )  under Atomically

run( forked under AllChoices under InJournal )     -- lines: tea, cup, tea, mug
```

A choice outside resumes the rest of the transaction twice, and each ending commits its log. Tea was saved
twice. With rows that replace by key nobody would notice. With a payment somebody would. A transaction of this
kind must stand outside whatever repeats the rest, as a scope must.

**N+1, seen and removed.** The report every reader has written:

```
line(order)  =  product <- Find(products, order.item) ; done "{order.quantity} x {product.name}"

reportOneByOne(customer)  =  orders <- Query(orders, customer, customer)
                             for each order: line(order)                   -- one after another
```

An observer in front of the store, chapter 2's journal keeping names only:

```
run( reportOneByOne(ann) under Seen under InTables(stock) )
  =  ([Query, Find, Find, Find], [2 x green tea, 2 x a cup, 2 x green tea])
```

One query and one find for each row: N+1, as a value in a test, with no profiler. Now, can a handler remove
it? Not from this program. A handler is offered one `Find` and the rest; the second `Find` does not exist
until the first is answered, because the program is a sequence and a sequence says that each step may depend
on the one before. Here it does not, and the program has not said so. That is the screen's trap from 9.1.

So the program says it, and a handler does the rest of the work:

```
effect Store, one more
  FindMany(table, keys)   -> the rows of those keys

handler Parking : A ! (Store + R)  =>  (ended(A) | atFind(table, key, rest)) ! (Store + R)
  done(a)                  => done ended(a)
  Find(table, key), rest   => done atFind(table, key, rest)               -- rest is not called: it is kept

together(parts)  =
  parked := each part under Parking                    -- every part, run to its first Find and stopped there
  while some of parked are atFind(table, …):
    rows   <- FindMany(table, the keys they wait for, each once)          -- ONE question
    parked := each of those, resumed with its own row; the others as they are
  done what each ended in

report(customer)  =  orders <- Query(orders, customer, customer)
                     together(for each order: line(order))
```

```
run( report(ann) under Seen under InTables(stock) )
  =  ([Query, FindMany], [2 x green tea, 2 x a cup, 2 x green tea])
```

`line` is unchanged: it still asks for one product as if it were alone. `together` holds the rest of every
line at its `Find`, asks once, and resumes each with its own row. Two of the three orders are for tea, and tea
was asked for once. A part that asks twice takes two rounds:

```
lineWithMaker(order)  =  product <- Find(products, order.item) ; maker <- Find(makers, product.maker) ; …

run( report(ann, lineWithMaker) under Seen under InTables(stock) )    -- asked: [Query, FindMany, FindMany]
                                                                       -- one by one: seven questions
```

Three questions where the sequence asks seven, and the count does not grow with the rows.

This is the best example in the chapter of the two halves each doing its part, so it is worth saying which
did what. The *data* half is the program saying "these are independent": `together` is structure in the
program, and no handler could have invented it. The *continuation* half is the batching: several programs
stopped in the middle, their rests held side by side, answered out of order with one question between them.
An interpreter of commands cannot do that, because it has returned to its caller before the second key
exists. Each rest is resumed once, so every technique of Part III has what `together` needs; I ran it on the
tree only. Under `AsSql` the one question is `SELECT * FROM products WHERE key IN (?, ?) ORDER BY key`, in
the file.

**In my library.** okay-store (`okay-store/README.md`, `specs/okay-store.md`) has the three levels of this
section and names them: a domain effect, written by the application; `Data`, collections of JSON documents by
key with `Get`, `Put`, `Delete`, `Query(where, order, limit)`, `Count`; and a backend, which is a value:
memory, lines appended to files, SQL by a dialect. A domain's handler is a translation into `Data` written
once, and it then runs on every backend. On top, `Mapping[A]` makes a type's own effect (`save`, `load`,
`find`, `count`) with fields named by a lambda the compiler checks, and one handler for every mapped type.
The spec's own summary of what that is not: "no session, no identity map, no lazy loading, no change tracking.
A value is a value".

The SQL is text in the core module, as here: `SqlStatement(sql, args)` and two dialects with no connection
in sight (`core/src/main/scala/okay/store/SqlData.scala`), so that JDBC runs it on the JVM and my serverless
library runs the same statements from a Worker. What the library has that this section could not: the
statements are *run*, and the handlers are checked against each other. One program is answered in memory, on
lines and on SQLite and the answers compared, and every form of a condition is compiled to SQL and compared
with a reference meaning on the same documents.

That test is where the hard part was, and it is a finding the miniature is unable to make. From the spec, as
"found on Postgres": SQL's logic has three values, so a condition on a missing field is NULL, its negation is
NULL too, and the row is lost, where the reference says a predicate on nothing is false and its negation
true. Every leaf of every compiled condition is now wrapped so that it is false and not NULL. An assertion on
the text of a statement would have passed before and after.

Transactions there are this section's handler, a log over any backend with everything written when the program
ends, and beside it the database's own transaction. The spec states the trap of section 8.6 for the second in
its own words: what ends the program early "cannot run a finalizer — an abort drops the rest, nothing is
thrown — so the transaction is left open", to be rolled back by the next one on the connection or by closing
it. And it says a transaction *within* a program has no place to be said, "split the program": this library
has neither my handler around a part nor a node like section 7.6's.

On N+1 the library is where `reportOneByOne` is. Relations are keys in fields and a second `find`, and a join
is "the program's, above the backend", by decision. I searched its core for a batching handler and found none.
`together` is not in any library of mine.

**What an ORM does well, and where it ends.** An object-relational mapper gives a great deal: the mapping
itself, migrations, a query language that survives a change of database, caching, change tracking so that a
modified object is written without anybody saying so, and relations that can be walked as fields. For a
system that is mostly forms over tables, that is most of the code not written.

It ends at the same place for two reasons that are one. A relation walked as a field is a query nobody asked
for by name, so N+1 arrives silently, and the remedies — fetch this relation eagerly, load in batches of this
size — are declared somewhere other than where the walk is written, per query or per mapping. And the session
that tracks changes is ambient state found by the code that runs inside it, which is section 7.6's
transaction again. What the effect gives is smaller and plainer: every reach is a question with a name, so an
observer can count them in a test, and a program that wants a batch says where.

## 9.3 A server

Two different things are called a server, and effects treat them differently.

**A platform is an effect.** What a program may ask of the place it runs in:

```
effect Platform
  KvGet(key)          -> Text, or nothing
  KvPut(key, value)   -> Unit
  Fetch(url)          -> Text

handler Memory(kv, pages) : A ! (Platform + R)  =>  (A, kv) ! (Failure + R)      -- for tests: kv carried
handler Directory(dir)    : A ! (Platform + R)  =>  A ! (Failure + R)            -- files: a real world
  KvGet(key), rest        => rest(the file named key in dir, or nothing)
  KvPut(key, value), rest => write that file ; rest(unit)
  Fetch(url), rest        => Refuse("no network on this platform: Fetch({url})")
```

The clock is chapter 8's and is answered as it was there. `Directory` is the nearest thing to a real platform
that needs no network: files in a temporary directory. Like the machine's clock in 8.7 it reaches the machine
it runs on. It keeps no state, because the directory is the state, and a new
handler on the same directory finds what an earlier one wrote. That is the property the rest of the section
uses.

```
kvOwes(Memory)       =  [holds, holds, holds]      -- got what was put; the last put wins; never put is nothing
kvOwes(Directory)    =  [holds, holds, holds]

run( Fetch("prices/tea") under Directory(dir) under Refusals )  =  refused("no network on this platform: …")
run( Fetch("prices/tea") under Memory({}, {prices/tea: 4}) )    =  ("4", {})
```

Everything here is in place. A platform is the first half and nothing else, and the one design point is the
last pair of lines: a platform that lacks something says so by name.

**A host is a handler.** The other thing called a server is what answers requests. Take the direction
seriously: a request is a question *the world* asks of the application.

```
effect Web
  Visit(request)   -> Response       -- request: a session or none, and what was entered
                                     -- response: the session, what is shown, and what is asked, if anything

shopper : List(Response) ! Web =
  r1 <- Visit(no session)
  r2 <- Visit(r1.session, {customer: ann, item: tea, quantity: 2})
  r3 <- Visit(r1.session, {press: Save})
  r4 <- Visit(r1.session, {press: Finish})
  done [r1, r2, r3, r4]
```

The client is a program. The application that serves it is a handler of `Visit`, written in terms of other
effects, and "request to response" is one clause of it. The application behind it is the order desk of this
chapter, a form, then a confirmation, then a save:

```
desk : Text ! (Screen + Store) =
  order <- Fill(orderForm)
  Show("{order.quantity} x {order.item} for {order.customer}")
  how   <- Press([Save, Cancel])
  if how = Cancel then done "cancelled"
  mine  <- Query(orders, customer, order.customer)
  key   := "{order.customer}-{the number of mine + 1}"
  Save(orders, {key, order})
  Show("saved as {key}")
  more  <- Press([Another, Finish])
  if more = Another then desk else done key
```

`desk` stops four times for one order, so a host has to get a stopped program from one visit to the next. I
wrote three hosts. They differ only in what they keep.

**The first keeps rests.**

```
handler Hosted(program, sessions) : C ! (Web + R)  =>  C ! R              -- R: whatever program asks
  Visit(no session), rest      => page <- program under Paged
                                  rest(page, as a response)               -- carrying sessions[a new id] := page's rest
  Visit(id, answers), rest     => page <- sessions[id](answers)
                                  rest(page, as a response)               -- carrying sessions[id] := page's rest
```

A table of rests, keyed by session, carried by the handler. Section 8.9's scheduler was this with a queue
where this has a table, and a `pick` where this has the visitor's own choice of which rest goes next. A web
server for a dialogue *is* a scheduler whose fibres are advanced by people.

**The second keeps answers.**

```
handler Replayed(program) : C ! (Web + R)  =>  C ! (Platform + R)
  Visit(id, answers), rest     => past <- KvGet("journal/{id}")
                                  KvPut("journal/{id}", past ++ [answers])
                                  page <- program under Recalled(id) under Paged,
                                          answered with each of past ++ [answers] in turn    -- from its first line
                                  rest(page, as a response)
```

Nothing is held between visits. Each visit runs the program again from the start and feeds it everything the
person has answered: section 11.3, with the journal on the platform. `Recalled(id)` is that section's
journalling handler for the store, writing each of the store's answers under `answers/{id}/{n}` and answering
from there when the n-th question comes round again.

**The third keeps the state.**

```
data At  =  atForm | atConfirm(order) | atMore(key)                       -- one case for each stop of desk

turn(at, answers) : (ended(Text) | next(At, shown)) ! Store               -- desk, cut at its stops, by hand

handler Stated : C ! (Web + R)  =>  C ! (Platform + Store + R)
  Visit(id, answers), rest     => at <- read(KvGet("state/{id}"))
                                  r  <- turn(at, answers)
                                  KvPut("state/{id}", write(where r stands))
                                  rest(r, as a response)
```

Section 11.4, with the same honesty about it: `At` and `turn` are `desk` written a second time, as the state
machine a compiler would make of it.

```
run( shopper under Hosted(desk) under InTables({}) )
run( shopper under Replayed(desk) under InTables({}) under Memory({}) )
run( shopper under Stated under InTables({}) under Memory({}) )
  -- all three: the same four responses, ending "done: ann-1", and one order, ann-1
```

and what each holds while the shopper looks at the confirmation:

| host | kept for session s1, at the second question |
| --- | --- |
| `Hosted` | a closure, in the handler's table |
| `Replayed` | `journal/s1` = `customer=ann&item=tea&quantity=2`; later also `answers/s1/0` and `answers/s1/1` |
| `Stated` | `state/s1` = `AtConfirm\|customer=ann&item=tea&quantity=2` |

**Another process.** Split the shopper in two, two visits and then two more, and end the run between them:

```
run( the second half under Hosted(desk) … )     =  [no such session, no such session]
```

A closure does not outlive the run that made it. For the other two hosts the file does it twice: with
`Memory`, handing the first run's keys to the second, and with `Directory`, where the second run has a new
handler on the same directory and nothing is handed over at all. Both hosts, on both platforms, give the four
expected responses and one order. One program, two platforms, and the host that survives a restart does so on
either, because all it asks is `KvGet` and `KvPut`.

**The finding.** The file's first `Replayed` had no `Recalled` in it. It journalled what the person answered,
which is what a replaying web host obviously has to keep, and the assertion that it agrees with the other two
hosts failed:

```
run( shopper under ReplayedPersonOnly(desk) under InTables({}) under Memory({}) )
  -- shown: [], [2 x tea for ann], [saved as ann-1], [done: ann-2]
  -- orders: ann-1, ann-2
```

The shopper was told `ann-1` and then `ann-2`, and there are two orders. The fourth visit ran `desk` again from
its first line with the three earlier answers, so the query and the save ran again, and the store is a world:
the second run's query saw the first run's order. This is section 11.3's first restriction, the code must be
deterministic, and what it means in an application is easy to state and easy to miss: *replay must journal
every answer the program was given, by the world as well as by the person*. The store's answers are part of
the session. I kept the broken host in the file as an assertion, and `Recalled` is what the working one adds.
Then the journal of a session is no longer three short lines. It is every row the program ever read.

Two holes remain in the miniature, both argued and not run. `Stated` saves the order and then writes the new
state in two steps; a process that dies between them serves the retried visit from the old state and saves a
second order. Section 11.7 named the remedy, a step committed in one piece and a stable key for what it does
outside. And all three hosts take a session's name from a counter read and written in two steps, which two
hosts at once would both read.

**In my library.** okay-serverless (`okay-serverless/README.md`, `specs/cloud.md`, `specs/durable.md`) is both
halves of this section.

The platform: each thing a Cloudflare Worker can use is an effect — the README lists `Kv`, `Blobs`, `Sql`,
`Queues`, `Fetch`, `Cache`, `Env`, `Services`, `Limits`, `Ai`, `Vectors`, `Analytics`, `Mails`, `Objects`,
`Tcp` and `Sockets` — and a cloud is a `Platform`, one `Port` for each effect, a port being a function from an
operation to a future of its answer (`core/src/main/scala/okay/serverless/cloud/Platform.scala`). There are
four: memory, Cloudflare, AWS, and a JVM process with one directory, the fallback of section 7.5. A port a
cloud does not have is `Port.missing`, which fails naming it, as `Directory` does here; the spec lists what
the AWS platform still answers that way. A Worker is an `App`, four programs (`fetch`, `scheduled`, `queue`,
`mail`) from the platform's events to a response: the host as a handler, with the platform calling it.

The durable part is `Stated`'s road, and it closes the first hole I left. `Durable.step(p)` runs `p` against
a snapshot, keeps its writes in the handler, and commits them all at once with the new state; if what it read
has changed meanwhile the step is run again, which the spec calls legitimate because `p` is a value and
nothing it wrote had left the handler. `Durable.once(name)` keeps an outside call's result in the step's
writes and gives it a stable idempotency key. The storage under it is three operations, a read with versions,
a list, and a commit that says whether it went in. On Cloudflare an object is one writer and a commit never
conflicts; on DynamoDB it is one conditional transaction, and the spec records the test, on a local DynamoDB:
two instances incrementing one counter at once, "twenty of twenty". The reason for not keeping the rest is in
the spec as it is in section 11.4: it "holds closures — code, not data".

What was hard, from `specs/cloud.md`, is what a platform in memory does not show. Calling an object on
Cloudflare followed a redirect on the way, so a language redirect came back as the page and a sign-in's
cookie was lost; the port now asks for redirects as they are, and the note beside the fix says "Memory never
followed one". Two effects have no local mode at all and, in the spec's words, are written to the bindings'
API, "not run". A handler for tests that behaves better than the real one hides the difference: section 8.7
said it of a clock.

And one thing a reader of chapter 11 is owed. For *screens* on Workers my libraries do not take `Stated`'s
road alone. okay-faces' hosting (`okay-faces/specs/cloudflare.md`) says a session's continuation "is code …
and is never stored"; what is stored is the request that opened the session, the last *point* the program
passed with its state as JSON, and the requests since. A woken session starts the program at the point and
plays those few requests again. A program that marks no points "is kept by all its requests", and the spec
adds in brackets: deterministic, then. So it is `Stated` at the points and `Replayed` between them, and it
meets this section's finding by the other remedy: the program is made again for each session, so that a
handler it holds is "never doubled by a journal played". A ledger shared by a person's sessions is listed as
not yet built (`okay-ui/specs/reference.md`). Section 11.6 says I no longer build on replay. For durable
processes that is what the files show. For a dialogue in a browser they show a bounded replay, and I would
rather the book say so.

**What a web framework does well, and where it ends.** A web framework gives routing, parsing, sessions,
middleware, templates and validation, and its request handler is the same shape as a clause of `Hosted`: a
function from a request to a response. Its insistence that nothing live in the process between two requests
is a virtue: any instance can serve any request. Most pages of most sites are one question and one answer,
and for those there is no rest to keep and nothing in this section to want.

It ends at the dialogue. A flow over four requests is four handlers and a convention about what is in the
session between them, which is `At` and `turn` written without the names, and usually without the type. And
the platform is called directly, so the code that runs on one cloud is that cloud's. What this section adds,
again narrowly: the dialogue as one program, and the choice of what a session *is* — a rest, a journal, a
state — made in one handler and changed without touching the dialogue. `Stated` did need the dialogue
rewritten; that is the price of the road that keeps least.

## 9.4 One application, three stacks

`desk` is a dozen lines and knows nothing of terminals, pages, tables or SQL. Three lines run it.

```
run( desk under FieldByField under Console(say, hear) under InTables({}) )         -- typed: ann, tea, 2, Save, Finish
  =  ("ann-1", {orders: {ann-1}})
  -- said: New order, customer:, item:, quantity:, 2 x tea for ann, [Save] [Cancel], saved as ann-1,
  --       [Another] [Finish]

run( desk under FieldByField under Scripted([customer: ann, item: tea, quantity: 2, press: Save, press: Finish])
          under InTables({}) )
  =  (([New order, 2 x tea for ann, saved as ann-1], "ann-1"), {orders: {ann-1}})

run( shopper under Hosted(desk) under AsSql under NoDatabase )
  =  the four responses of 9.3
  -- said: SELECT * FROM orders WHERE customer = ? ORDER BY key                           [ann]
  --       INSERT INTO orders (key, customer, item, quantity) VALUES (?, ?, ?, ?)
  --         ON CONFLICT (key) DO UPDATE SET customer = …, item = …, quantity = …         [ann-1, ann, tea, 2]
```

A terminal with a store in memory; a test; pages over requests with the store as SQL. In the third, the thing
run is the shopper and the application is one of its handlers, which is the direction of section 9.3. The
durable hosts of 9.3 are the fourth and fifth lines, on two platforms each.

This is section 7.3's line and section 7.4's, with nothing left to the imagination but the size. What it
shows that those sections could only state: the lines differ in *kind* of handler, not only in which
implementation. The terminal answers in place and the pages keep the rest, and `desk` cannot tell. Section 7.5
said a program that only asks runs wherever its questions can be answered. The qualification this chapter
adds is that "can be answered" sometimes means "can be kept".

What it does not show is in the limits of each part. The third line saved nothing: `NoDatabase` returns no
rows, so `desk` computed `ann-1` from an empty answer and would compute it again for a second order. On the
forty lines the stacks are checked when they run and not before: a `desk` under no store fails at its first
`Query`, with "nobody answers", and the file asserts that. With rows it would not compile, and none of this
file is on stage 4.

## 9.5 The three, on the two halves

| | operations | what its handlers do with the rest | where the second half came in |
| --- | --- | --- | --- |
| a screen | `Show`, `Enter`, `Press`, `Fill` | in place; later | a handler that must return to its caller |
| a store | `Find`, `Save`, `Query` | in place; at the end | several rests held, to ask once for all |
| a platform | `KvGet`, `KvPut`, `Fetch` | in place | nowhere |
| a host | `Visit` | in place for the client; keeps its sessions | the session is a rest, or stands in for one |

Read the last column. A terminal, a script, three stores, two platforms and SQL as text are command objects
and an interpreter: chapter 7 said most of an architecture is the first half, and of the handlers in this
chapter that is true of all but four. The four are the chapter. `Paged`, because a web server cannot wait.
`Hosted`, because somebody has to hold what `Paged` hands out. `Parking`, because batching is holding several
programs in the middle. And `Atomically`, which calls the rest in place and is in the list for what it does
not do: it commits at the end and relies on a dropped rest never arriving there.

Two of the findings are one finding. A form asked field by field and a report that finds row by row are
programs that put independent things in sequence, and in both cases the cost is a round trip each, to a person
or to a database. The remedy was the same: say in the program that they are independent — `Fill(form)`,
`together(parts)` — and let a handler do them at once. A sequence is a promise as well as a convenience.

And the third finding is chapter 11's, met from the other side. Whatever stands in for the rest of a program
has to stand in for all of it. A journal of the person's answers was not the session; the store's answers
were part of it too.

## What was run

Every listing is an assertion of `code/scala/applications.scala`, main `book.applications`, under
`sh code/run-all.sh` (`holds    scala: chapter 9, screens, servers and stores`). The file is on
`code/scala/kernel.scala`, uses `Refuse` of `examples.scala` and `carry`, `refusals`, `allChoices` and `among`
of `catalogue.scala`, and has its own definitions in an object, `app`. It has no cast and no `@unchecked`.
Where the notation says `refused(why)` the file says `Left(why)`; a page's `waiting` and `ended` are the
cases of `Page`.

| section | what is asserted |
| --- | --- |
| 9.1 | the terminal's result and what it said; the script, a script that does not fit, one that ends early |
| 9.1 | five pages, one at a time; an earlier rest resumed again, twice; a form lowered; a form in one page |
| 9.2 | `storeOwes` for three stores; four statements as text; a hostile value and a hostile name |
| 9.2 | the journal keeps both versions; the transaction kept, refused, as SQL, and committed twice |
| 9.2 | one by one: four questions; together: two; two rounds: three against seven; `IN (?, ?)` |
| 9.3 | `kvOwes` for two platforms; `Fetch` refused and answered |
| 9.3 | three hosts agree on four visits and on eight; what each keeps; the host that doubles the order |
| 9.3 | a second run: closures gone; answers and states kept, in memory and in a directory |
| 9.4 | the three stacks, with what was said at the terminal and to the database; `desk` with no store |

Two handlers keep a world on purpose and say so: `Console` (two functions; the real one would read the
keyboard and is not run) and `Directory` (a temporary directory, removed afterwards). Everything else that
remembers carries it.

**What rests on one technique.** All of it: one rendering, the Scala tree. `Console`, `Scripted`, the stores,
the platforms, `AsSql`, `Stated` and `Replayed` need what every technique has. `Paged`, `Hosted` and
`together` need a rest that can be handed out and resumed once, later, which every technique also has; I say
so from section 6.3's requirement and did not write a second rendering. Going back needs the rest twice.

**What is argued and not run.** That a database would accept the statements and answer as the tables do. The
two holes of `Stated` and of the session counter. That rows would turn a missing handler in 9.4 into a compile
error. Everything said of component frameworks, ORMs and web frameworks, which is general and names no
product; that continuation-based web frameworks came first is from memory, and the backlog has it to check.

**What is reported from my own files and not run here.** okay-ui's vocabulary, its `steps` and `Play`, and the
decimal field found in a real browser (`okay-ui/README.md`, `specs/business.md`, `specs/reference.md`,
`core/src/main/scala/okay/business/Business.scala`). okay-faces' `Face` and its hosting on Workers
(`okay-faces/README.md`, `specs/cloudflare.md`). okay-store's levels, its SQL as text, the agreement tests,
the NULL found on Postgres, its two transactions, and that it has no batching
(`okay-store/README.md`, `specs/okay-store.md`, `core/src/main/scala/okay/store/SqlData.scala`; the last by
searching the core). okay-serverless's effects, ports and platforms, `Durable`, the redirect and the two
effects with no local mode, and "twenty of twenty" (`okay-serverless/README.md`, `specs/cloud.md`,
`specs/durable.md`, `core/src/main/scala/okay/serverless/cloud/Platform.scala`). okay-shop, a small business's
site built on three of these, was read (`okay-shop/README.md`, `specs/shop.md`) and is not described: it
would add a fourth list of handlers and no new idea.
