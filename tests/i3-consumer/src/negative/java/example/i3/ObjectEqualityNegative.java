package example.i3;

final class ObjectEqualityNegative {
    void reject() {
        Soma.eventTable().payload.eq(new Object());
    }
}
