package samples_60;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

public class GettingDirectoriesPMAStart extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		ServletOutputStream out = response.getOutputStream();
		out.println("<html>");
		String sessionID = Core.connect();
		if (sessionID == null) {
			out.println("Unable to connect to PMA.start");
		} else {
			out.println("Successfully connected to PMA.start" + "<br/>");
			List<String> rootDirs = Core.getRootDirectories();
			out.println("Directories found in " + rootDirs.get(0) + ":" + "<br/>");
			List<String> dirs = Core.getDirectories(rootDirs.get(0), sessionID);
			for (String d : dirs) {
				out.println(d + "<br/>");
			}
			// not always needed; depends on whether the client (e.g. browser) still needs to SessionID as well
			Core.disconnect(sessionID);
		}
		out.println("</html>");			
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}
