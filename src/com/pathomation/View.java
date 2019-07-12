package com.pathomation;

/**
 * View class to interact with PMA.view
 * 
 * @author Yassine Iddaoui
 *
 */
public class View {

	/**
	 * This method is used to determine whether the Java SDK runs in debugging mode
	 * or not. When in debugging mode (flag = true), extra output is produced when
	 * certain conditions in the code are not met
	 * 
	 * @param flag Debugging mode (activated or deactivated)
	 */
	public static void setDebugFlag(boolean flag) {
		PMA.setDebugFlag(flag);
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
	 * This method is used to get PMA.view version
	 * 
	 * @param pmaViewURL PMA.view URL
	 * @return PMA.view version
	 */
	public static String getVersionInfo(String pmaViewURL) {
		// purposefully DON'T use helper function apiUrl() here:
		// why? because GetVersionInfo can be invoked WITHOUT a valid SessionID;
		// apiUrl() takes session information into account

		String url = PMA.join(pmaViewURL, "api/json/GetVersionInfo");
		String version = "";
		try {
			// Are we looking at PMA.view/studio 2.x?
			if (PMA.debug) {
				System.out.println(url);
			}
			// we remove leading/trailing quotes
			String contents = PMA.urlReader(url).replaceAll("^\"+", "").replaceAll("\"+$", "").replaceAll("^'+", "")
					.replaceAll("'+$", "");
			return contents;
		} catch (Exception e) {
			version = null;
		}

		url = PMA.join(pmaViewURL, "viewer/version");
		try {
			// Oops, perhaps this is a PMA.view 1.x version
			if (PMA.debug) {
				System.out.println(url);
			}
			// we remove leading/trailing quotes
			String contents = PMA.urlReader(url).replaceAll("^\"+", "").replaceAll("\"+$", "").replaceAll("^'+", "")
					.replaceAll("'+$", "");
			return contents;
		} catch (Exception e) {
			version = null;
		}
		return version;
	}
}
