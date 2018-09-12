package samples_40;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.Core;

public class GettingDriveLettersFromPMAStart extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		ServletOutputStream out = response.getOutputStream();
		out.println("<html>");
		// establish a default connection to PMA.start
		if (Core.connect() != null) {
			out.println("The following drives were found on your system:" + "<br/>");
			for (String rootDirectory : Core.getRootDirectories()) {
				out.println(rootDirectory + "<br/>");
			}
			out.println("Can't find all the drives you're expecting? For network-connectivity (e.g. mapped drive access) you need PMA.core instead of PMA.start");
		} else {
			out.println("Unable to find PMA.start");
		}
		out.println("</html>");			
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}

