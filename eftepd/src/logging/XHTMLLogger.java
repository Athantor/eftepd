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
package logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.SAXException;

/**
 * @author athantor
 * 
 */
public class XHTMLLogger implements Logger {

	private static volatile XHTMLLogger instance = null;
	private static volatile Document logdoc = null;
	private static volatile File logfile = null;
	private static volatile Node connel = null, ctlel = null, xfrel = null,
			miscel = null;

	/**
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * 
	 */
	private XHTMLLogger() throws IOException, ParserConfigurationException {
		if (logfile == null) {
			throw new IllegalStateException(
					"Can't create instance b'coz log file is not set");
		}

		checkLogfile();
		saveLog();
	}

	public static void setFile(File f) {
		if (instance != null) {
			throw new IllegalStateException(
					"Can't change file after instantiation");
		}

		if (f == null) {
			throw new IllegalArgumentException("Invalid file: " + f);
		}

		logfile = new File(f.getAbsolutePath());

	}

	public static synchronized XHTMLLogger getInstance() throws IOException,
			ParserConfigurationException {
		if (instance == null) {
			instance = new XHTMLLogger();
		}

		return instance;
	}

	/**
	 * @throws IOException
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * 
	 */
	private static synchronized void checkLogfile() throws IOException,
			ParserConfigurationException {
		if (!logfile.exists()) {
			logfile.createNewFile();
			initDoc();
		} else {
			try {
				parseOldLog();
			} catch (SAXException e) {
				initDoc();
			}
		}
	}

	/**
	 * @throws ParserConfigurationException
	 * @throws ParserConfigurationException
	 * 
	 */
	private static synchronized void initDoc()
			throws ParserConfigurationException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		dbf.setNamespaceAware(false);
		dbf.setIgnoringComments(false);
		dbf.setValidating(false);
		dbf.setIgnoringElementContentWhitespace(false);
		dbf.setCoalescing(false);

		logdoc = dbf.newDocumentBuilder().newDocument();

		ProcessingInstruction pi = logdoc.createProcessingInstruction(
				"xml-stylesheet", "type=\"text/css\" href=\"log.css\"");

		logdoc.appendChild(pi);

		Element html = logdoc.createElementNS("http://www.w3.org/1999/xhtml",
				"html");
		Element head = logdoc.createElement("head");
		Element title = logdoc.createElement("title");
		Element body = logdoc.createElement("body");

		title.setTextContent("eftepd — log");

		head.appendChild(title);

		html.appendChild(head);
		html.appendChild(body);

		logdoc.appendChild(html);

		initTree();

	}

	/**
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 * 
	 */
	private static synchronized void parseOldLog()
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		dbf.setNamespaceAware(false);
		dbf.setIgnoringComments(false);
		dbf.setValidating(false);
		dbf.setIgnoringElementContentWhitespace(false);
		dbf.setCoalescing(false);

		DocumentBuilder db = dbf.newDocumentBuilder();
		logdoc = db.parse(logfile);

		if (logdoc.getElementsByTagName("body").getLength() != 1) {
			throw new SAXException(
					String
							.format(
									"There is/are %s '<body>' elements in doc; should be just one",
									logdoc.getElementsByTagName("body")
											.getLength()));
		}

		NodeList nl = logdoc.getElementsByTagName("body").item(0)
				.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node n = nl.item(i);

			if (n.getNodeType() == Node.ELEMENT_NODE
					&& n.getNodeName().compareTo("table") == 0
					&& n.getAttributes().getNamedItem("id") != null) {
				String id = n.getAttributes().getNamedItem("id")
						.getTextContent();

				if (id.compareTo("connlogtbl") == 0) {
					connel = n;
				} else if (id.compareTo("controllogtbl") == 0) {
					ctlel = n;
				} else if (id.compareTo("xfrlogtbl") == 0) {
					xfrel = n;
				} else if (id.compareTo("misclogtbl") == 0) {
					miscel = n;
				}

			}
		}

		if (connel == null || ctlel == null || xfrel == null || miscel == null) {
			System.err
					.println("**> Invalid log format; required tables not found; creating new ones");

			initTree();
		}

	}

	/**
	 * 
	 */
	private static synchronized void initTree() {

		Node body = logdoc.getElementsByTagName("body").item(0);
		Element titel;

		if (connel == null) {

			Element ce = logdoc.createElement("table");
			ce.setAttribute("id", "connlogtbl");
			ce.setAttribute("class", "logtable");
			ce.appendChild(logdoc.createComment("connections log"));

			titel = logdoc.createElement("h1");
			titel.setTextContent("Connections");
			body.appendChild(titel);

			body.appendChild(ce);
			connel = ce;
		}

		if (ctlel == null) {
			Element te = logdoc.createElement("table");
			te.setAttribute("id", "controllogtbl");
			te.setAttribute("class", "logtable");
			te.appendChild(logdoc.createComment("control connection log"));

			titel = logdoc.createElement("h1");
			titel.setTextContent("Control");
			body.appendChild(titel);

			body.appendChild(te);
			ctlel = te;
		}

		if (xfrel == null) {
			Element xe = logdoc.createElement("table");
			xe.setAttribute("id", "xfrlogtbl");
			xe.setAttribute("class", "logtable");
			xe.appendChild(logdoc.createComment("transfers log"));

			titel = logdoc.createElement("h1");
			titel.setTextContent("Transfer");
			body.appendChild(titel);

			body.appendChild(xe);
			xfrel = xe;
		}

		if (miscel == null) {
			Element me = logdoc.createElement("table");
			me.setAttribute("id", "misclogtbl");
			me.setAttribute("class", "logtable");
			me.appendChild(logdoc.createComment("misc log"));

			titel = logdoc.createElement("h1");
			titel.setTextContent("Misc");
			body.appendChild(titel);

			body.appendChild(me);
			miscel = me;

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see logging.Logger#addConnectionMsg(java.net.Socket, java.lang.String)
	 */
	@Override
	public synchronized void addConnectionMsg(Socket csock, String msg, Lvl l) {

		if (csock == null && msg == null) {
			return;
		}

		try {
			checkLogfile();
		} catch (Exception e) {
			System.err.println("**> Can't reread log: "
					+ e.getLocalizedMessage());
		}

		addMsg(connel, l, new Date().toString(), msg, csock.getInetAddress()
				.getHostName(), csock.getInetAddress().getHostAddress(), String
				.valueOf(csock.getPort()));

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see logging.Logger#addCtlMsg(java.net.Socket, java.lang.String)
	 */
	@Override
	public synchronized void addCtlMsg(Socket csock, String msg, Lvl l) {
		if (csock == null && msg == null) {
			return;
		}

		try {
			checkLogfile();
		} catch (Exception e) {
			System.err.println("**> Can't reread log: "
					+ e.getLocalizedMessage());
		}

		addMsg(ctlel, l, new Date().toString(), csock.getInetAddress()
				.getHostName(), csock.getInetAddress().getHostAddress(), String
				.valueOf(csock.getPort()), msg);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see logging.Logger#addXfrMsg(java.net.Socket, java.lang.String)
	 */
	@Override
	public synchronized void addXfrMsg(Socket csock, String msg, Lvl l) {
		if (csock == null && msg == null) {
			return;
		}

		try {
			checkLogfile();
		} catch (Exception e) {
			System.err.println("**> Can't reread log: "
					+ e.getLocalizedMessage());
		}
		
		addMsg(xfrel, l, new Date().toString(), csock.getInetAddress()
				.getHostName(), csock.getInetAddress().getHostAddress(), String
				.valueOf(csock.getPort()), msg);

	}

	/**
	 * @param tbl
	 *            Table to write to
	 * @param cells
	 *            cells to write
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	private synchronized void addMsg(Node tbl, Lvl l, String... cells) {

		Element tr = logdoc.createElement("tr");

		switch (l) {
		case CRITICAL:
			tr.setAttribute("class", "loglvlcritical");
			break;
		case ERROR:
			tr.setAttribute("class", "loglvlerror");
			break;
		case WARNING:
			tr.setAttribute("class", "loglvlwarning");
			break;
		case NOTICE:
			tr.setAttribute("class", "loglvlnotice");
			break;
		case NORMAL:
		default:
			// tr.setAttribute("class", "log");
			break;
		}

		tr.setAttribute("id", "id-" + UUID.randomUUID().toString());

		for (String s : cells) {
			Element ttd = logdoc.createElement("td");
			// ttd.setAttribute("id", "id-" + UUID.randomUUID().toString());
			ttd.setTextContent(s.replaceAll("&", "&amp;").replaceAll("<",
					"&lt;").replaceAll(">", "&gt").replaceAll("\"", "&quot;")
					.replaceAll("\\p{Cntrl}", "\ufffd"));

			tr.appendChild(ttd);
		}

		tbl.appendChild(tr);

		saveLog();

	}

	public synchronized void saveLog() {
		try {

			FileWriter fw = new FileWriter(logfile, false);

			TransformerFactory.newInstance().newTransformer().transform(
					new DOMSource(logdoc), new StreamResult(fw));

			fw.flush();
			fw.close();
		} catch (Exception e) {
			System.err
					.println("**> Can't save log: " + e.getLocalizedMessage());
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see logging.Logger#addMiscMsg(java.net.Socket, java.lang.String)
	 */
	@Override
	public synchronized void addMiscMsg(Socket csock, String msg, Lvl l) {

		if (msg == null) {
			msg = "";
		}

		addMsg(miscel, l, new Date().toString(), msg);
	}

}
