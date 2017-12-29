/**
 * 
 */
package uk.ac.lancs.treeroute;

import java.util.Collection;

public interface ConnectionResponse {
    void ready(Connection conn, Collection<LinkUpdate> updates);
    void failed();
}