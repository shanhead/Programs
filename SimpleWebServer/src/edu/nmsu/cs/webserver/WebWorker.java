package edu.nmsu.cs.webserver;

/**
 * Web worker: an object of this class executes in its own new thread to receive and respond to a
 * single HTTP request. After the constructor the object executes on its "run" method, and leaves
 * when it is done.
 *
 * One WebWorker object is only responsible for one client connection. This code uses Java threads
 * to parallelize the handling of clients: each WebWorker runs in its own thread. This means that
 * you can essentially just think about what is happening on one client at a time, ignoring the fact
 * that the entirety of the webserver execution might be handling other clients, too.
 *
 * This WebWorker class (i.e., an object of this class) is where all the client interaction is done.
 * The "run()" method is the beginning -- think of it as the "main()" for a client interaction. It
 * does three things in a row, invoking three methods in this class: it reads the incoming HTTP
 * request; it writes out an HTTP header to begin its response, and then it writes out some HTML
 * content for the response content. HTTP requests and responses are just lines of text (in a very
 * particular format).
 * 
 * @author Jon Cook, Ph.D.
 *
 **/

/*
 * Code modified by:    Shannon Head
 * Date:                19 February 2021
 * Changes made:        - Implemented ability to locate and serve HTML files, if they can be located
 * 						- If files are requested but not found, 404 Not Found error will be served
 * 						- Separated writeContent() function into 3 functions: writeGenericContent()
 * 						where no filepath is provided, writeFileContent() where the filepath provided
 * 						could be located, and writeError(), where the path was incorrect
 * 						- Implemented the file reader to parse for tags <cs371server> and <cs371date>,
 * 						and replace them with custom server message and date format, respectively
 */

/*
 * Code Modified by:	Shannon Head
 * Date:				28 February 2021
 * Changes Made:		1) Cleaned up and reorganized code from Program 1 (P1) by:
 * 							- rewriting function run() entirely and modifying other functions as 
 * 							necessary to match changes. 
 * 							- combining functions writeGenericContent(), writeFileContent(), and
 * 							writeError() back into a single function writeContent() which is called
 * 							once at the end of run().
 * 						2) Modified run() to parse the requested filepath for file type to be passed to 
 * 						writeHTTPHeader() as parameter contentType.
 * *Not done*						3) Implemented the ability to process images
 */

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.imageio.ImageIO;

public class WebWorker implements Runnable
{

	private Socket socket;

	/**
	 * Constructor: must have a valid open socket
	 **/
	public WebWorker(Socket s)
	{
		socket = s;
	}

	/**
	 * Worker thread starting point. Each worker handles just one HTTP request and then returns, which
	 * destroys the thread. This method assumes that whoever created the worker created it with a
	 * valid open socket object.
	 **/
	public void run()
	{
		String conType = "";  			//content type
		String path, fileType;
		boolean valid = false;			// will become true if a valid file is found
		boolean isImage = true;			// passed to writeContent
		File tempfile;					// or if no file is requested.
		
		System.err.println("Handling connection...");
		
		
		try
		{			
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			path = readHTTPRequest(is);

			// case: no file requested
			if (path == null)
			{
				conType = "text/html";
				isImage = false;
				valid = true;
			}
			
			// case: file requested; identify file type and attempt to validate.
			else 
			{
				fileType = path.substring(path.indexOf('.') + 1);
				fileType = fileType.toLowerCase();
				
				tempfile = new File(path);
				if (tempfile.exists())
					valid = true;
				
				switch (fileType)
				{
				case "html":
					isImage = false;
					conType = "text/html";
					break;
				case "gif":
				case "jpeg":
				case "png":
					conType = "image/".concat(fileType);
					break;
				case "ico":
					conType = "image/x-icon";
				default:
					break;
				}
			}
			
			// with all information gathered, call other methods.
			writeHTTPHeader(os, conType, valid);
			writeContent(os, path, isImage);
			os.flush();
			socket.close();
			
		}
		catch (Exception e)
		{
			e.printStackTrace();
		} // end catch
		
		System.err.println("Done handling connection.");
		return; 

	} // end function run

	/**
	 * Read the HTTP request header.
	 * 
	 * @return path
	 * 			String representing filepath of requested file, if one exists.
	 **/
	private String readHTTPRequest(InputStream is) throws IOException
	{
		String line;
		String path = "";
		BufferedReader r = new BufferedReader(new InputStreamReader(is));
		boolean keepGoing = true;
		
		
		while (keepGoing)
		{
			try
			{
				while (!r.ready())
				{
					Thread.sleep(1);
				}
				
				line = r.readLine();
				System.err.println("Request line: (" + line + ")");
				
				// case: null line, end of http request
				if (line.length() == 0)
					keepGoing = false;
				
                // case: GET request, parse for filepath
				else if (line.substring(0, 3).compareTo("GET") == 0)
				{
					path = line.substring(line.indexOf(' ') + 1);
					path = path.substring(0, path.indexOf(' '));
					
					// case: no supplied path
					if (path.length() < 2)
						path = null;
					
            	    // case: supply rest of filepath by getting current working directory
					else
					{
						String directory = System.getProperty("user.dir");
						path = directory.concat(path);
					}
				}
				
				else continue;
			}
			
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return path;
	} //end readHTTPRequest

	/**
	 * Write the HTTP header lines to the client network connection.
	 * 
	 * @param os
	 *          is the OutputStream object to write to
	 * @param contentType
	 *          is the string MIME content type (e.g. "text/html")
	 * @param valid
	 *          is a boolean indicating whether to send 200 OK or 404 message in header
	 **/
	private void writeHTTPHeader(OutputStream os, String contentType, boolean valid) throws Exception
	{
		Date d = new Date();
		DateFormat df = DateFormat.getDateTimeInstance();
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
      
		if (valid)
			os.write("HTTP/1.1 200 OK\n".getBytes());
		else
			os.write("HTTP/1.0 404\n".getBytes());
         
      	os.write("Date: ".getBytes());
		os.write((df.format(d)).getBytes());
		os.write("\n".getBytes());
		os.write("Server: Shan's CS 371 server\n".getBytes());
//		os.write("Last-Modified: Mon, 22 Feb 2021 18:17:15 MST\n".getBytes());
//		os.write("Content-Length: 438\n".getBytes());
		os.write("Connection: close\n".getBytes());
		os.write("Content-Type: ".getBytes());
		os.write(contentType.getBytes());
		os.write("\n\n".getBytes()); // HTTP header ends with 2 newlines
		return;
	} // end function writeHTTPHeader

	/**
	 * Write HTML text content to client connection; must be done after writing HTTP header
	 * 
	 * @param os
	 * 			is the OutputStream object to write to
	 * @param path
	 * 			is the filepath the user is trying to access; null for generic content.
	 * @param isPic
	 * 			is the boolean indicating whether the requested file is an image
	 */
	
	private void writeContent(OutputStream os, String path, boolean isPic) throws Exception
	{
		
		String serverTag = "<cs371server>";
		String dateTag= "<cs371date>";
		String line;
		
		// case: no path requested, write generic content
		if (path == null)
		{
			os.write("<html lang=\"en\"><head><title>Homepage</title></head><body>\n".getBytes());
			os.write("<h3>Welcome to Shan's 371 Simple Web Server!</h3>\n".getBytes());
			os.write("</body></html>\n".getBytes());
			return;
		}
		
		// case: path exists
		try
		{
			File file = new File(path);
			
			if (isPic)
			{
				String format = path.substring(path.indexOf('.') + 1);
				BufferedImage pic = ImageIO.read(file);
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(pic, format, baos);
				os.write(baos.toByteArray());
			}
			
			else
			{	
				BufferedReader r = new BufferedReader(new FileReader(file));
			
				while (true)
				{
					line = r.readLine();
					if (line == null)
						break;
				
					// replaces <cs371server> tag with my server information
					if (line.contains(serverTag))
					{
						String myServer = "Shan's CS371 Server";
						line = line.replaceAll(serverTag, myServer);
					}
	            
					// replaces <cs371date> tag with the current formatted date
					if ( line.contains( dateTag ))
					{
						String form = "EEEEEEEEE, dd MMMMMMMMM yyyy";
						SimpleDateFormat dateForm = new SimpleDateFormat( form );
						Date date = new Date();
						String dateStr = dateForm.format( date );
						line = line.replaceAll( dateTag, dateStr );
					}
	            
					os.write(line.getBytes());
				}
			}
		}
			
		catch (Exception e)
		{
			System.err.println("\tError occurred in writeContent: " + e);
		    os.write("<html><head></head><body>\n".getBytes());
		    os.write("<h3>404 Not Found</h3>\n".getBytes());
		    os.write("<h3>The page you were looking for could not be found</h3>\n".getBytes());
		    os.write("</body></html>\n".getBytes());
		}
	
	}
	
} // end class