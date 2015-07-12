package alex.offlinefictionmanager.discovery;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class TestDBConnection
{
	private static String SqlConnectionConfig = "sql_connection_config.xml";
	private static String SqlConnectionConfigSchema = "sql_connection_config.xsd";

	public static void main(String[] args)
	{
		TestDBConnection testconn = new TestDBConnection();
		testconn.run();
	}

	public void run()
	{
		String connectionUrl = LoadConnectionUrl();

		// Declare the JDBC objects.
		Connection con = null;
		Statement stmt = null;
		ResultSet rs = null;

		try
		{
			// Establish the connection.
			Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
			con = DriverManager.getConnection(connectionUrl);

			// Create and execute an SQL statement that returns some data.
			String SQL = "CREATE TABLE tbl_testcreate( mycol int NOT NULL, yourcol nvarchar(20) NOT NULL )";
			// SQL = "DROP TABLE tbl_testcreate";
			stmt = con.createStatement();
			stmt.execute(SQL);
			// rs = stmt.executeQuery(SQL);

			// SQL = "DROP TABLE tbl_testcreate";
			// stmt.execute(SQL);
			// Iterate through the data in the result set and display it.
			/*
			 * while (rs.next())
			 * {
			 * System.out.println(rs.getString(2)
			 * + " "
			 * + rs.getString(3));
			 * }
			 */
		}

		// Handle any errors that may have occurred.
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (rs != null)
				try
				{
					rs.close();
				}
				catch (Exception e)
				{
				}
			if (stmt != null)
				try
				{
					stmt.close();
				}
				catch (Exception e)
				{
				}
			if (con != null)
				try
				{
					con.close();
				}
				catch (Exception e)
				{
				}
		}
	}

	private String LoadConnectionUrl()
	{
		try
		{
			// URL schemaFile = new File(DiscoveryManager.ConfigurationSchemaFile).toURI().toURL();
			URL schemaFile = this.getClass().getResource(TestDBConnection.SqlConnectionConfigSchema);
			// Source xmlFile = new StreamSource(new File(DiscoveryManager.ConfigurationFile));
			Source xmlFile = new StreamSource(this.getClass().getResourceAsStream(TestDBConnection.SqlConnectionConfig));
			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = schemaFactory.newSchema(schemaFile);
			Validator validator = schema.newValidator();

			try
			{
				validator.validate(xmlFile);
			}
			catch (SAXException | IOException e)
			{
				// LogError("Failed to validate schema for Discovery Manager configuration.", e);
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(this.getClass().getResourceAsStream("sql_connection_config.xml"));

			HashMap<String, String> nameValueMap = new HashMap<String, String>();

			String connectionUrl = "jdbc:sqlserver://!server!;"
				+ "database=!database!;"
				+ "user=!username!;"
				+ "password=!password!;"
				+ "encrypt=!encrypt!;hostNameInCertificate=!hostNameInCertificate!;loginTimeout=30;";

			XPathFactory xpathFactory = XPathFactory.newInstance();
			XPath xpath = xpathFactory.newXPath();

			try
			{
				XPathExpression expr = xpath.compile("/SqlConnection/*");
				NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
				for (int i = 0; i < nodes.getLength(); ++i)
				{
					Element e = (Element) (nodes.item(i));
					String name = e.getTagName();
					String value = e.getTextContent();
					nameValueMap.put(name, value);
				}
			}
			catch (XPathExpressionException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			connectionUrl = connectionUrl.replaceAll("!server!", nameValueMap.get("server"));
			connectionUrl = connectionUrl.replaceAll("!database!", nameValueMap.get("database"));
			connectionUrl = connectionUrl.replaceAll("!username!", nameValueMap.get("username"));
			connectionUrl = connectionUrl.replaceAll("!password!", nameValueMap.get("password"));
			connectionUrl = connectionUrl.replaceAll("!encrypt!", nameValueMap.get("encrypt"));
			connectionUrl = connectionUrl.replaceAll("!hostNameInCertificate!",
				nameValueMap.get("hostNameInCertificate"));

			return connectionUrl;
		}
		catch (SAXException | ParserConfigurationException | IOException e)
		{
			// LogError("Unknown critical error when loading configuration for Discovery Manager.", e);
			throw new RuntimeException(e);
		}
	}
}
