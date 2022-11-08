# nifi-signal-messenger

nifi-signal-messenger is a [NiFi](https://nifi.apache.org) extension that makes it possible to
send and receive [Signal](https://signal.org/) messages in NiFi. This extension uses the daemon mode
of [signal-cli](https://github.com/AsamK/signal-cli) is therefore highly dependent on signal-cli. 
You can use either linked or registered numbers with this extension.

## Getting started

### Setup a signal-cli configuration
This NiFi extension needs an existing signal-cli daemon running with the flag --http to start and work properly.
So the first thing you'll need to do (if you don't already have a signal-cli daemon running) is to
download and install signal-cli v0.11.5 [which is really well describes in this how-to](https://packaging.gitlab.io/signal-cli/installation/standalone/).
Then either register a number (as described in the how-to) or make a link to an existing number.

	/path/to/signal-cli/bin/signal-cli --config /path/to/configuration/Signal daemon --receive-mode manual --send-read-receipts --http 0.0.0.0:8090

### Create a controller service in NiFi
--- TODO ---

### Add processors
--- TODO ---

## Building from source
 
	mvn package

and grab the following two nars and drop them in either ''lib'' or ''extensions'' directory in your
NiFi installation.

	./nifi-signal-messenger-nar/target/nifi-signal-messenger-nar-<version>.nar 
	./nifi-signal-messenger-api-nar/target/nifi-signal-messenger-api-nar-<version>.nar
