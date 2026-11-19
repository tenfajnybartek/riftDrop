package pl.tenfajnybartek.riftdrop.mining;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class DropRule {
    private final String id;
    private final ItemStack itemStack;
    private final boolean fortune;
    private final String name;
    private final double chance;
    private final IntRange height;
    private final IntRange amount;
    private final IntRange points;
    private final int exp;
    private final int neededLevel;

    /**
     * Tworzy nowy obiekt DropRule.
     * 
     * @param id Stabilny klucz z drops.yml
     * @param name Nazwa dropu (wyświetlana graczom)
     * @param fortune Czy enchant Fortune zwiększa szansę na ten drop
     * @param itemStack ItemStack który ma wypaść
     * @param chance Szansa na drop w procentach (0–100)
     * @param height Zakres wysokości Y gdzie drop jest dostępny
     * @param amount Zakres ilości itemów które mogą wypaść
     * @param points Zakres punktów doświadczenia za wykopanie
     * @param exp Ilość Minecraft XP za wykopanie
     * @param neededLevel Minimalny poziom gracza wymagany do odblokowaniа dropu (0 = brak wymagania)
     * @throws IllegalArgumentException jeśli name lub itemStack są null/puste
     */
    public DropRule(String id, String name,
                boolean fortune,
                ItemStack itemStack,
                double chance,
                IntRange height,
                IntRange amount,
                IntRange points,
                int exp,
                int neededLevel) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name cannot be null/empty");
        if (itemStack == null) throw new IllegalArgumentException("itemStack cannot be null");

        this.id = Objects.requireNonNull(id);
        this.name = name;
        this.fortune = fortune;
        this.itemStack = itemStack.clone();
        this.chance = normalizeChance(chance);
        this.height = height;
        this.amount = amount;
        this.neededLevel = Math.max(0, neededLevel);
        this.points = points;

        this.exp = exp;
    }

    private static double normalizeChance(double c) {
        if (!Double.isFinite(c) || c < 0.0) {
            throw new IllegalArgumentException("chance must be >= 0.0 (percentage value, e.g., 1.2 for 1.2%)");
        }
        if (c > 100.0) {
            throw new IllegalArgumentException("chance must be <= 100.0 (percentage value, max 100%)");
        }
        return c / 100.0;
    }

    public String getId() { return id; }

    public String getStorageKey() {
        return "id:" + id;
    }

    public String getName() {
        return this.name;
    }

    public boolean isFortune() {
        return this.fortune;
    }

    public ItemStack getItemStack() {
        return this.itemStack.clone();
    }

    public double getChance() {
        return this.chance;
    }

    public IntRange getHeight() {
        return this.height;
    }

    public IntRange getAmount() {
        return this.amount;
    }

    public IntRange getPoints() {
        return this.points;
    }

    public int getExp() {
        return this.exp;
    }

    public int getNeededLevel() {
        return this.neededLevel;
    }

    public boolean isUnlocked(int playerLevel) {
        return playerLevel >= this.neededLevel;
    }

    @Override
    public String toString() {
        return "DropRule{" +
                "name='" + name + '\'' +
                ", fortune=" + fortune +
                ", chance=" + chance +
                ", height=" + height +
                ", amount=" + amount +
                ", points=" + points +
                ", exp=" + exp +
                ", neededLevel=" + neededLevel +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DropRule)) return false;
        DropRule drop = (DropRule) o;
        return id.equals(drop.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
