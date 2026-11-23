// THE LAWS, AS LINES (chapter 4) in Rust, on STATE MACHINES written by hand as code/rust/bank.rs is (chapter
// 3e): the twelve observations of code/laws-expected.txt. A program is an enum with one variant per place it
// can stop. Three things are machines too, each written once: `Then` (one machine run to its end, then
// another), `Under` (a machine with a handler: its questions answered, the others still asked) and `All` (the
// rest cloned once per option). So a handler's state is a field of a machine, and cloning the machine clones it.
//
// What lines 1 to 3 compare: `Then`, which is this file's sequencing, against machines written by hand. They
// are laws of that one combinator, seen on these machines. They say nothing of the hand-written machines
// themselves: that `Bump` is `x <- Get ; Put(x + 1) ; done x` is the author's translation, and no line checks it.
// `rustc --edition 2021 -O code/rust/laws.rs -o /tmp/laws-rs && /tmp/laws-rs`

use std::fmt::Display;

#[derive(Clone, Debug)]
enum Op {
    Get,
    Put(i64),
    Refuse(String),
    Now,
    Choose(Vec<i64>),
}

#[derive(Clone, Debug)]
enum Answer {
    Number(i64),
    Unit,
}

/// a program stopped somewhere: the REST OF THE PROGRAM is a value of the type
trait Machine: Sized {
    type Out;
    /// continue from where it stopped, with the answer to the question it stopped at (at the start: `Unit`)
    fn step(self, answer: Answer) -> Step<Self>;
}

enum Step<M: Machine> {
    Ask(Op, M),
    Done(M::Out),
}

/// running: only a machine with no question left has a value
fn run<M: Machine>(m: M) -> M::Out {
    match m.step(Answer::Unit) {
        Step::Done(a) => a,
        Step::Ask(op, _) => panic!("nobody answers {op:?}"),
    }
}

// ---- sequencing, as machines

/// `done a`
#[derive(Clone)]
struct Done<A>(A);
impl<A> Machine for Done<A> {
    type Out = A;
    fn step(self, _: Answer) -> Step<Self> {
        Step::Done(self.0)
    }
}

/// `a <- p ; f(a)`. No closure: `f` is a function pointer and captures nothing, so a later machine can use an
/// earlier variable only if `f` hands it over in the machine it returns
#[derive(Clone)]
enum Then<P: Machine, Q> {
    First(P, fn(P::Out) -> Q),
    Second(Q),
}
impl<P: Machine, Q: Machine> Machine for Then<P, Q> {
    type Out = Q::Out;
    fn step(self, answer: Answer) -> Step<Self> {
        let second = match self {
            Then::First(p, f) => match p.step(answer) {
                Step::Ask(op, rest) => return Step::Ask(op, Then::First(rest, f)),
                Step::Done(a) => f(a).step(Answer::Unit),
            },
            Then::Second(q) => q.step(answer),
        };
        match second {
            Step::Ask(op, rest) => Step::Ask(op, Then::Second(rest)),
            Step::Done(b) => Step::Done(b),
        }
    }
}
fn then<P: Machine, Q>(p: P, f: fn(P::Out) -> Q) -> Then<P, Q> {
    Then::First(p, f)
}

/// `a <- p ; b <- q ; done (a, b)`: the variable `Then` cannot keep is a field of the second place
#[derive(Clone)]
enum Both<P: Machine, Q> {
    First(P, Q),
    Second(P::Out, Q),
}
impl<P: Machine, Q: Machine> Machine for Both<P, Q> {
    type Out = (P::Out, Q::Out);
    fn step(self, answer: Answer) -> Step<Self> {
        let (a, second) = match self {
            Both::First(p, q) => match p.step(answer) {
                Step::Ask(op, rest) => return Step::Ask(op, Both::First(rest, q)),
                Step::Done(a) => (a, q.step(Answer::Unit)),
            },
            Both::Second(a, q) => (a, q.step(answer)),
        };
        match second {
            Step::Ask(op, rest) => Step::Ask(op, Both::Second(a, rest)),
            Step::Done(b) => Step::Done((a, b)),
        }
    }
}

// ---- handlers

/// what a handler says of a question
enum Reply<B> {
    Pass,           // not mine; I am shown the answer on its way back (`seen`)
    Resume(Answer), // the rest goes on with this
    Leave(B),       // the rest is dropped
}

trait Handler<A> {
    type Out;
    fn on(&mut self, op: &Op) -> Reply<Self::Out>;
    fn seen(&mut self, _op: &Op, _answer: &Answer) {}
    fn done(self, a: A) -> Self::Out;
}

/// `m under h`: a machine again. It stops where `m` asks a question that is not `h`'s, and remembers which
#[derive(Clone)]
struct Under<H, M> {
    h: H,
    m: M,
    asked: Option<Op>,
}
fn under<H, M>(h: H, m: M) -> Under<H, M> {
    Under { h, m, asked: None }
}
impl<M: Machine, H: Handler<M::Out>> Machine for Under<H, M> {
    type Out = H::Out;
    fn step(self, answer: Answer) -> Step<Self> {
        let Under { mut h, m, asked } = self;
        if let Some(op) = asked {
            h.seen(&op, &answer);
        }
        let mut step = m.step(answer);
        loop {
            match step {
                Step::Done(a) => return Step::Done(h.done(a)),
                Step::Ask(op, rest) => match h.on(&op) {
                    Reply::Resume(a) => step = rest.step(a),
                    Reply::Leave(out) => return Step::Done(out), // `rest` is dropped here, with all it held
                    Reply::Pass => return Step::Ask(op.clone(), Under { h, m: rest, asked: Some(op) }),
                },
            }
        }
    }
}

/// the state: the cell is the handler's field, and `done` gives its content back beside the value
#[derive(Clone)]
struct Cell(i64);
impl<A> Handler<A> for Cell {
    type Out = (A, i64);
    fn on(&mut self, op: &Op) -> Reply<Self::Out> {
        match op {
            Op::Get => Reply::Resume(Answer::Number(self.0)),
            Op::Put(n) => {
                self.0 = *n;
                Reply::Resume(Answer::Unit)
            }
            _ => Reply::Pass,
        }
    }
    fn done(self, a: A) -> Self::Out {
        (a, self.0)
    }
}

struct Refusals;
impl<A> Handler<A> for Refusals {
    type Out = Result<A, String>;
    fn on(&mut self, op: &Op) -> Reply<Self::Out> {
        if let Op::Refuse(why) = op { Reply::Leave(Err(why.clone())) } else { Reply::Pass }
    }
    fn done(self, a: A) -> Self::Out {
        Ok(a)
    }
}

struct FixedClock(i64);
impl<A> Handler<A> for FixedClock {
    type Out = A;
    fn on(&mut self, op: &Op) -> Reply<A> {
        if let Op::Now = op { Reply::Resume(Answer::Number(self.0)) } else { Reply::Pass }
    }
    fn done(self, a: A) -> A {
        a
    }
}

/// the observer: it passes every question, and writes down those of the state with their answers
#[derive(Default)]
struct Log(Vec<String>);
impl<A> Handler<A> for Log {
    type Out = (Vec<String>, A);
    fn on(&mut self, _op: &Op) -> Reply<Self::Out> {
        Reply::Pass
    }
    fn seen(&mut self, op: &Op, answer: &Answer) {
        match (op, answer) {
            (Op::Get, Answer::Number(x)) => self.0.push(format!("Get={x}")),
            (Op::Put(n), _) => self.0.push(format!("Put({n})")),
            _ => {}
        }
    }
    fn done(self, a: A) -> Self::Out {
        (self.0, a)
    }
}

/// every option taken. A `Reply` never holds the rest of the program, so this is no `Handler`: it is a machine
/// that owns the rest, clones it once per option, and runs the copies one after another
struct All<M: Machine> {
    now: M,
    later: Vec<(M, i64)>,
    got: Vec<M::Out>,
}
fn all<M: Machine>(m: M) -> All<M> {
    All { now: m, later: Vec::new(), got: Vec::new() }
}
impl<M: Machine + Clone> Machine for All<M> {
    type Out = Vec<M::Out>;
    fn step(self, answer: Answer) -> Step<Self> {
        let All { now, mut later, mut got } = self;
        let mut step = now.step(answer);
        loop {
            match step {
                Step::Ask(Op::Choose(options), rest) => {
                    later.extend(options.iter().rev().map(|&option| (rest.clone(), option)))
                }
                Step::Ask(op, rest) => return Step::Ask(op, All { now: rest, later, got }),
                Step::Done(a) => got.push(a),
            }
            let Some((copy, option)) = later.pop() else { return Step::Done(got) };
            step = copy.step(Answer::Number(option));
        }
    }
}

// ---- the programs of the twelve lines, each as the machine it becomes

/// `x <- Get ; done x`
#[derive(Clone)]
enum Get {
    Start,
    AtGet,
}
impl Machine for Get {
    type Out = i64;
    fn step(self, answer: Answer) -> Step<Self> {
        match (self, answer) {
            (Get::Start, _) => Step::Ask(Op::Get, Get::AtGet),
            (Get::AtGet, Answer::Number(x)) => Step::Done(x),
            (_, answer) => panic!("cannot go on with {answer:?}"),
        }
    }
}

/// `Put(n) ; done a`
#[derive(Clone)]
enum Set {
    Start(i64, i64),
    AtPut(i64),
}
impl Machine for Set {
    type Out = i64;
    fn step(self, _: Answer) -> Step<Self> {
        match self {
            Set::Start(n, a) => Step::Ask(Op::Put(n), Set::AtPut(a)),
            Set::AtPut(a) => Step::Done(a),
        }
    }
}
/// `put(n) = Put(n) ; done n`
fn put(n: i64) -> Set {
    Set::Start(n, n)
}

/// `x <- Get ; Put(x + 1) ; done x`
#[derive(Clone)]
enum Bump {
    Start,
    AtGet,
    AtPut(i64),
}
impl Machine for Bump {
    type Out = i64;
    fn step(self, answer: Answer) -> Step<Self> {
        match (self, answer) {
            (Bump::Start, _) => Step::Ask(Op::Get, Bump::AtGet),
            (Bump::AtGet, Answer::Number(x)) => Step::Ask(Op::Put(x + 1), Bump::AtPut(x)),
            (Bump::AtPut(x), _) => Step::Done(x),
            (_, answer) => panic!("cannot go on with {answer:?}"),
        }
    }
}

/// `Put(1) ; x <- Get ; Refuse("stop at {x}") ; Put(9) ; done 9`
#[derive(Clone)]
enum Stopped {
    Start,
    AtPut1,
    AtGet,
    AtRefuse,
    AtPut9,
}
impl Machine for Stopped {
    type Out = i64;
    fn step(self, answer: Answer) -> Step<Self> {
        match (self, answer) {
            (Stopped::Start, _) => Step::Ask(Op::Put(1), Stopped::AtPut1),
            (Stopped::AtPut1, _) => Step::Ask(Op::Get, Stopped::AtGet),
            (Stopped::AtGet, Answer::Number(x)) => Step::Ask(Op::Refuse(format!("stop at {x}")), Stopped::AtRefuse),
            (Stopped::AtRefuse, _) => Step::Ask(Op::Put(9), Stopped::AtPut9),
            (Stopped::AtPut9, _) => Step::Done(9),
            (_, answer) => panic!("cannot go on with {answer:?}"),
        }
    }
}

/// `Put(1) ; Refuse("no")`
#[derive(Clone)]
enum Lost {
    Start,
    AtPut,
    AtRefuse,
}
impl Machine for Lost {
    type Out = i64;
    fn step(self, _: Answer) -> Step<Self> {
        match self {
            Lost::Start => Step::Ask(Op::Put(1), Lost::AtPut),
            Lost::AtPut => Step::Ask(Op::Refuse("no".into()), Lost::AtRefuse),
            Lost::AtRefuse => panic!("a refusal is not answered"),
        }
    }
}

/// `t <- Now ; Put(t) ; x <- Get ; done x + t`
#[derive(Clone)]
enum Timed {
    Start,
    AtNow,
    AtPut(i64),
    AtGet(i64),
}
impl Machine for Timed {
    type Out = i64;
    fn step(self, answer: Answer) -> Step<Self> {
        match (self, answer) {
            (Timed::Start, _) => Step::Ask(Op::Now, Timed::AtNow),
            (Timed::AtNow, Answer::Number(t)) => Step::Ask(Op::Put(t), Timed::AtPut(t)),
            (Timed::AtPut(t), _) => Step::Ask(Op::Get, Timed::AtGet(t)),
            (Timed::AtGet(t), Answer::Number(x)) => Step::Done(x + t),
            (_, answer) => panic!("cannot go on with {answer:?}"),
        }
    }
}

/// `Put(1) ; x <- Choose([10, 20]) ; y <- Get ; Put(y + x) ; z <- Get ; done z`
#[derive(Clone)]
enum Forked {
    Start,
    AtPut1,
    AtChoose,
    AtGet(i64),
    AtPut,
    AtLast,
}
impl Machine for Forked {
    type Out = i64;
    fn step(self, answer: Answer) -> Step<Self> {
        match (self, answer) {
            (Forked::Start, _) => Step::Ask(Op::Put(1), Forked::AtPut1),
            (Forked::AtPut1, _) => Step::Ask(Op::Choose(vec![10, 20]), Forked::AtChoose),
            (Forked::AtChoose, Answer::Number(x)) => Step::Ask(Op::Get, Forked::AtGet(x)),
            (Forked::AtGet(x), Answer::Number(y)) => Step::Ask(Op::Put(y + x), Forked::AtPut),
            (Forked::AtPut, _) => Step::Ask(Op::Get, Forked::AtLast),
            (Forked::AtLast, Answer::Number(z)) => Step::Done(z),
            (_, answer) => panic!("cannot go on with {answer:?}"),
        }
    }
}

fn held<A: Display>((a, s): (A, i64)) -> String {
    format!("{a}, cell {s}")
}
fn outcome<A: Display>(r: Result<A, String>) -> String {
    match r {
        Ok(a) => format!("paid({a})"),
        Err(why) => format!("refused({why})"),
    }
}

/// what a program over the state is seen to do: its questions with their answers, its value, the cell after
fn seen<M: Machine<Out = i64>>(m: M) -> String {
    let ((lines, a), s) = run(under(Cell(0), under(Log::default(), m)));
    format!("{} => {a}, cell {s}", lines.join(" "))
}

fn main() {
    // 1-3. sequencing: `Done` is a unit of `Then` on each side, and the grouping of two `Then`s does not matter
    println!("1 done then f: {} = {}", seen(then(Done(5), put)), seen(put(5)));
    println!("2 p then done: {} = {}", seen(then(Bump::Start, Done)), seen(Bump::Start));
    fn f(x: i64) -> Set {
        put(x + 1)
    }
    fn g(y: i64) -> Set {
        Set::Start(y * 2, y)
    }
    let (left, right) = (then(then(Get::Start, f), g), then(Get::Start, |x| then(f(x), g)));
    println!("3 grouping: {} = {}", seen(left), seen(right));

    // 4-5. a handler is a fold: what it makes of `Done`, and of one machine after another
    println!("4 handling done: {} | {}", held(run(under(Cell(3), Done(7)))), outcome(run(under(Refusals, Done(7)))));
    let whole = run(under(Cell(0), then(Both::First(Bump::Start, Bump::Start), |(a, b)| Done(a + b))));
    let (a, s1) = run(under(Cell(0), Bump::Start));
    let (b, s2) = run(under(Cell(s1), Bump::Start));
    println!("5 handling a sequence: {} = {}", held(whole), held((a + b, s2)));

    // 6. a handler sees every operation of its effect, in order, and nothing of another's
    let ((lines, r), s) = run(under(Cell(0), under(Log::default(), under(Refusals, Stopped::Start))));
    println!("6 its own and no other's: {} => {}, cell {s}", lines.join(" "), outcome(r));

    // 7. a question nobody answers: every question is an `Op`, so the types do not object; the machine stops
    // at the question, and has no value
    let unanswered = match under(Cell(0), Timed::Start).step(Answer::Unit) {
        Step::Ask(op, _) => format!("{op:?}"),
        Step::Done(_) => "it ran".to_string(),
    };
    println!("7 nobody answers: {unanswered}");

    // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
    let inside = outcome(run(under(Refusals, under(Cell(0), Lost::Start))).map(held));
    let (refused, after) = run(under(Cell(0), under(Refusals, Lost::Start)));
    println!("8 state then failure: {inside} | failure then state: {}, cell {after}", outcome(refused));

    // 9. two handlers that only answer: either order
    let clock_inside = held(run(under(Cell(0), under(FixedClock(7), Timed::Start))));
    let clock_outside = held(run(under(FixedClock(7), under(Cell(0), Timed::Start))));
    println!("9 two that only answer: {clock_inside} = {clock_outside}");

    // 10-11. the rest resumed twice. Inside: the machine that `All` clones holds the cell, so each copy has
    // its own. Outside: the copies hold no cell, and ask the one machine around them
    let own: Vec<String> = run(all(under(Cell(0), Forked::Start))).into_iter().map(held).collect();
    println!("10 twice, state inside: {}", own.join(" | "));
    let (paths, last) = run(under(Cell(0), all(Forked::Start)));
    let paths: Vec<String> = paths.iter().map(i64::to_string).collect();
    println!("11 twice, state outside: {}, cell {last}", paths.join(" | "));

    // 12. what this handler owes its own operations: the four equations of a state
    let (after_put, _) = run(under(Cell(0), then(put(3), |_| Get::Start)));
    let (last_put, _) = run(under(Cell(0), then(put(3), |_| then(put(4), |_| Get::Start))));
    let ((x, y), _) = run(under(Cell(0), Both::First(Get::Start, Get::Start)));
    let (_, put_back) = run(under(Cell(5), then(Get::Start, put)));
    println!("12 the cell's own: {after_put} {last_put} {x}={y} cell {put_back}");
}
