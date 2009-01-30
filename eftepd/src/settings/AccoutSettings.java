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
package settings;

import java.io.File;
import java.io.IOException;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author athantor
 * 
 */
public class AccoutSettings {

	private volatile static TreeMap<String, Account> sett = null;
	private volatile static File settfile = null;
	private volatile static AccoutSettings instance = null;

	public static final String accNameSpace = "http://athantor.mooo.com/ns/eftepd/accounts";

	private AccoutSettings() {
		if (settfile == null) {
			throw new IllegalStateException(
					"Can't create instance b'coz settings file is not set");
		}

		sett = new TreeMap<String, Account>();
	}

	public static void setFile(File f) {
		if (instance != null) {
			throw new IllegalStateException(
					"Can't change file after instantiation");
		}

		if (f == null || !f.exists() || !f.canRead()) {
			throw new IllegalArgumentException("Invalid file or no access: "
					+ f);
		}

		settfile = new File(f.getAbsolutePath());
	}

	public static AccoutSettings getInstance() throws IOException,
			ParserConfigurationException, SAXException {

		if (instance == null) {
			instance = new AccoutSettings();
		}

		readSettings();
		return instance;
	}

	private static void readSettings() throws IOException,
			ParserConfigurationException, SAXException {
		if (!settfile.canRead()) {
			throw new IOException("Can't read history file");
		}

		sett.clear();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setIgnoringComments(true);
		dbf.setValidating(false);
		dbf.setIgnoringElementContentWhitespace(true);
		dbf.setCoalescing(true);

		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(settfile);

		if (doc.getChildNodes().getLength() != 1
				|| doc.getChildNodes().item(0).getNodeName()
						.compareTo("eftepd") != 0) {
			throw new SAXException("rootnode.nodeName != 'eftepd'");
		}

		NodeList nl = doc.getElementsByTagNameNS(accNameSpace, "account");
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);

			String un = null, pw = null;
			Integer mod = 0;
			File hd = null;
			Long q = -2L;

			if (n.getAttributes().getNamedItem("active") == null
					|| n.getAttributes().getNamedItem("active")
							.getTextContent().equalsIgnoreCase("yes")) {
				mod |= Account.Mods.ACTIVE.getMod();
			} else {
				mod &= (~Account.Mods.ACTIVE.getMod());
			}

			for (int j = 0; j < n.getChildNodes().getLength(); j++) {
				Node sn = n.getChildNodes().item(j);

				if (sn.getNodeType() == Node.ELEMENT_NODE) {

					if (sn.getNodeName().compareTo("username") == 0) {
						un = sn.getTextContent().trim();
					}

					if (sn.getNodeName().compareTo("password") == 0) {

						if (sn.getAttributes().getNamedItem("needed") != null
								&& sn.getAttributes().getNamedItem("needed")
										.getTextContent().trim()
										.equalsIgnoreCase("yes")) {

							mod |= Account.Mods.PASSREQ.getMod();
						} else {
							mod &= (~Account.Mods.PASSREQ.getMod());
						}

						pw = sn.getTextContent().trim();
					}

					if (sn.getNodeName().compareTo("homedir") == 0) {
						hd = new File(sn.getTextContent().trim());
					}

					if (sn.getNodeName().compareTo("quota") == 0) {
						try {
							q = Long.parseLong(sn.getTextContent().trim());
						} catch (NumberFormatException e) {
							q = -2L;
						}
					}

				}

			}

			if (un == null || pw == null || hd == null || q == -2L) {
				System.out.println(String.format(
						"**> Invalid account: %s,  %s, %s", un, hd, q));

			} else {

				if (un.length() == 0) {
					un = "anonymous";
					mod |= Account.Mods.ANONYMOUS.getMod();
				}

				if (pw.length() == 0
						&& (mod & Account.Mods.PASSREQ.getMod()) != 0) {
					pw = null;
					System.err
							.println(String
									.format(
											"**> Invalid account [%s]: Password needed but none given",
											un));
					continue;
				}

				if (sett.containsKey(un)) {
					System.out.println(String.format(
							"**> Duplicate account: %s", un));
				} else {

					try {
						Account acc = new Account(un, pw, hd, mod, q);
						sett.put(un, acc);
					} catch (IllegalArgumentException e) {
						System.err.println(String.format(
								"**> Invalid account [%s]: %s", un, e
										.getLocalizedMessage()));
					}

				}

			}

		}

	}

	public void reload() throws IOException, ParserConfigurationException,
			SAXException {
		readSettings();
	}

	public Account getUserAccount(String uname) {
		return sett.get(uname);
	}

	public Boolean hasAccount(String uname) {
		return sett.containsKey(uname);
	}

}
