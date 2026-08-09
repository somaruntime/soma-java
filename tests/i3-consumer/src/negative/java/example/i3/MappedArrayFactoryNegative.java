package example.i3;

final class MappedArrayFactoryNegative {
    void reject() {
        Soma.eventTable().map(view -> view.label()).toArray(String[]::new);
    }
}
