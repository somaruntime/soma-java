package example.i3;

final class FlatMapNegative {
    void reject() {
        Soma.eventTable().map(view -> view.label())
                .flatMap(value -> java.util.stream.Stream.of(value));
    }
}
