package ws.palladian.core;

import org.apache.commons.lang3.Validate;

final class ImmutableStringValue implements NominalValue {

    private final String value;

    public ImmutableStringValue(String value) {
        Validate.notNull(value, "value must not be null");
        this.value = value;
    }

    @Override
    public String getString() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

}
