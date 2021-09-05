package org.bbottema.javasocksproxyserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;

import static java.lang.String.format;
import static org.bbottema.javasocksproxyserver.Utils.getSocketInfo;

public class ProxyHandler implements Runnable {

	protected static final Logger LOGGER = LoggerFactory.getLogger(ProxyHandler.class);

    protected InputStream m_ClientInput = null;
    protected OutputStream m_ClientOutput = null;
    protected InputStream m_ServerInput = null;
    protected OutputStream m_ServerOutput = null;
    protected Object m_lock;
    protected Socks4Impl comm = null;
    protected HashMap environment = null;

    protected boolean isClosed = false;

	Socket m_ClientSocket;
	Socket m_ServerSocket = null;
	byte[] m_Buffer = new byte[SocksConstants.DEFAULT_BUF_SIZE];

	public ProxyHandler(Socket clientSocket) {
		m_lock = this;
		m_ClientSocket = clientSocket;
		try {
			m_ClientSocket.setSoTimeout(SocksConstants.DEFAULT_PROXY_TIMEOUT);
		} catch (SocketException e) {
			LOGGER.print("Socket Exception during seting Timeout.");
		}
		LOGGER.print("Proxy Created.");
	}

    public HashMap getEnvironment() {
        return environment;
    }

    public void setEnvironment(HashMap environment) {
        this.environment = environment;
    }

    public void setLock(Object lock) {
		this.m_lock = lock;
	}

	public void run() {
		LOGGER.debug("Proxy Started.");
		setLock(this);

		try {
            if (prepareClient()) {
                processRelay();
                close();
            } else {
                LOGGER.error("Proxy - client socket is null !");
            }
        }catch (Exception e) {
            close();
            LOGGER.error("Proxy - client socket processRelay fail!");
        }
	}

	public synchronized void close() {
	    if (isClosed){
	        return;
        }
        isClosed = true;
	    closeOther();
		try {
			if (m_ClientOutput != null) {
				m_ClientOutput.flush();
				m_ClientOutput.close();
			}
		} catch (IOException e) {
			// ignore
		}
		try {
			if (m_ServerOutput != null) {
				m_ServerOutput.flush();
				m_ServerOutput.close();
			}
		} catch (IOException e) {
			// ignore
		}

		try {
			if (m_ClientSocket != null) {
				m_ClientSocket.close();
			}
		} catch (IOException e) {
			// ignore
		}

		try {
			if (m_ServerSocket != null) {
				m_ServerSocket.close();
			}
		} catch (IOException e) {
			// ignore
		}

		m_ServerSocket = null;
		m_ClientSocket = null;

		LOGGER.debug("Proxy Closed.");
	}
	public void closeOther() {

    }

	public void sendToClient(byte[] buffer) {
		sendToClient(buffer, buffer.length);
	}

	public void sendToClient(byte[] buffer, int len) {
		if (m_ClientOutput != null && len > 0 && len <= buffer.length) {
			try {
				m_ClientOutput.write(buffer, 0, len);
				m_ClientOutput.flush();
			} catch (IOException e) {
				LOGGER.error("Sending data to client");
			}
		}
	}

	public void sendToServer(byte[] buffer, int len) {
		if (m_ServerOutput != null && len > 0 && len <= buffer.length) {
			try {
				m_ServerOutput.write(buffer, 0, len);
				m_ServerOutput.flush();
			} catch (IOException e) {
				LOGGER.error("Sending data to server");
			}
		}
	}

	public boolean isActive() {
		return m_ClientSocket != null && m_ServerSocket != null;
	}

	public void connectToServer(String server, int port) throws IOException {

		if (server.equals("")) {
			close();
			LOGGER.error("Invalid Remote Host Name - Empty String !!!");
			return;
		}

		m_ServerSocket = new Socket(server, port);
		m_ServerSocket.setSoTimeout(SocksConstants.DEFAULT_PROXY_TIMEOUT);

		LOGGER.debug("Connected to " + getSocketInfo(m_ServerSocket));
		prepareServer();
	}

	protected void prepareServer() throws IOException {
		synchronized (m_lock) {
			m_ServerInput = m_ServerSocket.getInputStream();
			m_ServerOutput = m_ServerSocket.getOutputStream();
		}
	}

	public boolean prepareClient() {
		if (m_ClientSocket == null) return false;

		try {
			m_ClientInput = m_ClientSocket.getInputStream();
			m_ClientOutput = m_ClientSocket.getOutputStream();
			return true;
		} catch (IOException e) {
			LOGGER.error("Proxy - can't get I/O streams!");
			LOGGER.error(e.getMessage(), e);
			return false;
		}
	}

	public void processRelay() throws Exception{
			byte SOCKS_Version = getByteFromClient();

			switch (SOCKS_Version) {
				case SocksConstants.SOCKS4_Version:
					comm = new Socks4Impl(this);
					break;
				case SocksConstants.SOCKS5_Version:
					comm = new Socks5Impl(this);
					break;
				default:
					LOGGER.error("Invalid SOKCS version : " + SOCKS_Version);
					return;
			}
			LOGGER.debug("Accepted SOCKS " + SOCKS_Version + " Request.");

			comm.authenticate(SOCKS_Version);
			comm.getClientCommand();

			switch (comm.socksCommand) {
				case SocksConstants.SC_CONNECT:
					comm.connect();
					relay();
					break;

				case SocksConstants.SC_BIND:
                    if (!SocksServer.SUPPORTBIND){
                        throw new UnsupportedOperationException("UnsupportedOperationException SC_BIND");
                    }
					comm.bind();
					relay();
					break;

				case SocksConstants.SC_UDP:
				    if (!SocksServer.SUPPORTBIND){
				        throw new UnsupportedOperationException("UnsupportedOperationException SC_UDP");
                    }
					comm.udp();
					break;
			}
	}

	public byte getByteFromClient() throws Exception {
		while (m_ClientSocket != null) {
			int b;
			try {
				b = m_ClientInput.read();
			} catch (InterruptedIOException e) {
				Thread.yield();
				continue;
			}
			return (byte) b; // return loaded byte
		}
		throw new Exception("Interrupted Reading GetByteFromClient()");
	}

	public void relay() {
        try {
            m_ClientSocket.setSoTimeout(0);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        new Thread(this::relayServer).start();
        int readNumber = 0;
        byte[] buffer = new byte[4096];
		try{
            while ((readNumber=(m_ClientInput.read(buffer)))>0) {
                sendToServer(buffer,readNumber);
            }
        }catch(Exception e){
		    close();
        }
	}
    public void relayServer() {
        int readNumber = 0;
        byte[] buffer = new byte[4096];
        try{
            while ((readNumber=(m_ServerInput.read(buffer)))>0) {
                sendToClient(buffer,readNumber);
            }
        }catch(Exception e){
            close();
        }
    }
    public int checkClientData() {
        synchronized (m_lock) {
            //	The client side is not opened.
            if (m_ClientInput == null) return -1;

            int dlen;

            try {
                dlen = m_ClientInput.read(m_Buffer, 0, SocksConstants.DEFAULT_BUF_SIZE);
            } catch (InterruptedIOException e) {
                return 0;
            } catch (IOException e) {
                LOGGER.debug("Client connection Closed!");
                close();    //	Close the server on this exception
                return -1;
            }

            if (dlen < 0) close();

            return dlen;
        }
    }


	protected void logData(final int traffic, final String dataSource) {
		LOGGER.debug(format("%s : %s >> <%s/%s:%d> : %d bytes.",
				dataSource,
				getSocketInfo(m_ClientSocket),
				"NA",
				comm.m_ServerIP.getHostAddress(),
				comm.m_nServerPort, traffic));
	}

	public int getPort() {
		return m_ServerSocket.getPort();
	}
}
