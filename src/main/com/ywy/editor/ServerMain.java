package com.ywy.editor;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;


/**
 * Created by john on 6/12/2017.
 */
public class ServerMain {
       // Base URI the Grizzly HTTP server will listen on
        public static final String BASE_URI = "http://localhost:8081/editor/";

        /**
         * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
         * @return Grizzly HTTP server.
         */
        public static HttpServer startServer() {
            // create a resource config that scans for JAX-RS resources and provided
            final ResourceConfig rc = new ResourceConfig().register(JacksonJsonProvider.class).registerClasses(TableServices.class);

            // create and start a new instance of grizzly http server
            // exposing the Jersey application at BASE_URI
            return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
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
