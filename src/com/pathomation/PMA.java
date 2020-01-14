package com.pathomation;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.filechooser.FileSystemView;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Helper class
 * 
 * @author Yassine Iddaoui
 *
 */
public class PMA {

	private static Map<String, String> urlContent = new HashMap<>();
	/**
	 * Cache mechanism to store the disk names on end user's side (To interact with
	 * PMA.start, paths should include the disk names if they are modified from
	 * their default value by end users)
	 */
	private static Map<String, String> diskLabels = new HashMap<String, String>();
	public static boolean debug = false;
	/**
	 * for logging purposes
	 */
	public static Logger logger = null;

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
	 * This method is used to cache results from requested URLs (GET method)
	 * 
	 * @param url      URL to request
	 * @param property Header value
	 * @return Data returned following a request to a specific URL
	 */
	public static String httpGet(String url, String property) {
		if (!urlContent.containsKey(url)) {
			if (debug) {
				System.out.println("Retrieving " + url);
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
				con.setRequestProperty("Accept", property);
				urlContent.put(url, getJSONAsStringBuffer(con).toString());
			} catch (Exception e) {
				e.printStackTrace();
				if (PMA.logger != null) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					PMA.logger.severe(sw.toString());
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
	 * This method is used to determine whether the Java SDK runs in debugging mode
	 * or not. When in debugging mode (flag = true), extra output is produced when
	 * certain conditions in the code are not met
	 * 
	 * @param flag Debugging mode (activated or deactivated)
	 */
	public static void setDebugFlag(Boolean flag) {
		debug = flag;
		if (flag) {
			System.out.println(
					"Debug flag enabled. You will receive extra feedback and messages from the Java SDK (like this one)");
			if (PMA.logger != null) {
				PMA.logger.severe(
						"Debug flag enabled. You will receive extra feedback and messages from the Java SDK (like this one)");
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
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
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
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
			}
			return null;
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
	public static List<String> xmlToStringArray(Document root, Integer... varargs) {
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
	public static List<String> xmlToStringArray(Element root, Integer... varargs) {
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
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
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
	public static JSONObject getJSONObjectResponse(String value) {
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
			if (PMA.logger != null) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				PMA.logger.severe(sw.toString());
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
		if (displayName.matches(".*\\s\\(..\\)") && !displayName.matches("Local\\sDisk\\s\\(..\\)") && !displayName.matches("Removable\\sDisk\\s\\(..\\)")) {
			value = displayName + "/" + value.substring(root.length());
		}
		return value.replace("\\", "/");
	}
}
