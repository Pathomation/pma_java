package com.pathomation;

/**
 * View class to interact with PMA.view
 * 
 * @author Yassine Iddaoui
 *
 */
public class View {
	
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
			System.out.println(url);
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
			System.out.println(url);
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
