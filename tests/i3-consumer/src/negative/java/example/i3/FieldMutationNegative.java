package example.i3;

final class FieldMutationNegative {
    void reject() {
        Soma.eventTable().amount.remove();
    }
}
