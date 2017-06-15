package com.ywy.editor;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.net.URI;


/**
 * Created by john on 6/12/2017.
 */
public class ServerMain {
    public static class CORSResponseFilter
            implements ContainerResponseFilter {

        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
                throws IOException {

            MultivaluedMap<String, Object> headers = responseContext.getHeaders();

            headers.add("Access-Control-Allow-Origin", "*");
            //headers.add("Access-Control-Allow-Origin", "http://podcastpedia.org"); //allows CORS requests only coming from podcastpedia.org
            headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
            headers.add("Access-Control-Allow-Headers", "X-Requested-With, Content-Type, X-Codingpedia");
        }

    }
       // Base URI the Grizzly HTTP server will listen on
        public static final String BASE_URI = "http://localhost:8081/editor/";

        /**
         * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
         * @return Grizzly HTTP server.
         */
        public static HttpServer startServer() {
            // create a resource config that scans for JAX-RS resources and provided
            final ResourceConfig rc = new ResourceConfig().register(JacksonJsonProvider.class).register(CORSResponseFilter.class).registerClasses(TableServices.class);
            HttpHandler httpHandler = new StaticHttpHandler("ui");
            HttpServer server = GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
            server.getServerConfiguration().addHttpHandler(httpHandler,"/");
            // create and start a new instance of grizzly http server
            // exposing the Jersey application at BASE_URI
            return server;
        }

        /**
         * Main method.
         * @param args
         * @throws IOException
         */
        public static void main(String[] args) throws IOException, InterruptedException {
            final HttpServer server = startServer();
            System.out.println(String.format("Jersey app started with WADL available at "
                    + "%sapplication.wadl\nHit enter to stop it...", BASE_URI));
            Thread.currentThread().join();
            server.shutdownNow();
        }

}
