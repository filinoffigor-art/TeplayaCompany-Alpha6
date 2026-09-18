package ru.teplayakompaniya.tk4;

/** Preview/validation only. Server remains the source of financial facts. Amounts are whole RUB. */
public final class Stage1Ledger {
    private Stage1Ledger() {}
    public static long personalFundsOutstanding(long balance) {
        return balance < 0 ? Math.negateExact(balance) : 0;
    }
    public static long balanceAfterReimbursement(long balance, long amount) {
        if (amount <= 0 || amount > personalFundsOutstanding(balance)) throw new IllegalArgumentException("Reimbursement exceeds outstanding personal funds");
        return Math.addExact(balance, amount);
    }
    public static long companyExpense(String operation, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative operation amount");
        if ("TRANSFER".equals(operation) || "PERSONAL_REIMBURSEMENT".equals(operation) || "PAYROLL_PAYMENT".equals(operation)) return 0;
        if ("PURCHASE".equals(operation) || "PAYROLL_ACCRUAL".equals(operation)) return amount;
        throw new IllegalArgumentException("Unknown operation");
    }
    public static long dayAccrual(int actualDays, long rate) {
        if (actualDays < 1 || rate <= 0) throw new IllegalArgumentException("Days and rate must be positive");
        return Math.multiplyExact(actualDays, rate);
    }
    public static long deferredFixedAmount(long unallocated, long earnedNow, boolean close) {
        if (unallocated < 0 || earnedNow < 0 || earnedNow > unallocated) throw new IllegalArgumentException("Invalid fixed allocation");
        if (close && earnedNow != unallocated) throw new IllegalArgumentException("Closing requires explicit allocation of the full obligation");
        return unallocated - earnedNow;
    }
}
