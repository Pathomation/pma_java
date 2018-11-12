/**
 
 */
package com.pathomation;

import java.awt.Image;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <h1>Java SDK</h1> Java wrapper library for PMA.start, a universal viewer for
 * whole slide imaging and microscopy
 * 
 * @author Yassine Iddaoui
 * @version 2.0.0.19
 */
public class Core {
	private static Map<String, Object> pmaSessions = new HashMap<String, Object>();
	private static Map<String, Object> pmaSlideInfos = new HashMap<String, Object>();
	private static final String pmaCoreLiteURL = "http://localhost:54001/";
	private static final String pmaCoreLiteSessionID = "SDK.Java";
	private static Boolean pmaUseCacheWhenRetrievingTiles = true;
	private static Map<String, Integer> pmaAmountOfDataDownloaded = new HashMap<String, Integer>() {
		{
			put(pmaCoreLiteSessionID, 0);
		}
	};

	/**
	 * This method is used to get the session's ID
	 * 
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return String returns the actual session's ID
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
	 * @return String returns PMA.core active session
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
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return String url related to the session's ID
	 * @throws Exception
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
				throw new Exception("Invalid sessionID:" + sessionID);
			}
		}
	}

	/**
	 * This method is used to retrieve HTML Code from URL
	 * 
	 * @param url
	 *            to get HTML code from
	 * @return String HTML code generated from the url argument
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
			return null;
		}
	}

	/**
	 * This method is used to parse a XML content
	 * 
	 * @param s
	 *            XML content to parse
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
			return null;
		}
	}

	/**
	 * This method is used to check if an instance of PMA.core is running (by
	 * checking the existence of value "true" in a XML file)
	 * 
	 * @param pmaCoreURL
	 *            it's an optional argument (String), default value set to
	 *            "pmaCoreLiteURL"
	 * @return Boolean true if an instance of PMA.core is running, false otherwise
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
			return null;
		}
	}

	/**
	 * This method is used to define which content will be received "XML" or "Json"
	 * 
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @param xml
	 *            it's an optional argument (Boolean), default value set to "true"
	 * @return String add sequence to the url to specify which content to be
	 *         received (XML or Json)
	 */
	private static String apiUrl(Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Boolean xml = true;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				throw new IllegalArgumentException("...");
			}
			xml = (Boolean) varargs[1];
		}
		// let's get the base URL first for the specified session
		String url;
		try {
			url = pmaUrl(sessionID);
		} catch (Exception e) {
			System.out.print(e.getMessage());
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
	 * This method is used to concatenate a couple of Strings while replacing "\\"
	 * by "/"
	 * 
	 * @param s
	 *            array of Strings
	 * @return String concatenation of a couple of String while making sure the
	 *         first string always ends with "/"
	 */
	private static String join(String... s) {
		String joinString = "";
		for (String ss : s) {
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
	 * This method is used to get a list of the values of "String" tags of a XML
	 * document
	 * 
	 * @param root
	 *            XML document
	 * @param limit
	 *            it's an optional argument (int), default value set to "0"
	 * @return List{@literal <}String{@literal >} a list of the values of "String"
	 *         tags of a XML document
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
	 * @param root
	 *            XML document
	 * @param limit
	 *            it's an optional argument (int), default value set to "0"
	 * @return List{@literal <}String{@literal >} a list of the values of "String"
	 *         tags of a XML document
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
	 * This method is used to encode a String to be compatible as a url
	 * 
	 * @param arg
	 *            string to be encoded
	 * @return String encoded String to be compatible as a url
	 */
	private static String pmaQ(String arg) {
		if (arg == null) {
			return "";
		} else {
			try {
				return URLEncoder.encode(arg, "UTF-8").replace("+", "%20");
			} catch (Exception e) {
				System.out.print(e.getMessage());
				return "";
			}
		}
	}

	/**
	 * This method is used to get the list of sessions
	 * 
	 * @param pmaControlURL
	 *            URL for PMA.Control
	 * @param pmaCoreSessionID
	 *            PMA.core session ID
	 * @return JSONArray containing the list of sessions
	 */
	public static JSONArray getSessions(String pmaControlURL, String pmaCoreSessionID) {
		String url = join(pmaControlURL, "api/Sessions?sessionID=" + pmaQ(pmaCoreSessionID));
		System.out.println(url);
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
			String jsonString = getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * 
	 * This method is used to get the list of sessions' IDs
	 * 
	 * @param pmaControlURL
	 *            URL for PMA.Control
	 * @param pmaCoreSessionID
	 *            PMA.core session ID
	 * @return Mapt{@literal <}String, Mapt{@literal <}String,
	 *         Stringt{@literal >}t{@literal >} containing the sessions' IDs
	 */
	public static Map<String, Map<String, String>> getSessionIds(String pmaControlURL, String pmaCoreSessionID) {
		JSONArray fullSessions = getSessions(pmaControlURL, pmaCoreSessionID);
		Map<String, Map<String, String>> newSession = new HashMap<>();
		for (int i = 0; i < fullSessions.length(); i++) {
			Map<String, String> sessData = new HashMap<String, String>();
			sessData.put("LogoPath", fullSessions.getJSONObject(i).getString("LogoPath"));
			sessData.put("StartsOn", fullSessions.getJSONObject(i).getString("StartsOn"));
			sessData.put("EndsOn", fullSessions.getJSONObject(i).getString("EndsOn"));
			sessData.put("ModuleId", fullSessions.getJSONObject(i).getString("ModuleId"));
			sessData.put("State", fullSessions.getJSONObject(i).getString("State"));
			newSession.put(fullSessions.getJSONObject(i).getString("Id"), sessData);
		}
		return newSession;
	}

	/**
	 * This method is used to get case collections
	 * 
	 * @param pmaControlURL
	 *            URL for PMA.Control
	 * @param pmaCoreSessionID
	 *            PMA.core session ID
	 * @return JSONArray containing the list of case sessions
	 */
	public static JSONArray getCaseCollections(String pmaControlURL, String pmaCoreSessionID) {
		String url = join(pmaControlURL, "api/CaseCollections?sessionID=" + pmaQ(pmaCoreSessionID));
		System.out.println(url);
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
			String jsonString = getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to get the list of projects
	 * 
	 * @param pmaControlURL
	 *            URL for PMA.Control
	 * @param pmaCoreSessionID
	 *            PMA.core session ID
	 * @return JSONArray containing the list of projects
	 */
	public static JSONArray getProjects(String pmaControlURL, String pmaCoreSessionID) {
		String url = join(pmaControlURL, "api/Projects?sessionID=" + pmaQ(pmaCoreSessionID));
		System.out.println(url);
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
			String jsonString = getJSONAsStringBuffer(con).toString();
			JSONArray jsonResponse = getJSONArrayResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to check if there is a PMA.core.lite or PMA.core instance
	 * running
	 * 
	 * @param pmacoreURL
	 *            it's an optional argument (String), default value set to
	 *            "pmaCoreLiteURL"
	 * @return Boolean checking if there is a PMA.core.lite or PMA.core instance
	 *         running
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
	 * @param pmacoreURL
	 *            it's an optional argument (String), default value set to
	 *            "pmaCoreLiteURL"
	 * @return String version number
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
			return null;
		}
	}

	/**
	 * This method is used to get version info from PMA.control instance running at
	 * pmacontrolURL
	 * 
	 * @param pmaControlURL
	 *            PMA Control's URL
	 * @return JSONObject containing the version info
	 */
	public static JSONObject getVersionInfoPmaControl(String pmaControlURL) {
		// Get version info from PMA.control instance running at pmacontrolURL
		// why? because GetVersionInfo can be invoked WITHOUT a valid SessionID;
		// apiUrl() takes session information into account
		String url = join(pmaControlURL, "api/version");
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
			String jsonString = getJSONAsStringBuffer(con).toString();
			JSONObject jsonResponse = getJSONResponse(jsonString);
			return jsonResponse;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to authenticate &amp; connect to a PMA.core instance
	 * using credentials
	 * 
	 * @param pmacoreURL
	 *            it's an optional argument (String), default value set to
	 *            "pmaCoreLiteURL"
	 * @param pmacoreUsername
	 *            it's an optional argument (String), default value set to ""
	 * @param pmacorePassword
	 *            it's an optional argument (String), default value set to ""
	 * @return String sessionID of the successfully created session
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
			System.out.print(e.getMessage());
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
			return null;
		}
	}

	/**
	 * This method is used to disconnect from a running PMA.core instance
	 * 
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
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
	 * Getter for member pmaCoreLiteSessionID
	 * 
	 * @return pmaCoreLiteSessionID
	 */

	public static String getPmaCoreLiteSessionID() {
		return pmaCoreLiteSessionID;
	}

	/**
	 * This method is used to test if sessionID is valid and the server is online
	 * and reachable This method works only for PMA.core, don't use for PMA.start
	 * for it will return always false
	 * 
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return boolean true if sessionID is valid and the server is online and
	 *         reachable, false otherwise
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
			return false;
		}
	}

	/**
	 * This method is used to get root-directories available for a sessionID
	 * 
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return List{@literal <}String{@literal >} Array of root-directories
	 *         available to sessionID
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
	 * This method is used to get sub-directories available to sessionID in the
	 * start directory
	 * 
	 * @param startDir
	 *            Start directory
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @param recursive
	 *            it's an optional argument (can be Boolean or Integer), it defines
	 *            if recursion applies and if yes its depth
	 * 
	 * @return List{@literal <}String{@literal >} sub-directories available to
	 *         sessionID in the start directory
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
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if ((!(varargs[1] instanceof Integer) && !(varargs[1] instanceof Boolean)) && (varargs[1] != null)) {
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
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to get the first non empty directory
	 * 
	 * @param startDir
	 *            it's an optional argument (String), default value set to "null"
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return String Path to the first non empty directory
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
	 * start directory
	 * 
	 * @param startDir
	 *            Start directory
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @param recursive
	 *            it's an optional argument (can be Boolean or Integer), it defines
	 *            if recursion applies and if yes its depth
	 * @return List{@literal <}String{@literal >} list of slides available to
	 *         sessionID in the start directory
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
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if ((!(varargs[1] instanceof Integer) && !(varargs[1] instanceof Boolean)) && (varargs[1] != null)) {
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
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to determine the file extension for a slide's path
	 * 
	 * @param slideRef
	 *            slide's path
	 * @return String file extension for a slide's path
	 */
	public static String getSlideFileExtension(String slideRef) {
		// Determine the file extension for this slide
		return FilenameUtils.getExtension(slideRef);
	}

	/**
	 * This method is used to determine file name (with extension) for a slide's
	 * path
	 * 
	 * @param slideRef
	 *            slide's path
	 * @return String file name for a slide's path
	 */
	public static String getSlideFileName(String slideRef) {
		// Determine the file name (with extension) for this slide
		return FilenameUtils.getName(slideRef);
	}

	/**
	 * This method is used to get the UID for a specific slide
	 * 
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return String UID for a specific slide's path
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

	// public void getFingerPrint(String slideRef, Object... varargs) {
	// //Get the fingerprint for a specific slide
	// Boolean strict = false;
	// String sessionID = null;
	// if (varargs.length > 0) {
	// if (!(varargs[0] instanceof Boolean) && varargs[0] != null) {
	// throw new IllegalArgumentException("...");
	// }
	// strict = (Boolean) varargs[0];
	// }
	// if (varargs.length > 1) {
	// if (!(varargs[1] instanceof String) && varargs[1] != null) {
	// throw new IllegalArgumentException("...");
	// }
	// sessionID = (String) varargs[1];
	// }
	// sessionID = sessionId(sessionID);
	// try {
	// String url = apiUrl(sessionID, false) + "GetFingerprint?sessionID=" +
	// pmaQ(sessionID) + "&strict=" + pmaQ(strict.toString()) + "&pathOrUid=" +
	// pmaQ(slideRef);
	// URL urlResource = new URL(url);
	// HttpURLConnection con;
	// if (url.startsWith("https")) {
	// con = (HttpsURLConnection) urlResource.openConnection();
	// } else {
	// con = (HttpURLConnection) urlResource.openConnection();
	// }
	// con.setRequestMethod("GET");
	// String jsonString = getJSONAsStringBuffer(con).toString();
	// }
	//
	//
	// catch (Exception e) {
	// System.out.print(e.getMessage());
	//
	// }
	// }

	/**
	 * This method is under construction
	 * 
	 * @return String information about session (Under construction)
	 */
	public static String whoAmI() {
		// Getting information about your Session (under construction)
		System.out.println("Under construction");
		return "Under construction";
	}

	/**
	 * This method is a getter for class member "pmaSessions"
	 * 
	 * @return Map{@literal <}String, Object{@literal >} value of class member
	 *         "pmaSessions"
	 */
	public static Map<String, Object> sessions() {
		return pmaSessions;
	}

	/**
	 * This method is used to get tile size information for sessionID
	 * 
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return List{@literal <}Integer{@literal >} two items (duplicated) list of
	 *         tile size information for sessionID
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
	 * 
	 * @param con
	 *            url to retrieve JSON from
	 * @return StringBuffer Json result
	 */
	public static StringBuffer getJSONAsStringBuffer(HttpURLConnection con) {
		try {
			BufferedReader in;
			if (Integer.toString(con.getResponseCode()).startsWith("2")) {
				in = new BufferedReader(new InputStreamReader(con.getInputStream()));
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
			return null;
		}
	}

	/**
	 * 
	 * @param value
	 *            json returned as String
	 * @return Boolean true if it's a JSONObject, false if it's an Array
	 */
	public static Boolean isJSONObject(String value) {
		if (value.startsWith("{")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * This method is used to get a JSONObject from a String argument
	 * 
	 * @param value
	 *            String argument
	 * @return JSONObject converts String argument to JSONObject
	 */
	public static JSONObject getJSONResponse(String value) {
		JSONObject jsonResponse = new JSONObject(value.toString());
		return jsonResponse;
	}

	/**
	 * This method is used to get a JSONArray from a String argument
	 * 
	 * @param value
	 *            String argument
	 * @return JSONArray converts String argument to JSONArray
	 */
	public static JSONArray getJSONArrayResponse(String value) {
		JSONArray jsonResponse = new JSONArray(value);
		return jsonResponse;
	}

	/**
	 * This method is used to get a raw image in the form of nested maps
	 * 
	 * @param slideRef
	 *            slide's path or UID
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return Map{@literal <}String, Object{@literal >} nested maps forming a raw
	 *         image
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
						throw new Exception("ImageInfo to " + slideRef + " resulted in: " + jsonResponse.get("Message")
								+ " (keep in mind that slideRef is case sensitive!)");
					} else if (jsonResponse.has("d")) {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = new ObjectMapper().readValue(jsonResponse.get("d").toString(),
								new TypeReference<Map<String, Object>>() {
								});
						((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(slideRef, jsonMap);
					} else {
						// we convert the Json object to a Map<String, Object>
						Map<String, Object> jsonMap = new ObjectMapper().readValue(jsonResponse.toString(),
								new TypeReference<Map<String, Object>>() {
								});
						((Map<String, Object>) pmaSlideInfos.get(sessionID)).put(slideRef, jsonMap);
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
				System.out.print(e.getMessage());
				return null;
			}
		}

		return (Map<String, Object>) ((Map<String, Object>) pmaSlideInfos.get(sessionID)).get(slideRef);
	}

	/**
	 * This method is used to determine the maximum zoom level that still represents
	 * an optical magnification
	 * 
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return int max zoom level that still represents an optical magnification
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
				return 0;
			}
		} else {
			try {
				return Integer.parseInt(info.get("NumberOfZoomLevels").toString());
			} catch (Exception e) {
				System.out.print("Something went wrong consulting the NumberOfZoomLevels key in info Map; value ="
						+ info.get("NumberOfZoomLevels").toString());
				return 0;
			}
		}
	}

	/**
	 * This method is used to get list with all zoom levels, from 0 to max zoom
	 * level
	 * 
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @param minNumberOfTiles
	 *            it's an optional argument (Integer), default value set to "0",
	 *            used to specify that you're only interested in zoom levels that
	 *            include at least a given number of tiles
	 * @return List{@literal <}Integer{@literal >} list with all zoom levels, from 0
	 *         to max zoom level
	 */
	public static List<Integer> getZoomLevelsList(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer minNumberOfTiles = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @param minNumberOfTiles
	 *            it's an optional argument (Integer), default value set to "0",
	 *            used to specify that you're only interested in zoom levels that
	 *            include at least a given number of tiles
	 * @return Map{@literal <}Integer,
	 *         List{@literal <}Integer{@literal >}{@literal >} map with the number
	 *         of tiles per zoom level
	 */
	public static Map<Integer, List<Integer>> getZoomLevelsDict(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String sessionID = null;
		Integer minNumberOfTiles = 0;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
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
	 * @param slideRef
	 *            slide's path
	 * @param zoomLevel
	 *            it's an optional argument (Integer), default value set to "null"
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return List{@literal <}Float{@literal >} two items list containing the
	 *         physical dimension in terms of pixels per micrometer of a slide
	 */
	public static List<Float> getPixelsPerMicrometer(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
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
	 * @param slideRef
	 *            slide's path
	 * @param zoomLevel
	 *            it's an optional argument (Integer), default value set to "null"
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return List{@literal <}Integer{@literal >} two items list with the total
	 *         dimensions of a slide image at a given zoom level
	 */
	public static List<Integer> getPixelDimensions(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
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
	 * @param slideRef
	 *            slide's path
	 * @param zoomLevel
	 *            it's an optional argument (Integer), default value set to "null"
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return List{@literal <}Integer{@literal >} three items list to determine the
	 *         number of tiles needed to reconstitute a slide at a given zoom level
	 */
	public static List<Integer> getNumberOfTiles(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof String) && varargs[1] != null) {
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return List{@literal <}Float{@literal >} two items list to determine the
	 *         physical dimensions of the sample represented by the slide
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return int number of channels for a slide (1 when slide is brightfield)
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return int number of layers for a slide
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return int number of Z-Stack layers for a slide
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return Boolean true if slide is a fluorescent image, false otherwise
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return Boolean true if slide contains multiple (stacked) layers, false
	 *         otherwise
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return Boolean true if slide is a z-stack, false otherwise
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
	 * @param slideRef
	 *            slide's path
	 * @param zoomLevel
	 *            it's an optional argument (Integer), default value set to "null"
	 * @param exact
	 *            it's an optional argument (Boolean), default value set to "null"
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return int magnification represented at a certain zoom level
	 */
	public static int getMagnification(String slideRef, Object... varargs) {
		// setting the default values when arguments' values are omitted
		Integer zoomLevel = null;
		Boolean exact = false;
		String sessionID = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof Integer) && varargs[0] != null) {
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Boolean) && varargs[1] != null) {
				throw new IllegalArgumentException("...");
			}
			exact = (Boolean) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof String) && varargs[2] != null) {
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
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return String URL that points to the barcode (alias for "label") for a slide
	 * @throws Exception
	 */
	public static String getBarcodeUrl(String slideRef, String... varargs) throws Exception {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the URL that points to the barcode (alias for "label") for a slide
		sessionID = sessionId(sessionID);
		String url = pmaUrl(sessionID) + "barcode" + "?SessionID=" + pmaQ(sessionID) + "&pathOrUid=" + pmaQ(slideRef);
		return url;
	}

	/**
	 * This method is used to get the barcode (alias for "label") image for a slide
	 * 
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return Image barcode (alias for "label") image for a slide
	 */
	public static Image getBarcodeImage(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the barcode (alias for "label") image for a slide
		sessionID = sessionId(sessionID);
		try {
			URL urlResource = new URL(getBarcodeUrl(slideRef, sessionID));
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to get the text encoded by the barcode
	 * 
	 * @param slideRef
	 *            slide's path or UID
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return Map{@literal <}String, Object{@literal >} Map containing the barcode
	 *         text
	 */
	public static Map<String, Object> getBarcodeText(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the text encoded by the barcode (if there IS a barcode on the slide to
		// begin with)
		sessionID = sessionId(sessionID);
		String url = apiUrl(sessionID, false) + "GetBarcode?sessionID=" + pmaQ(sessionID) + "&pathOrUid="
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
					throw new Exception("get_barcode_text on " + slideRef + " resulted in: "
							+ jsonResponse.get("Message") + " (keep in mind that slideRef is case sensitive!)");
				} else {
					// we convert the Json object to a Map<String, Object>
					Map<String, Object> jsonMap = new ObjectMapper().readValue(jsonResponse.get("d").toString(),
							new TypeReference<Map<String, Object>>() {
							});
					return jsonMap;
				}
			} else {
				return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * This method is used to get the URL that points to the label for a slide
	 * 
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return String url that points to the label for a slide
	 * @throws Exception
	 */
	public static String getLabelUrl(String slideRef, String... varargs) throws Exception {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the URL that points to the label for a slide
		return getBarcodeUrl(slideRef, sessionID);
	}

	/**
	 * This method is used to get the label image for a slide
	 * 
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return Image label Image for a slide
	 */
	public static Image getLabelImage(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the label image for a slide
		sessionID = sessionId(sessionID);
		try {
			URL urlResource = new URL(getLabelUrl(slideRef, sessionID));
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to get the URL that points to the thumbnail for a slide
	 * 
	 * @param slideRef
	 *            slide's path or UID
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return String URL that points to the thumbnail for a slide
	 * @throws Exception
	 */
	public static String getThumbnailUrl(String slideRef, String... varargs) throws Exception {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the URL that points to the thumbnail for a slide
		sessionID = sessionId(sessionID);
		String url = pmaUrl(sessionID) + "thumbnail" + "?SessionID=" + pmaQ(sessionID) + "&pathOrUid=" + pmaQ(slideRef);
		return url;
	}

	/**
	 * This method is used to get the thumbnail image for a slide
	 * 
	 * @param slideRef
	 *            slide's path
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return Image thumbnail image for a slide
	 */
	public static Image getThumbnailImage(String slideRef, String... varargs) {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Get the thumbnail image for a slide
		sessionID = sessionId(sessionID);
		try {
			String url = getThumbnailUrl(slideRef, sessionID);
			URL urlResource = new URL(url);
			URLConnection con = urlResource.openConnection();
			Image img = ImageIO.read(con.getInputStream());
			pmaAmountOfDataDownloaded.put(sessionID,
					pmaAmountOfDataDownloaded.get(sessionID) + con.getInputStream().toString().length());
			return img;
		} catch (Exception e) {
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to get a single tile at position (x, y)
	 * 
	 * @param slideRef
	 *            slide's path or UID
	 * @param x
	 *            it's an optional argument (Integer), default value set to "0"
	 * @param y
	 *            it's an optional argument (Integer), default value set to "0"
	 * @param zoomLevel
	 *            it's an optional argument (Integer), default value set to "null"
	 * @param zStack
	 *            it's an optional argument (Integer), default value set to "0"
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @param format
	 *            it's an optional argument (String), default value set to "jpg"
	 * @param quality
	 *            it's an optional argument (Integer), default value set to "100"
	 * @return Image single tile at position (x, y)
	 * @throws Exception
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
				throw new IllegalArgumentException("...");
			}
			x = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				throw new IllegalArgumentException("...");
			}
			y = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof String) && varargs[4] != null) {
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof String) && varargs[5] != null) {
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof Integer) && varargs[6] != null) {
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
		if (zoomLevel == null) {
			zoomLevel = 0;
		}
		String url;
		url = pmaUrl(sessionID);
		if (url == null) {
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
			System.out.print(e.getMessage());
			return null;
		}
	}

	/**
	 * This method is used to get all tiles with a (fromX, fromY, toX, toY)
	 * rectangle
	 * 
	 * @param slideRef
	 *            slide's path or UID
	 * @param fromX
	 *            it's an optional argument (Integer), default value set to "0"
	 * @param fromY
	 *            it's an optional argument (Integer), default value set to "0"
	 * @param toX
	 *            it's an optional argument (Integer), default value set to "null"
	 * @param toY
	 *            it's an optional argument (Integer), default value set to "null"
	 * @param zoomLevel
	 *            it's an optional argument (Integer), default value set to "null"
	 * @param zStack
	 *            it's an optional argument (Integer), default value set to "0"
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @param format
	 *            it's an optional argument (String), default value set to "jpg"
	 * @param quality
	 *            it's an optional argument (Integer), default value set to "100"
	 * @return Stream all tiles with a (fromX, fromY, toX, toY) rectangle
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
				throw new IllegalArgumentException("...");
			}
			fromX = (Integer) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Integer) && varargs[1] != null) {
				throw new IllegalArgumentException("...");
			}
			fromY = (Integer) varargs[1];
		}
		if (varargs.length > 2) {
			if (!(varargs[2] instanceof Integer) && varargs[2] != null) {
				throw new IllegalArgumentException("...");
			}
			toX = (Integer) varargs[2];
		}
		if (varargs.length > 3) {
			if (!(varargs[3] instanceof Integer) && varargs[3] != null) {
				throw new IllegalArgumentException("...");
			}
			toY = (Integer) varargs[3];
		}
		if (varargs.length > 4) {
			if (!(varargs[4] instanceof Integer) && varargs[4] != null) {
				throw new IllegalArgumentException("...");
			}
			zoomLevel = (Integer) varargs[4];
		}
		if (varargs.length > 5) {
			if (!(varargs[5] instanceof Integer) && varargs[5] != null) {
				throw new IllegalArgumentException("...");
			}
			zStack = (Integer) varargs[5];
		}
		if (varargs.length > 6) {
			if (!(varargs[6] instanceof String) && varargs[6] != null) {
				throw new IllegalArgumentException("...");
			}
			sessionID = (String) varargs[6];
		}
		if (varargs.length > 7) {
			if (!(varargs[7] instanceof String) && varargs[7] != null) {
				throw new IllegalArgumentException("...");
			}
			format = (String) varargs[7];
		}
		if (varargs.length > 8) {
			if (!(varargs[8] instanceof Integer) && varargs[8] != null) {
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
					return getTile(slideRef, x, y, varZoomLevel, varZStack, varSessionID, varFormat, varQualty);
				} catch (Exception e) {
					System.out.print(e.getMessage());
					return null;
				}
			}
		}).limit((varToX - varFromX + 1) * (varToY - varFromY + 1));
	}

	/**
	 * This method is used to launch the default web browser and load a web-based
	 * viewer for the slide
	 * 
	 * @param slideRef
	 *            slide's path or UID
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @throws Exception
	 */
	public static void showSlide(String slideRef, String... varargs) throws Exception {
		// setting the default value when arguments' value is omitted
		String sessionID = varargs.length > 0 ? varargs[0] : null;
		// Launch the default web browser and load a web-based viewer for the slide
		sessionID = sessionId(sessionID);
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
			System.out.print(e.getMessage());
		}
	}

	/**
	 * This method is used to get sub-directories available to sessionID in the
	 * start directory
	 * 
	 * @param slideRef
	 *            slide's path or UID
	 * @param sessionID
	 *            it's an optional argument (String), default value set to "null"
	 * @return List{@literal <}String{@literal >} List of all files related to
	 *         selected slide
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
			System.out.print(e.getMessage());
			return null;
		}
	}

}
