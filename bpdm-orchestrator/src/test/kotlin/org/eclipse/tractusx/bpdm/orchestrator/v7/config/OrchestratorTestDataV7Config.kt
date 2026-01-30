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

import org.eclipse.tractusx.bpdm.orchestrator.config.StateMachineConfigProperties
import org.eclipse.tractusx.bpdm.orchestrator.config.TaskConfigProperties
import org.eclipse.tractusx.bpdm.orchestrator.v7.util.OrchestratorAssertRepositoryV7
import org.eclipse.tractusx.bpdm.orchestrator.v7.util.OrchestratorTestDataClientV7
import org.eclipse.tractusx.bpdm.test.testdata.orchestrator.BusinessPartnerTestDataFactory
import org.eclipse.tractusx.bpdm.test.testdata.orchestrator.OrchestratorExpectedResultFactoryV7
import org.eclipse.tractusx.bpdm.test.testdata.orchestrator.OrchestratorRequestFactoryCommon
import org.eclipse.tractusx.bpdm.test.testdata.orchestrator.OrchestratorRequestFactoryV7
import org.eclipse.tractusx.orchestrator.api.client.OrchestrationApiClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["test.v7.enabled"], havingValue = "true", matchIfMissing = false)
class OrchestratorTestDataV7Config {

    @Bean
    fun orchestratorRequestFactoryV7(
        businessPartnerTestDataFactory: BusinessPartnerTestDataFactory
    ): OrchestratorRequestFactoryV7{
        return OrchestratorRequestFactoryV7(businessPartnerTestDataFactory, OrchestratorRequestFactoryCommon())
    }

    @Bean
    fun orchestratorAssertRepositoryV7(): OrchestratorAssertRepositoryV7 {
        return OrchestratorAssertRepositoryV7()
    }

    @Bean
    fun orchestratorExpectedResultFactoryV7(
        taskConfigProperties: TaskConfigProperties,
        stateMachineConfigProperties: StateMachineConfigProperties
    ): OrchestratorExpectedResultFactoryV7 {
        return OrchestratorExpectedResultFactoryV7(
            taskConfigProperties.taskPendingTimeout,
            taskConfigProperties.taskRetentionTimeout,
            stateMachineConfigProperties.modeSteps
        )
    }

    @Bean
    fun orchestratorTestDataClientV7(
        @Qualifier(OrchestratorTestClientV7Config.ORCHESTRATOR_CLIENT_QUALIFIER)
        orchestratorClient: OrchestrationApiClient,
        requestFactory: OrchestratorRequestFactoryV7
    ): OrchestratorTestDataClientV7{
        return OrchestratorTestDataClientV7(orchestratorClient, requestFactory)
    }
}