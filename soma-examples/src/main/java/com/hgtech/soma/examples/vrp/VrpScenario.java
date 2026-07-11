package com.hgtech.soma.examples.vrp;

import com.hgtech.soma.examples.vrp.generated.CustomerBatch;
import com.hgtech.soma.examples.vrp.generated.CustomerTable;
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowBatch;
import com.hgtech.soma.examples.vrp.generated.InsertionCandidateRowTable;
import com.hgtech.soma.examples.vrp.generated.RouteBatch;
import com.hgtech.soma.examples.vrp.generated.RouteTable;
import com.hgtech.soma.examples.vrp.generated.RouteVisitRowBatch;
import com.hgtech.soma.examples.vrp.generated.RouteVisitRowTable;
import com.hgtech.soma.examples.vrp.generated.TravelCostBatch;
import com.hgtech.soma.examples.vrp.generated.TravelCostTable;
import com.hgtech.soma.examples.vrp.generated.UnassignedCustomerRowBatch;
import com.hgtech.soma.examples.vrp.generated.UnassignedCustomerRowTable;
import com.hgtech.soma.examples.vrp.generated.VehicleBatch;
import com.hgtech.soma.examples.vrp.generated.VehicleTable;
import com.hgtech.soma.runtime.SomaRuntimeException;

/** Parent-owned route sequence 与 insertion workspace 的正式 VRP 场景。 */
public final class VrpScenario {
    private VrpScenario() { }

    public static ScenarioResult run() {
        CustomerId customerOne = new CustomerId(1L);
        CustomerId customerTwo = new CustomerId(2L);
        RouteId routeId = new RouteId(10L);
        VehicleId vehicleId = new VehicleId(1000L);
        LocationId depot = new LocationId(100L);
        LocationId firstLocation = new LocationId(101L);
        LocationId secondLocation = new LocationId(102L);

        CustomerTable customers = CustomerTable.create();
        VehicleTable vehicles = VehicleTable.create();
        RouteTable routes = RouteTable.create();
        TravelCostTable travel = TravelCostTable.create();
        UnassignedCustomerRowTable unassigned = UnassignedCustomerRowTable.create();
        InsertionCandidateRowTable candidates = InsertionCandidateRowTable.create();
        try {
            customers.addBatch(new CustomerBatch(2)
                    .addValues(customerOne, 1L, firstLocation, 2, 0L, 20L, 2L,
                            CustomerState.UNASSIGNED, false, null, false, 0,
                            false, 0L)
                    .addValues(customerTwo, 2L, secondLocation, 1, 0L, 30L, 1L,
                            CustomerState.UNASSIGNED, false, null, false, 0,
                            false, 0L));
            vehicles.addBatch(new VehicleBatch(1)
                    .addValues(vehicleId, 10, depot, depot, 0L));
            travel.addBatch(new TravelCostBatch(4)
                    .addValues(new LocationPairKey(depot, firstLocation), 1000L, 60L)
                    .addValues(new LocationPairKey(firstLocation, depot), 1000L, 60L)
                    .addValues(new LocationPairKey(depot, secondLocation), 700L, 45L)
                    .addValues(new LocationPairKey(secondLocation, depot), 700L, 45L));
            routes.addBatch(new RouteBatch(1).addValues(routeId, vehicleId, 0,
                    0L, 0L, 0L, false, new RouteVisitRowBatch(0)));
            unassigned.addBatch(new UnassignedCustomerRowBatch(2)
                    .addValues(customerOne, 2, 20L, 1L)
                    .addValues(customerTwo, 1, 30L, 2L));

            require(routes.visits(routeId).size() == 0,
                    "required child starts as logical-present empty sequence");
            require(vehicles.byVehicleId().firstOrThrow().vehicleId.equals(vehicleId)
                            && routes.findByVehicle(vehicleId).count() == 1L
                            && routes.byRouteId().firstOrThrow().routeId.equals(routeId)
                            && unassigned.byDueThenInput().firstOrThrow().customerId
                            .equals(customerOne),
                    "vehicle and derived unassigned workspaces use Owner access paths");
            require(travel.fetch(new LocationPairKey(depot, secondLocation))
                            .distanceMeters == 700L,
                    "required travel lookup");

            candidates.replaceAll(new InsertionCandidateRowBatch(2)
                    .addValues(customerOne, routeId, 0, 2000L, 2L, 0L)
                    .addValues(customerTwo, routeId, 0, 1400L, 1L, 0L));
            InsertionCandidateRow chosen = candidates.byBestDelta().firstOrThrow();
            require(chosen.customerId.equals(customerTwo),
                    "maintained insertion order selects the cheapest feasible customer");

            RouteVisitRowBatch inserted = new RouteVisitRowBatch(1)
                    .addValues(0, chosen.customerId, 1L, 2L, 1);
            routes.replaceVisits(routeId, inserted);
            routes.mutate(routeId).setLoad(1).setTotalDistanceMeters(1400L)
                    .setTotalDurationSeconds(90L).setRouteVersion(1L).commit();
            customers.mutate(chosen.customerId).setState(CustomerState.ASSIGNED)
                    .setAssignedRoute(routeId).setAssignedPosition(0)
                    .setArrivalMinute(1L).commit();
            unassigned.filter(row -> row.customerId().equals(chosen.customerId)).remove();
            candidates.filter(row -> row.customerId().equals(chosen.customerId)).remove();

            RouteVisitRowTable liveVisits = routes.visits(routeId);
            require(liveVisits.byPosition().firstOrThrow().customerId.equals(customerTwo),
                    "hot route-local traversal uses the live child facade");
            Route exported = routes.fetch(routeId);
            require(exported.visits.size() == 1 && exported.routeVersion == 1L,
                    "keyed route fetch recursively materializes detached visit List");
            require(customers.findByState(CustomerState.ASSIGNED).count() == 1L
                            && customers.findByState(CustomerState.UNASSIGNED).count() == 1L,
                    "customer state index follows cross-table application commit");
            require(unassigned.size() == 1 && customers.fetch(customerTwo).arrivalMinute == 1L,
                    "derived unassigned workspace and optional arrival snapshot are synchronized");
            expectCode("missing_key", () -> travel.fetch(
                    new LocationPairKey(firstLocation, secondLocation)));

            return new ScenarioResult(exported.visits.size(), routes.runtimePlan().schemaHash(),
                    routes.statsSnapshot().childInstanceCount());
        } finally {
            candidates.release();
            unassigned.release();
            travel.release();
            routes.release();
            vehicles.release();
            customers.release();
        }
    }

    private static void expectCode(String code, Action action) {
        try {
            action.run();
            throw new AssertionError("expected " + code);
        } catch (SomaRuntimeException failure) {
            require(code.equals(failure.code()), "unexpected runtime code " + failure.code());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run(); }

    public static final class ScenarioResult {
        public final int visits;
        public final String schemaHash;
        public final long childInstances;
        ScenarioResult(int visits, String schemaHash, long childInstances) {
            this.visits = visits;
            this.schemaHash = schemaHash;
            this.childInstances = childInstances;
        }
    }
}
