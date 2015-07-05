package alex.offlinefictionmanager.discovery;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * 
 * @author Alex
 *
 */
public class DiscoveryManager
{
	public static String ConfigurationFile = "discovery_config.xml";
	public static String ConfigurationSchemaFile = "discovery_config.xsd";

	/**
	 * The maximum number of Internet bound requests we will do per minute.
	 * The purpose of this limit is to reduce the effect of the tool on the sites we are accessing.
	 */
	@LoadFromXml
	private int maxRequestsPerMinuteRate;

	/**
	 * The maximum number of requests we will make per session.
	 * The purpose of this limit is to reduce the effect of the tool on the computer it runs on.
	 */
	@LoadFromXml
	private int maxDiscoverLimitPerSession;

	public static void main(String[] args)
	{
		// Initial plan:
		// Load starting point for searching for new stories to download
		// Load list of "already downloaded stories"
		// Attempt to discover new stories - existing stories should not be re-added
		// With list of new stories, chapters, send list to next step - new stories should be broken down into chapter
		// download requests

		DiscoveryManager manager = new DiscoveryManager();
		manager.LoadConfiguration();
		manager.Discover();
		manager.RequestDownload();

		System.out.println(manager.maxRequestsPerMinuteRate);
		System.out.println(manager.maxDiscoverLimitPerSession);
	}

	public DiscoveryManager()
	{
	}

	public void LoadConfiguration()
	{
		try
		{
			URL schemaFile = new File(DiscoveryManager.ConfigurationSchemaFile).toURI().toURL();
			Source xmlFile = new StreamSource(new File(DiscoveryManager.ConfigurationFile));
			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = schemaFactory.newSchema(schemaFile);
			Validator validator = schema.newValidator();

			try
			{
				validator.validate(xmlFile);
			}
			catch (SAXException | IOException e)
			{
				LogError("Failed to validate schema for Discovery Manager configuration.", e);
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new RuntimeException(e);
			}

			File fXmlFile = new File(DiscoveryManager.ConfigurationFile);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);

			Field[] fields = this.getClass().getDeclaredFields();
			for (Field f : fields)
			{
				if (f.isAnnotationPresent(LoadFromXml.class))
				{
					String fieldName = f.getName();
					try
					{
						f.setInt(this, 2);
					}
					catch (IllegalArgumentException | IllegalAccessException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		catch (SAXException | ParserConfigurationException | IOException e)
		{
			LogError("Unknown critical error when loading configuration for Discovery Manager.", e);
			throw new RuntimeException(e);
		}

	}

	public void Discover()
	{

	}

	public void RequestDownload()
	{

	}

	private void LogError(String message, Exception e)
	{
		System.err.println(message);
		System.err.println(e.getMessage());
		e.printStackTrace();
	}
}

@Retention(RetentionPolicy.RUNTIME)
@interface LoadFromXml
{
}
