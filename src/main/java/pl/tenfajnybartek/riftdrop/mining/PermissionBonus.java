package pl.tenfajnybartek.riftdrop.mining;


public class PermissionBonus {
    private final String name;
    private final String perm;
    private final Double chance;

    public PermissionBonus(String name, String perm, Double chance) {
        this.name = name;
        this.perm = perm;
        this.chance = chance;
    }

    public PermissionBonus(String perm, Double chance) {
        this.name = perm;
        this.perm = perm;
        this.chance = chance;
    }

    public String getName() {
        return this.name;
    }

    public String getPerm() {
        return this.perm;
    }

    public Double getChance() {
        return this.chance;
    }
}
