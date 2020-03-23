# nifi-signal-messeger

nifi-signal-messenger is a [NiFi](https://nifi.apache.org) extension that makes it possible to
send and receive [Signal](https://signal.org/) messages. This extension uses the configuration directory
that [signal-cli](https://github.com/AsamK/signal-cli) produces and is therefore highly dependent on 
signal-cli.

## Getting started

## Building

This project uses the signal-cli-jar to correctly parse and use the configuration used by signal-cli.
The jar is not published to any public repository so you'll need to make sure maven can find
the library locally (at least) before building nifi-signal-messenger. First, [download signal-cli](https://github.com/AsamK/signal-cli/releases) 
and extract it (for instance in /opt/signal-cli-0.6.5). Then run the following command to add the library
to your local repository:

	mvn install:install-file \
	   -Dfile=/opt/signal-cli-0.6.5/lib/signal-cli-0.6.5.jar \
	   -DgroupId=com.github.turasa \
	   -DartifactId=signal-cli \
	   -Dversion=0.6.5 \
	   -Dpackaging=jar \
	   -DgeneratePom=true

After the command finishes run:
 
	mvn package

and grab the following two nars and drop them in either ''lib'' or ''extensions'' directory in your
NiFi installation.

	./nifi-signal-messenger-nar/target/nifi-signal-messenger-nar-<version>.nar 
	./nifi-signal-messenger-api-nar/target/nifi-signal-messenger-api-nar-<version>.nar