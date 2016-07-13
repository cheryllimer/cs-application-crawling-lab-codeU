package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;

	// the index where the results go
	private JedisIndex index;

	// queue of URLs to be indexed
	public Queue<String> queue = new LinkedList<String>();

	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 *
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 *
	 * @return
	 */
	public int queueSize() {
		return queue.size();
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b
	 *
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException
	{
        String current = queue.remove();
        Elements para;

        if(!testing)
        {
        	if(index.isIndexed(current))
        		return null;
        	para = wf.fetchWikipedia(current);
        }
        else
        	para = wf.readWikipedia(current);

        index.indexPage(current, para);

        queueInternalLinks(para);

        return current;
	}

	/**
	 * Parses paragraphs and adds internal links to the queue.
	 *
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs)
	{
		String base = "https://en.wikipedia.org";
		String url;
		for(Element para: paragraphs)
        {

        	Elements links = para.select("a");

        	for(Element link: links)
        	{
        		String sLink = link.toString();
        		if(sLink.contains("/wiki/"))
        		{
        			int index = sLink.indexOf("\"", 10);
					sLink = sLink.substring(9, index);

        			url = base.concat(sLink);
        			queue.add(url);
        		}
        	}
        }
	}

	public static void main(String[] args) throws IOException {

		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis);
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);

		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

		} while (res == null);

		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
