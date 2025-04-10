package ru.quipy.config

import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ServerCustomizer {
    @Bean
    fun jettyCustomizer(): JettyServletWebServerFactory {
        val factory = JettyServletWebServerFactory()

        val connectionCustomizer = JettyServerCustomizer {
            (it.connectors[0].getConnectionFactory("h2c") as HTTP2ServerConnectionFactory).maxConcurrentStreams = 1_000_000;
        }

        factory.serverCustomizers.add(connectionCustomizer)

        return factory
    }
}