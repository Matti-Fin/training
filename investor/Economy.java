package investor;

import javax.swing.*;

public class Economy {

    // --------- Core settlement (per circuitum / per START) ---------

    public static void onPassOrLandStart(GameState state, Player pl) {
        // 1) Loan interest + principal installment
        settleLoanAtStart(state, pl);

        // 2) Dividends
        payDividendsAtStart(state, pl);

        // 3) Tax
        settleTaxAtStart(state, pl);

        // 4) Welfare (if requested)
        settleWelfareAtStart(state, pl);

        // Reset per-circuit trackers after settlement
        pl.circCustomerIncome = 0;
        pl.circDividendsIncome = 0;
        pl.circInterestPaid = 0;
        pl.requestedWelfareThisCirc = false;
    }

    // --------- Trackers helpers (call these from your existing logic) ---------

    public static void recordCustomerIncome(Player owner, long amount) {
        if (owner == null) return;
        owner.circCustomerIncome += Math.max(0, amount);
    }

    public static void recordDividends(Player p, long amount) {
        if (p == null) return;
        p.circDividendsIncome += Math.max(0, amount);
    }

    public static void recordInterestPaid(Player p, long amount) {
        if (p == null) return;
        p.circInterestPaid += Math.max(0, amount);
    }
    
    // --- Economy tuning (needed by Economy.java) ---

    // loan term options in turns: short / medium / long
    public int[] loanTermsTurns = new int[] { 2, 6, 12 };

    // leverage threshold where risk starts rising faster
    public double riskLeverageThreshold = 0.35;

    // tax rate per circuitum (per full lap)
    public double taxRate = 0.20;

    // welfare eligibility thresholds
    public long welfareNetWorthThreshold = 900;
    public long welfareCashThreshold = 150;

    // welfare payout per circuitum
    public long welfarePerCirc = 140;


    // --------- Loan logic ---------

    public static void offerLoanWithTerm(GameState state, Player pl, long amount) {
        if (amount <= 0) return;

        int[] terms = state.cfg.loanTermsTurns; // e.g. {2, 6, 12}
        String[] labels = new String[] {
                "Short (" + terms[0] + " rounds)",
                "Mid (" + terms[1] + " rounds)",
                "Long (" + terms[2] + " rounds)"
        };

        int pick = JOptionPane.showOptionDialog(
                null,
                "Choose repayment term for loan $" + amount + "\n\n" +
                        "Short = fast payoff (1–2 rounds)\n" +
                        "Long = 10–15 rounds style\n\n" +
                        "Repayments happen at START.",
                "Loan term",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                labels,
                labels[1]
        );

        if (pick < 0) return;

        int term = terms[Math.max(0, Math.min(terms.length - 1, pick))];

        pl.cash += amount;
        pl.debt += amount;

        // set/refresh amortization schedule (simple: fixed principal per round)
        pl.loanPrincipalRemaining += amount;
        pl.loanTermRemainingTurns = Math.max(pl.loanTermRemainingTurns, term);

        // compute installment now (ceil)
        pl.loanPrincipalPerStart = (long) Math.ceil(pl.loanPrincipalRemaining / (double) pl.loanTermRemainingTurns);

        if (state.infoLabel != null) {
            state.infoLabel.setText("Loan taken: $" + amount + " | Term: " + term + " rounds");
        }
    }

    private static void settleLoanAtStart(GameState state, Player pl) {
        if (pl.debt <= 0) return;

        double rate = computeLoanRatePerCirc(state, pl); // e.g. 0.02 = 2%
        long interest = (long) Math.ceil(pl.debt * rate);

        // pay interest
        long payInterest = Math.min(pl.cash, interest);
        pl.cash -= payInterest;
        recordInterestPaid(pl, payInterest);

        long unpaidInterest = interest - payInterest;

        // pay principal installment
        long principalPay = 0;
        if (pl.loanPrincipalRemaining > 0 && pl.loanTermRemainingTurns > 0) {
            long installment = Math.max(1, Math.min(pl.loanPrincipalPerStart, pl.loanPrincipalRemaining));
            principalPay = Math.min(pl.cash, installment);
            pl.cash -= principalPay;
            pl.debt -= principalPay;
            pl.loanPrincipalRemaining -= principalPay;

            pl.loanTermRemainingTurns = Math.max(0, pl.loanTermRemainingTurns - 1);
            if (pl.loanTermRemainingTurns == 0 && pl.loanPrincipalRemaining > 0) {
                // term ended but principal still exists -> keep paying 1 round minimum schedule
                pl.loanTermRemainingTurns = 1;
            }

            if (pl.loanTermRemainingTurns > 0) {
                pl.loanPrincipalPerStart = (long) Math.ceil(pl.loanPrincipalRemaining / (double) pl.loanTermRemainingTurns);
            } else {
                pl.loanPrincipalPerStart = 0;
            }
        }

        // if couldn’t pay full interest -> mark delinquent (minimal penalties now)
        if (unpaidInterest > 0) {
            pl.missedInterestFlag = true;
            if (state.infoLabel != null) {
                state.infoLabel.setText("WARNING: Unpaid interest $" + unpaidInterest + " (loan distress)");
            }
        } else {
            pl.missedInterestFlag = false;
        }
    }

    private static double computeLoanRatePerCirc(GameState state, Player pl) {
        // LoanRate = FedRate + RiskSpread
        double fed = state.fedRate / 100.0;
        double spread = computeRiskSpread(state, pl);
        // cap to sane range
        return clamp(fed + spread, 0.005, 0.30);
    }

    private static double computeRiskSpread(GameState state, Player pl) {
        // Leverage = Debt / max(1, NetWorth)
        long nw = Math.max(1, pl.netWorth());
        double lev = pl.debt / (double) nw;

        // RiskSpread = 1.5% + 6% * max(0, lev - 0.35)
        double s = 0.015 + 0.06 * Math.max(0.0, lev - state.cfg.riskLeverageThreshold);

        // penalties from prior catches etc can add here later
        if (pl.extraRiskSpreadTurnsLeft > 0) s += pl.extraRiskSpreadAdd;

        return clamp(s, 0.0, 0.25);
    }

    // --------- Dividends (minimal stub; you can hook your securities later) ---------

    private static void payDividendsAtStart(GameState state, Player pl) {
        // If you later add sector holdings, compute real dividends here.
        // For now: no automatic dividends unless your code calls recordDividends(...)
        if (pl.circDividendsIncome > 0) {
            pl.cash += pl.circDividendsIncome;
        }
    }

    // --------- Tax ---------

    private static void settleTaxAtStart(GameState state, Player pl) {
        long taxableIncome = (pl.circCustomerIncome + pl.circDividendsIncome) - pl.circInterestPaid;
        if (taxableIncome <= 0) {
            pl.lastComputedTax = 0;
            return;
        }

        long tax = (long) Math.ceil(taxableIncome * state.cfg.taxRate);

        Object[] opts = new Object[] { "Pay tax ($" + tax + ")", "Evade (risk)" };
        int pick = JOptionPane.showOptionDialog(
                null,
                "START settlement:\n\nTaxable income: $" + taxableIncome + "\nTax due: $" + tax + "\n\nPay or evade?",
                "Tax",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                opts,
                opts[0]
        );

        pl.lastComputedTax = tax;

        if (pick == 0) {
            long pay = Math.min(pl.cash, tax);
            pl.cash -= pay;
            pl.taxEvasionOpen = false;
            pl.evadedTaxAmount = 0;
            pl.taxEvasionRoundsOpen = 0;

            if (state.infoLabel != null) state.infoLabel.setText("Tax paid: $" + pay);
        } else if (pick == 1) {
            pl.taxEvasionOpen = (tax > 0);
            pl.evadedTaxAmount = tax;
            pl.taxEvasionRoundsOpen = 0;

            if (state.infoLabel != null) state.infoLabel.setText("Tax evasion chosen (open case)");
        }
    }

    // --------- Welfare ---------

    private static void settleWelfareAtStart(GameState state, Player pl) {
        if (!pl.requestedWelfareThisCirc) return;

        boolean eligible = (pl.netWorth() < state.cfg.welfareNetWorthThreshold) && (pl.cash < state.cfg.welfareCashThreshold);
        long amount = state.cfg.welfarePerCirc;

        pl.cash += amount;

        if (!eligible) {
            pl.welfareFraudOpen = true;
            pl.welfareFraudAmount += amount;
        }

        if (state.infoLabel != null) {
            state.infoLabel.setText(eligible ? ("Welfare: +$" + amount) : ("Welfare claimed (FRAUD OPEN): +$" + amount));
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
