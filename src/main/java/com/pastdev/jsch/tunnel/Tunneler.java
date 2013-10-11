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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.jcraft.jsch.JSchException;
import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.IOUtils;
import com.pastdev.jsch.SessionFactory;
import com.pastdev.jsch.SessionFactory.SessionFactoryBuilder;
import com.pastdev.jsch.proxy.SshProxy;


public class Tunneler implements Closeable {
    private static final String PROPERTY_JSCH_TUNNELS_FILE = "jsch.tunnels.file";
    private static final String PROPERTY_JSCH_DOT_JSCH = "jsch.dotJsch";
    private static final Pattern PATTERN_TUNNELS_CFG_COMMENT_LINE = Pattern.compile( "^\\s*#.*$" );
    private static Logger logger = LoggerFactory.getLogger( Tunneler.class );

    private File dotJschDir;
    private DefaultSessionFactory sessionFactory;
    private List<TunnelConnection> tunnelConnections;

    public Tunneler() throws JSchException, IOException {
        sessionFactory = new DefaultSessionFactory();
        sessionFactory.setConfig( "PreferredAuthentications", "publickey,keyboard-interactive,password" );
        setTunnelConnections();
    }

    @Override
    public void close() {
        for ( TunnelConnection tunnelConnection : tunnelConnections ) {
            IOUtils.closeAndLogException( tunnelConnection );
        }
    }

    private File dotJschDir() {
        if ( dotJschDir == null ) {
            String dotSshString = System.getProperty( PROPERTY_JSCH_DOT_JSCH );
            if ( dotSshString != null ) {
                dotJschDir = new File( dotSshString );
            }
            else {
                dotJschDir = new File(
                        new File( System.getProperty( "user.home" ) ),
                        ".jsch" );
            }
        }
        return dotJschDir;
    }

    private void setTunnelConnections() throws IOException, JSchException {
        File tunnels = null;
        String tunnelsString = System.getProperty( PROPERTY_JSCH_TUNNELS_FILE );
        if ( tunnelsString != null && !tunnelsString.isEmpty() ) {
            tunnels = new File( tunnelsString );
        }
        else {
            tunnels = new File( dotJschDir(), "tunnels.cfg" );
        }

        if ( !tunnels.exists() ) {
            throw new FileNotFoundException( "tunnels.cfg not found.  must be at ${user.home}/.jsch/tunnels.cfg or specified as system property ssh.tunnels.file" );
        }

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream( tunnels );
            setTunnelConnections( inputStream );
        }
        finally {
            if ( inputStream != null ) {
                IOUtils.closeAndLogException( inputStream );
            }
        }
    }

    private void setTunnelConnections( InputStream tunnels ) throws IOException, JSchException {
        // A tunnelSpec looks like this:
        // user@host->tunnelUser@tunnelHost->tunnel2User@tunnel2Host|localAlias:localPort:destinationHostname:destinationPort
        Map<String, Set<Tunnel>> tunnelMap = new HashMap<String, Set<Tunnel>>();
        BufferedReader reader = new BufferedReader( new InputStreamReader( tunnels ) );
        String line = null;
        while ( (line = reader.readLine()) != null ) {
            if ( PATTERN_TUNNELS_CFG_COMMENT_LINE.matcher( line ).matches() ) {
                continue;
            }

            String[] pathAndTunnel = line.split( "\\|" );
            Set<Tunnel> tunnelList = tunnelMap.get( pathAndTunnel[0] );
            if ( tunnelList == null ) {
                tunnelList = new HashSet<Tunnel>();
                tunnelMap.put( pathAndTunnel[0], tunnelList );
            }
            tunnelList.add( new Tunnel( pathAndTunnel[1] ) );
        }

        tunnelConnections = new ArrayList<TunnelConnection>();
        SessionFactoryCache sessionFactoryCache = new SessionFactoryCache( sessionFactory );
        for ( String path : tunnelMap.keySet() ) {
            tunnelConnections.add( new TunnelConnection( sessionFactoryCache.getSessionFactory( path ),
                    new ArrayList<Tunnel>( tunnelMap.get( path ) ) ) );
        }
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
