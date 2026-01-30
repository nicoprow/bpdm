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

package org.eclipse.tractusx.bpdm.test.testdata.orchestrator

import org.eclipse.tractusx.orchestrator.api.model.BusinessPartner
import org.eclipse.tractusx.orchestrator.api.model.TaskCreateRequest
import org.eclipse.tractusx.orchestrator.api.model.TaskCreateRequestEntry
import org.eclipse.tractusx.orchestrator.api.model.TaskMode
import java.util.*

class OrchestratorRequestFactoryV7(
    private val businessPartnerTestDataFactory: BusinessPartnerTestDataFactory
) {

    fun buildTaskCreateSingle(mode: TaskMode, seed: String): TaskCreateRequest{
        return TaskCreateRequest(
            mode = mode,
            listOf(buildTaskCreateEntry(seed))
        )
    }

    fun buildTaskCreateEntry(seed: String): TaskCreateRequestEntry {
        return TaskCreateRequestEntry(
            recordId = UUID.randomUUID().toString(),
            businessPartner = buildAdditionalAddressOfSiteBusinessPartner(seed)
        )
    }

    fun buildAdditionalAddressOfSiteBusinessPartner(seed: String): BusinessPartner{
        return businessPartnerTestDataFactory.createFullBusinessPartner(seed)
    }

}