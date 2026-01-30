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

package org.eclipse.tractusx.bpdm.orchestrator.v7

import org.eclipse.tractusx.orchestrator.api.model.TaskCreateRequest
import org.eclipse.tractusx.orchestrator.api.model.TaskCreateResponse
import org.eclipse.tractusx.orchestrator.api.model.TaskMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class BusinessPartnerTaskCreationV7IT: UnscheduledOrchestratorTestV7() {

    /**
     * WHEN gate creates a new task for new sharing member record
     * THEN gate receives created task response with new sharing member record ID
     */
    @ParameterizedTest
    @EnumSource(TaskMode::class)
    fun `create task for new sharing member record`(taskMode: TaskMode){
        //WHEN
        val requestEntry = requestFactory.buildTaskCreateEntry(testName).copy(recordId = null)
        val createRequest = TaskCreateRequest(taskMode, listOf(requestEntry))
        val response = orchestratorClient.goldenRecordTasks.createTasks(createRequest)

        //THEN
        val expectedEntry = resultFactory.buildCreatedTaskClientState(requestEntry.businessPartner, taskMode)
        val expectedResponse = TaskCreateResponse(listOf(expectedEntry))
        assertRepo.assertTaskCreateResponseEqual(response, expectedResponse)
    }

}