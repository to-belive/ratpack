/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ratpack.ssl

import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContextBuilder
import ratpack.test.internal.RatpackGroovyDslSpec

import javax.net.ssl.*
import java.nio.channels.ClosedChannelException
import java.security.KeyStore
import java.security.Security

class HttpsTruststoreSpec extends RatpackGroovyDslSpec {
  private setupServerConfig(String keystore, String truststore) {
    def builder = SslContextBuilder.forServer(
      keyManagerFactory(keystore)
    )
    if (truststore) {
      builder.trustManager(trustManagerFactory(truststore))
    }

    builder.clientAuth(ClientAuth.REQUIRE)

    serverConfig {
      ssl builder.build()
    }
  }

  private def setupRequestSpec(String keystore, String truststore) {
    resetRequest()
    def builder = SslContextBuilder.forClient()
    if (keystore) {
      builder.keyManager(keyManagerFactory(keystore))
    }
    if (truststore) {
      builder.trustManager(trustManagerFactory(truststore))
    }

    requestSpec {
      it.sslContext builder.build()
    }
  }

  private setupHandlers() {
    handlers {
      get("foo") {
        render "SSL VERIFIED"
      }
    }
  }

  def "can serve content over HTTPS with client SSL authentication"() {
    given:
    setupServerConfig("server_dummy.keystore", "server_dummy.truststore")
    setupHandlers()

    when:
    setupRequestSpec("client_dummy.keystore", "client_dummy.truststore")

    then:
    def address = applicationUnderTest.address
    address.scheme == "https"
    getText("foo") == "SSL VERIFIED"
  }

  def "throw exception for [#clientKeystore, #clientTruststore, #serverKeystore, #serverTruststore]"() {
    given:
    setupServerConfig(serverKeystore, serverTruststore)
    setupHandlers()

    when:
    setupRequestSpec(clientKeystore, clientTruststore)
    get("foo")

    then:
    UncheckedIOException ex = thrown()
    ex.getCause() instanceof SSLHandshakeException || ex.getCause() instanceof ClosedChannelException || ex.getCause() instanceof SSLProtocolException || ex.getCause() instanceof SSLException


    where:
    clientKeystore          | clientTruststore          | serverKeystore          | serverTruststore
    "dummy.keystore"        | "client_dummy.truststore" | "server_dummy.keystore" | "server_dummy.truststore"
    "client_dummy.keystore" | "client_dummy.truststore" | "dummy.keystore"        | "server_dummy.truststore"
    "client_dummy.keystore" | "client_dummy.truststore" | "server_dummy.keystore" | null
  }

  static String getAlgorithm() {
    String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm")
    if (algorithm == null) {
      algorithm = "SunX509"
    }
    algorithm
  }

  static KeyManagerFactory keyManagerFactory(String file) {
    KeyStore keyStore = KeyStore.getInstance("JKS")
    keyStore.load(HttpsTruststoreSpec.getResource(file).newInputStream(), "password" as char[])
    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(algorithm)
    keyManagerFactory.init(keyStore, "password" as char[])
    keyManagerFactory
  }

  static TrustManagerFactory trustManagerFactory(String file) {
    KeyStore trustStore = KeyStore.getInstance("JKS")
    trustStore.load(HttpsTruststoreSpec.getResource(file).newInputStream(), "password" as char[])
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(algorithm)
    trustManagerFactory.init(trustStore)
    trustManagerFactory
  }

}
