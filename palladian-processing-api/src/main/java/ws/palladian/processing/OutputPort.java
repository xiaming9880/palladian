/**
 * Created on: 21.11.2012 17:16:06
 */
package ws.palladian.processing;

import java.util.List;

import org.apache.commons.lang3.Validate;

/**
 * <p>
 * 
 * </p>
 * 
 * @author Klemens Muthmann
 * @version 1.0
 * @since 0.1.8
 */
public final class OutputPort extends Port {

    List<InputPort> nextInput;

    /**
     * <p>
     * 
     * </p>
     * 
     * @param identifier
     */
    public OutputPort(String identifier, List<InputPort> nextInput) {
        super(identifier);

        Validate.notEmpty(nextInput);

        this.nextInput = nextInput;
    }

    public OutputPort(String identifier) {
        super(identifier);
    }

    public void fire() {
        PipelineDocument<?> document = poll();
        for (InputPort inputPort : nextInput) {
            inputPort.put(document);
        }
    }

    /**
     * <p>
     * 
     * </p>
     * 
     * @param inputPort
     */
    public void connectWith(InputPort inputPort) {
        Validate.notNull(inputPort);

        nextInput.add(inputPort);
    }

}
