package example.i2;

final class ObjectEqualityNegative {
    void use(AllTypesTable table) {
        table.payload.eq(new Payload("not-keyable"));
    }
}
