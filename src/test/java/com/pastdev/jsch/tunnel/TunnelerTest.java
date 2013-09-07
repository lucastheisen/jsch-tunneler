package com.pastdev.jsch.tunnel;


import static org.junit.Assert.assertEquals;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;


import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.jcraft.jsch.JSchException;
import com.pastdev.jsch.DefaultSessionFactory;


public class TunnelerTest {
    private static Logger logger = LoggerFactory.getLogger( TunnelerTest.class );
    private static final String TUNNEL_SPEC = "" +
            "user1@server1:22->user2@server2->server3:2222->server4|local1:1234:remote:5678\n" +
            "user1@server1:22->user2@server2->server3:2222->server4|local1:4321:remote:8765\n" +
            "user1@server1:22->user2@server2->server3:2222|local2:1234:remote:5678\n";

    @Test
    public void testGetTunnelConnections() throws IOException, JSchException {
        logger.info( "testing getTunnelConnections" );
        DefaultSessionFactory defaultSessionFactory = new DefaultSessionFactory();
        List<TunnelConnection> tunnelConnections = Tunneler.getTunnelConnections(
                defaultSessionFactory, new ByteArrayInputStream(
                        TUNNEL_SPEC.getBytes( Charset.forName( "UTF-8" ) ) ) );
        for ( TunnelConnection tunnelConnection : tunnelConnections ) {
            logger.debug( "TunnelConnection: {}", tunnelConnection );
        }
        assertEquals( 2, tunnelConnections.size() );
    }
}
