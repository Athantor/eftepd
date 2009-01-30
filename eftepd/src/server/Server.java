/*
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 ---
 Copyright (C) 2009, Krzysztof Kundzicz <athantor@gmail.com>
 */

/**
 * 
 */
package server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.xml.parsers.ParserConfigurationException;

import logging.XHTMLLogger;
import logging.Logger.Lvl;
import settings.SettingsManager;

/**
 * @author athantor
 * 
 */
public final class Server {

	private final ServerSocket ssock;
	private volatile SettingsManager smngr;
	private XHTMLLogger logger;
	private ClientManager cmngr;

	/**
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * 
	 */
	public Server(SettingsManager sm) throws IOException,
			ParserConfigurationException {
		ssock = new ServerSocket();
		cmngr = new ClientManager();

		smngr = sm;

		XHTMLLogger.setFile(new File(smngr.getTheDir().getAbsolutePath()
				+ File.separatorChar + "eftepd.log.xml"));
		logger = XHTMLLogger.getInstance();
	}

	public void dajesz() throws IOException {
		logger.addMiscMsg(null, "Server starting", Lvl.NOTICE);

		cmngr.start();

		String addr = null;
		int port = 21;
		Integer climit = 50;

		if (smngr.getServerSett().hasProperty("BindPort")) {
			try {
				port = Integer.parseInt(smngr.getServerSett().getProperty(
						"BindPort"));
			} catch (Exception e) {
			}
		}

		try {
			if (smngr.getServerSett().hasProperty("BindAddress")) {
				addr = smngr.getServerSett().getProperty("BindAddress");
				ssock.bind(new InetSocketAddress(addr, port));
			} else {
				ssock.bind(new InetSocketAddress(port));
			}

			logger.addMiscMsg(null, "Bound to address: "
					+ ssock.getInetAddress().toString() + ":"
					+ ssock.getLocalPort(), Lvl.NORMAL);

		} catch (Exception e) {
			logger.addMiscMsg(null, "Failed to bind: "
					+ e.getLocalizedMessage(), Lvl.CRITICAL);
			return;
		}

		if (smngr.getServerSett().hasProperty("ConnectionsLimit")) {
			try {
				climit = Integer.parseInt(smngr.getServerSett().getProperty(
						"ConnectionsLimit"));
			} catch (Exception e) {
			}
		}

		while (true) {

			Socket cs = ssock.accept();

			if (cmngr.getClientsCount() >= climit) {
				logger.addConnectionMsg(cs, "limit exceeded: "
						+ cmngr.getClientsCount(), Lvl.NOTICE);
				cs.getOutputStream().write(
						"421 There is too many clients connected\r\n"
								.getBytes());
				cs.close();
			} else {
				ClientConnection cc = new ClientConnection(cs, logger, smngr);
				cmngr.addClient(cc);
			}
		}
	}

	public void finalize() {
		logger.addMiscMsg(null, "Server exiting", Lvl.NOTICE);
	}
}
