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

package org.eclipse.tractusx.bpdm.orchestrator.v7.util

import org.eclipse.tractusx.bpdm.test.testdata.orchestrator.OrchestratorRequestFactoryV7
import org.eclipse.tractusx.orchestrator.api.client.OrchestrationApiClient
import org.eclipse.tractusx.orchestrator.api.model.BusinessPartner
import org.eclipse.tractusx.orchestrator.api.model.TaskClientStateDto
import org.eclipse.tractusx.orchestrator.api.model.TaskCreateRequest
import org.eclipse.tractusx.orchestrator.api.model.TaskErrorDto
import org.eclipse.tractusx.orchestrator.api.model.TaskErrorType
import org.eclipse.tractusx.orchestrator.api.model.TaskMode
import org.eclipse.tractusx.orchestrator.api.model.TaskStep
import org.eclipse.tractusx.orchestrator.api.model.TaskStepReservationRequest
import org.eclipse.tractusx.orchestrator.api.model.TaskStepReservationResponse
import org.eclipse.tractusx.orchestrator.api.model.TaskStepResultEntryDto
import org.eclipse.tractusx.orchestrator.api.model.TaskStepResultRequest

class OrchestratorTestDataClientV7(
    private val orchestratorClient: OrchestrationApiClient,
    private val requestFactory: OrchestratorRequestFactoryV7
) {

    fun createTask(seed: String, taskMode: TaskMode = TaskMode.UpdateFromSharingMember, recordId: String? = null): TaskClientStateDto {
        val newTask = requestFactory.buildTaskCreateEntry(seed).copy(recordId = recordId)
        val createRequest = TaskCreateRequest(taskMode, listOf(newTask))
        val createResult = orchestratorClient.goldenRecordTasks.createTasks(createRequest)

        return createResult.createdTasks.single()
    }

    fun reserveTasks(step: TaskStep): TaskStepReservationResponse {
        val reservationRequest = TaskStepReservationRequest(step = step)
        return orchestratorClient.goldenRecordTasks.reserveTasksForStep(reservationRequest)
    }

    fun reserveAndResolveTask(taskId: String, step: TaskStep, seed: String): TaskStepResultEntryDto{
        reserveTasks(step)
        val businessPartnerResult = requestFactory.buildAdditionalAddressOfSiteBusinessPartner(seed)
        val resultEntry = TaskStepResultEntryDto(taskId, businessPartnerResult, emptyList())
        val resultRequest = TaskStepResultRequest(step, listOf(resultEntry))
        orchestratorClient.goldenRecordTasks.resolveStepResults(resultRequest)

        return resultEntry
    }

    fun failTask(taskId: String, step: TaskStep): TaskStepResultEntryDto {
        reserveTasks(step)

        val resultEntry = TaskStepResultEntryDto(taskId, BusinessPartner.empty, listOf(TaskErrorDto(TaskErrorType.Unspecified, "Error Description")))
        val resultRequest = TaskStepResultRequest(step, listOf(resultEntry))
        orchestratorClient.goldenRecordTasks.resolveStepResults(resultRequest)

        return resultEntry
    }

}