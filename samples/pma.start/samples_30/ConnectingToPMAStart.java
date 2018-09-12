package samples_30;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

public class ConnectingToPMAStart extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
			
		ServletOutputStream out = response.getOutputStream();
		String sessionID = Core.connect();
		out.println("<html>");
		if (sessionID == null) {
			out.println("Unable to connect to PMA.start");
		} else {
			out.println("Successfully connected to PMA.start; sessionID = " + sessionID);
			// not always needed; depends on whether the client (e.g. browser) still needs to SessionID as well
			Core.disconnect();
		}
		out.println("</html>");	
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}

