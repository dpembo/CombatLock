package uk.co.pembo.combatlock;

public enum CombatRole {
    AGGRESSOR,
    DEFENDER;

    /**
     * Config key used under on-combat-start / on-combat-end, and the
     * suffix used for the "entered-combat-<role>" message key.
     */
    public String configKey() {
        return this == AGGRESSOR ? "aggressor" : "defender";
    }
}
