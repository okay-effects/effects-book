// The laws of code/laws-expected.txt in Java, in DIRECT STYLE ON VIRTUAL THREADS: the mechanism of Loom.java
// (chapter 3d), and twelve observations on it (chapter 4). A program is plain code, so `done` is `return` and
// "then" is the next statement. A handler answers by returning, keeps what it remembers in a field, and says
// in `done` what it makes of the result. The rest is a parked thread: lines 10 and 11 cannot be run.
// `java code/java/LoomLaws.java`

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

public class LoomLaws {
  sealed interface Op<X> {}
  record Get() implements Op<Long> {}
  record Put(long n) implements Op<Void> {
    public String toString() { return "Put(" + n + ")"; }
  }
  record Refuse(String why) implements Op<Void> {}
  record Now() implements Op<Long> {
    public String toString() { return "Now"; }
  }

  // ---- the mechanism: two hand-over points between the body's thread and the thread that handles

  record Asked(Op<?> op) {}
  record Finished(Object value) {}
  record Answer(Object value) {}
  /** thrown at the question a body is parked in when its rest is dropped: its `finally` blocks run */
  static final class Abandoned extends Error {
    Abandoned() { super(null, null, false, false); }
  }
  record Channel(SynchronousQueue<Object> toHandler, SynchronousQueue<Object> toBody) {}
  static final ScopedValue<Channel> CHANNEL = ScopedValue.newInstance();

  @SuppressWarnings("unchecked")
  static <X> X ask(Op<X> op) {
    var channel = CHANNEL.get();
    try {
      channel.toHandler().put(new Asked(op));
      if (channel.toBody().take() instanceof Answer a) return (X) a.value();
      throw new Abandoned();
    } catch (InterruptedException e) {
      throw new Abandoned();
    }
  }

  /** a handler answers by RETURNING; it may ask whoever is outside; it leaves by throwing Leave, with what the
   * program under it has come to. `done` is what it makes of a result that reaches it */
  static final Object PASS = new Object();
  interface Outer { Object ask(Op<?> op); }
  interface Handler {
    Object on(Op<?> op, Outer outer);
    default Object done(Object a) { return a; }
  }
  static final class Leave extends RuntimeException {
    final Handler by;
    final Object result;
    Leave(Handler by, Object result) { super(null, null, false, false); this.by = by; this.result = result; }
  }

  /** handlers are listed innermost first */
  static Object answer(List<Handler> handlers, int from, Op<?> op) {
    for (int i = from; i < handlers.size(); i++) {
      int next = i + 1;
      Object x = handlers.get(i).on(op, o -> answer(handlers, next, o));
      if (x != PASS) return x;
    }
    throw new IllegalStateException("nobody answers " + op);
  }

  /** a result on its way out: what each handler from `from` outward makes of it */
  static Object ended(List<Handler> handlers, int from, Object a) {
    for (Handler h : handlers.subList(from, handlers.size())) a = h.done(a);
    return a;
  }

  /** `body under handlers[0] under handlers[1] ...`. The value passes every `done`; the result of a handler
   * that left passes only those outside it. What comes out is typed by whoever listed the handlers: one cast,
   * as in `ask` */
  @SuppressWarnings("unchecked")
  static <B> B run(List<Handler> handlers, Supplier<?> body) {
    var channel = new Channel(new SynchronousQueue<>(), new SynchronousQueue<>());
    var thread = Thread.ofVirtual().start(() -> ScopedValue.where(CHANNEL, channel).run(() -> {
      try {
        Object value = body.get();
        channel.toHandler().put(new Finished(value));
      } catch (Abandoned | InterruptedException left) {
        // the rest was dropped: the stack has been unwound, the thread ends
      }
    }));
    try {
      while (true) {
        switch (channel.toHandler().take()) {
          case Finished f -> { return (B) ended(handlers, 0, f.value()); }
          case Asked a -> {
            Object x;
            try {
              x = answer(handlers, 0, a.op());
            } catch (RuntimeException failed) {
              channel.toBody().put(PASS);        // not an Answer: the body unwinds
              thread.join();
              if (!(failed instanceof Leave leave)) throw failed;
              return (B) ended(handlers, handlers.indexOf(leave.by) + 1, leave.result);
            }
            channel.toBody().put(new Answer(x));  // the one resumption
          }
          default -> throw new IllegalStateException();
        }
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  // ---- the handlers of the chapter

  /** a value with the cell's content after it */
  record Held<A>(A value, long cell) {
    public String toString() { return value + ", cell " + cell; }
  }
  static final class Cell implements Handler {
    long s;
    Cell(long s) { this.s = s; }
    public Object on(Op<?> op, Outer outer) {
      return switch (op) {
        case Get g -> s;
        case Put p -> { s = p.n(); yield null; }
        default -> PASS;
      };
    }
    public Object done(Object a) { return new Held<>(a, s); }
  }

  record Paid(Object value) {
    public String toString() { return "paid(" + value + ")"; }
  }
  record Refused(String why) {
    public String toString() { return "refused(" + why + ")"; }
  }
  /** zero resumptions: it leaves, and the rest is dropped */
  static final class Refusals implements Handler {
    public Object on(Op<?> op, Outer outer) {
      if (op instanceof Refuse r) throw new Leave(this, new Refused(r.why()));
      return PASS;
    }
    public Object done(Object a) { return new Paid(a); }
  }

  record FixedClock(long now) implements Handler {
    public Object on(Op<?> op, Outer outer) { return op instanceof Now ? (Object) now : PASS; }
  }

  /** a value with the questions of the state that were asked on the way to it */
  record Logged(List<String> lines, Object value) {
    public String toString() { return String.join(" ", lines) + " => " + value; }
  }
  /** the observer, an interceptor: every question of the state asked again outward, written down with its
   * answer */
  static final class Log implements Handler {
    final List<String> lines = new ArrayList<>();
    public Object on(Op<?> op, Outer outer) {
      if (!(op instanceof Get || op instanceof Put)) return PASS;
      Object x = outer.ask(op);
      lines.add(op instanceof Get ? "Get=" + x : op.toString());
      return x;
    }
    public Object done(Object a) { return new Logged(lines, a); }
  }

  // ---- the programs of the twelve lines: ordinary Java, top to bottom

  static long get() { return ask(new Get()); }
  static long put(long n) {
    ask(new Put(n));
    return n;
  }
  static long bump() {
    long x = get();
    ask(new Put(x + 1));
    return x;
  }
  static long stopped() {
    ask(new Put(1));
    long x = get();
    ask(new Refuse("stop at " + x));
    return put(9);
  }
  static long lost() {
    ask(new Put(1));
    ask(new Refuse("no"));
    return 0;
  }
  static long timed() {
    long t = ask(new Now());
    ask(new Put(t));
    return get() + t;
  }

  /** what a program over the state is seen to do: its questions with their answers, its value, the cell after */
  static String seen(Supplier<Long> p) { return run(List.of(new Log(), new Cell(0)), p).toString(); }
  /** `p under Cell(s)` */
  static <A> Held<A> cell(long s, Supplier<A> p) { return run(List.of(new Cell(s)), p); }

  public static void main(String[] args) {
    // 1-3. sequencing: `done` is a value in hand, "then" is the next statement, grouping is where a call ends
    System.out.println("1 done then f: " + seen(() -> { long x = 5; return put(x); }) + " = " + seen(() -> put(5)));
    System.out.println("2 p then done: " + seen(() -> { long x = bump(); return x; }) + " = " + seen(LoomLaws::bump));
    LongUnaryOperator f = x -> put(x + 1), g = y -> { ask(new Put(y * 2)); return y; };
    Supplier<Long> getThenF = () -> f.applyAsLong(get());
    LongUnaryOperator fThenG = x -> g.applyAsLong(f.applyAsLong(x));
    System.out.println("3 grouping: " + seen(() -> g.applyAsLong(getThenF.get()))
        + " = " + seen(() -> { long x = get(); return fThenG.applyAsLong(x); }));

    // 4-5. a handler is a fold: what it makes of a value, and of one program after another
    System.out.println("4 handling done: " + cell(3, () -> 7L) + " | " + run(List.of(new Refusals()), () -> 7L));
    Held<Long> whole = cell(0, () -> bump() + bump());
    Held<Long> first = cell(0, LoomLaws::bump), second = cell(first.cell(), LoomLaws::bump);
    System.out.println("5 handling a sequence: " + whole + " = "
        + new Held<>(first.value() + second.value(), second.cell()));

    // 6. a handler sees every operation of its effect, in order, and nothing of another's
    System.out.println("6 its own and no other's: "
        + run(List.of(new Refusals(), new Log(), new Cell(0)), LoomLaws::stopped));

    // 7. a question nobody answers: the program has no value, and the run says which question
    String unanswered;
    try {
      unanswered = "it ran: " + cell(0, LoomLaws::timed);
    } catch (IllegalStateException e) {
      unanswered = e.getMessage().replace("nobody answers ", "");
    }
    System.out.println("7 nobody answers: " + unanswered);

    // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
    System.out.println("8 state then failure: " + run(List.of(new Cell(0), new Refusals()), LoomLaws::lost)
        + " | failure then state: " + run(List.of(new Refusals(), new Cell(0)), LoomLaws::lost));

    // 9. two handlers that only answer: either order
    System.out.println("9 two that only answer: " + run(List.of(new FixedClock(7), new Cell(0)), LoomLaws::timed)
        + " = " + run(List.of(new Cell(0), new FixedClock(7)), LoomLaws::timed));

    // 10-11. the rest resumed twice: a handler here returns one answer, and there is one thread to give it to
    String once = "cannot: the rest is a parked thread; a thread's stack cannot be copied, so it is resumed once";
    System.out.println("10 twice, state inside: " + once);
    System.out.println("11 twice, state outside: " + once);

    // 12. what this handler owes its own operations: the four equations of a state
    Held<Long> afterPut = cell(0, () -> { ask(new Put(3)); return get(); });
    Held<Long> lastPut = cell(0, () -> { ask(new Put(3)); ask(new Put(4)); return get(); });
    Held<String> twice = cell(0, () -> { long x = get(), y = get(); return x + "=" + y; });
    Held<Long> putBack = cell(5, () -> put(get()));
    System.out.println("12 the cell's own: " + afterPut.value() + " " + lastPut.value() + " " + twice.value()
        + " cell " + putBack.cell());
  }
}
