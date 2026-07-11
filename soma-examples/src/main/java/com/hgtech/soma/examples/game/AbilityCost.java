package com.hgtech.soma.examples.game;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
@SomaTable(name = "ability_costs", defaultCapacity = 128)
public final class AbilityCost {
    @SomaKey public UnitAbilityKey unitAbilityKey;
    @SomaField public int actionPointCost;
    @SomaField public int range;
    @SomaField public int baseDamage;
}
