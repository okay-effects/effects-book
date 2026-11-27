// javac: method handler in class Handlers
package rows;
import static rows.Fx.*;
import static rows.Laws.*;
/** a clause that runs the rest itself: the rest still asks the clock around it, so its row is not empty, and a
 * handler that says it asks nothing cannot be put inside the clock */
class RunsTheRest {
  static <A> Handler<State, Void, A, A, Object> runsTheRest() {
    return handling(State.class, op -> switch (op) {
      case State.Get g -> on(g, (none, k) -> done(run(k.apply(1L, none))));
      case State.Put p -> answer(p, null);
    });
  }
  static <R extends State & Clock> Prog<Long, R> lookThenAsk() {
    return Laws.<R>get().then(x -> ask(new Clock.Now()));
  }
  Long x() { return run(Fx.<Long>top().handler(fixedClock(7)).handler(runsTheRest()).handle(lookThenAsk())); }
}
