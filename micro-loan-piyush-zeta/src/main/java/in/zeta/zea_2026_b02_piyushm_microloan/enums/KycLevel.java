package in.zeta.zea_2026_b02_piyushm_microloan.enums;

public enum KycLevel {
    NO_KYC(0), MIN_KYC(1), FULL_KYC(2);

    private final int rank;

    KycLevel(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public boolean meetsRequirement(KycLevel required) {
        return this.rank >= required.rank;
    }
}
