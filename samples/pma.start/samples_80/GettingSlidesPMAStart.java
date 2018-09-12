package samples_80;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

public class GettingSlidesPMAStart extends HttpServlet {

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
			// for this demo, we don't know where we can expect to find actual slides
			// the getFirstNonEmptyDirectory() method wraps around recursive calls to getDirectories() and is useful to "just" find a bunch of slides in "just" any folder
			String dir = Core.getFirstNonEmptyDirectory("/", sessionID);
			out.println("Looking for slides in " + dir + ":" + "<br/>");
			List<String> slides = Core.getSlides(dir, sessionID);
			for (String slide : slides) {
				out.println(slide + "<br/>");
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

