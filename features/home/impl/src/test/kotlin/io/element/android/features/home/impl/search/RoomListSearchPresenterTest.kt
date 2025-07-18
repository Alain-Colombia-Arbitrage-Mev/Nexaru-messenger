/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.datasource.aRoomListRoomSummaryFactory
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.eventformatter.test.FakeRoomLastMessageFormatter
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomListSearchPresenterTest {
    @Test
    fun `present - initial state`() = runTest {
        val presenter = createRoomListSearchPresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().let { state ->
                assertThat(state.isSearchActive).isFalse()
                assertThat(state.query).isEmpty()
                assertThat(state.results).isEmpty()
            }
        }
    }

    @Test
    fun `present - toggle search visibility`() = runTest {
        val presenter = createRoomListSearchPresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().let { state ->
                assertThat(state.isSearchActive).isFalse()
                state.eventSink(RoomListSearchEvents.ToggleSearchVisibility)
            }
            awaitItem().let { state ->
                assertThat(state.isSearchActive).isTrue()
                state.eventSink(RoomListSearchEvents.ToggleSearchVisibility)
            }
            awaitItem().let { state ->
                assertThat(state.isSearchActive).isFalse()
            }
        }
    }

    @Test
    fun `present - query search changes`() = runTest {
        val roomListService = FakeRoomListService()
        val presenter = createRoomListSearchPresenter(roomListService)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().let { state ->
                assertThat(
                    roomListService.allRooms.currentFilter.value
                ).isEqualTo(
                    RoomListFilter.None
                )
                state.eventSink(RoomListSearchEvents.QueryChanged("Search"))
            }
            awaitItem().let { state ->
                assertThat(state.query).isEqualTo("Search")
                assertThat(
                    roomListService.allRooms.currentFilter.value
                ).isEqualTo(
                    RoomListFilter.NormalizedMatchRoomName("Search")
                )
                state.eventSink(RoomListSearchEvents.ClearQuery)
            }
            awaitItem().let { state ->
                assertThat(state.query).isEmpty()
                assertThat(
                    roomListService.allRooms.currentFilter.value
                ).isEqualTo(
                    RoomListFilter.None
                )
            }
        }
    }

    @Test
    fun `present - room list changes`() = runTest {
        val roomListService = FakeRoomListService()
        val presenter = createRoomListSearchPresenter(roomListService)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().let { state ->
                assertThat(state.results).isEmpty()
            }
            roomListService.postAllRooms(
                listOf(aRoomSummary())
            )
            awaitItem().let { state ->
                assertThat(state.results).hasSize(1)
            }
            roomListService.postAllRooms(emptyList())
            awaitItem().let { state ->
                assertThat(state.results).isEmpty()
            }
        }
    }
}

fun TestScope.createRoomListSearchPresenter(
    roomListService: RoomListService = FakeRoomListService(),
): RoomListSearchPresenter {
    return RoomListSearchPresenter(
        dataSource = RoomListSearchDataSource(
            roomListService = roomListService,
            roomSummaryFactory = aRoomListRoomSummaryFactory(
                dateFormatter = FakeDateFormatter(),
                roomLastMessageFormatter = FakeRoomLastMessageFormatter(),
            ),
            coroutineDispatchers = testCoroutineDispatchers(),
        ),
    )
}
