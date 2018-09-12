package samples_10;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.pathomation.*;

public class IdentifyingPMAStart extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		ServletOutputStream out = response.getOutputStream();
		out.println("<html>");
		// test for PMA.core.lite (PMA.start)
		out.println("Are you running PMA.core.lite? " + (Core.isLite() ? "Yes!" : "no :-(") + "<br />");
		out.println(
				"Seeing 'no' and want to see 'yes'? Make sure PMA.start is running on your system or download it from "
						+ "<a href = \"http://free.pathomation.com\">http://free.pathomation.com</a>");
		out.println("</html>");

	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		this.doGet(request, response);
	}

}
