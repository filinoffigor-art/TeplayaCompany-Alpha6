package ru.teplayakompaniya.tk4;

import org.junit.Test;
import static org.junit.Assert.*;

public class Stage1LedgerTest {
    @Test public void ownFundsAreNotASecondExpense() {
        long balance=100000-135000;
        assertEquals(-35000,balance);
        assertEquals(35000,Stage1Ledger.personalFundsOutstanding(balance));
        assertEquals(-15000,Stage1Ledger.balanceAfterReimbursement(balance,20000));
        assertEquals(135000,Stage1Ledger.companyExpense("PURCHASE",135000)+Stage1Ledger.companyExpense("PERSONAL_REIMBURSEMENT",20000));
        assertEquals(0,Stage1Ledger.companyExpense("TRANSFER",50000));
    }
    @Test(expected=IllegalArgumentException.class) public void cannotReimburseMoreThanOwed(){Stage1Ledger.balanceAfterReimbursement(-35000,35001);}
    @Test public void dailyWorkerAccrualIsNotRepeatedOnPayment(){
        long accrued=Stage1Ledger.dayAccrual(3,5000);
        assertEquals(15000,accrued);
        assertEquals(15000,Stage1Ledger.companyExpense("PAYROLL_ACCRUAL",accrued)+Stage1Ledger.companyExpense("PAYROLL_PAYMENT",15000));
    }
    @Test public void transferSplitsOneFixedObligation(){
        long remainder=Stage1Ledger.deferredFixedAmount(60000,20000,false);
        assertEquals(40000,remainder);
        assertEquals(0,Stage1Ledger.deferredFixedAmount(remainder,40000,true));
    }
    @Test(expected=IllegalArgumentException.class) public void cannotAccrueFixedPayTwice(){Stage1Ledger.deferredFixedAmount(40000,60000,false);}
    @Test(expected=ArithmeticException.class) public void rejectsOverflow(){Stage1Ledger.dayAccrual(3,Long.MAX_VALUE);}
}
