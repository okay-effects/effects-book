// javac: inference variable R has incompatible bounds
package rows;
import static rows.Fx.*;
import static rows.Bank.*;
/** the clock is left with no handler: the part asks more than the handlers around it answer */
class Unhandled {
  Result x() {
    return run(Fx.<Result>top().handler(inMemory(balances(100))).handler(refusals()).handle(pay("ann", "bob", 30)));
  }
}
