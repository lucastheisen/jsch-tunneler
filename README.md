jsch-tunneler
=============

A kind of poor-mans vpn.  Get started by creating a file called:
  
    ${user.home}/.jsch/tunnels.cfg
  
A simple BNF syntax for this file is:

    <line>        ::= "#" <comment> | <path> "|" <tunnel>
    <path>        ::= <destination> | <proxy> <destination>
    <proxy>       ::= <destination> "->" | <proxy> <destination> "->" 
    <destination> ::= <hostname> | <username> "@" <hosthame> | 
                      <hostname> ":" <port> | <username> "@" <hostname> ":" <port>
    <tunnel>      ::= <localPort> ":" <destinationHostname> ":" <destinationPort> |
                      <localAlias> ":" <localPort> ":" <destinationHostname> ":" <destinationPort>     

For example, say you have a home network with a linux gateway (called baz) and 
2 windows servers (called foo and bar).  From outside, you want to be able to 
remote desktop (port 3389) to either server, and on bar, you are running a mysql 
database (port 3306) that only allows localhost users.  If your username is joe, 
the tunnels might look like this:

    # Remote desktop on foo
    joe@baz|13389:foo:3389
    # Remote desktop on bar
    joe@baz|23389:bar:3389
    # MySQL on bar
    joe@baz->bar|23306:bar:3306
    # or fully spelled out it would look more like this
    # joe@baz:22->joe@bar:22|localhost:23306:bar:3306

Once that file is created, you can compile this project using maven, and the then
run the executable jar thusly:

    java -jar tunneler.jar
    
Then you could connect to foo via remote desktop on localhost port 13389, bar on 
localhost port 23389, and bar mysql on port 23306.  There is a lot more possible, 
but I dont have time to document it here.  And for the most part its the same as

    ssh joe@baz -L 13389:foo:3389
    
But this will open up connections to as many different servers/ports as you want
to put into the tunnels config.

Lastly, this is ssh-agent/pageant capable, so if you have them running it will
pull your identity from them, otherwise it looks in ${user.home}/.ssh/ for the
standard files, or you can specify the .ssh folder with the java option:

    -Djsch.dotSsh=C:/Users/joe/.ssh

Or you can specify the private key files you want to use with:

    -Djsch.privateKey.files=C:/Users/joe/.ssh/id_dsa,C:/Users/joe/.ssh/id_rsa,C:/Users/joe/.ssh/id_ecdsa
    
The known_hosts file will also be found in your home directory, or via the 
jsch.dotSsh setting or explicitly with:

    -Djsch.knownHosts.file=C:/Users/joe/.ssh/known_hosts

    
