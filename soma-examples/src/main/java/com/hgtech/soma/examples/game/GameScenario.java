package com.hgtech.soma.examples.game;

import com.hgtech.soma.examples.game.generated.AbilityCostBatch;
import com.hgtech.soma.examples.game.generated.AbilityCostTable;
import com.hgtech.soma.examples.game.generated.GameUnitBatch;
import com.hgtech.soma.examples.game.generated.GameUnitTable;
import com.hgtech.soma.examples.game.generated.MapTileRowBatch;
import com.hgtech.soma.examples.game.generated.MapTileRowTable;
import com.hgtech.soma.examples.game.generated.MoveCandidateRowBatch;
import com.hgtech.soma.examples.game.generated.MoveCandidateRowTable;
import com.hgtech.soma.examples.game.generated.PendingDamageRowBatch;
import com.hgtech.soma.examples.game.generated.PendingDamageRowTable;
import com.hgtech.soma.examples.game.generated.PlayerBatch;
import com.hgtech.soma.examples.game.generated.PlayerTable;

/** Unit.position为事实源、occupancy为可重建cache的正式game-loop场景。 */
public final class GameScenario {
    private GameScenario() { }

    public static ScenarioResult run() {
        PlayerId player = new PlayerId(1L);
        PlayerId opponent = new PlayerId(2L);
        UnitId actor = new UnitId(10L);
        UnitId target = new UnitId(20L);
        UnitClassId soldier = new UnitClassId(100L);
        GridPosition origin = new GridPosition(0, 0);
        GridPosition destination = new GridPosition(1, 0);

        PlayerTable players = PlayerTable.create();
        GameUnitTable units = GameUnitTable.create();
        AbilityCostTable costs = AbilityCostTable.create();
        MapTileRowTable map = MapTileRowTable.create();
        MoveCandidateRowTable moves = MoveCandidateRowTable.create();
        PendingDamageRowTable damage = PendingDamageRowTable.create();
        try {
            players.addBatch(new PlayerBatch(2)
                    .addValues(player, 1, 0L).addValues(opponent, 2, 0L));
            units.addBatch(new GameUnitBatch(2)
                    .addValues(actor, player, soldier, origin, 10, 3, 1,
                            UnitState.READY, false, null)
                    .addValues(target, opponent, soldier, new GridPosition(2, 0),
                            8, 2, 2, UnitState.READY, false, null));
            costs.addBatch(new AbilityCostBatch(2)
                    .addValues(new UnitAbilityKey(soldier, AbilityId.MOVE), 1, 1, 0)
                    .addValues(new UnitAbilityKey(soldier, AbilityId.ATTACK), 2, 2, 3));
            map.addBatch(new MapTileRowBatch(3)
                    .addValues(origin, TerrainType.PLAIN, 1, false, true, actor)
                    .addValues(destination, TerrainType.FOREST, 2, false, false, null)
                    .addValues(new GridPosition(2, 0), TerrainType.PLAIN,
                            1, false, true, target));

            GameUnit next = units.byTurnOrder().firstOrThrow();
            require(next.unitId.equals(actor) && units.findByPlayer(player).count() == 1L,
                    "ordered and grouped unit access");
            int moveCost = costs.fetch(new UnitAbilityKey(soldier, AbilityId.MOVE))
                    .actionPointCost;
            moves.replaceAll(new MoveCandidateRowBatch(2)
                    .addValues(actor, destination, moveCost + 1, 2)
                    .addValues(actor, new GridPosition(0, 1), moveCost + 3, 1));
            MoveCandidateRow selected = moves.byTotalCost().firstOrThrow();
            require(selected.position.equals(destination),
                    "selected-unit workspace chooses the maintained minimum cost");

            units.mutate(actor).setPosition(selected.position)
                    .setActionPoints(selected.remainingActionPoints)
                    .setState(UnitState.MOVED).commit();
            // Unit.position先提交，occupancy cache随后同步；失败时由game loop重建cache。
            map.update(row -> {
                if (row.position().equals(origin)) row.clearOccupantUnit();
                if (row.position().equals(destination)) row.setOccupantUnit(actor);
            });
            require(units.fetch(actor).position.equals(destination),
                    "unit position remains authoritative");
            require(map.filter(row -> row.position().equals(destination)
                            && row.occupantUnitPresent()
                            && row.occupantUnit().equals(actor)).count() == 1L,
                    "occupancy cache follows the authoritative move");

            damage.addBatch(new PendingDamageRowBatch(1)
                    .addValues(1L, actor, target, costs.fetch(
                            new UnitAbilityKey(soldier, AbilityId.ATTACK)).baseDamage));
            PendingDamageRow pending = damage.byResolutionOrder().firstOrThrow();
            GameUnit before = units.fetch(pending.targetUnit);
            int remaining = before.hp - pending.damage;
            units.mutate(pending.targetUnit).setHp(remaining)
                    .setState(remaining <= 0 ? UnitState.DEAD : before.state).commit();
            players.mutate(player).setScore(3L).commit();
            damage.clear();
            require(units.fetch(target).hp == 5 && damage.size() == 0
                            && players.fetch(player).score == 3L,
                    "pending damage is a phase buffer, not history storage");

            return new ScenarioResult(units.materialize().size(),
                    units.runtimePlan().schemaHash(), units.statsSnapshot().sidecarDirtyCount());
        } finally {
            damage.release();
            moves.release();
            map.release();
            costs.release();
            units.release();
            players.release();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class ScenarioResult {
        public final int units;
        public final String schemaHash;
        public final long sidecarDirtyCount;
        ScenarioResult(int units, String schemaHash, long sidecarDirtyCount) {
            this.units = units;
            this.schemaHash = schemaHash;
            this.sidecarDirtyCount = sidecarDirtyCount;
        }
    }
}
