package demo;


import org.bbottema.javasocksproxyserver.Logger;
import org.bbottema.javasocksproxyserver.LoggerFactory;
import org.bbottema.javasocksproxyserver.ProxyHandler;
import org.bbottema.javasocksproxyserver.SocksServer;

import static java.lang.Integer.parseInt;


public class StartProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartProxy.class);

	private static final int DEFAULT_PORT = 8888;

	public static void main(String[] args) {
		new SocksServer().start(determinePort(args), ProxyHandler.class);
	}

	private static int determinePort(String[] args) {
		if (args.length == 1) {
			try {
				return parseInt(args[0].trim());
			} catch (Exception e) {
                LOGGER.debug("Unable to parse port from command-line parameter, defaulting to: " + DEFAULT_PORT);
			}
		} else {
            LOGGER.debug("Port not passed as command-line parameter, defaulting to: " + DEFAULT_PORT);
		}
		return DEFAULT_PORT;
	}
}
