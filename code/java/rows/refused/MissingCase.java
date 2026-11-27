// javac: the switch expression does not cover all possible input values
package rows;
import static rows.Fx.*;
import rows.Bank.Accounts;
/** a handler of Accounts with no clause for Move */
class MissingCase {
  static <A, R> Handler<Accounts, Void, A, A, R> h() {
    return handling(Accounts.class, op -> switch (op) {
      case Accounts.Balance b -> answer(b, 0L);
    });
  }
}
