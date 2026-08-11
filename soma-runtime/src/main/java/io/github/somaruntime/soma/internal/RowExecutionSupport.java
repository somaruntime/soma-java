package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/** Low-level row value/callback mechanics shared without sharing plan traversal. */
final class RowExecutionSupport {

    private RowExecutionSupport() {
    }

    static boolean callbackTest(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowPredicate callback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try {
            return callback.test();
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    bound.operation, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
            cursor.leave();
        }
    }

    static void callbackAction(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowAction callback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try {
            callback.accept();
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    bound.operation, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
            cursor.leave();
        }
    }

    static <R> R callbackMap(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowMapper<R> callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try {
            return callback.apply();
        } catch (Exception failure) {
            if (!applicationCallback) {
                if (failure instanceof RuntimeException) {
                    throw (RuntimeException) failure;
                }
                throw new AssertionError("generated materializer threw checked failure", failure);
            }
            throw SomaFailures.callbackFailure(
                    bound.operation, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
            cursor.leave();
        }
    }

    static boolean callbackMapBoolean(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowToBooleanMapper callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try { return callback.applyAsBoolean(); }
        catch (Exception failure) {
            throw mapperFailure(failure, applicationCallback, bound.provenance);
        } finally { CallbackExecutionScope.exit(); cursor.leave(); }
    }

    static byte callbackMapByte(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowToByteMapper callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try { return callback.applyAsByte(); }
        catch (Exception failure) {
            throw mapperFailure(failure, applicationCallback, bound.provenance);
        } finally { CallbackExecutionScope.exit(); cursor.leave(); }
    }

    static short callbackMapShort(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowToShortMapper callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try { return callback.applyAsShort(); }
        catch (Exception failure) {
            throw mapperFailure(failure, applicationCallback, bound.provenance);
        } finally { CallbackExecutionScope.exit(); cursor.leave(); }
    }

    static char callbackMapChar(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowToCharMapper callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try { return callback.applyAsChar(); }
        catch (Exception failure) {
            throw mapperFailure(failure, applicationCallback, bound.provenance);
        } finally { CallbackExecutionScope.exit(); cursor.leave(); }
    }

    static int callbackMapInt(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowToIntMapper callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try { return callback.applyAsInt(); }
        catch (Exception failure) {
            throw mapperFailure(failure, applicationCallback, bound.provenance);
        } finally { CallbackExecutionScope.exit(); cursor.leave(); }
    }

    static long callbackMapLong(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowToLongMapper callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try { return callback.applyAsLong(); }
        catch (Exception failure) {
            throw mapperFailure(failure, applicationCallback, bound.provenance);
        } finally { CallbackExecutionScope.exit(); cursor.leave(); }
    }

    static float callbackMapFloat(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowToFloatMapper callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try { return callback.applyAsFloat(); }
        catch (Exception failure) {
            throw mapperFailure(failure, applicationCallback, bound.provenance);
        } finally { CallbackExecutionScope.exit(); cursor.leave(); }
    }

    static double callbackMapDouble(
            BoundRowPlan bound,
            int locator,
            GeneratedCallbacks.RowToDoubleMapper callback,
            boolean applicationCallback) {
        GeneratedQueryCursor cursor = bound.logical.owner().queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try { return callback.applyAsDouble(); }
        catch (Exception failure) {
            throw mapperFailure(failure, applicationCallback, bound.provenance);
        } finally { CallbackExecutionScope.exit(); cursor.leave(); }
    }

    private static RuntimeException mapperFailure(
            Exception failure,
            boolean applicationCallback,
            Object provenance) {
        if (applicationCallback) {
            return SomaFailures.callbackFailure(
                    SomaOperation.QUERY, failure, provenance);
        }
        if (failure instanceof RuntimeException) return (RuntimeException) failure;
        throw new AssertionError(
                "generated primitive materializer threw checked failure", failure);
    }

    static int compare(
            BoundRowPlan bound,
            int left,
            int right,
            LogicalRowPlan.Stage stage) {
        if (stage.kind == LogicalRowPlan.StageKind.TYPED_ORDER) {
            GeneratedOrder<?> order = stage.order;
            GeneratedTableLayout layout = bound.logical.owner().layout();
            for (int ordinal = 0; ordinal < order.size(); ordinal++) {
                int result = layout.compareStored(
                        bound.root.directory,
                        left,
                        right,
                        order.fieldIndex(ordinal));
                if (result != 0) {
                    return order.descending(ordinal) ? -result : result;
                }
            }
            return 0;
        }
        if (stage.kind != LogicalRowPlan.StageKind.CALLBACK_ORDER) {
            throw new AssertionError("row stage is not an order");
        }
        GeneratedTable table = bound.logical.owner();
        GeneratedQueryCursor leftCursor = table.queryCursor();
        GeneratedQueryCursor rightCursor = table.secondaryQueryCursor();
        leftCursor.enter(left);
        rightCursor.enter(right);
        CallbackExecutionScope.enter();
        try {
            return stage.comparator.compare();
        } catch (Exception failure) {
            throw SomaFailures.callbackFailure(
                    bound.operation, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
            rightCursor.leave();
            leftCursor.leave();
        }
    }

    /** Canonical stable comparison schedule for an opaque row Comparator. */
    static void stableCallbackSort(
            BoundRowPlan bound,
            IntLocatorBuffer values,
            LogicalRowPlan.Stage stage) {
        if (stage.kind != LogicalRowPlan.StageKind.CALLBACK_ORDER) {
            throw new AssertionError("canonical callback sort requires callback order");
        }
        if (values.size() < 2) return;
        int[] scratch = new int[values.size()];
        stableCallbackMergeSort(
                bound, values.backing(), scratch, 0, values.size(), stage);
    }

    private static void stableCallbackMergeSort(
            BoundRowPlan bound,
            int[] values,
            int[] scratch,
            int from,
            int to,
            LogicalRowPlan.Stage stage) {
        int length = to - from;
        if (length < 2) return;
        int middle = from + length / 2;
        stableCallbackMergeSort(
                bound, values, scratch, from, middle, stage);
        stableCallbackMergeSort(
                bound, values, scratch, middle, to, stage);
        int left = from;
        int right = middle;
        int output = from;
        while (left < middle && right < to) {
            if (compare(bound, values[left], values[right], stage) <= 0) {
                scratch[output++] = values[left++];
            } else {
                scratch[output++] = values[right++];
            }
        }
        while (left < middle) scratch[output++] = values[left++];
        while (right < to) scratch[output++] = values[right++];
        System.arraycopy(scratch, from, values, from, length);
    }

    static int arrayLength(long value, Object provenance) {
        return arrayLength(value, SomaOperation.QUERY, provenance);
    }

    static int arrayLength(
            long value,
            SomaOperation operation,
            Object provenance) {
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw SomaFailures.failure(
                    SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                    operation,
                    "operation result exceeds Java array/container boundary",
                    provenance);
        }
        return (int) value;
    }

    static long arrayBytes(long elements, long bytesPerElement, Object provenance) {
        return CheckedLong.add(
                32L,
                CheckedLong.multiply(
                        elements, bytesPerElement, SomaOperation.QUERY, provenance),
                SomaOperation.QUERY,
                provenance);
    }
}
