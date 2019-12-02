/**
 * 
 */
package uk.ac.lancs.networks;

/**
 * Indicates that a service looked up by handle was not found.
 * 
 * @author simpsons
 */
public class UnknownServiceHandleException extends NetworkLogicException {
    private static final long serialVersionUID = 1L;

    private final String handle;

    /**
     * Create an exception.
     * 
     * @param networkName the name of the network to which this error
     * pertains
     */
    public UnknownServiceHandleException(String networkName, String handle) {
        super(networkName, handle);
        this.handle = handle;
    }

    /**
     * Create an exception with a cause.
     * 
     * @param networkName the name of the network to which this error
     * pertains
     * 
     * @param cause the cause
     */
    public UnknownServiceHandleException(String networkName, String handle,
                                         Throwable cause) {
        super(networkName, handle, cause);
        this.handle = handle;
    }

    /**
     * Get the sought handle.
     * 
     * @return the handle
     */
    public String getHandle() {
        return handle;
    }
}
