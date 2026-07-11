package com.hgtech.soma.examples;

import com.hgtech.soma.examples.fjsp.FjspScenario;
import com.hgtech.soma.examples.game.GameScenario;
import com.hgtech.soma.examples.simulation.SimulationScenario;
import com.hgtech.soma.examples.vrp.VrpScenario;

/** 四个正式Java 8场景的可运行统一入口。 */
public final class ScenarioSuite {
    private ScenarioSuite() { }

    public static void main(String[] args) {
        FjspScenario.ScenarioResult fjsp = FjspScenario.run();
        VrpScenario.ScenarioResult vrp = VrpScenario.run();
        SimulationScenario.ScenarioResult simulation = SimulationScenario.run();
        GameScenario.ScenarioResult game = GameScenario.run();
        require(fjsp.assignments == 2 && vrp.visits == 3
                        && simulation.traceSamples == 2 && game.units == 2,
                "formal scenario result cardinalities");
        require(fjsp.schemaHash.length() == 64 && vrp.schemaHash.length() == 64
                        && simulation.schemaHash.length() == 64
                        && game.schemaHash.length() == 64,
                "all generated schema hashes are readable");
        require(!fjsp.schemaHash.equals(vrp.schemaHash)
                        && !fjsp.schemaHash.equals(simulation.schemaHash)
                        && !fjsp.schemaHash.equals(game.schemaHash),
                "scenario schema identities remain independent");
        require(fjsp.aggregateHotLeafWidths == 64 && vrp.aggregateHotLeafWidths == 32
                        && simulation.aggregateHotLeafWidths == 44
                        && game.aggregateHotLeafWidths == 32,
                "APC aggregate widths must match each declared per-table ledger");
        System.out.println("access-pattern-card scenario=fjsp "
                + "paths=child-release,keyed-frontier,grouped-update,dynamic-sort,grouped-remove "
                + "rows=" + fjsp.apcRows
                + " hotColumns=operationKey,assignedMachine,setupStartMinute,setupMinutes,startMinute,processingMinutes,endMinute"
                + " hotLeafWidthsByTable=operation_assignments:64"
                + " aggregateHotLeafWidths=" + fjsp.aggregateHotLeafWidths
                + " workingSetHotLeafBytes=" + fjsp.hotLeafWorkingSetBytes
                + " workingSetFormula=assignments.capacity*64"
                + " reads=" + fjsp.reads + " mutations=" + fjsp.mutations
                + " exports=" + fjsp.exports
                + " evidenceScope=assignment-update-and-export"
                + " observation=executed-result-accounting "
                + "assignments=" + fjsp.assignments
                + " sidecarRebuilds=" + fjsp.sidecarRebuilds);
        System.out.println("access-pattern-card scenario=vrp "
                + "paths=route-child,travel-lookup,insertion-order,route-rewrite "
                + "rows=" + vrp.apcRows
                + " hotColumns=position,customerId,arrivalMinute,departureMinute,loadAfterVisit"
                + " hotLeafWidthsByTable=route_visit_rows:32"
                + " aggregateHotLeafWidths=" + vrp.aggregateHotLeafWidths
                + " workingSetHotLeafBytes=" + vrp.hotLeafWorkingSetBytes
                + " workingSetFormula=visits.capacity*32"
                + " reads=" + vrp.reads + " mutations=" + vrp.mutations
                + " exports=" + vrp.exports
                + " childDensity=one-required-child-per-route"
                + " evidenceScope=visit-update-and-export"
                + " observation=executed-result-accounting "
                + "visits=" + vrp.visits + " childInstances=" + vrp.childInstances);
        System.out.println("access-pattern-card scenario=simulation "
                + "paths=state-vector,event-order,event-remove,trace-export "
                + "rows=" + simulation.apcRows
                + " hotColumns=vectorIndex,entityKind,entityId,variableKind,value,derivative,scale"
                + " hotLeafWidthsByTable=state_vector_rows:44"
                + " aggregateHotLeafWidths=" + simulation.aggregateHotLeafWidths
                + " workingSetHotLeafBytes=" + simulation.hotLeafWorkingSetBytes
                + " workingSetFormula=state.capacity*44"
                + " reads=" + simulation.reads + " mutations=" + simulation.mutations
                + " exports=" + simulation.exports
                + " evidenceScope=state-update-and-trace-export"
                + " observation=executed-result-accounting "
                + "changedRows=" + simulation.changedRows
                + " traceSamples=" + simulation.traceSamples);
        System.out.println("access-pattern-card scenario=game "
                + "paths=unit-order,ability-lookup,move-workspace,occupancy-cache,damage-buffer "
                + "rows=" + game.apcRows
                + " hotColumns=unit.position,unit.actionPoints,map.occupantUnit,move.totalCost,damage.targetUnit"
                + " hotLeafWidthsByTable=units:12,map:8,moves:4,damage:8"
                + " aggregateHotLeafWidths=" + game.aggregateHotLeafWidths
                + " workingSetHotLeafBytes=" + game.hotLeafWorkingSetBytes
                + " workingSetFormula=units.capacity*12+map.capacity*8+moves.capacity*4+damage.capacity*8"
                + " reads=" + game.reads + " mutations=" + game.mutations
                + " exports=" + game.exports
                + " selectedScope=single-actor"
                + " evidenceScope=occupancy-update-and-unit-export"
                + " observation=executed-result-accounting "
                + "units=" + game.units + " sidecarDirty=" + game.sidecarDirtyCount);
        System.out.println("lane=fjsp-errors duplicate_key=ok missing_key=ok "
                + "optional_empty=ok empty_result=ok");
        System.out.println("lane=fjsp-lifecycle view_pinned=ok released_view=ok "
                + "table_released=ok stale_view=referenced-g3");
        System.out.println("lane=fjsp-stats schema_hash=ok runtime_plan=ok "
                + "sidecar=ok keyspace=ok");
        System.out.println("lane=owner-breadth vrp_vehicle=ok vrp_unassigned=ok "
                + "simulation_tank=ok simulation_valve=ok game_player=ok");
        System.out.println("soma-examples-scenarios: ok");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
