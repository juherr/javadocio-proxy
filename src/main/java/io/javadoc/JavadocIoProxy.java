package io.javadoc;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.port;

public class JavadocIoProxy {

    private static final String JAVADOC_IO_INDEX_URL = "http://www.javadoc.io/doc";
    private static final String JAVADOC_IO_STATIC_URL = "http://static.javadoc.io";

    public static void main(String... args) throws IOException {
        port(getHerokuAssignedPort());

        get("/", (request, response) -> {
            response.redirect("http://www.javadoc.io/");
            return "";
        });
        get("/*", (request, response) -> {
            String url = request.pathInfo();
            int position = url.indexOf("/", 1); // skip root
            if (position == -1) {
                response.redirect("http://www.javadoc.io/");
                return "";
            }
            int splitAt = url.indexOf("/", position + 1); // skip groupId and artifactId separator
            if (splitAt == -1) {
                response.redirect(JAVADOC_IO_INDEX_URL + url);
                return "";
            }
            String project = url.substring(0, splitAt);
            String pageUrl = url.substring(splitAt);
            if ("/".equals(pageUrl)) {
                response.redirect(JAVADOC_IO_INDEX_URL + url);
                return "";
            }
            try {
                response.redirect(getUrl(request.userAgent(), project, pageUrl), HttpURLConnection.HTTP_SEE_OTHER);
            } catch (IllegalStateException ex) {
                halt(500, ex.getMessage());
            }
            return "";
        });
    }

    private static int getHerokuAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return 4567; //return default port if heroku-port isn't set (i.e. on localhost)
    }

    private static String getUrl(String agent, String project, String pageUrl) throws IOException {
        System.setProperty("http.agent", agent);
        HttpURLConnection.setFollowRedirects(false);
        URL url = new URL(JAVADOC_IO_INDEX_URL + project);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_SEE_OTHER) {
            throw new IllegalStateException("Unexpected response: " + connection.getResponseCode());
        }
        String location = connection.getHeaderField("Location");
        if (!location.startsWith("/doc/")) {
            throw new IllegalStateException("Unexpected location: " + location);
        }
        String versionUrl = location.substring(4); // remove "/doc"
        return JAVADOC_IO_STATIC_URL + versionUrl + pageUrl;
    }
}
