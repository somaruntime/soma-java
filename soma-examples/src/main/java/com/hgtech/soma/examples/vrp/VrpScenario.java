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
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.SomaRuntimeException;

/** Parent-owned route sequence 与 insertion workspace 的正式 VRP 场景。 */
public final class VrpScenario {
    private VrpScenario() { }

    public static ScenarioResult run() {
        CustomerId customerOne = new CustomerId(1L);
        CustomerId customerTwo = new CustomerId(2L);
        CustomerId customerThree = new CustomerId(3L);
        RouteId routeId = new RouteId(10L);
        VehicleId vehicleId = new VehicleId(1000L);
        LocationId depot = new LocationId(100L);
        LocationId firstLocation = new LocationId(101L);
        LocationId secondLocation = new LocationId(102L);
        LocationId thirdLocation = new LocationId(103L);

        CustomerTable customers = CustomerTable.create();
        VehicleTable vehicles = VehicleTable.create();
        RouteTable routes = RouteTable.create();
        TravelCostTable travel = TravelCostTable.create();
        UnassignedCustomerRowTable unassigned = UnassignedCustomerRowTable.create();
        InsertionCandidateRowTable candidates = InsertionCandidateRowTable.create();
        try {
            customers.addBatch(new CustomerBatch(3)
                    .addValues(customerOne, 1L, firstLocation, 2, 0L, 20L, 2L,
                            CustomerState.ASSIGNED, true, routeId, true, 0,
                            true, 1L)
                    .addValues(customerTwo, 2L, secondLocation, 1, 0L, 30L, 1L,
                            CustomerState.ASSIGNED, true, routeId, true, 1,
                            true, 3L)
                    .addValues(customerThree, 3L, thirdLocation, 1, 0L, 40L, 1L,
                            CustomerState.UNASSIGNED, false, null, false, 0,
                            false, 0L));
            vehicles.addBatch(new VehicleBatch(1)
                    .addValues(vehicleId, 10, depot, depot, 0L));
            travel.addBatch(new TravelCostBatch(6)
                    .addValues(new LocationPairKey(depot, firstLocation), 1000L, 60L)
                    .addValues(new LocationPairKey(firstLocation, depot), 1000L, 60L)
                    .addValues(new LocationPairKey(depot, secondLocation), 700L, 45L)
                    .addValues(new LocationPairKey(secondLocation, depot), 700L, 45L)
                    .addValues(new LocationPairKey(depot, thirdLocation), 1400L, 60L)
                    .addValues(new LocationPairKey(thirdLocation, depot), 1400L, 60L));
            routes.addBatch(new RouteBatch(1).addValues(routeId, vehicleId, 3,
                    2400L, 180L, 0L, false, new RouteVisitRowBatch(2)
                            .addValues(0, customerOne, 1L, 2L, 2)
                            .addValues(1, customerTwo, 3L, 4L, 3)));
            unassigned.addBatch(new UnassignedCustomerRowBatch(1)
                    .addValues(customerThree, 1, 40L, 3L));

            require(routes.visits(routeId).size() == 2,
                    "route rewrite starts from a non-empty two-row sequence");
            require(vehicles.byVehicleId().firstOrThrow().vehicleId.equals(vehicleId)
                            && routes.findByVehicle(vehicleId).count() == 1L
                            && routes.byRouteId().firstOrThrow().routeId.equals(routeId)
                            && unassigned.byDueThenInput().firstOrThrow().customerId
                            .equals(customerThree),
                    "vehicle and derived unassigned workspaces use Owner access paths");
            require(travel.fetch(new LocationPairKey(depot, secondLocation))
                            .distanceMeters == 700L,
                    "required travel lookup");

            candidates.replaceAll(new InsertionCandidateRowBatch(2)
                    .addValues(customerThree, routeId, 1, 2000L, 4L, 0L)
                    .addValues(customerThree, routeId, 0, 1400L, 3L, 0L));
            int[] chosenRows = candidates.byBestDelta().limit(1).rowIndexes();
            require(chosenRows.length == 1, "insertion order requires one candidate");
            int chosenRow = chosenRows[0];
            LongColumnView candidateCustomer = candidates.customerIdValueColumn();
            IntColumnView insertAfter = candidates.insertAfterPositionColumn();
            CustomerId chosenCustomer;
            int chosenInsertAfter;
            try {
                chosenCustomer = new CustomerId(candidateCustomer.getLong(chosenRow));
                chosenInsertAfter = insertAfter.getInt(chosenRow);
            } finally {
                insertAfter.close();
                candidateCustomer.close();
            }
            require(chosenCustomer.equals(customerThree) && chosenInsertAfter == 0,
                    "maintained insertion order selects the cheapest feasible customer");

            RouteVisitRowBatch inserted = new RouteVisitRowBatch(3)
                    .addValues(0, customerOne, 1L, 2L, 2)
                    .addValues(1, chosenCustomer, 3L, 4L, 3)
                    .addValues(2, customerTwo, 5L, 6L, 4);
            routes.replaceVisits(routeId, inserted);
            routes.mutate(routeId).setLoad(4).setTotalDistanceMeters(3800L)
                    .setTotalDurationSeconds(270L).setRouteVersion(1L).commit();
            customers.mutate(chosenCustomer).setState(CustomerState.ASSIGNED)
                    .setAssignedRoute(routeId).setAssignedPosition(1)
                    .setArrivalMinute(3L).commit();
            customers.mutate(customerTwo).setAssignedPosition(2)
                    .setArrivalMinute(5L).commit();
            unassigned.filter(row -> row.customerIdValue() == chosenCustomer.value).remove();
            candidates.filter(row -> row.customerIdValue() == chosenCustomer.value).remove();

            RouteVisitRowTable liveVisits = routes.visits(routeId);
            int[] visitRows = liveVisits.byPosition().rowIndexes();
            LongColumnView visitCustomers = liveVisits.customerIdValueColumn();
            IntColumnView positions = liveVisits.positionColumn();
            LongColumnView arrivals = liveVisits.arrivalMinuteColumn();
            IntColumnView loads = liveVisits.loadAfterVisitColumn();
            try {
                require(visitRows.length == 3
                                && visitCustomers.getLong(visitRows[0]) == customerOne.value
                                && visitCustomers.getLong(visitRows[1]) == customerThree.value
                                && visitCustomers.getLong(visitRows[2]) == customerTwo.value
                                && positions.getInt(visitRows[2]) == 2
                                && arrivals.getLong(visitRows[2]) == 5L
                                && loads.getInt(visitRows[2]) == 4,
                        "non-empty insertion rewrites the shifted route segment");
            } finally {
                loads.close();
                arrivals.close();
                positions.close();
                visitCustomers.close();
            }
            Route exported = routes.fetch(routeId);
            require(exported.visits.size() == 3 && exported.routeVersion == 1L,
                    "keyed route fetch recursively materializes detached visit List");
            require(customers.findByState(CustomerState.ASSIGNED).count() == 3L
                            && customers.findByState(CustomerState.UNASSIGNED).count() == 0L,
                    "customer state index follows cross-table application commit");
            int customerThreeRow = customers.rowIndexOf(customerThree.value);
            LongColumnView arrival = customers.arrivalMinuteColumn();
            try {
                require(unassigned.size() == 0 && arrival.isPresent(customerThreeRow)
                                && arrival.getLong(customerThreeRow) == 3L,
                    "derived unassigned workspace and optional arrival snapshot are synchronized");
            } finally {
                arrival.close();
            }
            expectCode("missing_key", () -> travel.fetch(
                    new LocationPairKey(firstLocation, secondLocation)));

            return new ScenarioResult(exported.visits.size(), routes.runtimePlan().schemaHash(),
                    routes.statsSnapshot().childInstanceCount(), 3, 32,
                    (long) liveVisits.capacity() * 32L, 5L, 9L,
                    exported.visits.size());
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
        public final int apcRows;
        public final int hotLeafBytesPerRow;
        public final long hotLeafWorkingSetBytes;
        public final long reads;
        public final long mutations;
        public final int exports;
        ScenarioResult(int visits, String schemaHash, long childInstances,
                       int apcRows, int hotLeafBytesPerRow,
                       long hotLeafWorkingSetBytes, long reads, long mutations,
                       int exports) {
            this.visits = visits;
            this.schemaHash = schemaHash;
            this.childInstances = childInstances;
            this.apcRows = apcRows;
            this.hotLeafBytesPerRow = hotLeafBytesPerRow;
            this.hotLeafWorkingSetBytes = hotLeafWorkingSetBytes;
            this.reads = reads;
            this.mutations = mutations;
            this.exports = exports;
        }
    }
}
