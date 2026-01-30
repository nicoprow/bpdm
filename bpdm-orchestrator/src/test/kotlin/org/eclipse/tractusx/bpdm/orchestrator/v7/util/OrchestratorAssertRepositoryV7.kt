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

import org.assertj.core.api.Assertions
import org.eclipse.tractusx.orchestrator.api.model.TaskClientStateDto
import org.eclipse.tractusx.orchestrator.api.model.TaskCreateResponse
import org.eclipse.tractusx.orchestrator.api.model.TaskProcessingStateDto
import org.eclipse.tractusx.orchestrator.api.model.TaskStateResponse
import org.eclipse.tractusx.orchestrator.api.model.TaskStepReservationEntryDto
import org.eclipse.tractusx.orchestrator.api.model.TaskStepReservationResponse
import java.time.temporal.ChronoUnit
import kotlin.collections.zip

class OrchestratorAssertRepositoryV7 {


    fun assertTaskCreateResponseEqual(actual: TaskCreateResponse, expected: TaskCreateResponse, isForNewRecord: Boolean){
        assertBusinessPartnerClientStatesEqual(actual.createdTasks, expected.createdTasks, ignoreTaskId = true, ignoreRecordId = isForNewRecord)
    }

    fun assertTaskStateResponseEqual(actual: TaskStateResponse, expected: TaskStateResponse){
        assertBusinessPartnerClientStatesEqual(actual.tasks, expected.tasks, ignoreTaskId = false, ignoreRecordId = false)
    }

    fun assertTaskReservationResponseEqual(actual: TaskStepReservationResponse, expected: TaskStepReservationResponse, ignoreRecordId: Boolean){
        assertTaskReservationEntriesEqual(actual.reservedTasks, expected.reservedTasks, ignoreRecordId)
        Assertions.assertThat(actual.timeout).isCloseTo(expected.timeout, Assertions.within(1, ChronoUnit.SECONDS))
    }

    fun assertTaskReservationEntriesEqual(actual: List<TaskStepReservationEntryDto>, expected: List<TaskStepReservationEntryDto>, ignoreRecordId: Boolean){
        val ignoredFields = listOfNotNull(
            TaskStepReservationEntryDto::recordId.name.takeIf { ignoreRecordId }
        ).toTypedArray()

        Assertions.assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields(*ignoredFields)
            .isEqualTo(expected)
    }

    fun assertBusinessPartnerClientStatesEqual(
        actual: List<TaskClientStateDto>,
        expected: List<TaskClientStateDto>,
        ignoreTaskId: Boolean = true,
        ignoreRecordId: Boolean = true
    ){
        val ignoredFields = listOfNotNull(
            TaskClientStateDto::taskId.name.takeIf { ignoreTaskId },
            TaskClientStateDto::recordId.name.takeIf { ignoreRecordId },
            TaskClientStateDto::processingState.name
        ).toTypedArray()

        Assertions.assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields(*ignoredFields)
            .isEqualTo(expected)

        assertProcessingStates(actual.map { it.processingState }, expected.map { it.processingState })
    }

    fun assertProcessingStates(actual: List<TaskProcessingStateDto>, expected: List<TaskProcessingStateDto>) {
        Assertions.assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields(TaskProcessingStateDto::createdAt.name)
            .ignoringFields(TaskProcessingStateDto::modifiedAt.name)
            .ignoringFields(TaskProcessingStateDto::timeout.name)
            .isEqualTo(expected)

        actual.zip(expected).forEach { (actualEntry, expectedEntry) ->
            Assertions.assertThat(actualEntry.timeout).isCloseTo(expectedEntry.timeout, Assertions.within(1, ChronoUnit.SECONDS))
        }
    }
}