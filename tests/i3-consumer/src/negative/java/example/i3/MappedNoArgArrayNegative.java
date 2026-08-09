package example.i3;

final class MappedNoArgArrayNegative {
    void reject() {
        Soma.eventTable().map(view -> view.label()).toArray();
    }
}
