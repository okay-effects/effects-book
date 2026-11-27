// javac: method answer in class Fx cannot be applied
package rows;
import static rows.Fx.*;
import rows.Bank.Accounts;
/** a Balance is answered with a Long, not a String */
class WrongAnswer {
  static <A, R> Handler<Accounts, Void, A, A, R> h() {
    return handling(Accounts.class, op -> switch (op) {
      case Accounts.Balance b -> answer(b, "a hundred");
      case Accounts.Move m -> answer(m, null);
    });
  }
}
