/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.eclipse.tractusx.bpdm.orchestrator.v7.config

import org.eclipse.tractusx.bpdm.common.util.BpdmWebClientProvider
import org.eclipse.tractusx.bpdm.orchestrator.v7.util.OrchestratorTestClientProviderV7
import org.eclipse.tractusx.bpdm.test.containers.KeyCloakInitializer
import org.eclipse.tractusx.orchestrator.api.client.OrchestrationApiClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["test.v7.enabled"], havingValue = "true", matchIfMissing = false)
class OrchestratorTestClientV7Config {

    companion object{
        const val ORCHESTRATOR_CLIENT_QUALIFIER = "orchestratorClientV7"
    }

    @Bean
    fun orchestratorTestClientProviderV7(
        webServerApplicationContext: ServletWebServerApplicationContext,
        clientProvider: BpdmWebClientProvider
    ): OrchestratorTestClientProviderV7{
        return OrchestratorTestClientProviderV7(webServerApplicationContext.webServer!!, clientProvider)
    }

    @Bean(name = [ORCHESTRATOR_CLIENT_QUALIFIER])
    fun orchestratorClientV7(
        testClientProvider: OrchestratorTestClientProviderV7
    ): OrchestrationApiClient{
        return testClientProvider.createClient(KeyCloakInitializer.CLIENT_ID_OPERATOR)
    }


}