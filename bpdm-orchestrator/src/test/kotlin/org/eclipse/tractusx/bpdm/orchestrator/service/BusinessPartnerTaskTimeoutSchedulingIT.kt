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

package org.eclipse.tractusx.bpdm.orchestrator.service

import org.assertj.core.api.Assertions.*
import org.assertj.core.api.ThrowableAssert
import org.assertj.core.data.TemporalUnitOffset
import org.eclipse.tractusx.bpdm.orchestrator.config.StateMachineConfigProperties
import org.eclipse.tractusx.bpdm.orchestrator.config.TaskConfigProperties
import org.eclipse.tractusx.bpdm.test.containers.PostgreSQLContextInitializer
import org.eclipse.tractusx.bpdm.test.testdata.orchestrator.BusinessPartnerTestDataFactory
import org.eclipse.tractusx.bpdm.test.testdata.orchestrator.OrchestratorRequestFactoryCommon
import org.eclipse.tractusx.bpdm.test.util.DbTestHelpers
import org.eclipse.tractusx.orchestrator.api.client.OrchestrationApiClient
import org.eclipse.tractusx.orchestrator.api.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.temporal.ChronoUnit

val WITHIN_ALLOWED_TIME_OFFSET: TemporalUnitOffset = within(1, ChronoUnit.SECONDS)

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "bpdm.security.enabled=false",
        "bpdm.api.upsert-limit=3",
        "bpdm.task.timeoutCheckCron=* * * * * ?",       // check every sec
        "bpdm.task.taskPendingTimeout=3s",
        "bpdm.task.taskRetentionTimeout=5s"
    ]
)
@ContextConfiguration(initializers = [PostgreSQLContextInitializer::class])
class GoldenRecordTaskControllerIT @Autowired constructor(
    private val orchestratorClient: OrchestrationApiClient,
    private val taskConfigProperties: TaskConfigProperties,
    private val dbTestHelpers: DbTestHelpers,
    private val stateMachineConfigProperties: StateMachineConfigProperties
) {

    private val testDataFactory = BusinessPartnerTestDataFactory(OrchestratorRequestFactoryCommon())
    private val defaultBusinessPartner1 = testDataFactory.createFullBusinessPartner("BP1")
    private val defaultBusinessPartner2 = testDataFactory.createFullBusinessPartner("BP2")

    @BeforeEach
    fun cleanUp() {
        dbTestHelpers.truncateDbTables()
    }

    @ParameterizedTest
    @EnumSource(TaskMode::class)
    fun `wait for task pending and retention timeout`(taskMode: TaskMode) {
        // create tasks
        val createdTasks = createTasksWithoutRecordId(taskMode).createdTasks
        val taskIds = createdTasks.map { it.toTaskSearchIdentity() }

        // check for state Pending
        checkStateForAllTasks(taskIds) {
            assertThat(it.resultState).isEqualTo(ResultState.Pending)
        }

        // wait for 1/2 pending time -> should still be pending
        Thread.sleep(taskConfigProperties.taskPendingTimeout.dividedBy(2).toMillis())
        checkStateForAllTasks(taskIds) {
            assertThat(it.resultState).isEqualTo(ResultState.Pending)
        }

        // wait for another 1/2 pending time plus 1sec -> should be in state Error / Timeout
        Thread.sleep(taskConfigProperties.taskPendingTimeout.dividedBy(2).plusSeconds(1).toMillis())
        checkStateForAllTasks(taskIds) {
            assertThat(it.resultState).isEqualTo(ResultState.Error)
            assertThat(it.errors.first().type).isEqualTo(TaskErrorType.Timeout)
        }

        // wait for 1/2 retention time -> should still be in state Error / Timeout
        Thread.sleep(taskConfigProperties.taskRetentionTimeout.dividedBy(2).toMillis())
        checkStateForAllTasks(taskIds) {
            assertThat(it.resultState).isEqualTo(ResultState.Error)
        }

        // wait for 1/2 retention time plus 1sec -> should be removed now
        Thread.sleep(taskConfigProperties.taskRetentionTimeout.dividedBy(2).plusSeconds(1).toMillis())
        val foundTasks = searchTaskStates(taskIds).tasks
        assertThat(foundTasks.size).isZero()
    }

    @ParameterizedTest
    @EnumSource(TaskMode::class)
    fun `wait for task retention timeout after success`(taskMode: TaskMode) {
        // create single task in UpdateFromPool mode (only one step)
        val createdTasks = createTasksWithoutRecordId(taskMode, listOf(defaultBusinessPartner1)).createdTasks
        val createdTask = createdTasks.single()
        val taskId = createdTask.taskId

        val allSteps = stateMachineConfigProperties.modeSteps[taskMode]!!
        allSteps.forEach { step ->
            reserveTasks(step).reservedTasks.single()
            // resolve with success
            val cleaningResult = TaskStepResultEntryDto(
                taskId = taskId,
                businessPartner = createdTask.businessPartnerResult
            )
            resolveTasks(step, listOf(cleaningResult))
        }

        // should be in state Success now
        createdTasks.searchTaskStates(listOf(taskId)).tasks.forEach {
            assertThat(it.processingState.resultState).isEqualTo(ResultState.Success)
        }

        // wait for 1/2 retention time -> should still be in state Success
        Thread.sleep(taskConfigProperties.taskRetentionTimeout.dividedBy(2).toMillis())
        createdTasks.searchTaskStates(listOf(taskId)).tasks.forEach {
            assertThat(it.processingState.resultState).isEqualTo(ResultState.Success)
        }

        // wait for 1/2 retention time -> should still be removed
        Thread.sleep(taskConfigProperties.taskRetentionTimeout.dividedBy(2).plusSeconds(1).toMillis())
        val foundTasks = createdTasks.searchTaskStates(listOf(taskId)).tasks
        assertThat(foundTasks.size).isZero()
    }

    @ParameterizedTest
    @EnumSource(TaskMode::class)
    fun `wait for task retention timeout after error`(taskMode: TaskMode) {
        // create single task
        val createdTasks = createTasksWithoutRecordId(taskMode, listOf(defaultBusinessPartner1)).createdTasks
        val firstStep = stateMachineConfigProperties.modeSteps[taskMode]!!.first()

        // reserve task
        val reservedTask = reserveTasks(firstStep).reservedTasks.single()
        val taskId = reservedTask.taskId
        val searchIdentity = reservedTask.toTaskSearchIdentity(createdTasks)

        // resolve with error
        val cleaningResult = TaskStepResultEntryDto(
            taskId = taskId,
            businessPartner = reservedTask.businessPartner,
            errors = listOf(TaskErrorDto(TaskErrorType.Unspecified, "Unfortunate event"))
        )
        resolveTasks(firstStep, listOf(cleaningResult))

        // should be in state Success now
        checkStateForAllTasks(listOf(searchIdentity)) {
            assertThat(it.resultState).isEqualTo(ResultState.Error)
        }

        // wait for 1/2 retention time -> should still be in state Success
        Thread.sleep(taskConfigProperties.taskRetentionTimeout.dividedBy(2).toMillis())
        checkStateForAllTasks(listOf(searchIdentity)) {
            assertThat(it.resultState).isEqualTo(ResultState.Error)
        }

        // wait for 1/2 retention time -> should still be removed
        Thread.sleep(taskConfigProperties.taskRetentionTimeout.dividedBy(2).plusSeconds(1).toMillis())
        val foundTasks = searchTaskStates(listOf(searchIdentity)).tasks
        assertThat(foundTasks.size).isZero()
    }


    private fun createTasks(mode: TaskMode,
                            entries: List<TaskCreateRequestEntry>? = null
    ): TaskCreateResponse{
        val resolvedEntries = entries ?: listOf(defaultBusinessPartner1, defaultBusinessPartner2).map { bp -> TaskCreateRequestEntry(null, bp) }
        return orchestratorClient.goldenRecordTasks.createTasks(TaskCreateRequest(mode = mode, requests = resolvedEntries))
    }

    private fun createTasksWithoutRecordId(mode: TaskMode, businessPartners: List<BusinessPartner>? = null): TaskCreateResponse =
        createTasks(mode, (businessPartners ?: listOf(defaultBusinessPartner1, defaultBusinessPartner2)).map{ bp -> TaskCreateRequestEntry(null, bp) })

    private fun reserveTasks(step: TaskStep, amount: Int = 3) =
        orchestratorClient.goldenRecordTasks.reserveTasksForStep(
            TaskStepReservationRequest(
                step = step,
                amount = amount
            )
        )

    private fun resolveTasks(step: TaskStep, results: List<TaskStepResultEntryDto>) =
        orchestratorClient.goldenRecordTasks.resolveStepResults(
            TaskStepResultRequest(step, results)
        )

    private fun searchTaskStates(taskIds: List<TaskStateRequest.Entry>) =
        orchestratorClient.goldenRecordTasks.searchTaskStates(
            TaskStateRequest(taskIds)
        )

    private fun checkStateForAllTasks(taskIds: List<TaskStateRequest.Entry>, checkFunc: (TaskProcessingStateDto) -> Unit) {
        searchTaskStates(taskIds).tasks
            .also { assertThat(it.size).isEqualTo(taskIds.size) }
            .forEach { stateDto -> checkFunc(stateDto.processingState) }
    }

    private fun assertProcessingStateDto(processingStateDto: TaskProcessingStateDto, resultState: ResultState, step: TaskStep, stepState: StepState) {
        assertThat(processingStateDto.resultState).isEqualTo(resultState)
        assertThat(processingStateDto.step).isEqualTo(step)
        assertThat(processingStateDto.stepState).isEqualTo(stepState)
    }

    private fun assertBadRequestException(shouldRaiseThrowable: ThrowableAssert.ThrowingCallable) {
        assertThatThrownBy(shouldRaiseThrowable)
            .isInstanceOfSatisfying(WebClientResponseException::class.java) {
                assertThat(it.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
    }

    private fun TaskClientStateDto.toTaskSearchIdentity() = TaskStateRequest.Entry(taskId, recordId)

    private fun TaskStepReservationEntryDto.toTaskSearchIdentity(createdTasks: List<TaskClientStateDto>) =
        TaskStateRequest.Entry(taskId, createdTasks.find { it.taskId == taskId }!!.recordId)

    private fun List<TaskClientStateDto>.searchTaskStates(taskIds: List<String>) =
        this.searchTaskStates(taskIds.toSet())

    private fun List<TaskClientStateDto>.searchTaskStates(taskIds: Set<String>) =
            this.filter { taskIds.contains(it.taskId) }
                .map { it.toTaskSearchIdentity() }
                .let { searchTaskStates(it) }


}
