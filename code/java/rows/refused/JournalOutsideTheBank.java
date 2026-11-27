// javac: method handler in class Handlers
package rows;
import static rows.Fx.*;
import static rows.Bank.*;
import java.util.ArrayList;
/** the journal asks Accounts of what is around it, and here nothing around it answers Accounts */
class JournalOutsideTheBank {
  Result x() {
    return run(Fx.<Result>top().handler(journal(new ArrayList<>())).handler(inMemory(balances(100)))
        .handler(fixedClock(42)).handler(refusals()).handle(pay("ann", "bob", 30)));
  }
}
