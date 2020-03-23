# nifi-signal-messenger

nifi-signal-messenger is a [NiFi](https://nifi.apache.org) extension that makes it possible to
send and receive [Signal](https://signal.org/) messages in NiFi. This extension uses the configuration directory
that [signal-cli](https://github.com/AsamK/signal-cli) produces and is therefore highly dependent on 
signal-cli. You can use either linked or registered numbers with this extension.

## Getting started

### Setup a signal-cli configuration
This NiFi extension needs an existing signal-cli configuration in order to start and work properly.
So the first thing you'll need to do (if you don't already have a signal-cli configuration directory) is to
download and install signal-cli v0.6.5 [which is really well describes in this how-to](https://github.com/AsamK/signal-cli/wiki/HowToUbuntu).
Then either register a number (as described in the how-to) or make a link to an existing number.
In order to control where the configuration directory is stored use the '--config' param. This
path needs to be accessed by NiFi later on.

### Create a controller service in NiFi
--- TODO ---

### Add processors
--- TODO ---

## Building from source

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