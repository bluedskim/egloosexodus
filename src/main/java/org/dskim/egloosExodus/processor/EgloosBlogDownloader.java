package org.dskim.egloosExodus.processor;

import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dskim.egloosExodus.staticSiteGenerator.StaticSiteGeneratorDelegator;
import org.dskim.egloosExodus.model.Post;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Data
public class EgloosBlogDownloader {
	private static final Logger logger = LogManager.getLogger(EgloosBlogDownloader.class);

	// 블로그 제목 ex) 하고 싶은 걸 하세요 Do What You Want
	String blogName = null;

	// 블로그 주소	ex) http://shed.egloos.com
	String blogBaseUrl = null;

	//int currentBlogNo = 1;
	//int currentBlogNo = 100;
	int currentBlogNo = 113;
	// int currentBlogNo = 10000;

	@Value("${blog.minSleepTime}")
	int minSleepTime;

	/**
	 * 블로그 메인 주소를 받아서 전체 블로그를 다운로드 한다.
	 *
	 * @param siteGen
	 * @param blogBaseUrl
	 * @return
	 * @throws Exception
	 */
	public boolean downLoadBlog(StaticSiteGeneratorDelegator siteGen, String blogBaseUrl) throws Exception {
		boolean isSuccess = false;
		this.blogName = blogName;
		this.blogBaseUrl = blogBaseUrl;

		List<Post> postList = null;
		while((postList = getBlogContent("http://" + blogBaseUrl + "/page/" + (currentBlogNo++))) != null) {
			for(Post post : postList) {
				siteGen.createPost(post);
			}
			//long sleepTime = minSleepTime * 1000 * (1L + (long) (Math.random() * (3L - 1L)));
			long sleepTime = minSleepTime * 1000 + (long)(1000 * 2 * Math.random());
			logger.debug("sleeping... {}", sleepTime);
			Thread.sleep(sleepTime);
		}

		this.blogName = null;
		this.blogBaseUrl = null;
		currentBlogNo = 1;
		return isSuccess;
	}

	/**
	 * 해당 페이지의 블로그를 읽어온다.
	 * 마지막 블로그라면 null 반환
	 *
	 * @param blogPostUrl
	 * @return
	 */
	public List<Post> getBlogContent(String blogPostUrl) throws Exception {
		List<Post> postList = null;
		boolean isLastPost = false;

		Document document = Jsoup.connect(blogPostUrl).get();
		logger.debug("document.title()={}", document.title());

		/*
		Element link = document.select("body > div.body > div > div.header > h1 > a").get(0);
		logger.debug("blogName={}", link.text());
		*/

		//logger.debug("등록된 포스트가 없습니다?={}", document.select("div:contains(등록된 포스트가 없습니다.)"));

		// 더 이상 블로그가 없다면
		if(document.select("div:contains(등록된 포스트가 없습니다.)").size() > 0) {
			logger.debug("등록된 포스트가 없습니다.");
			return postList;
		}

		Elements blogsOfThePage = document.select("div.post_view");

		if(blogsOfThePage.size() > 0) {
			postList = new ArrayList<>();

			for (Element blogPost : blogsOfThePage) {
				//'신고' 삭제
				blogPost.selectFirst("span:matchesOwn(신고)").parent().parent().remove();

				Post post = new Post();
				//logger.debug("blogPost={}", blogPost);
				Element postTitleArea = blogPost.select("div.post_title_area").first();
				//logger.debug("entry-title={}", postTitleArea);
				Element title = blogPost.select("a").first();
				logger.debug("title={}", title.text());
				post.setTitle(title.text());
				post.setId(title.attr("name"));

				Element category = blogPost.selectFirst("span.post_title_category");
				post.setCategory(category.text());

				Element date = blogPost.select("abbr").first();
				logger.debug("date={}", date.text());
				post.setDate(date.text());
				//Element commentCount = postTitleArea.select("span.txt").first();

				//logger.debug("덧글수={}", new StringTokenizer(commentCount.text(), ":").);
				if(postTitleArea.select("ul > li.post_info_cmtcount") != null
					&& postTitleArea.select("ul > li.post_info_cmtcount").size() > 0) {
					Element commentCount = postTitleArea.select("ul > li.post_info_cmtcount").first();
					logger.debug("덧글수=[{}]", commentCount.text().substring(commentCount.text().lastIndexOf(": ")+1).trim());
				} else {
					logger.debug("덧글수 없음");
				}

				Elements images = blogPost.select("img[src~=(?i)\\.(png|jpe?g|gif)]");
				for (Element image : images) {
					/*
					System.out.println("\nsrc : " + image.attr("src"));
					System.out.println("height : " + image.attr("height"));
					System.out.println("width : " + image.attr("width"));
					System.out.println("alt : " + image.attr("alt"));
					*/
					// 이런 이미지들은 제외 http://md.egloos.com/img/icon/ico_badreport.png
					if(image.attr("src").contains("/md") == false
						&& image.attr("src").contains("/profile") == false ) {
						logger.debug(" \t img src={}", image.attr("src"));
						if(post.getAttachments() == null) {
							post.setAttachments(new ArrayList());
						}
						String imageIndex = Integer.toString(post.getAttachments().size()+1);
						String tempImagePath = "/attachment/" + post.getId() + "_" + imageIndex + "." + StringUtils.substringAfterLast(image.attr("src"), ".");
						post.getAttachments().add(new String[]{tempImagePath, image.attr("src")});
						image.attr("src", tempImagePath);
					}
				}

				Element hentry = blogPost.select("div.hentry").first();
				post.setBodyText(hentry.text());
				post.setBodyHtml(hentry.html());
				Element comment = blogPost.select("ul.comment_list").first();
				if(comment != null) {
					post.setBodyHtml(post.getBodyHtml() + "\n\n<ul>" + comment.html() + "</ul>");
				}
				logger.debug("post.getBodyText()={}", post.getBodyText());
				logger.debug("post.getBodyHtml()={}", post.getBodyHtml());

				Element tagListContainer = blogPost.select("div.post_taglist").first();
				if(tagListContainer != null) {
					Elements tagAnchors = tagListContainer.select("a");
					for (Element tagAnchor : tagAnchors) {
						logger.debug("tagAnchor={}", tagAnchor.text());
						if("".equals(post.getTags())) {
							post.setTags("\"" + tagAnchor.text() + "\"");
						} else {
							post.setTags(post.getTags() + ",\"" + tagAnchor.text() + "\"");
						}
					}
				}

				//logger.debug("markdownmarkdownmarkdownmarkdown\n{}\nmarkdownmarkdownmarkdownmarkdown", htmlToMd(hentry.html()));

				postList.add(post);
			}
		}

		return postList;
	}

	private String htmlToMd(String html) throws Exception {
		String mdStr = null;
		//ProcessBuilder builder = new ProcessBuilder("/home/bluedskim/Utils/shell/echoFromStdIn.sh");
		ProcessBuilder builder = new ProcessBuilder();
		builder.command("/usr/bin/pandoc", "-f", "html+raw_html", "-t", "markdown");
		builder.redirectErrorStream(true);
		Process pr = builder.start();

		//Process pr = rt.exec("docker exec keen_wing pandoc");
		OutputStream stdin = pr.getOutputStream ();
		InputStream stderr = pr.getErrorStream ();
		InputStream stdout = pr.getInputStream ();
		stdin.write(html.getBytes());
		stdin.flush();
		stdin.close();

		pr.waitFor();

		mdStr = IOUtils.toString(stdout);
		//logger.debug("exitValue={}, out={}", pr.exitValue(), mdStr);
		//logger.debug("stderr={}", IOUtils.toString(stderr));

		stderr.close();
		stdout.close();

		pr.destroy();
		if (pr.isAlive()) {
			pr.destroyForcibly();
		}

		return mdStr;
	}
}
