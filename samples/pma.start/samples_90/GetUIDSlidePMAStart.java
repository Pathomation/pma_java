package samples_90;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

public class GetUIDSlidePMAStart extends HttpServlet {

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
			String dir = Core.getFirstNonEmptyDirectory("/", sessionID);
			out.println("Looking for slides in " + dir + "<br/>");
			for (String slide : Core.getSlides(dir, sessionID)) {
				try {
					out.println(slide + " - " + Core.getUid(slide, sessionID) + "<br/>");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
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


