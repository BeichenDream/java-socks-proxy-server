package org.bbottema.javasocksproxyserver;



import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class SocksServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);

    public static final boolean SUPPORTBIND = false;

	protected boolean stopping = false;
	protected HashMap env;

    {
        env = new HashMap();
    }
	
	public synchronized void start(int listenPort,Class proxyHandlerClass) {
		start(listenPort, ServerSocketFactory.getDefault(),proxyHandlerClass);
	}
	
	public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory,Class proxyHandlerClass) {
		this.stopping = false;
		this.env = env;
		new Thread(new ServerProcess(listenPort, serverSocketFactory,env,proxyHandlerClass)).start();
	}

    public HashMap getEnv() {
        return env;
    }

    public synchronized void stop() {
		stopping = true;
	}

	public boolean isAlive(){
	    return !stopping;
    }
	
	private class ServerProcess implements Runnable {
		
		protected final int port;
		private final ServerSocketFactory serverSocketFactory;
		protected final Class proxyHandlerClass;
		protected final HashMap environment;
		
		public ServerProcess(int port, ServerSocketFactory serverSocketFactory, HashMap environment, Class proxyHandlerClass) {
			this.port = port;
			this.serverSocketFactory = serverSocketFactory;
			this.proxyHandlerClass = proxyHandlerClass;
			this.environment=environment;
		}
		
		@Override
		public void run() {
			LOGGER.print("SOCKS server started...");
			try {
				handleClients(port);
				LOGGER.print("SOCKS server stopped...");
			} catch (IOException e) {
				LOGGER.print("SOCKS server crashed...");
				Thread.currentThread().interrupt();
			}
		}

		protected void handleClients(int port) throws IOException {
			final ServerSocket listenSocket = serverSocketFactory.createServerSocket(port);
			listenSocket.setSoTimeout(SocksConstants.LISTEN_TIMEOUT);
			
			LOGGER.print("SOCKS server listening at port: " + listenSocket.getLocalPort());

			while (true) {
				synchronized (SocksServer.this) {
					if (stopping) {
						break;
					}
				}
				handleNextClient(listenSocket);
			}

			try {
				listenSocket.close();
			} catch (IOException e) {
				// ignore
			}
		}

		private void handleNextClient(ServerSocket listenSocket) {
			try {
				final Socket clientSocket = listenSocket.accept();
				clientSocket.setSoTimeout(SocksConstants.DEFAULT_SERVER_TIMEOUT);
				LOGGER.print("Connection from : " + Utils.getSocketInfo(clientSocket));
				ProxyHandler proxyHandler = (ProxyHandler) proxyHandlerClass.getConstructor(Socket.class).newInstance(clientSocket);
                proxyHandler.setEnvironment(environment);
				new Thread(proxyHandler).start();
			} catch (InterruptedIOException e) {
				//	This exception is thrown when accept timeout is expired
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}
}
