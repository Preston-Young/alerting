/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

/*
 *   Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package org.opensearch.alerting

import org.apache.http.HttpHost
import org.junit.After
import org.opensearch.client.Request
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestClient
import org.opensearch.client.WarningsHandler
import org.opensearch.common.io.PathUtils
import org.opensearch.common.settings.Settings
import org.opensearch.common.xcontent.DeprecationHandler
import org.opensearch.common.xcontent.NamedXContentRegistry
import org.opensearch.common.xcontent.XContentType
import org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_ENABLED
import org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH
import org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_KEYPASSWORD
import org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_PASSWORD
import org.opensearch.commons.ConfigConstants.OPENSEARCH_SECURITY_SSL_HTTP_PEMCERT_FILEPATH
import org.opensearch.commons.rest.SecureRestClientBuilder
import org.opensearch.test.rest.OpenSearchRestTestCase
import java.io.IOException

/**
 * Must support below 3 scenario runs:
 * 1. Without Security plugin
 * 2. With Security plugin and https
 * 3. With Security plugin and http
 *    Its possible to have security enabled with http transport.
 * client() - > admin user
 * adminClient() -> adminDN/super-admin user
 */

abstract class ODFERestTestCase : OpenSearchRestTestCase() {

    fun isHttps(): Boolean {
        return System.getProperty("https", "false")!!.toBoolean()
    }

    fun securityEnabled(): Boolean {
        return System.getProperty("security", "false")!!.toBoolean()
    }

    override fun getProtocol(): String {
        return if (isHttps()) {
            "https"
        } else {
            "http"
        }
    }

    override fun preserveIndicesUponCompletion(): Boolean {
        return true
    }

    @Throws(IOException::class)
    @After
    open fun wipeAllODFEIndices() {
        val response = client().performRequest(Request("GET", "/_cat/indices?format=json&expand_wildcards=all"))

        val xContentType = XContentType.fromMediaTypeOrFormat(response.entity.contentType.value)
        xContentType.xContent().createParser(
            NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
            response.entity.content
        ).use { parser ->
            for (index in parser.list()) {
                val jsonObject: Map<*, *> = index as java.util.HashMap<*, *>
                val indexName: String = jsonObject["index"] as String
                // .opendistro_security isn't allowed to delete from cluster
                if (".opendistro_security" != indexName) {
                    var request = Request("DELETE", "/$indexName")
                    // TODO: remove PERMISSIVE option after moving system index access to REST API call
                    val options = RequestOptions.DEFAULT.toBuilder()
                    options.setWarningsHandler(WarningsHandler.PERMISSIVE)
                    request.options = options.build()
                    adminClient().performRequest(request)
                }
            }
        }
    }

    /**
     * Returns the REST client settings used for super-admin actions like cleaning up after the test has completed.
     */
    override fun restAdminSettings(): Settings {
        return Settings
            .builder()
            .put("http.port", 9200)
            .put(OPENSEARCH_SECURITY_SSL_HTTP_ENABLED, isHttps())
            .put(OPENSEARCH_SECURITY_SSL_HTTP_PEMCERT_FILEPATH, "sample.pem")
            .put(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH, "test-kirk.jks")
            .put(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_PASSWORD, "changeit")
            .put(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_KEYPASSWORD, "changeit")
            .build()
    }

    @Throws(IOException::class)
    override fun buildClient(settings: Settings, hosts: Array<HttpHost>): RestClient {
        if (securityEnabled()) {
            val keystore = settings.get(OPENSEARCH_SECURITY_SSL_HTTP_KEYSTORE_FILEPATH)
            return when (keystore != null) {
                true -> {
                    // create adminDN (super-admin) client
                    val uri = javaClass.classLoader.getResource("sample.pem").toURI()
                    val configPath = PathUtils.get(uri).parent.toAbsolutePath()
                    SecureRestClientBuilder(settings, configPath).setSocketTimeout(60000).build()
                }
                false -> {
                    // create client with passed user
                    val userName = System.getProperty("user")
                    val password = System.getProperty("password")
                    SecureRestClientBuilder(hosts, isHttps(), userName, password).setSocketTimeout(60000).build()
                }
            }
        } else {
            val builder = RestClient.builder(*hosts)
            configureClient(builder, settings)
            builder.setStrictDeprecationMode(true)
            return builder.build()
        }
    }
}
