// THE LAWS, AS LINES (chapter 4) in Zig, on STATE MACHINES written by hand as code/zig/bank.zig is (chapter
// 3e): the twelve observations of code/laws-expected.txt. A program is a tagged union with one case per place
// it can stop. Three things are machines too, each written once: `Then` (one machine run to its end, then
// another), `Under` (a machine with a handler: its questions answered, the others still asked) and `All` (the
// rest copied once per option). So a handler's state is a field of a machine, and copying the machine copies
// it. Nothing here allocates.
//
// What lines 1 to 3 compare: `Then`, which is this file's sequencing, against machines written by hand. They
// are laws of that one combinator, seen on these machines. They say nothing of the hand-written machines
// themselves: that `Bump` is `x <- Get ; Put(x + 1) ; done x` is the author's translation, and no line checks it.
// `zig run code/zig/laws.zig`

const std = @import("std");
const Writer = std.Io.Writer;

const Why = union(enum) { stop_at: i64, no };
const Op = union(enum) { get, put: i64, refuse: Why, now, choose: []const i64 };

/// a machine is any type with `Out` and `step`: continue from where it stopped, with the answer to the question
/// it stopped at (at the start, and for a question answered with nothing: 0)
fn Step(comptime M: type) type {
    return union(enum) { ask: struct { op: Op, rest: M }, done: M.Out };
}

/// running: only a machine with no question left has a value
fn run(m: anytype) @TypeOf(m).Out {
    return switch (m.step(0)) {
        .done => |a| a,
        .ask => @panic("nobody answers"),
    };
}

// ---- sequencing, as machines

/// `done a`
fn Done(comptime A: type) type {
    return struct {
        a: A,
        const Out = A;
        fn step(self: @This(), _: i64) Step(@This()) {
            return .{ .done = self.a };
        }
    };
}
fn done(a: i64) Done(i64) {
    return .{ .a = a };
}

/// `a <- p ; f(a)`. Zig has no closure: `f` is a pointer to a function and captures nothing, so a later machine
/// can use an earlier variable only if `f` hands it over in the machine it returns
fn Then(comptime P: type, comptime Q: type) type {
    return union(enum) {
        first: struct { p: P, f: *const fn (P.Out) Q },
        second: Q,
        const Out = Q.Out;
        const Self = @This();

        fn step(self: Self, given: i64) Step(Self) {
            const second = switch (self) {
                .first => |s| switch (s.p.step(given)) {
                    .ask => |a| return .{ .ask = .{ .op = a.op, .rest = .{ .first = .{ .p = a.rest, .f = s.f } } } },
                    .done => |a| s.f(a).step(0),
                },
                .second => |q| q.step(given),
            };
            return switch (second) {
                .ask => |a| .{ .ask = .{ .op = a.op, .rest = .{ .second = a.rest } } },
                .done => |b| .{ .done = b },
            };
        }
    };
}
fn then(p: anytype, comptime Q: type, to: *const fn (@TypeOf(p).Out) Q) Then(@TypeOf(p), Q) {
    return .{ .first = .{ .p = p, .f = to } };
}

/// `a <- p ; b <- q ; done (a, b)`: the variable `Then` cannot keep is a field of the second place
fn Both(comptime P: type, comptime Q: type) type {
    return union(enum) {
        first: struct { p: P, q: Q },
        second: struct { a: P.Out, q: Q },
        const Out = struct { P.Out, Q.Out };
        const Self = @This();

        fn step(self: Self, given: i64) Step(Self) {
            const a: P.Out, const second = switch (self) {
                .first => |s| switch (s.p.step(given)) {
                    .ask => |asked| return .{
                        .ask = .{ .op = asked.op, .rest = .{ .first = .{ .p = asked.rest, .q = s.q } } },
                    },
                    .done => |a| .{ a, s.q.step(0) },
                },
                .second => |s| .{ s.a, s.q.step(given) },
            };
            return switch (second) {
                .ask => |asked| .{ .ask = .{ .op = asked.op, .rest = .{ .second = .{ .a = a, .q = asked.rest } } } },
                .done => |b| .{ .done = .{ a, b } },
            };
        }
    };
}
fn both(p: anytype, q: anytype) Both(@TypeOf(p), @TypeOf(q)) {
    return .{ .first = .{ .p = p, .q = q } };
}

// ---- handlers

/// what a handler says of a question: not mine (and it is shown the answer on its way back, in `seen`); the
/// rest goes on with this; the rest is dropped
fn Reply(comptime B: type) type {
    return union(enum) { pass, resume_with: i64, leave: B };
}

/// `m under h`: a machine again. It stops where `m` asks a question that is not `h`'s, and remembers which.
/// A handler is any type with `Out`, `on`, `seen` and `done`
fn Under(comptime H: type, comptime M: type) type {
    return struct {
        h: H,
        m: M,
        asked: ?Op = null,
        const Out = H.Out(M.Out);
        const Self = @This();

        fn step(self: Self, given: i64) Step(Self) {
            var h = self.h;
            if (self.asked) |op| h.seen(op, given);
            var next = self.m.step(given);
            while (true) switch (next) {
                .done => |a| return .{ .done = h.done(M.Out, a) },
                .ask => |asked| switch (h.on(M.Out, asked.op)) {
                    .resume_with => |a| {
                        const after = asked.rest.step(a); // not straight into `next`, which `asked` is part of
                        next = after;
                    },
                    .leave => |out| return .{ .done = out }, // the rest is not stepped again
                    .pass => return .{
                        .ask = .{ .op = asked.op, .rest = .{ .h = h, .m = asked.rest, .asked = asked.op } },
                    },
                },
            };
        }
    };
}
fn under(h: anytype, m: anytype) Under(@TypeOf(h), @TypeOf(m)) {
    return .{ .h = h, .m = m };
}

/// the state: the cell is the handler's field, and `done` gives its content back beside the value
const Cell = struct {
    s: i64,
    const Out = Held;

    fn on(self: *Cell, comptime A: type, op: Op) Reply(Held(A)) {
        switch (op) {
            .get => return .{ .resume_with = self.s },
            .put => |n| {
                self.s = n;
                return .{ .resume_with = 0 };
            },
            else => return .pass,
        }
    }
    fn seen(_: *Cell, _: Op, _: i64) void {}
    fn done(self: *Cell, comptime A: type, a: A) Held(A) {
        return .{ .value = a, .cell = self.s };
    }
};

const Refusals = struct {
    const Out = Outcome;

    fn on(_: *Refusals, comptime A: type, op: Op) Reply(Outcome(A)) {
        return if (op == .refuse) .{ .leave = .{ .refused = op.refuse } } else .pass;
    }
    fn seen(_: *Refusals, _: Op, _: i64) void {}
    fn done(_: *Refusals, comptime A: type, a: A) Outcome(A) {
        return .{ .paid = a };
    }
};

const FixedClock = struct {
    at: i64,
    fn Out(comptime A: type) type {
        return A;
    }
    fn on(self: *FixedClock, comptime A: type, op: Op) Reply(A) {
        return if (op == .now) .{ .resume_with = self.at } else .pass;
    }
    fn seen(_: *FixedClock, _: Op, _: i64) void {}
    fn done(_: *FixedClock, comptime A: type, a: A) A {
        return a;
    }
};

/// the observer: it passes every question, and writes down those of the state with their answers
const Log = struct {
    lines: [8]Line = undefined,
    len: usize = 0,
    const Out = Logged;

    fn on(_: *Log, comptime A: type, _: Op) Reply(Logged(A)) {
        return .pass;
    }
    fn seen(self: *Log, op: Op, answer: i64) void {
        if (op != .get and op != .put) return;
        self.lines[self.len] = .{ .op = op, .answer = answer };
        self.len += 1;
    }
    fn done(self: *Log, comptime A: type, a: A) Logged(A) {
        return .{ .lines = self.lines, .len = self.len, .value = a };
    }
};

/// every option taken. A `Reply` never holds the rest of the program, so this is no handler: it is a machine
/// that owns the rest, copies it once per option, and runs the copies one after another
fn All(comptime M: type) type {
    return struct {
        now: M,
        later: [4]Copy = undefined,
        waiting: usize = 0,
        got: List(M.Out) = .{},
        const Copy = struct { rest: M, option: i64 };
        const Out = List(M.Out);
        const Self = @This();

        fn step(self: Self, given: i64) Step(Self) {
            var mine = self;
            var next = self.now.step(given);
            while (true) {
                switch (next) {
                    .ask => |asked| if (asked.op == .choose) {
                        var i = asked.op.choose.len;
                        while (i > 0) : (i -= 1) {
                            mine.later[mine.waiting] = .{ .rest = asked.rest, .option = asked.op.choose[i - 1] };
                            mine.waiting += 1; // assignment copies the machine
                        }
                    } else {
                        mine.now = asked.rest;
                        return .{ .ask = .{ .op = asked.op, .rest = mine } };
                    },
                    .done => |a| {
                        mine.got.items[mine.got.len] = a;
                        mine.got.len += 1;
                    },
                }
                if (mine.waiting == 0) return .{ .done = mine.got };
                mine.waiting -= 1;
                const copy = mine.later[mine.waiting];
                const after = copy.rest.step(copy.option);
                next = after;
            }
        }
    };
}
fn all(m: anytype) All(@TypeOf(m)) {
    return .{ .now = m };
}

// ---- the programs of the twelve lines, each as the machine it is

/// `x <- Get ; done x`
const Get = union(enum) {
    start,
    at_get,
    const Out = i64;

    fn step(self: Get, given: i64) Step(Get) {
        return switch (self) {
            .start => .{ .ask = .{ .op = .get, .rest = .at_get } },
            .at_get => .{ .done = given },
        };
    }
};
const get: Get = .start;

/// `Put(n) ; done a`
const Set = union(enum) {
    start: struct { n: i64, a: i64 },
    at_put: i64,
    const Out = i64;

    fn step(self: Set, _: i64) Step(Set) {
        return switch (self) {
            .start => |s| .{ .ask = .{ .op = .{ .put = s.n }, .rest = .{ .at_put = s.a } } },
            .at_put => |a| .{ .done = a },
        };
    }
};
/// `put(n) = Put(n) ; done n`
fn put(n: i64) Set {
    return .{ .start = .{ .n = n, .a = n } };
}

/// `x <- Get ; Put(x + 1) ; done x`
const Bump = union(enum) {
    start,
    at_get,
    at_put: i64,
    const Out = i64;

    fn step(self: Bump, given: i64) Step(Bump) {
        return switch (self) {
            .start => .{ .ask = .{ .op = .get, .rest = .at_get } },
            .at_get => .{ .ask = .{ .op = .{ .put = given + 1 }, .rest = .{ .at_put = given } } },
            .at_put => |x| .{ .done = x },
        };
    }
};
const bump: Bump = .start;

/// `Put(1) ; x <- Get ; Refuse("stop at {x}") ; Put(9) ; done 9`
const Stopped = union(enum) {
    start,
    at_put1,
    at_get,
    at_refuse,
    at_put9,
    const Out = i64;

    fn step(self: Stopped, given: i64) Step(Stopped) {
        return switch (self) {
            .start => .{ .ask = .{ .op = .{ .put = 1 }, .rest = .at_put1 } },
            .at_put1 => .{ .ask = .{ .op = .get, .rest = .at_get } },
            .at_get => .{ .ask = .{ .op = .{ .refuse = .{ .stop_at = given } }, .rest = .at_refuse } },
            .at_refuse => .{ .ask = .{ .op = .{ .put = 9 }, .rest = .at_put9 } },
            .at_put9 => .{ .done = 9 },
        };
    }
};
const stopped: Stopped = .start;

/// `Put(1) ; Refuse("no")`
const Lost = union(enum) {
    start,
    at_put,
    at_refuse,
    const Out = i64;

    fn step(self: Lost, _: i64) Step(Lost) {
        return switch (self) {
            .start => .{ .ask = .{ .op = .{ .put = 1 }, .rest = .at_put } },
            .at_put => .{ .ask = .{ .op = .{ .refuse = .no }, .rest = .at_refuse } },
            .at_refuse => unreachable, // a refusal is not answered
        };
    }
};
const lost: Lost = .start;

/// `t <- Now ; Put(t) ; x <- Get ; done x + t`
const Timed = union(enum) {
    start,
    at_now,
    at_put: i64,
    at_get: i64,
    const Out = i64;

    fn step(self: Timed, given: i64) Step(Timed) {
        return switch (self) {
            .start => .{ .ask = .{ .op = .now, .rest = .at_now } },
            .at_now => .{ .ask = .{ .op = .{ .put = given }, .rest = .{ .at_put = given } } },
            .at_put => |t| .{ .ask = .{ .op = .get, .rest = .{ .at_get = t } } },
            .at_get => |t| .{ .done = given + t },
        };
    }
};
const timed: Timed = .start;

/// `Put(1) ; x <- Choose([10, 20]) ; y <- Get ; Put(y + x) ; z <- Get ; done z`
const Forked = union(enum) {
    start,
    at_put1,
    at_choose,
    at_get: i64,
    at_put,
    at_last,
    const Out = i64;

    fn step(self: Forked, given: i64) Step(Forked) {
        return switch (self) {
            .start => .{ .ask = .{ .op = .{ .put = 1 }, .rest = .at_put1 } },
            .at_put1 => .{ .ask = .{ .op = .{ .choose = &.{ 10, 20 } }, .rest = .at_choose } },
            .at_choose => .{ .ask = .{ .op = .get, .rest = .{ .at_get = given } } },
            .at_get => |x| .{ .ask = .{ .op = .{ .put = given + x }, .rest = .at_put } },
            .at_put => .{ .ask = .{ .op = .get, .rest = .at_last } },
            .at_last => .{ .done = given },
        };
    }
};
const forked: Forked = .start;

// ---- what the handlers give back, and how each is written

fn write(out: *Writer, v: anytype) anyerror!void {
    if (@TypeOf(v) == i64) try out.print("{d}", .{v}) else try v.show(out);
}

fn Held(comptime A: type) type {
    return struct {
        value: A,
        cell: i64,
        fn show(self: @This(), out: *Writer) anyerror!void {
            try write(out, self.value);
            try out.print(", cell {d}", .{self.cell});
        }
    };
}

fn Outcome(comptime A: type) type {
    return union(enum) {
        paid: A,
        refused: Why,
        fn show(self: @This(), out: *Writer) anyerror!void {
            switch (self) {
                .paid => |a| {
                    try out.writeAll("paid(");
                    try write(out, a);
                    try out.writeAll(")");
                },
                .refused => |why| switch (why) {
                    .stop_at => |x| try out.print("refused(stop at {d})", .{x}),
                    .no => try out.writeAll("refused(no)"),
                },
            }
        }
    };
}

const Line = struct { op: Op, answer: i64 };

fn Logged(comptime A: type) type {
    return struct {
        lines: [8]Line,
        len: usize,
        value: A,
        fn show(self: @This(), out: *Writer) anyerror!void {
            for (self.lines[0..self.len]) |line| switch (line.op) {
                .get => try out.print("Get={d} ", .{line.answer}),
                .put => |n| try out.print("Put({d}) ", .{n}),
                else => {},
            };
            try out.writeAll("=> ");
            try write(out, self.value);
        }
    };
}

fn List(comptime A: type) type {
    return struct {
        items: [4]A = undefined,
        len: usize = 0,
        fn show(self: @This(), out: *Writer) anyerror!void {
            for (self.items[0..self.len], 0..) |item, i| {
                if (i > 0) try out.writeAll(" | ");
                try write(out, item);
            }
        }
    };
}

/// what a program over the state is seen to do: its questions with their answers, its value, the cell after
fn seen(out: *Writer, m: anytype) !void {
    try write(out, run(under(Cell{ .s = 0 }, under(Log{}, m))));
}

// the functions a `Then` goes on with; Zig has no function literal, so each has a name
fn f(x: i64) Set {
    return put(x + 1);
}
fn g(y: i64) Set {
    return .{ .start = .{ .n = y * 2, .a = y } };
}
fn fThenG(x: i64) Then(Set, Set) {
    return then(f(x), Set, g);
}
fn sum(pair: Both(Bump, Bump).Out) Done(i64) {
    return done(pair[0] + pair[1]);
}
fn thenGet(_: i64) Get {
    return get;
}
fn thenPut4ThenGet(_: i64) Then(Set, Get) {
    return then(put(4), Get, thenGet);
}

fn laws(out: *Writer) !void {
    const empty = Cell{ .s = 0 };

    // 1-3. sequencing: `Done` is a unit of `Then` on each side, and the grouping of two `Then`s does not matter
    try out.writeAll("1 done then f: ");
    try seen(out, then(done(5), Set, put));
    try out.writeAll(" = ");
    try seen(out, put(5));
    try out.writeAll("\n2 p then done: ");
    try seen(out, then(bump, Done(i64), done));
    try out.writeAll(" = ");
    try seen(out, bump);
    try out.writeAll("\n3 grouping: ");
    try seen(out, then(then(get, Set, f), Set, g));
    try out.writeAll(" = ");
    try seen(out, then(get, Then(Set, Set), fThenG));

    // 4-5. a handler is a fold: what it makes of `Done`, and of one machine after another
    try out.writeAll("\n4 handling done: ");
    try write(out, run(under(Cell{ .s = 3 }, done(7))));
    try out.writeAll(" | ");
    try write(out, run(under(Refusals{}, done(7))));
    try out.writeAll("\n5 handling a sequence: ");
    try write(out, run(under(empty, then(both(bump, bump), Done(i64), sum))));
    try out.writeAll(" = ");
    const first = run(under(empty, bump));
    const second = run(under(Cell{ .s = first.cell }, bump));
    try write(out, Held(i64){ .value = first.value + second.value, .cell = second.cell });

    // 6. a handler sees every operation of its effect, in order, and nothing of another's
    try out.writeAll("\n6 its own and no other's: ");
    try write(out, run(under(empty, under(Log{}, under(Refusals{}, stopped)))));

    // 7. a question nobody answers: every question is an `Op`, so the types do not object; the machine stops
    // at the question, and has no value
    try out.writeAll("\n7 nobody answers: ");
    try out.writeAll(switch (under(empty, timed).step(0)) {
        .ask => |asked| switch (asked.op) {
            .get => "Get",
            .put => "Put",
            .refuse => "Refuse",
            .now => "Now",
            .choose => "Choose",
        },
        .done => "it ran",
    });

    // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
    try out.writeAll("\n8 state then failure: ");
    try write(out, run(under(Refusals{}, under(empty, lost))));
    try out.writeAll(" | failure then state: ");
    try write(out, run(under(empty, under(Refusals{}, lost))));

    // 9. two handlers that only answer: either order
    try out.writeAll("\n9 two that only answer: ");
    try write(out, run(under(empty, under(FixedClock{ .at = 7 }, timed))));
    try out.writeAll(" = ");
    try write(out, run(under(FixedClock{ .at = 7 }, under(empty, timed))));

    // 10-11. the rest resumed twice. Inside: the machine that `All` copies holds the cell, so each copy has
    // its own. Outside: the copies hold no cell, and ask the one machine around them
    try out.writeAll("\n10 twice, state inside: ");
    try write(out, run(all(under(empty, forked))));
    try out.writeAll("\n11 twice, state outside: ");
    try write(out, run(under(empty, all(forked))));

    // 12. what this handler owes its own operations: the four equations of a state
    const after_put = run(under(empty, then(put(3), Get, thenGet))).value;
    const last_put = run(under(empty, then(put(3), Then(Set, Get), thenPut4ThenGet))).value;
    const x, const y = run(under(empty, both(get, get))).value;
    const put_back = run(under(Cell{ .s = 5 }, then(get, Set, put))).cell;
    try out.print("\n12 the cell's own: {d} {d} {d}={d} cell {d}\n", .{ after_put, last_put, x, y, put_back });
}

pub fn main(init: std.process.Init) !void {
    var buffer: [4096]u8 = undefined;
    var stdout = std.Io.File.stdout().writer(init.io, &buffer);
    try laws(&stdout.interface);
    try stdout.interface.flush();
}
