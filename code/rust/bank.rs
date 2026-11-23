// The scenario of code/expected.txt in Rust, as a STATE MACHINE (chapter 3e): the program is an enum with one
// variant per place it can stop, each holding the variables alive there. This is what a compiler writes for an
// `async fn`; here it is written by hand, so that it is `Clone` — and a rest that can be cloned can be resumed
// twice. No closure, no allocation per step, no garbage collector.
// `rustc -O code/rust/bank.rs -o /tmp/bank-rs && /tmp/bank-rs`

use std::collections::BTreeMap;
use std::fmt;

#[derive(Clone, Debug)]
enum Op {
    Balance(String),
    Move(String, String, i64),
    Now,
    Refuse(String),
}

#[derive(Clone, Debug)]
enum Answer {
    Number(i64),
    Unit,
}

// The program, as one would like to write it:
//
//     fn pay(from, to, amount) -> String {
//         let have = ask(Balance(from));
//         if have < amount { ask(Refuse(format!("{from} has {have}, needs {amount}"))) }
//         ask(Move(from, to, amount));
//         let at = ask(Now);
//         format!("moved {amount} from {from} to {to} at {at}")
//     }
//
// and as the machine it becomes: the REST OF THE PROGRAM is a value of this type.
#[derive(Clone, Debug)]
enum Pay {
    Start { from: String, to: String, amount: i64 },
    AtBalance { from: String, to: String, amount: i64 },
    AtRefuse,
    AtMove { from: String, to: String, amount: i64 },
    AtNow { from: String, to: String, amount: i64 },
}

enum Step {
    Ask(Op, Pay),
    Done(String),
}

impl Pay {
    /// continue from where it stopped, with the answer to the question it stopped at
    fn step(self, answer: Answer) -> Step {
        match (self, answer) {
            (Pay::Start { from, to, amount }, _) => {
                Step::Ask(Op::Balance(from.clone()), Pay::AtBalance { from, to, amount })
            }
            (Pay::AtBalance { from, to, amount }, Answer::Number(have)) if have < amount => {
                Step::Ask(Op::Refuse(format!("{from} has {have}, needs {amount}")), Pay::AtRefuse)
            }
            (Pay::AtBalance { from, to, amount }, _) => {
                Step::Ask(Op::Move(from.clone(), to.clone(), amount), Pay::AtMove { from, to, amount })
            }
            (Pay::AtMove { from, to, amount }, _) => Step::Ask(Op::Now, Pay::AtNow { from, to, amount }),
            (Pay::AtNow { from, to, amount }, Answer::Number(at)) => {
                Step::Done(format!("moved {amount} from {from} to {to} at {at}"))
            }
            (state, answer) => panic!("{state:?} cannot go on with {answer:?}"),
        }
    }
}

fn pay(from: &str, to: &str, amount: i64) -> Pay {
    Pay::Start { from: from.into(), to: to.into(), amount }
}

enum Outcome {
    Paid(String),
    Refused(String),
}

/// what a handler says of a question
enum Reply {
    Pass,            // not mine; I am shown the answer on its way back (`seen`)
    Resume(Answer),  // the rest goes on with this
    Leave(Outcome),  // the rest is dropped
    Instead(Op),     // ask this of whoever is outside, in its place
}

trait Handler {
    fn on(&mut self, op: &Op) -> Reply;
    fn seen(&mut self, _op: &Op, _answer: &Answer) {}
}

/// handlers are listed innermost first
fn answer(handlers: &mut [&mut dyn Handler], op: &Op) -> Result<Answer, Outcome> {
    let Some((first, outer)) = handlers.split_first_mut() else { panic!("nobody answers {op}") };
    match first.on(op) {
        Reply::Resume(a) => Ok(a),
        Reply::Leave(outcome) => Err(outcome),
        Reply::Instead(other) => answer(outer, &other),
        Reply::Pass => {
            let a = answer(outer, op)?;
            first.seen(op, &a);
            Ok(a)
        }
    }
}

/// the whole interpreter: a loop, constant stack
fn run(handlers: &mut [&mut dyn Handler], mut step: Step) -> Outcome {
    loop {
        match step {
            Step::Done(receipt) => return Outcome::Paid(receipt),
            Step::Ask(op, rest) => match answer(handlers, &op) {
                Ok(a) => step = rest.step(a),
                Err(outcome) => return outcome, // `rest` is dropped here: Rust frees what it held
            },
        }
    }
}

struct Accounts(BTreeMap<String, i64>);
impl Accounts {
    fn new(ann: i64) -> Self {
        Accounts(BTreeMap::from([("ann".to_string(), ann), ("bob".to_string(), 0)]))
    }
}
impl Handler for Accounts {
    fn on(&mut self, op: &Op) -> Reply {
        match op {
            Op::Balance(a) => Reply::Resume(Answer::Number(*self.0.get(a).unwrap_or(&0))),
            Op::Move(from, to, n) => {
                *self.0.entry(from.clone()).or_default() -= n;
                *self.0.entry(to.clone()).or_default() += n;
                Reply::Resume(Answer::Unit)
            }
            _ => Reply::Pass,
        }
    }
}

struct FixedClock(i64);
impl Handler for FixedClock {
    fn on(&mut self, op: &Op) -> Reply {
        if let Op::Now = op { Reply::Resume(Answer::Number(self.0)) } else { Reply::Pass }
    }
}

struct Refusals;
impl Handler for Refusals {
    fn on(&mut self, op: &Op) -> Reply {
        if let Op::Refuse(why) = op { Reply::Leave(Outcome::Refused(why.clone())) } else { Reply::Pass }
    }
}

#[derive(Default)]
struct Journal(Vec<String>);
impl Handler for Journal {
    fn on(&mut self, _op: &Op) -> Reply {
        Reply::Pass
    }
    fn seen(&mut self, op: &Op, answer: &Answer) {
        if let Op::Balance(_) | Op::Move(..) = op {
            self.0.push(format!("{op} -> {answer}"));
        }
    }
}

struct Limit(i64);
impl Handler for Limit {
    fn on(&mut self, op: &Op) -> Reply {
        match op {
            Op::Move(_, _, n) if *n > self.0 => {
                Reply::Instead(Op::Refuse(format!("a move of {n} is over the limit of {}", self.0)))
            }
            _ => Reply::Pass,
        }
    }
}

struct DryRun;
impl Handler for DryRun {
    fn on(&mut self, op: &Op) -> Reply {
        if let Op::Move(..) = op { Reply::Resume(Answer::Unit) } else { Reply::Pass }
    }
}

impl fmt::Display for Op {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Op::Balance(a) => write!(f, "Balance({a})"),
            Op::Move(from, to, n) => write!(f, "Move({from},{to},{n})"),
            Op::Now => write!(f, "Now"),
            Op::Refuse(why) => write!(f, "Refuse({why})"),
        }
    }
}
impl fmt::Display for Answer {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Answer::Number(n) => write!(f, "{n}"),
            Answer::Unit => write!(f, "()"),
        }
    }
}
impl fmt::Display for Outcome {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            Outcome::Paid(receipt) => write!(f, "paid: {receipt}"),
            Outcome::Refused(why) => write!(f, "refused: {why}"),
        }
    }
}
impl fmt::Display for Accounts {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "ann={} bob={}", self.0["ann"], self.0["bob"])
    }
}

fn main() {
    let start = |p: Pay| p.step(Answer::Unit);

    let mut rich = Accounts::new(100);
    println!("1 {}", run(&mut [&mut Refusals, &mut FixedClock(42), &mut rich], start(pay("ann", "bob", 30))));
    println!("1 balances: {rich}");

    let mut poor = Accounts::new(10);
    println!("2 {}", run(&mut [&mut Refusals, &mut FixedClock(42), &mut poor], start(pay("ann", "bob", 30))));
    println!("2 balances: {poor}");

    let mut journal = Journal::default();
    let mut bank = Accounts::new(100);
    run(&mut [&mut journal, &mut Refusals, &mut FixedClock(42), &mut bank], start(pay("ann", "bob", 30)));
    for line in &journal.0 {
        println!("3 journal: {line}");
    }

    let mut limited = Accounts::new(100);
    let over =
        run(&mut [&mut Limit(50), &mut Refusals, &mut FixedClock(42), &mut limited], start(pay("ann", "bob", 80)));
    println!("4 {over}");
    println!("4 balances: {limited}");

    let mut seen = Journal::default();
    let mut small = Accounts::new(10);
    run(&mut [&mut seen, &mut Refusals, &mut FixedClock(42), &mut small], start(pay("ann", "bob", 30)));
    println!("5 left early: the journal has {} line, {}", seen.0.len(), seen.0.join("; "));

    // 6. the rest is a value: stop at Balance, clone it, and continue each copy with a supposed answer
    let Step::Ask(Op::Balance(_), rest) = start(pay("ann", "bob", 30)) else { unreachable!() };
    let outcomes: Vec<String> = [100, 10]
        .iter()
        .map(|&supposed| {
            let copy = rest.clone().step(Answer::Number(supposed));
            let outcome = run(&mut [&mut Refusals, &mut FixedClock(42), &mut DryRun], copy);
            format!("{supposed} -> {}", outcome.to_string().replace("paid: ", ""))
        })
        .collect();
    println!("6 twice: {}", outcomes.join(" | "));
}
