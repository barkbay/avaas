/******************************************************************************
 **
 ** This library is free software; you can redistribute it and/or
 ** modify it under the terms of the GNU Lesser General Public
 ** License as published by the Free Software Foundation; either
 ** version 2.1 of the License, or (at your option) any later version.
 **
 ** This library is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 ** Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public
 ** License along with this library; if not, write to the Free Software
 ** Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *********************************************************************************/

 package avaas.clamav.client;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.github.sps.metrics.OpenTsdbReporter;
import com.github.sps.metrics.opentsdb.OpenTsdb;
import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.ExportMetricReader;
import org.springframework.boot.actuate.metrics.reader.MetricReader;
import org.springframework.boot.actuate.metrics.reader.MetricRegistryMetricReader;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.servlet.MultipartConfigElement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static springfox.documentation.builders.PathSelectors.regex;

@Configuration
@EnableAutoConfiguration
@EnableSwagger2
@ComponentScan({"avaas"})
/**
 * Spring Boot application which acts as a REST endpoint for clamd server.
 */
public class Application {

    @Value("${clamd.maxfilesize}")
    private String maxfilesize;
    @Value("${clamd.maxrequestsize}")
    private String maxrequestsize;
    @Value("${opentsdb.url}")
    private String opentsdbUrl;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        Map<String, Object> defaults = new HashMap<String, Object>();
        defaults.put("clamd.host", "127.0.0.1");
        defaults.put("clamd.port", 3310);
        defaults.put("clamd.timeout", 2000);
        defaults.put("clamd.maxfilesize", "20000KB");
        defaults.put("clamd.maxrequestsize", "20000KB");
        defaults.put("opentsdb.url", "http://localhost:4242");
        app.setDefaultProperties(defaults);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }

    @Bean
    MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(maxfilesize);
        factory.setMaxRequestSize(maxrequestsize);
        return factory.createMultipartConfig();
    }

    @Bean
    @ExportMetricReader
    public MetricReader metricReader() {
        return new MetricRegistryMetricReader(metricRegistry());
    }

    @Bean
    public MetricRegistry metricRegistry() {
        final MetricRegistry metricRegistry = new MetricRegistry();

        // On remonte les métriques de la JVM
        metricRegistry.register("jvm.gc",new GarbageCollectorMetricSet());
        metricRegistry.register("jvm.mem",new MemoryUsageGaugeSet());
        metricRegistry.register("jvm.thread-states",new ThreadStatesGaugeSet());

        // On enregistre le rapporteur OpenTSDB
        OpenTsdbReporter.forRegistry(metricRegistry)
                .withBatchSize(5)
                .withTags(ImmutableMap.of("foo", "bar"))
                .build(OpenTsdb.forService(opentsdbUrl).create())
                .start(5L, TimeUnit.SECONDS);

        return metricRegistry;
    }


    @Bean
    public Docket newsApi() {
        return new Docket(DocumentationType.SWAGGER_2)
                .groupName("avaas")
                .apiInfo(apiInfo())
                .select()
                .paths(regex("/api.*"))
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("AVAAS : AntiVirus As A Service")
                .description("Antivirus REST service")
                .license("GNU Lesser General Public License 2.1")
                .version("1.0")
                .build();
    }

}
