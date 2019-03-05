package com.pathomation.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.pathomation.Core;

/**
 * Wrapper class around PMA.UI JavaScript framework
 * 
 * @author Yassine Iddaoui
 *
 */
public class UI {

	public static String pmaStartUIJavascriptPath = "http://localhost:54001/Scripts/pmaui/";
	public static String pmaUIJavascriptPath = "pma.ui/";
	public static boolean pmaUIFrameworkEmbedded = false;
	public static int pmaUIViewportCount = 0;
	public static List<String> pmaUIViewports = new ArrayList<>();
	public static int pmaUIGalleryCount = 0;
	public static List<String> pmaUIGalleries = new ArrayList<>();
	public static int pmaUILoaderCount = 0;
	public static List<String> pmaUILoaders = new ArrayList<>();
	// for logging purposes
	public static Logger logger = null;

	/**
	 * Internal helper function to prevent PMA.UI framework from being loaded more
	 * than once
	 * 
	 * @param sessionID session ID of the actual session
	 * @return The links related to JS and CSS files to be included
	 */
	public static String pmaEmbedPmaUIFramework(String sessionID) {
		if (!pmaUIFrameworkEmbedded) {
			if (!pmaStartUIJavascriptPath.endsWith("/")) {
				pmaUIJavascriptPath += "/";
			}
		}
		String htmlCode = "<!-- include PMA.UI script & css -->\n" + "<script src='" + pmaUIJavascriptPath
				+ "pma.ui.view.min.js' type='text/javascript'></script>\n" + "<link href='" + pmaUIJavascriptPath
				+ "pma.ui.view.min.css' type='text/css' rel='stylesheet'>\n"
				+ "<!-- include PMA.UI.components script & css -->\n" + "<script src='" + pmaUIJavascriptPath
				+ "PMA.UI.components.all.min.js' type='text/javascript'></script>\n" + "<link href='"
				+ pmaUIJavascriptPath + "PMA.UI.components.all.min.css' type='text/css' rel='stylesheet'>\n"
				+ "<script>\n\tvar pma_ui_context = new PMA.UI.Components.Context({\n\t\tcaller: 'PMA.JAVA UI class'\n\t});\n</script>\n";
		// System.out.print("<!-- include PMA.UI script & css -->\n");
		// System.out.print(
		// "<script src='" + pmaUIJavascriptPath + "pma.ui.view.min.js'
		// type='text/javascript'></script>\n");
		// System.out.print(
		// "<link href='" + pmaUIJavascriptPath + "pma.ui.view.min.css' type='text/css'
		// rel='stylesheet'>\n");
		// System.out.print("<!-- include PMA.UI.components script & css -->\n");
		// System.out.print("<script src='" + pmaUIJavascriptPath
		// + "PMA.UI.components.all.min.js' type='text/javascript'></script>\n");
		// System.out.print("<link href='" + pmaUIJavascriptPath
		// + "PMA.UI.components.all.min.css' type='text/css' rel='stylesheet'>\n");
		// System.out.print(
		// "<script>var pma_ui_context = new PMA.UI.Components.Context({ caller:
		// 'PMA.JAVA UI class' });</script>");
		pmaUIFrameworkEmbedded = true;
		return htmlCode;
	}

	/**
	 * Output HTML code to display a single slide through a PMA.UI viewport control
	 * authentication against PMA.core happens through a pre-established
	 * 
	 * @param server    PMA.Start or PMA.core instance
	 * @param slideRef  path to the slide
	 * @param sessionID session id of the actual session
	 * @param varargs   Array of optional arguments
	 *                  <p>
	 *                  options : First optional argument(Map{@literal <}String,
	 *                  String{@literal >}), default value(null), Map of the options
	 *                  defining the display of the view port
	 *                  </p>
	 * @return List of embed slides
	 */
	@SuppressWarnings("unchecked")
	public static List<String> embedSlideBySessionID(String server, String slideRef, String sessionID,
			Map<String, String>... varargs) {
		// setting the default value when argument's value is omitted
		Map<String, String> options = varargs.length > 0 ? varargs[0] : null;
		// pmaEmbedPmaUIFramework(sessionID);
		pmaUIViewportCount++;
		String viewPortID = "pma_viewport" + pmaUIViewportCount;
		pmaUIViewports.add(viewPortID);
		String htmlCode = pmaEmbedPmaUIFramework(sessionID) + "<div id=\"" + viewPortID + "\"></div>\n"

				+ "<script type=\"text/javascript\">\n"
				// initialize the viewport
				+ "\tvar " + viewPortID + "= new PMA.UI.View.Viewport({\n" + "\t\tcaller: \"PMA.JAVA UI class\",\n"
				+ "\t\telement: \"#" + viewPortID + "\",\n" + "\t\timage: \"" + slideRef + "\",\n"
				+ "\t\tserverUrls: [\"" + server + "\"],\n" + "\t\tsessionID: \"" + sessionID + "\"\n" + "\t\t},\n"
				+ "\t\tfunction () {\n" + "\t\t\tconsole.log(\"Success!\");\n" + "\t\t},\n" + "\t\tfunction () {\n"
				+ "\t\t\tconsole.log(\"Error! Check the console for details.\");\n" + "\t\t});\n" + "</script>";
		List<String> results = new ArrayList<>();
		results.add(htmlCode);
		results.add(viewPortID);
		return results;
		// return htmlCode;
		// return viewPortID;
	}

	/**
	 * Output HTML code to display a single slide through a PMA.UI viewport control
	 * authentication against PMA.core happens in real-time through the provided
	 * $username and $password credentials Note that the username and password and
	 * NOT rendered in the HTML output (authentication happens on the server-side).
	 * 
	 * @param server   PMA.Start or PMA.core instance
	 * @param slideRef path to the slide
	 * @param username credentials' username
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 password : First optional argument(String), default
	 *                 value(""), credentials' password
	 *                 </p>
	 *                 <p>
	 *                 options : Second optional argument(Map{@literal <}String,
	 *                 String{@literal >}), default value(null), Map of the options
	 *                 defining the display of the view port
	 *                 </p>
	 * @return List of embed slides by username
	 */

	@SuppressWarnings("unchecked")
	public static List<String> embedSlideByUsername(String server, String slideRef, String username,
			Object... varargs) {
		// setting the default values when arguments' values are omitted
		String password = "";
		Map<String, String> options = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("embedSlideByUsername() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			password = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Map<?, ?>) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("embedSlideByUsername() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}

			options = (Map<String, String>) varargs[1];
		}

		String session = Core.connect(server, username, password);
		return embedSlideBySessionID(server, slideRef, session, options);
	}

	/**
	 * Output HTML code to display a gallery that shows all thumbnails that exist in
	 * a specific folder hosted by the specified PMA.core instance authentication
	 * against PMA.core happens through a pre-established SessionID
	 * 
	 * @param server    PMA.Start or PMA.core instance
	 * @param path      folder containing the slides
	 * @param sessionID session id of the actual session
	 * @param varargs   Array of optional arguments
	 *                  <p>
	 *                  options : First optional argument(Map{@literal <}String,
	 *                  String{@literal >}), default value(null), Map of the options
	 *                  defining the display of the view port
	 *                  </p>
	 * @return List of embed galleries by session ID
	 */
	@SuppressWarnings("unchecked")
	public static List<String> embedGalleryBySessionID(String server, String path, String sessionID,
			Map<String, String>... varargs) {
		// setting the default value when argument's value is omitted
		Map<String, String> options = varargs.length > 0 ? varargs[0] : null;
		pmaUIGalleryCount++;
		String galleryID = "pma_gallery" + pmaUIGalleryCount;
		pmaUIGalleries.add(galleryID);
		String htmlCode = pmaEmbedPmaUIFramework(sessionID) + "<div id=\"" + galleryID + "\"></div>\n"
				+ "<script type=\"text/javascript\">\n"
				+ "\tnew PMA.UI.Authentication.SessionLogin(pma_ui_context, [{\n\t\tserverUrl: \"" + server
				+ "\",\n\t\tsessionId: \"" + sessionID + "\"\n\t}]);\n"

				// create a gallery that will display the contents of a directory
				+ "\tvar " + galleryID + " = new PMA.UI.Components.Gallery(pma_ui_context, {\n" + "\t\telement: \"#"
				+ galleryID + "\",\n" + "\t\tthumbnailWidth: 200,\n" + "\t\tthumbnailHeight: 150,\n" + "\t\tmode: \""
				+ ((options != null) ? (options.containsKey("mode") ? options.get("mode") : "horizontal")
						: "horizontal")
				+ "\",\n" + "\t\tshowFileName: true,\n" + "\t\tshowBarcode: true,\n" + "\t\tbarcodeRotation: 180,\n"
				+ "\t\tfilenameCallback: function (path) {\n"
				// show the filename without extension
				+ "\t\t\treturn path.split('/').pop().split('.')[0];\n" + "\t\t}\n" + "\t});\n"

				// load the contents of a directory
				+ "\t" + galleryID + ".loadDirectory(\"" + server + "\", \"" + path + "\");\n" + "</script>";
		List<String> results = new ArrayList<>();
		results.add(htmlCode);
		results.add(galleryID);
		return results;
	}

	/**
	 * Output HTML code to display a gallery that shows all thumbnails that exist in
	 * a specific folder hosted by the specified PMA.core instance authentication
	 * against PMA.core happens in real-time through the provided $username and
	 * $password credentials Note that the username and password are NOT rendered in
	 * the HTML output (authentication happens on the server-side). * @param server
	 * PMA.Start of PMA.core
	 * 
	 * @param server   PMA.Start or PMA.core instance
	 * @param path     folder containing the slides
	 * @param username credentials' username
	 * @param varargs  Array of optional arguments
	 *                 <p>
	 *                 password : First optional argument(String), default
	 *                 value(""), credentials' password
	 *                 </p>
	 *                 <p>
	 *                 options : Second optional argument(Map{@literal <}String,
	 *                 String{@literal >}), default value(null), Map of the options
	 *                 defining the display of the view port
	 *                 </p>
	 * @return List of embed galleries by username
	 */
	@SuppressWarnings("unchecked")
	public static List<String> embedGalleryByUsername(String server, String path, String username, Object... varargs) {
		// setting the default values when arguments' values are omitted
		String password = "";
		Map<String, String> options = null;
		if (varargs.length > 0) {
			if (!(varargs[0] instanceof String) && varargs[0] != null) {
				if (logger != null) {
					logger.severe("embedGalleryByUsername() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}
			password = (String) varargs[0];
		}
		if (varargs.length > 1) {
			if (!(varargs[1] instanceof Map<?, ?>) && varargs[1] != null) {
				if (logger != null) {
					logger.severe("embedGalleryByUsername() : Illegal argument");
				}
				throw new IllegalArgumentException("...");
			}

			options = (Map<String, String>) varargs[1];
		}
		String session = Core.connect(server, username, password);
		return embedGalleryBySessionID(server, path, session, options);
	}

	/**
	 * Output HTML code to couple an earlier instantiated PMA.UI gallery to a PMA.UI
	 * viewport. The PMA.UI viewport can be instantiated earlier, or not at all
	 * 
	 * @param galleryDiv  HTML Div holding the gallery
	 * @param viewportDiv HTML Div holding the viewport
	 * @return Html code generated couple an earlier instantiated PMA.UI gallery to
	 *         a PMA.UI viewport
	 * @throws Exception if galleryDiv is not a PMA.UI gallery or galleryDiv is not
	 *                   a valid PMA.UI gallery container or viewportDiv is not a
	 *                   PMA.UI viewport
	 */
	public static String linkGalleryToViewport(String galleryDiv, String viewportDiv) throws Exception {
		// verify the validity of the galleryDiv argument
		if (pmaUIViewports.contains(galleryDiv)) {
			if (logger != null) {
				logger.severe(
						"galleryDiv is not a PMA.UI gallery (it's actually a viewport; did you switch the arguments up?)");
			}
			throw new Exception(
					"galleryDiv is not a PMA.UI gallery (it's actually a viewport; did you switch the arguments up?)");
		}
		if (!pmaUIGalleries.contains(galleryDiv)) {
			if (logger != null) {
				logger.severe("galleryDiv is not a valid PMA.UI gallery container");
			}
			throw new Exception("galleryDiv is not a valid PMA.UI gallery container");
		}

		// verify the validity of the $viewportDiv argument
		if (pmaUIGalleries.contains(viewportDiv)) {
			if (logger != null) {
				logger.severe(
						"viewportDiv is not a PMA.UI viewport (it's actually a gallery; did you switch the arguments up?)");
			}
			throw new Exception(
					"viewportDiv is not a PMA.UI viewport (it's actually a gallery; did you switch the arguments up?)");
		}

		pmaUILoaderCount++;
		String loaderID = "pma_slideLoader" + pmaUILoaderCount;
		pmaUILoaders.add(loaderID);

		String htmlCode = "";
		if (!pmaUIViewports.contains(viewportDiv)) {
			// viewport container doesn't yet exist, but this doesn't have to be a
			// showstopper; just create it on the fly
			pmaUIViewports.add(viewportDiv);
			pmaUIViewportCount++;
			htmlCode = "<div id=\"" + viewportDiv + "\"></div>\n";
		}

		htmlCode += "<script>\n"
				// create an image loader that will allow us to load images easily
				+ "\tvar " + loaderID + " = new PMA.UI.Components.SlideLoader(pma_ui_context, {\n" + "\t\telement: \"#"
				+ viewportDiv + "\",\n" + "\t\ttheme: PMA.UI.View.Themes.Default,\n" + "\t\toverview: {\n"
				+ "\t\t\tcollapsed: false\n" + "\t\t},\n"
				// the channel selector is only displayed for images that have multiple channels
				+ "\t\tchannels: {\n" + "\t\t\tcollapsed: false\n" + "\t\t},\n"
				// the barcode is only displayed if the image actually contains one
				+ "\t\tbarcode: {\n" + "\t\t\tcollapsed: false,\n" + "\t\t\trotation: 180\n" + "\t\t},\n"
				+ "\t\tloadingBar: true,\n" + "\t\tsnapshot: true,\n" + "\t\tdigitalZoomLevels: 2,\n"
				+ "\t\tscaleLine: true,\n" + "\t\tfilename: true\n" + "\t});\n"

				// listen for the slide selected event to load the selected image when clicked
				+ "\t" + galleryDiv + ".listen(PMA.UI.Components.Events.SlideSelected, function (args) {\n"
				// load the image with the image loader
				+ "\t\t" + loaderID + ".load(args.serverUrl, args.path);\n" + "\t});\n" + "</script>";
		return htmlCode;
	}

}
