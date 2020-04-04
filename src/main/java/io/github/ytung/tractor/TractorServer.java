package io.github.ytung.tractor;

import javax.servlet.ServletRegistration;

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereServlet;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class TractorServer extends Application<Configuration> {

    public static void main(String[] args) throws Exception {
        new TractorServer().run("server");
    }

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/assets", "/", "index.html"));
    }

    @Override
    public void run(Configuration configuration, Environment environment) throws Exception {
        environment.jersey().setUrlPattern("/api/*");

        AtmosphereServlet servlet = new AtmosphereServlet();
        servlet.framework().addInitParameter("com.sun.jersey.config.property.packages", "io.github.ytung.tractor");
        servlet.framework().addInitParameter(ApplicationConfig.WEBSOCKET_CONTENT_TYPE, "application/json");
        servlet.framework().addInitParameter(ApplicationConfig.WEBSOCKET_SUPPORT, "true");

        ServletRegistration.Dynamic servletHolder = environment.servlets().addServlet("Tractor", servlet);
        servletHolder.addMapping("/tractor");
    }
}
