package samples_20;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

public class GettingVersionInformationPMAStart extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		ServletOutputStream out = response.getOutputStream();
		out.println("<html>");
		// test for PMA.core.lite (PMA.start)
		if (!Core.isLite()) {
			// don't bother running this script if PMA.start isn't active
			out.println("PMA.start is not running. Please start PMA.start first");
		} else {
			// assuming we have PMA.start running; what's the version number?
			out.println("You are running PMA.start version " + Core.getVersionInfo());
		}
		out.println("</html>");
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}
