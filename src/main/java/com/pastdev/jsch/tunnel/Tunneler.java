package com.pastdev.jsch.tunnel;


import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.jcraft.jsch.JSchException;
import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.IOUtils;
import com.pastdev.jsch.SessionFactory;
import com.pastdev.jsch.SessionFactory.SessionFactoryBuilder;
import com.pastdev.jsch.proxy.SshProxy;


public class Tunneler implements Closeable {
    private static final File DEFAULT_DOT_SSH;
    private static final File DEFAULT_HOME;
    private static final String DEFAULT_HOSTNAME = "localhost";
    private static final int DEFAULT_PORT = 22;
    private static final String DEFAULT_USERNAME = System.getProperty( "user.name" ).toLowerCase();

    private static Logger logger = LoggerFactory.getLogger( Tunneler.class );

    static {
        DEFAULT_HOME = new File( System.getProperty( "user.home" ) );
        DEFAULT_DOT_SSH = new File( DEFAULT_HOME, ".ssh" );
    }

    private DefaultSessionFactory sessionFactory;
    private List<TunnelConnection> tunnelConnections;

    public Tunneler() throws JSchException, IOException {
        sessionFactory = new DefaultSessionFactory( DEFAULT_USERNAME, DEFAULT_HOSTNAME, DEFAULT_PORT );
        sessionFactory.setIdentitiesFromPrivateKeys( getPrivateKeys() );
        sessionFactory.setKnownHosts( getKnownHosts() );

        tunnelConnections = getTunnelConnections( sessionFactory );
    }

    @Override
    public void close() {
        for ( TunnelConnection tunnelConnection : tunnelConnections ) {
            IOUtils.closeAndLogException( tunnelConnection );
        }
    }

    static String getKnownHosts() {
        String knownHosts = System.getProperty( "ssh.known_hosts.file" );
        if ( knownHosts != null && !knownHosts.isEmpty() ) {
            return knownHosts;
        }

        File knownHostsFile = new File( DEFAULT_DOT_SSH, "known_hosts" );
        if ( knownHostsFile.exists() ) {
            return knownHostsFile.getAbsolutePath();
        }

        return null;
    }

    static List<String> getPrivateKeys() {
        String identitiesString = System.getProperty( "ssh.identity.files" );
        if ( identitiesString != null && !identitiesString.isEmpty() ) {
            return Arrays.asList( identitiesString.split( "," ) );
        }

        List<String> identities = new ArrayList<String>();
        for ( File file : new File[] {
                new File( DEFAULT_DOT_SSH, "id_rsa" ),
                new File( DEFAULT_DOT_SSH, "id_dsa" ),
                new File( DEFAULT_DOT_SSH, "id_ecdsa" ) } ) {
            if ( file.exists() ) {
                identities.add( file.getAbsolutePath() );
            }
        }
        return identities;
    }

    static TunnelConnection getTunnelConnection( DefaultSessionFactory defaultSessionFactory, String tunnelSpec ) {
        return null;
    }

    static List<TunnelConnection> getTunnelConnections( DefaultSessionFactory defaultSessionFactory ) throws IOException, JSchException {
        File tunnels = null;
        String tunnelsString = System.getProperty( "ssh.tunnels.file" );
        if ( tunnelsString != null && !tunnelsString.isEmpty() ) {
            tunnels = new File( tunnelsString );
        }
        else {
            File dotJsch = new File( DEFAULT_HOME, ".jsch" );
            tunnels = new File( dotJsch, "tunnels.cfg" );
        }

        if ( !tunnels.exists() ) {
            throw new FileNotFoundException( "tunnels.cfg not found.  must be at ~/.jsch/tunnels.cfg or specified as system property ssh.tunnels.file" );
        }

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream( tunnels );
            return getTunnelConnections( defaultSessionFactory, inputStream );
        }
        finally {
            if ( inputStream != null ) {
                IOUtils.closeAndLogException( inputStream );
            }
        }
    }

    static List<TunnelConnection> getTunnelConnections( DefaultSessionFactory defaultSessionFactory, InputStream tunnels ) throws IOException, JSchException {
        // A tunnelSpec looks like this:
        // user@host->tunnelUser@tunnelHost->tunnel2User@tunnel2Host|localAlias:localPort:destinationHostname:destinationPort
        Map<String, Set<Tunnel>> tunnelMap = new HashMap<String, Set<Tunnel>>();
        BufferedReader reader = new BufferedReader( new InputStreamReader( tunnels ) );
        String line = null;
        while ( (line = reader.readLine()) != null ) {
            String[] pathAndTunnel = line.split( "\\|" );
            Set<Tunnel> tunnelList = tunnelMap.get( pathAndTunnel[0] );
            if ( tunnelList == null ) {
                tunnelList = new HashSet<Tunnel>();
                tunnelMap.put( pathAndTunnel[0], tunnelList );
            }
            tunnelList.add( new Tunnel( pathAndTunnel[1] ) );
        }

        List<TunnelConnection> tunnelConnections = new ArrayList<TunnelConnection>();
        SessionFactoryCache sessionFactoryCache = new SessionFactoryCache( defaultSessionFactory );
        for ( String path : tunnelMap.keySet() ) {
            tunnelConnections.add( new TunnelConnection( sessionFactoryCache.getSessionFactory( path ),
                    new ArrayList<Tunnel>( tunnelMap.get( path ) ) ) );
        }

        return tunnelConnections;
    }

    public void open() throws JSchException {
        for ( TunnelConnection tunnelConnection : tunnelConnections ) {
            tunnelConnection.open();
        }
    }

    public static void main( String[] args ) throws JSchException, IOException {
        logger.info( "Starting up tunneler" );
        Tunneler tunneler = new Tunneler();
        tunneler.open();

        logger.info( "Tunneler started, press enter to quit" );
        new BufferedReader( new InputStreamReader( System.in ) ).readLine();

        logger.info( "Closing down tunneler" );
        tunneler.close();
    }

    static class SessionFactoryCache {
        private Map<String, SessionFactory> sessionFactoryByPath;
        private DefaultSessionFactory defaultSessionFactory;

        SessionFactoryCache( DefaultSessionFactory defaultSessionFactory ) {
            this.defaultSessionFactory = defaultSessionFactory;
            this.sessionFactoryByPath = new HashMap<String, SessionFactory>();
        }

        public SessionFactory getSessionFactory( String path ) throws JSchException {
            SessionFactory sessionFactory = null;
            String key = null;
            for ( String part : path.split( "\\-\\>" ) ) {
                if ( key == null ) {
                    key = part;
                }
                else {
                    key += "->" + part;
                }
                if ( sessionFactoryByPath.containsKey( key ) ) {
                    sessionFactory = sessionFactoryByPath.get( key );
                    continue;
                }

                SessionFactoryBuilder builder = null;
                if ( sessionFactory == null ) {
                    builder = defaultSessionFactory.newSessionFactoryBuilder();
                }
                else {
                    builder = sessionFactory.newSessionFactoryBuilder()
                            .setProxy( new SshProxy( sessionFactory ) );
                }

                // start with [username@]hostname[:port]
                String[] userAtHost = part.split( "\\@" );
                String hostname = null;
                if ( userAtHost.length == 2 ) {
                    builder.setUsername( userAtHost[0] );
                    hostname = userAtHost[1];
                }
                else {
                    hostname = userAtHost[0];
                }

                // left with hostname[:port]
                String[] hostColonPort = hostname.split( "\\:" );
                builder.setHostname( hostColonPort[0] );
                if ( hostColonPort.length == 2 ) {
                    builder.setPort( Integer.parseInt( hostColonPort[1] ) );
                }

                sessionFactory = builder.build();
            }
            return sessionFactory;
        }
    }
}
