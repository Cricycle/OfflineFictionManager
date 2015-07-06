package alex.offlinefictionmanager.discovery;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

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

import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 
 * @author Alex
 *
 */
public class DiscoveryManager
{
	private static String ConfigurationFile = "discovery_config.xml";
	private static String ConfigurationSchemaFile = "discovery_config.xsd";
	private static String StartingPointTempResource = "temporary_source.txt";
	private static String UserAgent = "Offline Fiction Manager v0 (cricycle.stackexchange@gmail.com)";

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

	private List<String> newSources;
	private List<Story> existingStories;
	private HashMap<String, String> discoveredStories;
	private HashMap<String, Story> newChapters;

	public static void main(String[] args)
	{
		// Initial plan:
		// Load starting point for searching for new stories to download
		// Load list of "already downloaded stories"
		// Attempt to discover new stories - existing stories should not be re-added
		// With list of new stories, chapters, send list to next step - new stories should be broken down into chapter
		// download requests

		DiscoveryManager manager = new DiscoveryManager();
		manager.LoadStartingPoint();
		manager.Discover();
		manager.RequestDownload();

		System.out.println(manager.maxRequestsPerMinuteRate);
		System.out.println(manager.maxDiscoverLimitPerSession);

		for (String storyName : manager.discoveredStories.keySet())
		{
			System.out.printf("Found story: NAME=%s, URL=%s\n", storyName, manager.discoveredStories.get(storyName));
		}

		System.out.println();

		for (String chapterURL : manager.newChapters.keySet())
		{
			System.out.printf("Found new chapter: URL=%s    Story=%s\n", chapterURL,
				manager.newChapters.get(chapterURL).storyName);
		}
	}

	public DiscoveryManager()
	{
		LoadConfiguration();
	}

	public void LoadConfiguration()
	{
		try
		{
			// URL schemaFile = new File(DiscoveryManager.ConfigurationSchemaFile).toURI().toURL();
			URL schemaFile = this.getClass().getResource(DiscoveryManager.ConfigurationSchemaFile);
			// Source xmlFile = new StreamSource(new File(DiscoveryManager.ConfigurationFile));
			Source xmlFile = new StreamSource(this.getClass().getResourceAsStream(DiscoveryManager.ConfigurationFile));
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

			// File fXmlFile = new File(DiscoveryManager.ConfigurationFile);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(this.getClass().getResourceAsStream(DiscoveryManager.ConfigurationFile));

			HashMap<String, String> nameValueMap = new HashMap<String, String>();
			HashMap<String, String> nameTypeMap = new HashMap<String, String>();
			getDiscoveryManagerProperties(doc, nameValueMap, nameTypeMap);

			Field[] fields = this.getClass().getDeclaredFields();
			for (Field f : fields)
			{
				if (f.isAnnotationPresent(LoadFromXml.class))
				{
					String fieldName = f.getName();
					try
					{
						f.setInt(this, Integer.parseInt(nameValueMap.get(fieldName)));
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

	private void getDiscoveryManagerProperties(Document doc, HashMap<String, String> nameValueMap,
		HashMap<String, String> nameTypeMap)
	{
		XPathFactory xpathFactory = XPathFactory.newInstance();
		XPath xpath = xpathFactory.newXPath();

		try
		{
			XPathExpression expr = xpath.compile("/DiscoveryManager/intproperty");
			NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
			for (int i = 0; i < nodes.getLength(); ++i)
			{
				Element e = (Element) (nodes.item(i));
				String name = e.getAttribute("name");
				String value = e.getAttribute("value");
				nameValueMap.put(name, value);
				nameTypeMap.put(name, "int");
			}
		}
		catch (XPathExpressionException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Loads what sites to look at for new stories/chapters
	 */
	public void LoadStartingPoint()
	{
		this.newSources = new ArrayList<String>();
		this.existingStories = new ArrayList<Story>();

		Scanner scanner = new Scanner(this.getClass().getResourceAsStream(DiscoveryManager.StartingPointTempResource));
		int numSiteSources = scanner.nextInt();
		scanner.nextLine();
		for (int i = 0; i < numSiteSources; ++i)
		{
			String site = scanner.nextLine(); // read whole line in case of spaces in URL
			this.newSources.add(site);
			// TODO log sources
		}

		int numKnownStories = scanner.nextInt();
		scanner.nextLine();
		for (int i = 0; i < numKnownStories; ++i)
		{
			String storyName = scanner.nextLine();
			String storySite = scanner.nextLine();
			int knownChapterCount = Integer.parseInt(scanner.nextLine());
			this.existingStories.add(new Story(storyName, storySite, knownChapterCount));
			// TODO: Log loading existing stories count
		}
	}

	/**
	 * Looks at sites for new stories/chapters
	 * Requires that LoadStartingPoint() has run
	 * Results in a list of websites, along with a reason for why we target it
	 */
	public void Discover()
	{
		DiscoverStories();
		DiscoverChapters();
	}

	/**
	 * Go through site sources for stories (author sites on FF.net)
	 */
	private void DiscoverStories()
	{
		for (String authorSite : this.newSources)
		{
			try
			{
				org.jsoup.nodes.Document doc = Jsoup.connect(authorSite).userAgent(DiscoveryManager.UserAgent).get();
				this.discoveredStories = new HashMap<>();

				// Look for stories by the user first
				org.jsoup.select.Elements authoredStories = doc.getElementsByClass("mystories");
				for (org.jsoup.nodes.Element e : authoredStories)
				{
					String betterStoryName = e.getElementsByClass("stitle").first().text();
					String storyURL = e.getElementsByClass("stitle").first().attr("abs:href");
					this.discoveredStories.put(betterStoryName, storyURL);
				}

				// Look at the user's favorite stories next
				org.jsoup.select.Elements favoritedStories = doc.getElementsByClass("favstories");
				for (org.jsoup.nodes.Element e : favoritedStories)
				{
					String betterStoryName = e.getElementsByClass("stitle").first().text();
					String storyURL = e.getElementsByClass("stitle").first().attr("abs:href");
					this.discoveredStories.put(betterStoryName, storyURL);
				}
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Given a link to some chapter in a story, + count of how many chapters already loaded, load more chaps
	 */
	private void DiscoverChapters()
	{
		for (Story knownStory : this.existingStories)
		{
			try
			{
				org.jsoup.nodes.Document doc = Jsoup.connect(knownStory.getURL()).userAgent(DiscoveryManager.UserAgent)
					.get();

				this.newChapters = new HashMap<String, Story>();

				// Look at the chapter selection and get the chapter count
				org.jsoup.select.Elements optionTags = doc.select("select").first().select("option");
				for (org.jsoup.nodes.Element e : optionTags)
				{
					int chapterNum = Integer.parseInt(e.attr("value"));
					if (chapterNum > knownStory.knownChapterCount)
					{
						this.newChapters.put(knownStory.getURL(chapterNum), knownStory);
					}
				}
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	/**
	 * Passes list of discovered sites to download new stories/chapters
	 */
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

class Story
{
	String storyName;
	String websiteURL;
	int knownChapterCount;

	public Story(String storyName, String websiteURL, int knownChapterCount)
	{
		this.storyName = storyName;
		this.websiteURL = websiteURL;
		this.knownChapterCount = knownChapterCount;
	}

	public String getURL()
	{
		return getURL(1);
	}

	public String getURL(int chapNum)
	{
		return String.format(websiteURL, chapNum);
	}
}