/**
 
 */
package com.pathomation;

import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import javax.swing.filechooser.FileSystemView;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <h1>Java SDK</h1>
 * <p>
 * Java wrapper library for PMA.start, a universal viewer for whole slide
 * imaging and microscopy
 * </p>
 * 
 * @author Yassine Iddaoui
 * @version 2.0.0.47
 */
public class Core {
	private static Map<String, Object> pmaSessions = new HashMap<String, Object>();
	private static Map<String, Object> pmaSlideInfos = new HashMap<String, Object>();
	private static final String pmaCoreLiteURL = "http://localhost:54001/";
	private static final String pmaCoreLiteSessionID = "SDK.Java";
	private static Boolean pmaUseCacheWhenRetrievingTiles = true;
	@SuppressWarnings("serial")
	private static Map<String, Integer> pmaAmountOfDataDownloaded = new HashMap<String, Integer>() {
		{
			put(pmaCoreLiteSessionID, 0);
		}
	};
	// for logging purposes
	public static Logger logger = null;
	// To store the Disk labels
	private static Map<String, String> diskLabels = new HashMap<String, String>();
	// Object Mapper for Jackson library
	private static ObjectMapper objectMapper = new ObjectMapper();
	private static Map<String, String> urlContent = new HashMap<>();

	/**
	 * This method is used to get the session's ID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return The same sessionID if explicited, otherwise it recovers a session's
	 *         ID
	 */
	private static String sessionId(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		if (sessionID == null) {
			// if the sessionID isn't specified, maybe we can still recover it somehow
			return firstSessionId();
		} else {
			// nothing to do in this case; a SessionID WAS passed along, so just continue
			// using it
			return sessionID;
		}
	}

	/**
	 * This method is used to get PMA.core active session
	 * 
	 * @return PMA.core active session
	 */
	private static String firstSessionId() {
		// do we have any stored sessions from earlier login events?
		if (pmaSessions.size() > 0) {
			// yes we do! This means that when there's a PMA.core active session AND
			// PMA.core.lite version running,
			// the PMA.core active will be selected and returned
			return pmaSessions.keySet().toArray()[0].toString();
		} else {
			// ok, we don't have stored sessions; not a problem per se...
			if (pmaIsLite()) {
				if (!pmaSlideInfos.containsKey(pmaCoreLiteSessionID)) {
					pmaSlideInfos.put(pmaCoreLiteSessionID, new HashMap<String, Object>());
				}
				if (!pmaAmountOfDataDownloaded.containsKey(pmaCoreLiteSessionID)) {
					pmaAmountOfDataDownloaded.put(pmaCoreLiteSessionID, 0);
				}
				return pmaCoreLiteSessionID;
			} else {
				// no stored PMA.core sessions found NOR PMA.core.lite
				return null;
			}
		}
	}

	/**
	 * This method is used to get the url related to the session's ID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Url related to the session's ID
	 * @throws Exception if sessionID is invalid
	 */
	private static String pmaUrl(String... varargs) throws Exception {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		sessionID = sessionId(sessionID);
		if (sessionID == null) {
			// sort of a hopeless situation; there is no URL to refer to
			return null;
		} else if (sessionID.equals(pmaCoreLiteSessionID)) {
			return pmaCoreLiteURL;
		} else {
			// assume sessionID is a valid session; otherwise the following will generate an
			// error
			if (pmaSessions.containsKey(sessionID)) {
				String url = pmaSessions.get(sessionID).toString();
				if (!url.endsWith("/")) {
					url = url + "/";
				}
				return url;
			} else {
				if (logger != null) {
					logger.severe("Invalid sessionID:" + sessionID);
				}
				throw new Exception("Invalid sessionID:" + sessionID);
			}
		}
	}

	/**
	 * This method is used to retrieve HTML Code from URL
	 * 
	 * @param url to get HTML code from
	 * @return HTML code generated from the url argument
	 */
	public static String urlReader(String url) {
		try {
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			InputStream in = con.getInputStream();
			String encoding = con.getContentEncoding();
			encoding = encoding == null ? "UTF-8" : encoding;
			return IOUtils.toString(in, encoding);
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to parse a XML content
	 * 
	 * @param s XML content to parse
	 * @return Document parsed XML
	 */
	public static Document domParser(String s) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = factory.newDocumentBuilder();
			InputSource inputStream = new InputSource();
			inputStream.setCharacterStream(new StringReader(s));
			return documentBuilder.parse(inputStream);
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to check if an instance of PMA.core is running (by
	 * checking the existence of value "true" in a XML file)
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmaCoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 * @return True if an instance of PMA.core is running, false otherwise
	 */
	private static Boolean pmaIsLite(String... varargs) {
		// setting the default value when argument's value is omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		String url = join(pmaCoreURL, "api/xml/IsLite");
		String contents = "";
		try {
			contents = urlReader(url);
			return domParser(contents).getChildNodes().item(0).getChildNodes().item(0).getNodeValue().toLowerCase()
					.toString().equals("true");
		} catch (Exception e) {
			// this happens when NO instance of PMA.core is detected
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to define which content will be received "XML" or "Json"
	 * for "API" Web service calls
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 *                <p>
	 *                xml : Second optional argument(Boolean), default value(true),
	 *                define if method will return XML or Json content
	 *                </p>
	 * @return Add a sequence to the url to specify which content to be received
	 *         (XML or Json)
	 */
	private static String apiUrl(Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Boolean xml = true;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("apiUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("apiUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			xml = (Boolean) varargs[1];
		}
		// let's get the base URL first for the specified session
		String url;
		try {
			url = pmaUrl(sessionID);
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			url = null;
		}
		if (url == null) {
			// sort of a hopeless situation; there is no URL to refer to
			return null;
		}
		// remember, _pma_url is guaranteed to return a URL that ends with "/"
		if (xml) {
			return join(url, "api/xml/");
		} else {
			return join(url, "api/json/");
		}
	}

	/**
	 * This method is used to define which content will be received "XML" or "Json"
	 * for "Admin" Web service calls
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 *                <p>
	 *                xml : Second optional argument(Boolean), default value(true),
	 *                define if method will return XML or Json content
	 *                </p>
	 * @return Adds sequence to the url to specify which content to be received (XML
	 *         or Json)
	 */
	private static String adminUrl(Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Boolean xml = true;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("adminUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("adminUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			xml = (Boolean) varargs[1];
		}
		// let's get the base URL first for the specified session
		String url;
		try {
			url = pmaUrl(sessionID);
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			url = null;
		}
		if (url == null) {
			// sort of a hopeless situation; there is no URL to refer to
			return null;
		}
		// remember, _pma_url is guaranteed to return a URL that ends with "/"
		if (xml) {
			return join(url, "admin/xml/");
		} else {
			return join(url, "admin/json/");
		}
	}

	/**
	 * This method is used to get a list of the values of "String" tags of a XML
	 * document
	 * 
	 * @param root    XML document
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                limit : First optional argument(int), default value(0),
	 *                defines a limit
	 *                </p>
	 * @return Values' list of tags named "string" in a XML document
	 */
	private static List<String> xmlToStringArray(Document root, Integer... varargs) {
		// setting the default value when argument's value is omitted
		int limit = varargs.length > 0 ? varargs[0] : 0;
		NodeList eLs = root.getElementsByTagName("string");
		List<String> l = new ArrayList<>();
		if (limit > 0) {
			for (int i = 0; i < limit; i++) {
				l.add(eLs.item(i).getFirstChild().getNodeValue());
			}
		} else {
			for (int i = 0; i < eLs.getLength(); i++) {
				l.add(eLs.item(i).getFirstChild().getNodeValue());
			}
		}
		return l;
	}

	/**
	 * This method is an overload of method xmlToStringArray to cope with "root"
	 * argument as an "Element" instead of a "Document"
	 * 
	 * @param root    XML document
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                limit : First optional argument(int), default value(0),
	 *                defines a limit
	 *                </p>
	 * @return Values' list of tags named "string" in a XML document
	 */
	private static List<String> xmlToStringArray(Element root, Integer... varargs) {
		// setting the default value when argument's value is omitted
		int limit = varargs.length > 0 ? varargs[0] : 0;
		NodeList eLs = root.getElementsByTagName("string");
		List<String> l = new ArrayList<>();
		if (limit > 0) {
			for (int i = 0; i < limit; i++) {
				l.add(eLs.item(i).getFirstChild().getNodeValue());
			}
		} else {
			for (int i = 0; i < eLs.getLength(); i++) {
				l.add(eLs.item(i).getFirstChild().getNodeValue());
			}
		}
		return l;
	}

	/**
	 * This method is used to cache requested URLs
	 * 
	 * @param url      URL to request
	 * @param property Header value
	 * @return Data returned following a request to a specific URL
	 */
	public static String httpGet(String url, String property) {
		if (!urlContent.containsKey(url)) {
			try {
				URL urlResource = new URL(url);
				HttpURLConnection con;
				if (url.startsWith("https")) {
					con = (HttpsURLConnection) urlResource.openConnection();
				} else {
					con = (HttpURLConnection) urlResource.openConnection();
				}
				con.setRequestMethod("GET");
				con.setRequestProperty("Accept", property);
				urlContent.put(url, Core.getJSONAsStringBuffer(con).toString());
			} catch (Exception e) {
				e.printStackTrace();
				if (logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					logger.severe(sw.toString());
				}
				return null;
			}
		}
		return urlContent.get(url).toString();
	}

	/**
	 * This method is used to clear the URLs cache
	 */
	public static void clearURLCache() {
		urlContent = new HashMap<>();
	}

	/**
	 * This method is used to concatenate a couple of Strings while replacing "\\"
	 * by "/"
	 * 
	 * @param varargs Array of String optional arguments, each argument is a string
	 *                to be concatenated
	 * @return Concatenation of a couple of String while making sure the first
	 *         string always ends with "/"
	 */
	public static String join(String... varargs) {
		String joinString = "";
		for (String ss : varargs) {
			if (!joinString.endsWith("/") && (!joinString.equals(""))) {
				joinString = joinString.concat("/");
			}
			if (ss != null) {
				joinString = joinString.concat(ss);
			}
		}
		return joinString;
	}

	/**
	 * This method is used to encode a String to be compatible as a url
	 * 
	 * @param arg string to be encoded
	 * @return Encoded String to be compatible as a url
	 */
	public static String pmaQ(String arg) {
		if (arg == null) {
			return "";
		} else {
			try {
				return URLEncoder.encode(arg, "UTF-8").replace("+", "%20");
			} catch (Exception e) {
				e.printStackTrace();
				if (logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					logger.severe(sw.toString());
				}
				return "";
			}
		}
	}

	/**
	 * This method is used to check if there is a PMA.core.lite or PMA.core instance
	 * running
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmaCoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 * @return Checks if there is a PMA.core.lite or PMA.core instance running
	 */
	public static Boolean isLite(String... varargs) {
		// setting the default value when argument's value is omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		// See if there's a PMA.core.lite or PMA.core instance running at pmacoreURL
		return pmaIsLite(pmaCoreURL);
	}

	/**
	 * This method is used to get the version number
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmaCoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 * @return Version number
	 */
	public static String getVersionInfo(String... varargs) {
		// setting the default value when argument's value is omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		// Get version info from PMA.core instance running at pmacoreURL
		// purposefully DON'T use helper function apiUrl() here:
		// why? because GetVersionInfo can be invoked WITHOUT a valid SessionID;
		// apiUrl() takes session information into account
		String url = join(pmaCoreURL, "api/xml/GetVersionInfo");
		String contents = "";
		try {
			contents = urlReader(url);
			return domParser(contents).getChildNodes().item(0).getChildNodes().item(0).getNodeValue().toString();
		} catch (Exception e) {
			// this happens when NO instance of PMA.core is detected
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to authenticate &amp; connect to a PMA.core instance
	 * using credentials
	 *
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmacoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 *                <p>
	 *                pmacoreUsername : Second optional argument(String), default
	 *                value(""), username for PMA.core instance
	 *                </p>
	 *                <p>
	 *                pmacorePassword : Third optional argument(String), default
	 *                value(""), password for PMA.core instance
	 *                </p>
	 * @return session's ID if session was created successfully, otherwise null
	 */
	public static String connect(String... varargs) {
		// setting the default values when arguments' values are omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		String pmaCoreUsername = varargs.length > 1 ? varargs[1] : "";
		String pmaCorePassword = varargs.length > 2 ? varargs[2] : "";
		// Attempt to connect to PMA.core instance; success results in a SessionID
		if (pmaCoreURL.equals(pmaCoreLiteURL)) {
			if (isLite()) {
				// no point authenticating localhost / PMA.core.lite
				return pmaCoreLiteSessionID;
			} else {
				return null;
			}
		}
		// purposefully DON'T use helper function apiUrl() here:
		// why? Because apiUrl() takes session information into account (which we
		// don't have yet)
		String url = join(pmaCoreURL, "api/xml/authenticate?caller=SDK.Java");
		if (!pmaCoreUsername.equals("")) {
			url = url.concat("&username=").concat(pmaQ(pmaCoreUsername));
		}
		if (!pmaCorePassword.equals("")) {
			url = url.concat("&password=").concat(pmaQ(pmaCorePassword));
		}
		String contents;
		Document dom;
		try {
			contents = urlReader(url);
			dom = domParser(contents);
		} catch (Exception e) {
			// Something went wrong; unable to communicate with specified endpoint
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
		try {
			Element loginResult = (Element) dom.getChildNodes().item(0);
			Node succ = loginResult.getElementsByTagName("Success").item(0);
			String sessionID;
			if (succ.getFirstChild().getNodeValue().toLowerCase().equals("false")) {
				sessionID = null;
			} else {
				sessionID = loginResult.getElementsByTagName("SessionId").item(0).getFirstChild().getNodeValue()
						.toString();
				pmaSessions.put(sessionID, pmaCoreURL);
				pmaSlideInfos.put(sessionID, new HashMap<String, Object>());
				pmaAmountOfDataDownloaded.put(sessionID, contents.length());
			}
			return sessionID;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to authenticate &amp; connect as an admin to a PMA.core
	 * instance using admin credentials
	 *
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                pmacoreURL : First optional argument(String), default
	 *                value(Class field pmaCoreLiteURL), url of PMA.core instance
	 *                </p>
	 *                <p>
	 *                pmacoreUsername : Second optional argument(String), default
	 *                value(""), username for PMA.core instance
	 *                </p>
	 *                <p>
	 *                pmacorePassword : Third optional argument(String), default
	 *                value(""), password for PMA.core instance
	 *                </p>
	 * @return session's ID if session was created successfully, otherwise null
	 */
	public static String adminConnect(String... varargs) {
		// setting the default values when arguments' values are omitted
		String pmaCoreURL = varargs.length > 0 ? varargs[0] : pmaCoreLiteURL;
		String pmaCoreUsername = varargs.length > 1 ? varargs[1] : "";
		String pmaCorePassword = varargs.length > 2 ? varargs[2] : "";
		// Attempt to connect to PMA.core instance; success results in a SessionID
		if (pmaCoreURL.equals(pmaCoreLiteURL)) {
			if (isLite()) {
				// no point authenticating localhost / PMA.core.lite
				return pmaCoreLiteSessionID;
			} else {
				return null;
			}
		}
		// purposefully DON'T use helper function apiUrl() here:
		// why? Because apiUrl() takes session information into account (which we
		// don't have yet)
		String url = join(pmaCoreURL, "admin/json/AdminAuthenticate?caller=SDK.Java");
		if (!pmaCoreUsername.equals("")) {
			url = url.concat("&username=").concat(pmaQ(pmaCoreUsername));
		}
		if (!pmaCorePassword.equals("")) {
			url = url.concat("&password=").concat(pmaQ(pmaCorePassword));
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (isJSONObject(jsonString)) {
				JSONObject jsonResponse = getJSONResponse(jsonString);
				if (jsonResponse.getBoolean("Success")) {
					String sessionID = jsonResponse.getString("SessionId");
					pmaSessions.put(sessionID, pmaCoreURL);
					pmaSlideInfos.put(sessionID, new HashMap<String, Object>());
					pmaAmountOfDataDownloaded.put(sessionID, jsonResponse.length());
					return sessionID;
				} else {
					return null;
				}
			} else {
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to disconnect from a running PMA.core instance
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return true if there was a PMA.core instance running to disconnect from,
	 *         false otherwise
	 */
	public static Boolean disconnect(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Disconnect from a PMA.core instance; return True if session exists; return
		// False if session didn't exist (anymore)
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID) + "DeAuthenticate?sessionID=" + pmaQ((sessionID));
		String contents = urlReader(url);
		pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + contents.length());
		if (pmaSessions.size() > 0) {
			// yes we do! This means that when there's a PMA.core active session AND
			// PMA.core.lite version running,
			// the PMA.core active will be selected and returned
			pmaSessions.remove(sessionID);
			pmaSlideInfos.remove(sessionID);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Getter for Class field pmaCoreLiteSessionID
	 * 
	 * @return value of Class field pmaCoreLiteSessionID
	 */
	public static String getPmaCoreLiteSessionID() {
		return pmaCoreLiteSessionID;
	}

	/**
	 * Getter for Class field pmaCoreLiteURL
	 * 
	 * @return value of Class field pmaCoreLiteURL
	 */
	public static String getPmaCoreLiteUrl() {
		return pmaCoreLiteURL;
	}

	/**
	 * This method is used to test if sessionID is valid and the server is online
	 * and reachable This method works only for PMA.core, don't use it for PMA.start
	 * for it will return always false
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return true if sessionID is valid and the server is online and reachable,
	 *         false otherwise
	 */
	public static boolean ping(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID, false) + "Ping?sessionID=" + pmaQ(sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			con.setRequestProperty("Accept", "application/json");
			String jsonString = Core.getJSONAsStringBuffer(con).toString();
			return jsonString.equals("true") ? true : false;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return false;
		}
	}

	/**
	 * This method is used to get root-directories available for a sessionID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Array of root-directories available to a session's ID
	 */
	public static List<String> getRootDirectories(String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Return a list of root-directories available to sessionID
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID) + "GetRootDirectories?sessionID=" + pmaQ(sessionID);
		String contents = urlReader(url);
		pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + contents.length());
		Document dom = domParser(contents);
		return xmlToStringArray((Element) dom.getFirstChild());
	}

	/**
	 * This method is used to create a new directory on PMA.core
	 * 
	 * @param sessionID a session ID
	 * @param path      path to create the new directory in
	 * @return true if directory was created successfully, false otherwise
	 */
	public static boolean createDirectory(String sessionID, String path) {
		try {
			// we only create folders on PMA.core
			if (isLite(pmaUrl(sessionID))) {
				return false;
			} else {
				String url = adminUrl(sessionID, false) + "CreateDirectory";
				URL urlResource = new URL(url);
				HttpURLConnection con;
				if (url.startsWith("https")) {
					con = (HttpsURLConnection) urlResource.openConnection();
				} else {
					con = (HttpURLConnection) urlResource.openConnection();
				}
				con.setRequestMethod("POST");
				con.setRequestProperty("Content-Type", "application/json");
				con.setUseCaches(false);
				con.setDoOutput(true);
				String input = "{ \"sessionID\": \"" + sessionID + "\", \"path\": \"" + path + "\" }";
				OutputStream os = con.getOutputStream();
				os.write(input.getBytes("UTF-8"));
				os.close();
				String jsonString = Core.getJSONAsStringBuffer(con).toString();
				return jsonString.equals("true") ? true : false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return false;
		}
	}

	/**
	 * This method is used to get sub-directories available to sessionID in the
	 * start directory following a recursive (or not) approach
	 * 
	 * @param startDir Start directory
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 recursivity : Second optional argument(Boolean or Integer),
	 *                 default value(Boolean, false), if it's a Boolean if defines
	 *                 either no recursivity or a limitless recursivity, if it's an
	 *                 Integer it defines a limited in depth recursivity or no
	 *                 recursivity at all if this Integer equals 0
	 *                 </p>
	 * @return Sub-directories available to a session's ID in a start directory
	 */
	public static List<String> getDirectories(String startDir, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		// we can either choose to have a non recursive call, a complete recursive call
		// or a recursive call to a certain depth, in the last case we use an integer to
		// define
		// depth
		// the following three variables intend to implement this
		Boolean recursive = false;
		Integer integerRecursive = 0;
		String booleanOrInteger = "";
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getDirectories() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if ((!(varargs[1] instanceof Integer) && !(varargs[1] instanceof Boolean)) && (varargs[1] != null)) {
				if (logger != null) {
					logger.severe("getDirectories() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			if (varargs[1] instanceof Boolean) {
				recursive = (Boolean) varargs[1];
				booleanOrInteger = "boolean";
			}
			if (varargs[1] instanceof Integer) {
				integerRecursive = (Integer) varargs[1];
				recursive = ((Integer) varargs[1]) > 0 ? true : false;
				booleanOrInteger = "integer";
			}
		}

		// Return a list of sub-directories available to sessionID in the startDir
		// directory
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID, false) + "GetDirectories?sessionID=" + pmaQ(sessionID) + "&path="
				+ pmaQ(startDir);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			List<String> dirs;
			if (isJSONObject(jsonString)) {
				JSONObject jsonResponse = getJSONResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (logger != null) {
						logger.severe("get_directories to " + startDir + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("get_directories to " + startDir + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					dirs = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						dirs.add(array.optString(i));
					}
					// return dirs;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				dirs = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					dirs.add(jsonResponse.optString(i));
				}
				// return dirs;
			}

			// we test if call is recursive, and if yes to which depth
			if (recursive) {
				for (String dir : getDirectories(startDir, sessionID)) {
					if (booleanOrInteger.equals("boolean")) {
						dirs.addAll(getDirectories(dir, sessionID, recursive));
					}
					if (booleanOrInteger.equals("integer")) {
						dirs.addAll(getDirectories(dir, sessionID, integerRecursive - 1));
					}
				}
			}
			return dirs;

		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get the first non empty directory
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                startDir : First optional argument(String), default
	 *                value(null), start directory
	 *                </p>
	 *                <p>
	 *                sessionID : Second optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Path to the first non empty directory found
	 */
	public static String getFirstNonEmptyDirectory(String... varargs) {
		// setting the default values when arguments' values are omitted
		String startDir = varargs.length > 0 ? varargs[0] : null;
		String sessionID = varargs.length > 1 ? varargs[1] : null;
		if ((startDir == null) || (startDir.equals(""))) {
			startDir = "/";
		}
		List<String> slides = getSlides(startDir, sessionID);
		if (slides.size() > 0) {
			return startDir;
		} else {
			if (startDir.equals("/")) {
				for (String dir : getRootDirectories(sessionID)) {
					String nonEmptyDir = getFirstNonEmptyDirectory(dir, sessionID);
					if (nonEmptyDir != null) {
						return nonEmptyDir;
					}
				}
			} else {
				for (String dir : getDirectories(startDir, sessionID)) {
					String nonEmptyDir = getFirstNonEmptyDirectory(dir, sessionID);
					if (nonEmptyDir != null) {
						return nonEmptyDir;
					}
				}
			}
		}
		return null;
	}

	/**
	 * This method is used to get a list of slides available to sessionID in the
	 * start directory following a recursive (or not) approach
	 * 
	 * @param startDir Start directory
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 recursivity : Second optional argument(Boolean or Integer),
	 *                 default value(Boolean, false), if it's a Boolean if defines
	 *                 either no recursivity or a limitless recursivity, if it's an
	 *                 Integer it defines a limited in depth recursivity or no
	 *                 recursivity at all if this Integer equals 0
	 *                 </p>
	 * @return List of slides available to a session's ID in a start directory
	 */
	public static List<String> getSlides(String startDir, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		// we can either choose to have a non recursive call, a complete recursive call
		// or a recursive call to a certain depth, in the last case we use an integer to
		// define
		// depth
		// the following three variables intend to implement this
		String booleanOrInteger = "";
		Boolean recursive = false;
		Integer integerRecursive = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getSlides() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if ((!(varargs[1] instanceof Integer) && !(varargs[1] instanceof Boolean)) && (varargs[1] != null)) {
				if (logger != null) {
					logger.severe("getSlides() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			if (varargs[1] instanceof Boolean) {
				recursive = (Boolean) varargs[1];
				booleanOrInteger = "boolean";
			}
			if (varargs[1] instanceof Integer) {
				integerRecursive = (Integer) varargs[1];
				recursive = ((Integer) varargs[1]) > 0 ? true : false;
				booleanOrInteger = "integer";
			}
		}

		// Return a list of slides available to sessionID in the startDir directory
		sessionID = sessionId(sessionID);
		if (startDir.startsWith("/")) {
			startDir = startDir.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFiles?sessionID=" + pmaQ(sessionID) + "&path=" + pmaQ(startDir);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			List<String> slides;
			if (isJSONObject(jsonString)) {
				JSONObject jsonResponse = getJSONResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (logger != null) {
						logger.severe("get_slides from " + startDir + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that startDir is case sensitive!)");
					}
					throw new Exception("get_slides from " + startDir + " resulted in: " + jsonResponse.get("Message")
							+ " (keep in mind that startDir is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					slides = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						slides.add(array.optString(i));
					}
					// return slides;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				slides = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					slides.add(jsonResponse.optString(i));
				}
				// return slides;
			}

			// we test if call is recursive, and if yes to which depth
			if (recursive) {
				for (String dir : getDirectories(startDir, sessionID)) {
					if (booleanOrInteger.equals("boolean")) {
						slides.addAll(getSlides(dir, sessionID, recursive));
					}
					if (booleanOrInteger.equals("integer")) {
						slides.addAll(getSlides(dir, sessionID, integerRecursive - 1));
					}
				}
			}
			return slides;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to determine the file extension for a slide's path
	 * 
	 * @param slideRef slide's path
	 * @return File extension extracted from a slide's path
	 */
	public static String getSlideFileExtension(String slideRef) {
		// Determine the file extension for this slide
		return FilenameUtils.getExtension(slideRef);
	}

	/**
	 * This method is used to determine file name (with extension) for a slide's
	 * path
	 * 
	 * @param slideRef slide's path
	 * @return File name extracted from a slide's path
	 */
	public static String getSlideFileName(String slideRef) {
		// Determine the file name (with extension) for this slide
		return FilenameUtils.getName(slideRef);
	}

	/**
	 * This method is used to get the UID for a defined slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return UID for a defined slide's path
	 */
	public static String getUid(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the UID for a specific slide
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID) + "GetUID?sessionID=" + pmaQ(sessionID) + "&path=" + pmaQ(slideRef);
		String contents = urlReader(url);
		pmaAmountOfDataDownloaded.put(sessionID, pmaAmountOfDataDownloaded.get(sessionID) + contents.length());
		Document dom = domParser(contents);
		return xmlToStringArray(dom).get(0);
	}

	/**
	 * This method is used to get the fingerprint for a specific slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 strict : First optional argument(Boolean), default
	 *                 value(false), loose fingerprint if false, strict fingerprint
	 *                 if true
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Second optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Fingerprint of the slide
	 */
	public static String getFingerPrint(String slideRef, Object... varargs) {
		// Get the fingerprint for a specific slide
		Boolean strict = false;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Boolean) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getFingerPrint() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			strict = (Boolean) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getFingerPrint() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[1];
		}
		// Get the fingerprint for a specific slide
		sessionID = sessionId(sessionID);
		String fingerprint;
		String url = apiUrl(sessionID, false) + "GetFingerprint?sessionID=" + pmaQ(sessionID) + "&strict="
				+ pmaQ(strict.toString()) + "&pathOrUid=" + pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (isJSONObject(jsonString)) {
				JSONObject jsonResponse = getJSONResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (logger != null) {
						logger.severe("get_fingerprint on " + slideRef + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("get_fingerprint on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else {
					return jsonResponse.getString("d");
				}
			} else {
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonString.length());
				fingerprint = jsonString.replace("\"", "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
		return fingerprint;
	}

	/**
	 * This method is under construction
	 * 
	 * @return Information about session (Under construction)
	 */
	public static String whoAmI() {
		// Getting information about your Session (under construction)
		System.out.println("Under construction");
		return "Under construction";
	}

	/**
	 * This method is a getter for class field pmaSessions
	 * 
	 * @return Value of class field pmaSessions
	 */
	public static Map<String, Object> sessions() {
		return pmaSessions;
	}

	/**
	 * This method is used to get tile size information for sessionID
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return A list of two items (duplicated) relative to the tile size
	 *         information for a session's ID
	 */
	@SuppressWarnings("unchecked")
	public static List<Integer> getTileSize(String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		sessionID = sessionId(sessionID);
		Map<String, Object> info;
		if (((Map<String, Object>) pmaSlideInfos.get(sessionID)).size() < 1) {
			String dir = getFirstNonEmptyDirectory(sessionID);
			List<String> slides = getSlides(dir, sessionID);
			info = getSlideInfo(slides.get(0), sessionID);
		} else {
			int getLength = ((Map<String, Object>) pmaSlideInfos.get(sessionID)).values().toArray().length;
			info = (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).values()
					.toArray()[new Random().nextInt(getLength)];
		}
		List<Integer> result = new ArrayList<>();
		result.add(Integer.parseInt(info.get("TileSize").toString()));
		result.add(Integer.parseInt(info.get("TileSize").toString()));
		return result;
	}

	/**
	 * This method is used to create a StringBuffer from a connection URL
	 * 
	 * @param con http connection URL to retrieve JSON from
	 * @return string buffer created from the connection URL
	 */
	public static StringBuffer getJSONAsStringBuffer(HttpURLConnection con) {
		try {
			BufferedReader in;
			if (Integer.toString(con.getResponseCode()).startsWith("2")) {
				in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
			} else {
				in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
			}
			String inputline;
			StringBuffer response = new StringBuffer();
			while ((inputline = in.readLine()) != null) {
				response.append(inputline);
			}
			in.close();
			return response;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to check if a json returned is an object
	 * 
	 * @param value json in String format
	 * @return True if it's a JSONObject, false otherwise
	 */
	public static Boolean isJSONObject(String value) {
		if (value.startsWith("{")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method is used to check if a json returned is an array
	 * 
	 * @param value json in String format
	 * @return True if it's a JSONObject, false otherwise
	 */
	public static Boolean isJSONArray(String value) {
		if (value.startsWith("[")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method is used to create a json Object out of a string
	 * 
	 * @param value json in String format
	 * @return Creates a Json object from a string
	 */
	public static JSONObject getJSONResponse(String value) {
		JSONObject jsonResponse = new JSONObject(value.toString());
		return jsonResponse;
	}

	/**
	 * This method is used to creates a json Array out of a string
	 * 
	 * @param value json in String format
	 * @return Creates a Json array from a string
	 */
	public static JSONArray getJSONArrayResponse(String value) {
		JSONArray jsonResponse = new JSONArray(value);
		return jsonResponse;
	}

	/**
	 * This method is used to get a raw image in the form of nested maps
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Nested maps forming a raw image
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> getSlideInfo(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Return raw image information in the form of nested maps
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (!((Map<String, Object>) pmaSlideInfos.get(sessionID)).containsKey(slideRef)) {
			try {
				String url = apiUrl(sessionID, false) + "GetImageInfo?SessionID=" + pmaQ(sessionID) + "&pathOrUid="
						+ pmaQ(slideRef);
				URL urlResource = new URL(url);
				HttpURLConnection con;
				if (url.startsWith("https")) {
					con = (HttpsURLConnection) urlResource.openConnection();
				} else {
					con = (HttpURLConnection) urlResource.openConnection();
				}
				con.setRequestMethod("GET");
				String jsonString = getJSONAsStringBuffer(con).toString();
				if (isJSONObject(jsonString)) {
					JSONObject jsonResponse = getJSONResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (logger != null) {
							logger.severe("ImageInfo to " + slideRef + " resulted in: " + jsonResponse.get("Message")
									+ " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("ImageInfo to " + slideRef + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that slideRef is case sensitive!)");
					} else if (jsonResponse.has("d")) {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS).readValue(jsonResponse.get("d").toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID))
								.put(jsonResponse.getJSONObject("d").optString("Filename"), jsonMap);
						((Map<String, Object>) pmaSlideInfos.get(sessionID))
								.put(jsonResponse.getJSONObject("d").optString("UID"), jsonMap);
					} else {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS).readValue(jsonResponse.toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(jsonResponse.getString("Filename"),
								jsonMap);
						((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(jsonResponse.getString("UID"),
								jsonMap);
					}
				} else {
					// JSONArray jsonResponse = getJSONArrayResponse(jsonString);
					// pmaAmountOfDataDownloaded.put(sessionID,
					// pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					// ((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(slideRef,
					// jsonResponse);
					return null;
				}
			} catch (Exception e) {
				e.printStackTrace();
				if (logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					logger.severe(sw.toString());
				}
				return null;
			}
		}
		return (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slideRef);
	}

	/**
	 * This method is used to get raw images in the form of nested maps
	 * 
	 * @param slideRefs List of slides' path or UID
	 * @param varargs   Array of optional arguments
	 *                  <p>
	 *                  sessionID : First optional argument(String), default
	 *                  value(null), session's ID
	 *                  </p>
	 * @return Nested maps forming raw images
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Map<String, Object>> getSlidesInfo(List<String> slideRefs, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Return raw image information in the form of nested maps
		sessionID = sessionId(sessionID);
		List<String> slideRefsNew = new ArrayList<>();
		for (String slideRef : slideRefs) {
			if (slideRef.startsWith("/")) {
				slideRef = slideRef.substring(1);
			}
			if (!((Map<String, Object>) pmaSlideInfos.get(sessionID)).containsKey(slideRef)) {
				slideRefsNew.add(slideRef);
			}
		}
		if (slideRefsNew.size() > 0) {
			try {
				String url = apiUrl(sessionID, false) + "GetImagesInfo";
				URL urlResource = new URL(url);
				HttpURLConnection con;
				if (url.startsWith("https")) {
					con = (HttpsURLConnection) urlResource.openConnection();
				} else {
					con = (HttpURLConnection) urlResource.openConnection();
				}
				con.setRequestMethod("POST");
				con.setRequestProperty("Content-Type", "application/json");
				con.setUseCaches(false);
				con.setDoOutput(true);
				// we convert the list of slide to a string of this fashion :
				// ["slide1","slide2"....]
				String slideRefsNewForJson = slideRefsNew.stream().map(n -> ("\"" + n + "\""))
						.collect(Collectors.joining(",", "[", "]"));
				String input = "{ \"sessionID\": \"" + sessionID + "\", \"pathOrUids\": " + slideRefsNewForJson + "}";
				OutputStream os = con.getOutputStream();
				os.write(input.getBytes("UTF-8"));
				os.close();
				String jsonString = getJSONAsStringBuffer(con).toString();
				if (isJSONObject(jsonString)) {
					JSONObject jsonResponse = getJSONResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (logger != null) {
							logger.severe("ImageInfos to " + slideRefs.toString() + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("ImageInfos to " + slideRefs.toString() + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else if (jsonResponse.has("d")) {
						JSONArray jsonArrayResponse = jsonResponse.getJSONArray("d");
						for (int i = 0; i < jsonArrayResponse.length(); i++) {
							// we convert the Json object to a Map<String, Object>
							Map<String, Object> jsonMap = objectMapper
									.readerFor(new TypeReference<Map<String, Object>>() {
									}).with(DeserializationFeature.USE_LONG_FOR_INTS)
									.readValue(jsonArrayResponse.getJSONObject(i).toString());
							// we store the map created for both the slide name & the UID
							((Map<String, Object>) pmaSlideInfos.get(sessionID))
									.put(jsonArrayResponse.getJSONObject(i).getString("Filename"), jsonMap);
							((Map<String, Object>) pmaSlideInfos.get(sessionID))
									.put(jsonArrayResponse.getJSONObject(i).getString("UID"), jsonMap);
						}
					} else {
						return null;
					}
				} else {
					JSONArray jsonArrayResponse = getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonArrayResponse.length());
					for (int i = 0; i < jsonArrayResponse.length(); i++) {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = objectMapper.readerFor(new TypeReference<Map<String, Object>>() {
						}).with(DeserializationFeature.USE_LONG_FOR_INTS)
								.readValue(jsonArrayResponse.getJSONObject(i).toString());
						// we store the map created for both the slide name & the UID
						((Map<String, Object>) pmaSlideInfos.get(sessionID))
								.put(jsonArrayResponse.getJSONObject(i).getString("Filename"), jsonMap);
						((Map<String, Object>) pmaSlideInfos.get(sessionID))
								.put(jsonArrayResponse.getJSONObject(i).getString("UID"), jsonMap);
					}
				}
				Map<String, Map<String, Object>> results = new HashMap<String, Map<String, Object>>();
				for (String slide : slideRefs) {
					results.put(slide,
							(Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slide));
				}
				return results;
			} catch (Exception e) {
				e.printStackTrace();
				if (logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					logger.severe(sw.toString());
				}
				return null;
			}
		}
		// if for all the slides, the image info data has been already stored on
		// pmaSlideInfos
		Map<String, Map<String, Object>> results = new HashMap<String, Map<String, Object>>();
		for (String slide : slideRefs) {
			results.put(slide, (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slide));
		}
		return results;
	}

	/**
	 * This method is used to determine the maximum zoom level that still represents
	 * an optical magnification
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Max zoom level that still represents an optical magnification
	 */
	public static int getMaxZoomLevel(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine the maximum zoomlevel that still represents an optical
		// magnification
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		if (info == null) {
			System.out.print("Unable to get information for " + slideRef + " from " + sessionID);
			return 0;
		} else if (info.containsKey("MaxZoomLevel")) {
			try {
				return Integer.parseInt(info.get("MaxZoomLevel").toString());
			} catch (Exception e) {
				System.out.print("Something went wrong consulting the MaxZoomLevel key in info Map; value ="
						+ info.get("MaxZoomLevel").toString());
				e.printStackTrace();
				if (logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					logger.severe(sw.toString());
					logger.severe("Something went wrong consulting the MaxZoomLevel key in info Map; value ="
							+ info.get("MaxZoomLevel").toString());
				}
				return 0;
			}
		} else {
			try {
				return Integer.parseInt(info.get("NumberOfZoomLevels").toString());
			} catch (Exception e) {
				System.out.print("Something went wrong consulting the NumberOfZoomLevels key in info Map; value ="
						+ info.get("NumberOfZoomLevels").toString());
				e.printStackTrace();
				if (logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					logger.severe(sw.toString());
					logger.severe("Something went wrong consulting the NumberOfZoomLevels key in info Map; value ="
							+ info.get("NumberOfZoomLevels").toString());
				}
				return 0;
			}
		}
	}

	/**
	 * This method is used to get list with all zoom levels, from 0 to max zoom
	 * level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 minNumberOfTiles : Second optional argument(Integer), default
	 *                 value(0), minimal number of tiles used to specify that you're
	 *                 only interested in zoom levels that include at least a given
	 *                 number of tiles
	 *                 </p>
	 * @return List with all zoom levels, from 0 to max zoom level
	 */
	public static List<Integer> getZoomLevelsList(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer minNumberOfTiles = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getZoomLevelsList() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getZoomLevelsList() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			minNumberOfTiles = (Integer) varargs[1];
		}
		// Obtain a list with all zoom levels, starting with 0 and up to and including
		// max zoom level
		// Use min_number_of_tiles argument to specify that you're only interested in
		// zoom levels that include at lease a given number of tiles
		List<Integer> result = new ArrayList<>();
		Set<Integer> set = getZoomLevelsDict(slideRef, sessionID, minNumberOfTiles).keySet();
		for (Integer i : set) {
			result.add(i);
		}
		Collections.sort(result);
		return result;
	}

	/**
	 * This method is used to get a map with the number of tiles per zoom level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 minNumberOfTiles : Second optional argument(Integer), default
	 *                 value(0), minimal number of tiles used to specify that you're
	 *                 only interested in zoom levels that include at least a given
	 *                 number of tiles
	 *                 </p>
	 * @return Map with the number of tiles per zoom level
	 */
	public static Map<Integer, List<Integer>> getZoomLevelsDict(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer minNumberOfTiles = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getZoomLevelsDict() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getZoomLevelsDict() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			minNumberOfTiles = (Integer) varargs[1];
		}
		// Obtain a map with the number of tiles per zoom level.
		// Information is returned as (x, y, n) lists per zoom level, with
		// x = number of horizontal tiles,
		// y = number of vertical tiles,
		// n = total number of tiles at specified zoom level (x * y)
		// Use min_number_of_tiles argument to specify that you're only interested in
		// zoom levels that include at least a given number of tiles
		List<Integer> zoomLevels = new ArrayList<Integer>();
		IntStream.range(0, getMaxZoomLevel(slideRef, sessionID) + 1).forEach(n -> {
			zoomLevels.add(n);
		});
		List<List<Integer>> dimensions = new ArrayList<>();
		for (int z : zoomLevels) {
			if (getNumberOfTiles(slideRef, z, sessionID).get(2) > minNumberOfTiles) {
				dimensions.add(getNumberOfTiles(slideRef, z, sessionID));
			}
		}
		List<Integer> zoomLevelsSubList = (List<Integer>) zoomLevels.subList(zoomLevels.size() - dimensions.size(),
				zoomLevels.size());
		Map<Integer, List<Integer>> d = new HashMap<>();
		for (int i = 0; i < zoomLevelsSubList.size() && i < dimensions.size(); i++) {
			d.put(zoomLevelsSubList.get(i), dimensions.get(i));
		}
		return d;
	}

	/**
	 * This method is used to get the physical dimension in terms of pixels per
	 * micrometer of a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 zoomLevel : First optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Second optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Two items list containing the physical dimension in terms of pixels
	 *         per micrometer of a slide
	 */
	public static List<Float> getPixelsPerMicrometer(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getZoomLevelsDict() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getZoomLevelsDict() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[1];
		}
		// Retrieve the physical dimension in terms of pixels per micrometer.
		// When zoom level is left to its default value of None, dimensions at the
		// highest zoom level are returned
		// (in effect returning the "native" resolution at which the slide was
		// registered)
		int maxZoomLevel = getMaxZoomLevel(slideRef, sessionID);
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		float xppm = (float) info.get("MicrometresPerPixelX");
		float yppm = (float) info.get("MicrometresPerPixelY");
		List<Float> result = new ArrayList<>();
		if ((zoomLevel == null) || (zoomLevel == maxZoomLevel)) {
			result.add(xppm);
			result.add(yppm);
			return result;
		} else {
			double factor = Math.pow(2, zoomLevel - maxZoomLevel);
			result.add((float) (xppm / factor));
			result.add((float) (yppm / factor));
			return result;
		}
	}

	/**
	 * This method is used to get the total dimensions of a slide image at a given
	 * zoom level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 zoomLevel : First optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Second optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Two items list with the total dimensions of a slide image at a given
	 *         zoom level
	 */
	public static List<Integer> getPixelDimensions(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getPixelDimensions() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getPixelDimensions() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[1];
		}
		// Get the total dimensions of a slide image at a given zoom level
		int maxZoomLevel = getMaxZoomLevel(slideRef, sessionID);
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		List<Integer> result = new ArrayList<>();
		if (zoomLevel == null || zoomLevel == maxZoomLevel) {
			result.add(Integer.parseInt(info.get("Width").toString()));
			result.add(Integer.parseInt(info.get("Height").toString()));
			return result;
		} else {
			double factor = Math.pow(2, zoomLevel - maxZoomLevel);
			result.add((int) (Integer.parseInt(info.get("Width").toString()) * factor));
			result.add((int) (Integer.parseInt(info.get("Height").toString()) * factor));
			return result;
		}
	}

	/**
	 * This method is used to determine the number of tiles needed to reconstitute a
	 * slide at a given zoom level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 zoomLevel : First optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Second optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Three items list to determine the number of tiles needed to
	 *         reconstitute a slide at a given zoom level
	 */
	public static List<Integer> getNumberOfTiles(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getNumberOfTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getNumberOfTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[1];
		}
		// Determine the number of tiles needed to reconstitute a slide at a given
		// zoomlevel
		List<Integer> pixels = getPixelDimensions(slideRef, zoomLevel, sessionID);
		List<Integer> sz = getTileSize(sessionID);
		int xTiles = (int) Math.ceil((double) pixels.get(0) / (double) sz.get(0));
		int yTiles = (int) Math.ceil((double) pixels.get(1) / (double) sz.get(0));
		int nTiles = xTiles * yTiles;
		List<Integer> result = new ArrayList<>();
		result.add(xTiles);
		result.add(yTiles);
		result.add(nTiles);
		return result;
	}

	/**
	 * This method is used to Determine the physical dimensions of the sample
	 * represented by the slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Two items list to determine the physical dimensions of the sample
	 *         represented by the slide
	 */
	public static List<Float> getPhysicalDimensions(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine the physical dimensions of the sample represented by the slide.
		// This is independent of the zoom level: the physical properties don't change
		// because the magnification changes
		List<Float> ppmData = getPixelsPerMicrometer(slideRef, sessionID);
		List<Integer> pixelSz = getPixelDimensions(slideRef, sessionID);
		List<Float> result = new ArrayList<>();
		result.add(pixelSz.get(0) * ppmData.get(0));
		result.add(pixelSz.get(1) * ppmData.get(1));
		return result;
	}

	/**
	 * This method is used to get the number of channels for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Number of channels for a slide (1 when slide is brightfield)
	 */
	@SuppressWarnings("unchecked")
	public static int getNumberOfChannels(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Number of fluorescent channels for a slide (when slide is brightfield, return
		// is always 1)
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		return ((List<Object>) ((List<Map<String, Object>>) ((List<Map<String, Object>>) info.get("TimeFrames")).get(0)
				.get("Layers")).get(0).get("Channels")).size();
	}

	/**
	 * This method is used to get the number of (z-stacked) layers for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Number of layers for a slide
	 */
	@SuppressWarnings("unchecked")
	public static int getNumberOfLayers(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Number of (z-stacked) layers for a slide
		Map<String, Object> info = getSlideInfo(slideRef, sessionID);
		return ((List<Object>) ((List<Map<String, Object>>) info.get("TimeFrames")).get(0).get("Layers")).size();
	}

	/**
	 * This method is used to get the number of (z-stacked) layers for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Number of Z-Stack layers for a slide
	 */
	public static int getNumberOfZStackLayers(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		return getNumberOfLayers(slideRef, sessionID);
	}

	/**
	 * This method is used to determine whether a slide is a fluorescent image or
	 * not
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return True if slide is a fluorescent image, false otherwise
	 */
	public static Boolean isFluorescent(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine whether a slide is a fluorescent image or not
		return getNumberOfChannels(slideRef, sessionID) > 1;
	}

	/**
	 * This method is used to determine whether a slide contains multiple (stacked)
	 * layers or not
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return True if slide contains multiple (stacked) layers, false otherwise
	 */
	public static Boolean isMultiLayer(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine whether a slide contains multiple (stacked) layers or not
		return getNumberOfLayers(slideRef, sessionID) > 1;
	}

	/**
	 * This method is used to determine whether a slide is a z-stack or not
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return True if slide is a z-stack, false otherwise
	 */
	public static Boolean isZStack(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Determine whether a slide is a z-stack or not
		return isMultiLayer(slideRef, sessionID);
	}

	/**
	 * This method is used to get the magnification represented at a certain zoom
	 * level
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 zoomLevel : First optional argument(Integer), default
	 *                 value(false), zoom level
	 *                 </p>
	 *                 <p>
	 *                 exact : Second optional argument(Boolean), default
	 *                 value(null), defines if exact or not
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Third optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Magnification represented at a certain zoom level
	 */
	public static int getMagnification(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		Boolean exact = false;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getMagnification() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getMagnification() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			exact = (Boolean) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof String) && varargs[2] != null) {
				if (logger != null) {
					logger.severe("getMagnification() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[2];
		}
		// Get the magnification represented at a certain zoom level
		float ppm = getPixelsPerMicrometer(slideRef, zoomLevel, sessionID).get(0);
		if (ppm > 0) {
			if (exact == true) {
				return (int) (40 / (ppm / 0.25));
			} else {
				return (int) (40 / ((int) (ppm / 0.25)));
			}

		} else {
			return 0;
		}
	}

	/**
	 * This method is used to get the URL that points to the barcode (alias for
	 * "label") for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return URL that points to the barcode (alias for "label") for a slide
	 */
	public static String getBarcodeUrl(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the URL that points to the barcode (alias for "label") for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url;
		try {
			url = pmaUrl(sessionID) + "barcode" + "?SessionID=" + pmaQ(sessionID) + "&pathOrUid=" + pmaQ(slideRef);
			return url;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}

	}

	/**
	 * This method is used to get the barcode (alias for "label") image for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Barcode (alias for "label") image for a slide
	 */
	public static Image getBarcodeImage(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the barcode (alias for "label") image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			URL urlResource = new URL(getBarcodeUrl(slideRef, sessionID));
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get the text encoded by the barcode
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return The barcode text
	 */
	public static String getBarcodeText(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the text encoded by the barcode (if there IS a barcode on the slide to
		// begin with)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String barcode;
		String url = apiUrl(sessionID, false) + "GetBarcodeText?sessionID=" + pmaQ(sessionID) + "&pathOrUid="
				+ pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (isJSONObject(jsonString)) {
				JSONObject jsonResponse = getJSONResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (logger != null) {
						logger.severe("get_barcode_text on " + slideRef + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("get_barcode_text on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else {
					return jsonResponse.getString("d").equals("null") ? null : jsonResponse.getString("d");
				}
			} else {
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonString.length());
				barcode = jsonString.replace("\"", "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
		return barcode;
	}

	/**
	 * This method is used to get the URL that points to the label for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Url that points to the label for a slide
	 */
	public static String getLabelUrl(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the URL that points to the label for a slide
		return getBarcodeUrl(slideRef, sessionID);
	}

	/**
	 * This method is used to get the label image for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Image label for a slide
	 */
	public static Image getLabelImage(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the label image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			URL urlResource = new URL(getLabelUrl(slideRef, sessionID));
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get the URL that points to the thumbnail for a slide
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 height : Second optional argument(Integer), default value(0),
	 *                 height of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *                 </p>
	 *                 <p>
	 *                 width : Third optional argument(Integer), default value(0),
	 *                 width of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *                 </p>
	 * @return URL that points to the thumbnail for a slide
	 */
	public static String getThumbnailUrl(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer height = 0;
		Integer width = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getThumbnailUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getThumbnailUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			height = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (logger != null) {
					logger.severe("getThumbnailUrl() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			width = (Integer) varargs[2];
		}
		// Get the URL that points to the thumbnail for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url;
		try {
			url = pmaUrl(sessionID) + "thumbnail" + "?SessionID=" + pmaQ(sessionID) + "&pathOrUid=" + pmaQ(slideRef)
					+ ((height > 0) ? "&h=" + height.toString() : "") + ((width > 0) ? "&w=" + width.toString() : "");
			return url;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get the thumbnail image for a slide
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 height : Second optional argument(Integer), default value(0),
	 *                 height of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *                 </p>
	 *                 <p>
	 *                 width : Third optional argument(Integer), default value(0),
	 *                 width of the requested thumbnail, if value set to 0 it will
	 *                 be ignored
	 *                 </p>
	 * @return Image thumbnail for a slide
	 */
	public static Image getThumbnailImage(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer height = 0;
		Integer width = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getThumbnailImage() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getThumbnailImage() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			height = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (logger != null) {
					logger.severe("getThumbnailImage() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			width = (Integer) varargs[2];
		}
		// Get the thumbnail image for a slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		try {
			String url = getThumbnailUrl(slideRef, sessionID, height, width);
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get a single tile at position (x, y)
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 x : First optional argument(Integer), default value(0), x
	 *                 position
	 *                 </p>
	 *                 <p>
	 *                 y : Second optional argument(Integer), default value(0), y
	 *                 position
	 *                 </p>
	 *                 <p>
	 *                 zoomLevel : Third optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 zStack : Fourth optional argument(Integer), default value(0),
	 *                 Number of z stacks
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Fifth optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 format : Sixth optional argument(String), default value(jpg),
	 *                 image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Seventh optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 * @return Single tile at position (x, y)
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belong to
	 */
	public static Image getTile(String slideRef, Object... varargs) throws Exception {
		// setting the default values when arguments' values are omitted
		Integer x = 0;
		Integer y = 0;
		Integer zoomLevel = null;
		Integer zStack = 0;
		String sessionID = null;
		String format = "jpg";
		Integer quality = 100;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			x = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			y = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (logger != null) {
					logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				if (logger != null) {
					logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof String) && varargs[4] != null) {
				if (logger != null) {
					logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof String) && varargs[5] != null) {
				if (logger != null) {
					logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof Integer) && varargs[6] != null) {
				if (logger != null) {
					logger.severe("getTile() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			quality = (Integer) varargs[6];
		}
		// Get a single tile at position (x, y)
		// Format can be 'jpg' or 'png'
		// Quality is an integer value and varies from 0
		// (as much compression as possible; not recommended) to 100 (100%, no
		// compression)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (zoomLevel == null) {
			zoomLevel = 0;
		}
		String url;
		url = pmaUrl(sessionID);
		if (url == null) {
			if (logger != null) {
				logger.severe("Unable to determine the PMA.core instance belonging to " + sessionID);
			}
			throw new Exception("Unable to determine the PMA.core instance belonging to " + sessionID);
		}
		url = "tile" + "?SessionID=" + pmaQ(sessionID) + "&channels=" + pmaQ("0") + "&layer=" + zStack.toString()
				+ "&timeframe=" + pmaQ("0") + "&layer=" + pmaQ("0") + "&pathOrUid=" + pmaQ(slideRef) + "&x="
				+ x.toString() + "&y=" + y.toString() + "&z=" + zoomLevel.toString() + "&format=" + pmaQ(format)
				+ "&quality=" + pmaQ(quality.toString()) + "&cache="
				+ pmaUseCacheWhenRetrievingTiles.toString().toLowerCase();
		try {
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get all tiles with a (fromX, fromY, toX, toY)
	 * rectangle
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 fromX : First optional argument(Integer), default value(0),
	 *                 starting x position
	 *                 </p>
	 *                 <p>
	 *                 fromY : Second optional argument(Integer), default value(0),
	 *                 starting y position
	 *                 </p>
	 *                 <p>
	 *                 toX : Third optional argument(Integer), default value(0),
	 *                 ending x position
	 *                 </p>
	 *                 <p>
	 *                 toY : Fourth optional argument(Integer), default value(0),
	 *                 ending y position
	 *                 </p>
	 *                 <p>
	 *                 zoomLevel : Fifth optional argument(Integer), default
	 *                 value(null), zoom level
	 *                 </p>
	 *                 <p>
	 *                 zStack : Sixth optional argument(Integer), default value(0),
	 *                 Number of z stacks
	 *                 </p>
	 *                 <p>
	 *                 sessionID : Seventh optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 *                 <p>
	 *                 format : Eigth optional argument(String), default value(jpg),
	 *                 image format
	 *                 </p>
	 *                 <p>
	 *                 quality : Ninth optional argument(Integer), default
	 *                 value(100), quality
	 *                 </p>
	 * @return All tiles with a (fromX, fromY, toX, toY) rectangle
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Stream getTiles(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer fromX = 0;
		Integer fromY = 0;
		Integer toX = null;
		Integer toY = null;
		Integer zoomLevel = null;
		Integer zStack = 0;
		String sessionID = null;
		String format = "jpg";
		Integer quality = 100;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			fromX = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			fromY = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			toX = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			toY = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof Integer) && varargs[4] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof Integer) && varargs[5] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof String) && varargs[6] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[6];
		}
		if (varargs.length > 7) {
			if (!(varargs[7] instanceof String) && varargs[7] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[7];
		}
		if (varargs.length > 8) {
			if (!(varargs[8] instanceof Integer) && varargs[8] != null) {
				if (logger != null) {
					logger.severe("getTiles() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			quality = (Integer) varargs[8];
		}
		// Get all tiles with a (fromX, fromY, toX, toY) rectangle. Navigate left to
		// right, top to bottom
		// Format can be 'jpg' or 'png'
		// Quality is an integer value and varies from 0
		// (as much compression as possible; not recommended) to 100 (100%, no
		// compression)
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		if (zoomLevel == null) {
			zoomLevel = 0;
		}
		if (toX == null) {
			toX = getNumberOfTiles(slideRef, zoomLevel, sessionID).get((0));
		}
		if (toY == null) {
			toY = getNumberOfTiles(slideRef, zoomLevel, sessionID).get((1));
		}
		// we declare final variable to use them in the enclosing scope for new
		// Supplier()
		final int varFromX = fromX;
		final int varToX = toX;
		final int varFromY = fromY;
		final int varToY = toY;
		final int varZoomLevel = zoomLevel;
		final int varZStack = zStack;
		final String varSessionID = sessionID;
		final String varSlideRef = slideRef;
		final String varFormat = format;
		final Integer varQualty = quality;
		// we use Stream to simulate the behavior of "yield" in Python
		return Stream.generate(new Supplier() {
			int x = varFromX;
			int y = varFromY - 1;

			@Override
			public Image get() {
				if (x <= varToX) {
					if (y < varToY) {
						y++;
					} else {
						y = varFromY - 1;
						x++;
					}
				}
				try {
					return getTile(varSlideRef, x, y, varZoomLevel, varZStack, varSessionID, varFormat, varQualty);
				} catch (Exception e) {
					e.printStackTrace();
					if (logger != null) {
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						logger.severe(sw.toString());
					}
					return null;
				}
			}
		}).limit((varToX - varFromX + 1) * (varToY - varFromY + 1));
	}

	/**
	 * This method is used to find out what forms where submitted for a specific
	 * slide
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Map of forms submitted for a defined slide
	 */
	public static Map<String, String> getSubmittedForms(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Find out what forms where submitted for a specific slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFormSubmissions?sessionID=" + pmaQ(sessionID) + "&pathOrUids="
				+ pmaQ(slideRef);
		Map<String, String> forms = new HashMap<>();
		Map<String, String> allForms = getAvailableForms(slideRef, sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (isJSONObject(jsonString)) {
					JSONObject jsonResponse = getJSONResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (logger != null) {
							logger.severe("getSubmittedForms on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getSubmittedForms on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						forms = null;
					}
				} else {
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						if (!forms.containsKey(jsonResponse.optJSONObject(i).get("FormID").toString())
								&& allForms != null) {
							forms.put(jsonResponse.optJSONObject(i).get("FormID").toString(),
									allForms.get(jsonResponse.optJSONObject(i).get("FormID").toString()));
						}
					}
					// should probably do some post-processing here, but unsure what that would
					// actually be??

				}
			} else {
				forms = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
		return forms;
	}

	/**
	 * This method is used to get submitted forms data in json Array format
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Submitted forms data in json Array format
	 */
	public static JSONArray getSubmittedFormData(String slideRef, String... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get all submitted form data associated with a specific slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		JSONArray data; // new HashMap<>();
		String url = apiUrl(sessionID, false) + "GetFormSubmissions?sessionID=" + pmaQ(sessionID) + "&pathOrUids="
				+ pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (isJSONObject(jsonString)) {
					JSONObject jsonResponse = getJSONResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (logger != null) {
							logger.severe("getSubmittedFormData on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getSubmittedFormData on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						data = null;
					}
				} else {
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					data = jsonResponse;
				}
				// should probably do some post-processing here, but unsure what that would
				// actually be??
			} else {
				data = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
		return data;
	}

	/**
	 * This method is used to prepare a form-dictionary that can be used later on to
	 * submit new form data for a slide
	 * 
	 * @param formID  Form's ID
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                sessionID : First optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Form-map that can be used later on to submit new form data for a
	 *         slide
	 */
	public static Map<String, String> prepareFormMap(String formID, String... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Prepare a form-dictionary that can be used later on to submit new form data
		// for a slide
		if (formID == null) {
			return null;
		}
		sessionID = sessionId(sessionID);
		Map<String, String> formDef = new HashMap<>();
		String url = apiUrl(sessionID, false) + "GetFormDefinitions?sessionID=" + pmaQ(sessionID);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (isJSONObject(jsonString)) {
					JSONObject jsonResponse = getJSONResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (logger != null) {
							logger.severe("" + jsonResponse.get("Message") + "");
						}
						throw new Exception("" + jsonResponse.get("Message") + "");
					} else {
						formDef = null;
					}
				} else {
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						if ((jsonResponse.optJSONObject(i).get("FormID").toString().equals(formID))
								|| (jsonResponse.optJSONObject(i).get("FormName").toString().equals(formID))) {
							for (int j = 0; j < jsonResponse.optJSONObject(i).getJSONArray("FormFields")
									.length(); j++) {
								formDef.put(jsonResponse.optJSONObject(i).getJSONArray("FormFields").getJSONObject(j)
										.getString("Label"), null);
							}
						}

					}
				}
			} else {
				formDef = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
		return formDef;
	}

	/**
	 * This method is used to get a Map of the forms available to fill out, either
	 * system-wide (leave slideref to "null"), or for a particular slide
	 * 
	 * @param varargs Array of optional arguments
	 *                <p>
	 *                slideRef : First optional argument(String), default
	 *                value(null), slide's path
	 *                </p>
	 *                <p>
	 *                sessionID : Second optional argument(String), default
	 *                value(null), session's ID
	 *                </p>
	 * @return Map of the forms available to fill out, either system-wide (leave
	 *         slideref to "null"), or for a particular slide
	 */
	public static Map<String, String> getAvailableForms(String... varargs) {
		// setting the default values when arguments' values are omitted
		String slideRef = varargs.length > 0 ? varargs[0] : null;
		String sessionID = varargs.length > 0 ? varargs[1] : null;
		// See what forms are available to fill out, either system-wide (leave slideref
		// to None), or for a particular slide
		sessionID = sessionId(sessionID);
		String url;
		Map<String, String> forms = new HashMap<>();
		if (slideRef != null) {
			if (slideRef.startsWith("/")) {
				slideRef = slideRef.substring(1);
			}
			String dir = FilenameUtils.getFullPath(slideRef).substring(0,
					FilenameUtils.getFullPath(slideRef).length() - 1);
			url = apiUrl(sessionID, false) + "GetForms?sessionID=" + pmaQ(sessionID) + "&path=" + pmaQ(dir);
		} else {
			url = apiUrl(sessionID, false) + "GetForms?sessionID=" + pmaQ(sessionID);
		}
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (isJSONObject(jsonString)) {
					JSONObject jsonResponse = getJSONResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (logger != null) {
							logger.severe("getAvailableForms on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getAvailableForms on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						forms = null;
					}
				} else {
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					for (int i = 0; i < jsonResponse.length(); i++) {
						forms.put(jsonResponse.optJSONObject(i).get("Key").toString(),
								jsonResponse.optJSONObject(i).getString("Value"));
					}
				}
			} else {
				forms = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
		return forms;
	}

	/**
	 * To be elaborated later
	 * 
	 * @param slideRef To be elaborated later
	 * @param formID   To be elaborated later
	 * @param formMap  To be elaborated later
	 * @param varargs  To be elaborated later
	 * @return To be elaborated later
	 */
	public static String submitFormData(String slideRef, String formID, String formMap, String... varargs) {
		// setting the default value when argument' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Not implemented yet
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		return null;
	}

	/**
	 * This method is used to retrieve the annotations for slide slideRef
	 * 
	 * @param slideRef slide's path
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return Annotations for a slide in a json Array format
	 */
	public static JSONArray getAnnotations(String slideRef, String... varargs) {
		// setting the default value when argument' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Retrieve the annotations for slide slideRef
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		// String dir = FilenameUtils.getFullPath(slideRef).substring(0,
		// FilenameUtils.getFullPath(slideRef).length() - 1);
		JSONArray data;
		String url = apiUrl(sessionID, false) + "GetAnnotations?sessionID=" + pmaQ(sessionID) + "&pathOrUid="
				+ pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (jsonString != null && jsonString.length() > 0) {
				if (isJSONObject(jsonString)) {
					JSONObject jsonResponse = getJSONResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					if (jsonResponse.has("Code")) {
						if (logger != null) {
							logger.severe("getAnnotations() on  " + slideRef + " resulted in: "
									+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
						}
						throw new Exception("getAnnotations() on  " + slideRef + " resulted in: "
								+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
					} else {
						data = null;
					}
				} else {
					JSONArray jsonResponse = getJSONArrayResponse(jsonString);
					pmaAmountOfDataDownloaded.put(sessionID,
							pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
					data = jsonResponse;
				}
			} else {
				data = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
		return data;
	}

	/**
	 * This method is used to launch the default web browser and load a web-based
	 * viewer for the slide
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @throws Exception if unable to determine the PMA.core instance the session ID
	 *                   belongs to
	 */
	public static void showSlide(String slideRef, String... varargs) throws Exception {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Launch the default web browser and load a web-based viewer for the slide
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String osCmd;
		if (System.getProperty("os.name").toLowerCase().equals("posix")) {
			osCmd = "open ";
		} else {
			osCmd = "start ";
		}
		String url;
		if (sessionID == pmaCoreLiteSessionID) {
			url = "http://free.pathomation.com/pma-view-lite/?path=" + pmaQ(slideRef);
		} else {
			url = pmaUrl(sessionID);
			if (url == null) {
				if (logger != null) {
					logger.severe("Unable to determine the PMA.core instance belonging to " + sessionID);
				}
				throw new Exception("Unable to determine the PMA.core instance belonging to " + sessionID);
			}
			url = "viewer/index.htm" + "?sessionID=" + pmaQ(sessionID) + "^&pathOrUid=" + pmaQ(slideRef); // note the ^&
																											// to escape
																											// a regular
																											// &
		}
		try {
			Runtime.getRuntime().exec(osCmd + url);
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
		}
	}

	/**
	 * This method is used to get sub-directories available to sessionID in the
	 * start directory for PMA.start ONLY
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return List of all files related to a selected slide
	 */
	public static List<String> enumerateFilesForSlide(String slideRef, String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Obtain all files actually associated with a specific slide
		// This is most relevant with slides that are defined by multiple files, like
		// MRXS or VSI
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "EnumerateAllFilesForSlide?sessionID=" + pmaQ(sessionID) + "&pathOrUid="
				+ pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (isJSONObject(jsonString)) {
				JSONObject jsonResponse = getJSONResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				if (jsonResponse.has("Code")) {
					if (logger != null) {
						logger.severe("get_slides from " + slideRef + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that slideRef is case sensitive!)");
					}
					throw new Exception("get_slides from " + slideRef + " resulted in: " + jsonResponse.get("Message")
							+ " (keep in mind that slideRef is case sensitive!)");
				} else if (jsonResponse.has("d")) {
					JSONArray array = jsonResponse.getJSONArray("d");
					List<String> files = new ArrayList<>();
					for (int i = 0; i < array.length(); i++) {
						files.add(array.optString(i));
					}
					return files;
				} else {
					return null;
				}
			} else {
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				List<String> files = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					files.add(jsonResponse.optString(i));
				}
				return files;
			}

		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to get sub-directories available to sessionID in the
	 * start directory for PMA.core ONLY
	 * 
	 * @param slideRef slide's path or UID
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 sessionID : First optional argument(String), default
	 *                 value(null), session's ID
	 *                 </p>
	 * @return List of all files related to a selected slide
	 */
	@SuppressWarnings("serial")
	public static List<Map<String, String>> enumerateFilesForSlidePMACore(String slideRef, String... varargs) {
		// setting the default value when argument's value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Obtain all files actually associated with a specific slide
		// This is most relevant with slides that are defined by multiple files, like
		// MRXS or VSI
		sessionID = sessionId(sessionID);
		if (slideRef.startsWith("/")) {
			slideRef = slideRef.substring(1);
		}
		String url = apiUrl(sessionID, false) + "GetFilenames?sessionID=" + pmaQ(sessionID) + "&pathOrUid="
				+ pmaQ(slideRef);
		try {
			URL urlResource = new URL(url);
			HttpURLConnection con;
			if (url.startsWith("https")) {
				con = (HttpsURLConnection) urlResource.openConnection();
			} else {
				con = (HttpURLConnection) urlResource.openConnection();
			}
			con.setRequestMethod("GET");
			String jsonString = getJSONAsStringBuffer(con).toString();
			if (isJSONArray(jsonString)) {
				JSONArray jsonResponse = getJSONArrayResponse(jsonString);
				pmaAmountOfDataDownloaded.put(sessionID,
						pmaAmountOfDataDownloaded.get(sessionID) + jsonResponse.length());
				List<Map<String, String>> result = new ArrayList<>();
				for (int i = 0; i < jsonResponse.length(); i++) {
					final int finalI = i;
					result.add(new HashMap<String, String>() {
						{
							put("LastModified", jsonResponse.getJSONObject(finalI).getString("LastModified"));
							put("Path", jsonResponse.getJSONObject(finalI).getString("Path"));
							put("Size", String.valueOf(jsonResponse.getJSONObject(finalI).getLong("Size")));
						}
					});
				}
				return result;
			} else {
				if (logger != null) {
					logger.severe("enumerateFilesForSlidePMACore() : Failure to get related files");
				}
				return null;
			}

		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to remove the drive name from a path returned by pma_java
	 * SDK why is this needed : for PMA.core(.lite) the path sent should include the
	 * drive name (if not blank) and the paths returned also include the drive name
	 * e.g. "Primary Disk (C:)/samples" it's necessary to remove drive name from the
	 * path's root to be able to use these paths in file system
	 * 
	 * @param lst List of paths (directories, root directories, slides...)
	 * @return List of paths formatted to remove the Disk label if it exists
	 */
	public static List<String> removeDriveName(List<String> lst) {
		try {
			for (int i = 0; i < lst.size(); i++) {
				String ss = lst.get(i);
				String root = ss.split("/")[0];
				if (root.matches(".*\\s\\(..\\)")) {
					if (root.equals(ss)) {
						ss = root.substring(root.length() - 3, root.length() - 1);
					} else {
						ss = root.substring(root.length() - 3, root.length() - 1) + "/"
								+ ss.substring(root.length() + 1);
					}
					lst.set(i, ss);
				}
			}
			return lst;
		} catch (Exception e) {
			e.printStackTrace();
			if (logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				logger.severe(sw.toString());
			}
			return null;
		}
	}

	/**
	 * This method is used to add the drive name to a path before sending it to
	 * pma_java SDK why is this needed? : for PMA.core(.lite) the path sent should
	 * include the drive name (if not blank) and the paths returned also include the
	 * drive name e.g. "Primary Disk (C:)/samples" it's necessary to add drive name
	 * to the path's root to be able to make a valid request to PMA.core(.lite)
	 * 
	 * @param value path to be formatted
	 * @return Path formatted to include the Disk label if it doesn't exist
	 */
	public static String createPathWithLabel(String value) {
		Path path = Paths.get(value);
		String root = path.getRoot().toString();
		String displayName;
		// if the label is already stored, no need to fetch it again
		// this operation is specially slow!
		if (diskLabels != null && diskLabels.containsKey(root)) {
			displayName = diskLabels.get(root).toString();
		} else {
			displayName = FileSystemView.getFileSystemView().getSystemDisplayName(new File(root));
			diskLabels.put(root, displayName);
		}
		// "Local Disk (x:)" refers to a blank disk name but Windows add the default
		// label "Local Disk"
		// we need to omit this case since for it PMA.core(.lite) would require a drive
		// letter instead of a drive name + letter
		if (displayName.matches(".*\\s\\(..\\)") && !displayName.matches("Local\\sDisk\\s\\(..\\)")) {
			value = displayName + "/" + value.substring(root.length());
		}
		return value;
	}

}
