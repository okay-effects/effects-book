// The scenario of code/expected.txt in Zig, as a STATE MACHINE written by hand (chapter 3e). Zig has no
// closures and no garbage collector, so the rest of the program cannot be a function that captured its
// variables: it is a tagged union with one case per place the program can stop. Zig copies a union by value,
// so the rest can be resumed twice. Nothing here allocates.
// `zig run code/zig/bank.zig`

const std = @import("std");

const Why = union(enum) {
    short: struct { who: []const u8, have: i64, needs: i64 },
    over: struct { amount: i64, most: i64 },
};
const Transfer = struct { from: []const u8, to: []const u8, amount: i64 };
const Op = union(enum) { balance: []const u8, move: Transfer, now, refuse: Why };
const Receipt = struct { transfer: Transfer, at: i64 };
const Outcome = union(enum) { paid: Receipt, refused: Why };

// The program, as one would like to write it:
//     have = ask(balance(from)); if (have < amount) ask(refuse(...)); ask(move(...)); at = ask(now); receipt
// and as the machine it is: the REST OF THE PROGRAM is a value of this type.
const Pay = union(enum) {
    start: Transfer,
    at_balance: Transfer,
    at_refuse,
    at_move: Transfer,
    at_now: Transfer,

    fn step(self: Pay, given: i64) Step {
        return switch (self) {
            .start => |t| .{ .ask = .{ .op = .{ .balance = t.from }, .rest = .{ .at_balance = t } } },
            .at_balance => |t| if (given < t.amount)
                .{ .ask = .{
                    .op = .{ .refuse = .{ .short = .{ .who = t.from, .have = given, .needs = t.amount } } },
                    .rest = .at_refuse,
                } }
            else
                .{ .ask = .{ .op = .{ .move = t }, .rest = .{ .at_move = t } } },
            .at_move => |t| .{ .ask = .{ .op = .now, .rest = .{ .at_now = t } } },
            .at_now => |t| .{ .done = .{ .transfer = t, .at = given } },
            .at_refuse => unreachable, // a refusal is not answered
        };
    }
};
const Step = union(enum) { ask: struct { op: Op, rest: Pay }, done: Receipt };

const Accounts = struct { ann: i64, bob: i64 = 0 };
const Line = struct { op: Op, answer: i64 };
const Journal = struct { lines: [8]Line = undefined, len: usize = 0 };

// a handler is a case of a union, not a closure
const Handler = union(enum) {
    accounts: *Accounts,
    clock: i64,
    refusals,
    journal: *Journal,
    limit: i64,
    dry,
};
const Reply = union(enum) { pass, resume_with: i64, leave: Outcome, instead: Op };

fn on(h: Handler, op: Op) Reply {
    switch (h) {
        .accounts => |b| switch (op) {
            .balance => |who| return .{ .resume_with = if (std.mem.eql(u8, who, "ann")) b.ann else b.bob },
            .move => |t| {
                b.ann -= t.amount; // the scenario moves from ann to bob only
                b.bob += t.amount;
                return .{ .resume_with = 0 };
            },
            else => {},
        },
        .clock => |at| if (op == .now) return .{ .resume_with = at },
        .refusals => if (op == .refuse) return .{ .leave = .{ .refused = op.refuse } },
        .limit => |most| if (op == .move and op.move.amount > most)
            return .{ .instead = .{ .refuse = .{ .over = .{ .amount = op.move.amount, .most = most } } } },
        .dry => if (op == .move) return .{ .resume_with = 0 },
        .journal => {},
    }
    return .pass;
}

const Left = error{Left};

/// handlers are listed innermost first; `left` receives the outcome when one of them leaves
fn answer(handlers: []const Handler, op: Op, left: *Outcome) Left!i64 {
    if (handlers.len == 0) @panic("nobody answers");
    switch (on(handlers[0], op)) {
        .resume_with => |a| return a,
        .leave => |outcome| {
            left.* = outcome;
            return error.Left;
        },
        .instead => |other| return answer(handlers[1..], other, left),
        .pass => {
            const a = try answer(handlers[1..], op, left);
            if (handlers[0] == .journal and (op == .balance or op == .move)) {
                const j = handlers[0].journal;
                j.lines[j.len] = .{ .op = op, .answer = a };
                j.len += 1;
            }
            return a;
        },
    }
}

/// the whole interpreter: a loop
fn run(handlers: []const Handler, first: Step) Outcome {
    var step = first;
    var left: Outcome = undefined;
    while (true) switch (step) {
        .done => |receipt| return .{ .paid = receipt },
        .ask => |asked| {
            const a = answer(handlers, asked.op, &left) catch return left;
            step = asked.rest.step(a);
        },
    };
}

fn pay(amount: i64) Step {
    const p: Pay = .{ .start = .{ .from = "ann", .to = "bob", .amount = amount } };
    return p.step(0);
}

fn showOutcome(out: anytype, outcome: Outcome, label: bool) !void {
    switch (outcome) {
        .paid => |r| {
            if (label) try out.writeAll("paid: ");
            const t = r.transfer;
            try out.print("moved {d} from {s} to {s} at {d}", .{ t.amount, t.from, t.to, r.at });
        },
        .refused => |why| {
            try out.writeAll("refused: ");
            switch (why) {
                .short => |s| try out.print("{s} has {d}, needs {d}", .{ s.who, s.have, s.needs }),
                .over => |o| try out.print("a move of {d} is over the limit of {d}", .{ o.amount, o.most }),
            }
        },
    }
}

fn showLine(out: anytype, line: Line) !void {
    switch (line.op) {
        .balance => |who| try out.print("Balance({s}) -> {d}", .{ who, line.answer }),
        .move => |t| try out.print("Move({s},{s},{d}) -> ()", .{ t.from, t.to, t.amount }),
        else => {},
    }
}

fn scenario(out: anytype) !void {
    var rich = Accounts{ .ann = 100 };
    try out.writeAll("1 ");
    try showOutcome(out, run(&.{ .refusals, .{ .clock = 42 }, .{ .accounts = &rich } }, pay(30)), true);
    try out.print("\n1 balances: ann={d} bob={d}\n", .{ rich.ann, rich.bob });

    var poor = Accounts{ .ann = 10 };
    try out.writeAll("2 ");
    try showOutcome(out, run(&.{ .refusals, .{ .clock = 42 }, .{ .accounts = &poor } }, pay(30)), true);
    try out.print("\n2 balances: ann={d} bob={d}\n", .{ poor.ann, poor.bob });

    var journal = Journal{};
    var bank = Accounts{ .ann = 100 };
    _ = run(&.{ .{ .journal = &journal }, .refusals, .{ .clock = 42 }, .{ .accounts = &bank } }, pay(30));
    for (journal.lines[0..journal.len]) |line| {
        try out.writeAll("3 journal: ");
        try showLine(out, line);
        try out.writeAll("\n");
    }

    var limited = Accounts{ .ann = 100 };
    try out.writeAll("4 ");
    const over = run(&.{ .{ .limit = 50 }, .refusals, .{ .clock = 42 }, .{ .accounts = &limited } }, pay(80));
    try showOutcome(out, over, true);
    try out.print("\n4 balances: ann={d} bob={d}\n", .{ limited.ann, limited.bob });

    var seen = Journal{};
    var small = Accounts{ .ann = 10 };
    _ = run(&.{ .{ .journal = &seen }, .refusals, .{ .clock = 42 }, .{ .accounts = &small } }, pay(30));
    try out.print("5 left early: the journal has {d} line, ", .{seen.len});
    try showLine(out, seen.lines[0]);

    // 6. the rest is a value: stop at balance, and step a COPY of it for each supposed answer
    const rest = pay(30).ask.rest;
    try out.writeAll("\n6 twice: ");
    for ([_]i64{ 100, 10 }, 0..) |supposed, i| {
        const copy = rest; // assignment copies the union
        if (i > 0) try out.writeAll(" | ");
        try out.print("{d} -> ", .{supposed});
        try showOutcome(out, run(&.{ .refusals, .{ .clock = 42 }, .dry }, copy.step(supposed)), false);
    }
    try out.writeAll("\n");
}

pub fn main(init: std.process.Init) !void {
    var buffer: [4096]u8 = undefined;
    var stdout = std.Io.File.stdout().writer(init.io, &buffer);
    try scenario(&stdout.interface);
    try stdout.interface.flush();
}
