/*
 * Copyright 2023, 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.test

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.core.ProgressCallback
import io.element.android.libraries.matrix.api.core.RoomAlias
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.createroom.CreateRoomParameters
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader
import io.element.android.libraries.matrix.api.media.MediaPreviewService
import io.element.android.libraries.matrix.api.notification.NotificationService
import io.element.android.libraries.matrix.api.notificationsettings.NotificationSettingsService
import io.element.android.libraries.matrix.api.oidc.AccountManagementAction
import io.element.android.libraries.matrix.api.pusher.PushersService
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.NotJoinedRoom
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.RoomMembershipObserver
import io.element.android.libraries.matrix.api.room.alias.ResolvedRoomAlias
import io.element.android.libraries.matrix.api.roomdirectory.RoomDirectoryService
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.sync.SlidingSyncVersion
import io.element.android.libraries.matrix.api.user.MatrixSearchUserResults
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.libraries.matrix.test.media.FakeMatrixMediaLoader
import io.element.android.libraries.matrix.test.media.FakeMediaPreviewService
import io.element.android.libraries.matrix.test.notification.FakeNotificationService
import io.element.android.libraries.matrix.test.notificationsettings.FakeNotificationSettingsService
import io.element.android.libraries.matrix.test.pushers.FakePushersService
import io.element.android.libraries.matrix.test.roomdirectory.FakeRoomDirectoryService
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.libraries.matrix.test.sync.FakeSyncService
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.simulateLongTask
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import java.util.Optional

class FakeMatrixClient(
    override val sessionId: SessionId = A_SESSION_ID,
    override val deviceId: DeviceId = A_DEVICE_ID,
    override val sessionCoroutineScope: CoroutineScope = TestScope(),
    private val userDisplayName: String? = A_USER_NAME,
    private val userAvatarUrl: String? = AN_AVATAR_URL,
    override val roomListService: RoomListService = FakeRoomListService(),
    override val mediaLoader: MatrixMediaLoader = FakeMatrixMediaLoader(),
    private val sessionVerificationService: FakeSessionVerificationService = FakeSessionVerificationService(),
    private val pushersService: FakePushersService = FakePushersService(),
    private val notificationService: FakeNotificationService = FakeNotificationService(),
    private val notificationSettingsService: FakeNotificationSettingsService = FakeNotificationSettingsService(),
    private val syncService: FakeSyncService = FakeSyncService(),
    private val encryptionService: FakeEncryptionService = FakeEncryptionService(),
    private val roomDirectoryService: RoomDirectoryService = FakeRoomDirectoryService(),
    private val mediaPreviewService: MediaPreviewService = FakeMediaPreviewService(),
    private val accountManagementUrlResult: (AccountManagementAction?) -> Result<String?> = { lambdaError() },
    private val resolveRoomAliasResult: (RoomAlias) -> Result<Optional<ResolvedRoomAlias>> = {
        Result.success(
            Optional.of(ResolvedRoomAlias(A_ROOM_ID, emptyList()))
        )
    },
    private val getNotJoinedRoomResult: (RoomIdOrAlias, List<String>) -> Result<NotJoinedRoom> = { _, _ -> lambdaError() },
    private val clearCacheLambda: () -> Unit = { lambdaError() },
    private val userIdServerNameLambda: () -> String = { lambdaError() },
    private val getUrlLambda: (String) -> Result<String> = { lambdaError() },
    private val canDeactivateAccountResult: () -> Boolean = { lambdaError() },
    private val deactivateAccountResult: (String, Boolean) -> Result<Unit> = { _, _ -> lambdaError() },
    private val currentSlidingSyncVersionLambda: () -> Result<SlidingSyncVersion> = { lambdaError() },
    private val availableSlidingSyncVersionsLambda: () -> Result<List<SlidingSyncVersion>> = { lambdaError() },
    private val ignoreUserResult: (UserId) -> Result<Unit> = { lambdaError() },
    private var unIgnoreUserResult: (UserId) -> Result<Unit> = { Result.success(Unit) },
    private val canReportRoomLambda: () -> Boolean = { false },
    private val isLivekitRtcSupportedLambda: () -> Boolean = { false },
    override val ignoredUsersFlow: StateFlow<ImmutableList<UserId>> = MutableStateFlow(persistentListOf()),
) : MatrixClient {
    var setDisplayNameCalled: Boolean = false
        private set
    var uploadAvatarCalled: Boolean = false
        private set
    var removeAvatarCalled: Boolean = false
        private set

    private val _userProfile: MutableStateFlow<MatrixUser> = MutableStateFlow(MatrixUser(sessionId, userDisplayName, userAvatarUrl))
    override val userProfile: StateFlow<MatrixUser> = _userProfile

    private var createRoomResult: Result<RoomId> = Result.success(A_ROOM_ID)
    private var createDmResult: Result<RoomId> = Result.success(A_ROOM_ID)
    private var findDmResult: Result<RoomId?> = Result.success(A_ROOM_ID)
    private val getRoomResults = mutableMapOf<RoomId, BaseRoom>()
    private val searchUserResults = mutableMapOf<String, Result<MatrixSearchUserResults>>()
    private val getProfileResults = mutableMapOf<UserId, Result<MatrixUser>>()
    private var uploadMediaResult: Result<String> = Result.success(AN_AVATAR_URL)
    private var setDisplayNameResult: Result<Unit> = Result.success(Unit)
    private var uploadAvatarResult: Result<Unit> = Result.success(Unit)
    private var removeAvatarResult: Result<Unit> = Result.success(Unit)
    var joinRoomLambda: (RoomId) -> Result<RoomInfo?> = {
        Result.success(null)
    }
    var joinRoomByIdOrAliasLambda: (RoomIdOrAlias, List<String>) -> Result<RoomInfo?> = { _, _ ->
        Result.success(null)
    }
    var knockRoomLambda: (RoomIdOrAlias, String, List<String>) -> Result<RoomInfo?> = { _, _, _ ->
        Result.success(null)
    }
    var getRoomInfoFlowLambda = { _: RoomId ->
        flowOf<Optional<RoomInfo>>(Optional.empty())
    }
    var logoutLambda: (Boolean, Boolean) -> Unit = { _, _ -> }

    override suspend fun getRoom(roomId: RoomId): BaseRoom? {
        return getRoomResults[roomId]
    }

    override suspend fun getJoinedRoom(roomId: RoomId): JoinedRoom? {
        return getRoomResults[roomId] as? JoinedRoom
    }

    override suspend fun findDM(userId: UserId): Result<RoomId?> {
        return findDmResult
    }

    override suspend fun ignoreUser(userId: UserId): Result<Unit> = simulateLongTask {
        return ignoreUserResult(userId)
    }

    override suspend fun unignoreUser(userId: UserId): Result<Unit> = simulateLongTask {
        return unIgnoreUserResult(userId)
    }

    override suspend fun createRoom(createRoomParams: CreateRoomParameters): Result<RoomId> = simulateLongTask {
        return createRoomResult
    }

    override suspend fun createDM(userId: UserId): Result<RoomId> = simulateLongTask {
        return createDmResult
    }

    override suspend fun getProfile(userId: UserId): Result<MatrixUser> {
        return getProfileResults[userId] ?: Result.failure(IllegalStateException("No profile found for $userId"))
    }

    override suspend fun searchUsers(searchTerm: String, limit: Long): Result<MatrixSearchUserResults> {
        return searchUserResults[searchTerm] ?: Result.failure(IllegalStateException("No response defined for $searchTerm"))
    }

    override fun syncService() = syncService

    override fun roomDirectoryService() = roomDirectoryService

    override suspend fun getCacheSize(): Long {
        return 0
    }

    override suspend fun clearCache() = simulateLongTask {
        clearCacheLambda()
    }

    override suspend fun logout(userInitiated: Boolean, ignoreSdkError: Boolean) = simulateLongTask {
        logoutLambda(ignoreSdkError, userInitiated)
    }

    override fun canDeactivateAccount() = canDeactivateAccountResult()

    override suspend fun deactivateAccount(password: String, eraseData: Boolean): Result<Unit> = simulateLongTask {
        deactivateAccountResult(password, eraseData)
    }

    override suspend fun getUserProfile(): Result<MatrixUser> = simulateLongTask {
        val result = getProfileResults[sessionId]?.getOrNull() ?: MatrixUser(sessionId, userDisplayName, userAvatarUrl)
        _userProfile.tryEmit(result)
        return Result.success(result)
    }

    override suspend fun getAccountManagementUrl(action: AccountManagementAction?): Result<String?> = simulateLongTask {
        accountManagementUrlResult(action)
    }

    override suspend fun uploadMedia(
        mimeType: String,
        data: ByteArray,
        progressCallback: ProgressCallback?
    ): Result<String> {
        return uploadMediaResult
    }

    override suspend fun setDisplayName(displayName: String): Result<Unit> = simulateLongTask {
        setDisplayNameCalled = true
        return setDisplayNameResult
    }

    override suspend fun uploadAvatar(mimeType: String, data: ByteArray): Result<Unit> = simulateLongTask {
        uploadAvatarCalled = true
        return uploadAvatarResult
    }

    override suspend fun removeAvatar(): Result<Unit> = simulateLongTask {
        removeAvatarCalled = true
        return removeAvatarResult
    }

    override suspend fun joinRoom(roomId: RoomId): Result<RoomInfo?> = joinRoomLambda(roomId)

    override suspend fun joinRoomByIdOrAlias(roomIdOrAlias: RoomIdOrAlias, serverNames: List<String>): Result<RoomInfo?> {
        return joinRoomByIdOrAliasLambda(roomIdOrAlias, serverNames)
    }

    override suspend fun knockRoom(roomIdOrAlias: RoomIdOrAlias, message: String, serverNames: List<String>): Result<RoomInfo?> {
        return knockRoomLambda(roomIdOrAlias, message, serverNames)
    }

    override fun sessionVerificationService(): SessionVerificationService = sessionVerificationService

    override fun pushersService(): PushersService = pushersService

    override fun notificationService(): NotificationService = notificationService
    override fun notificationSettingsService(): NotificationSettingsService = notificationSettingsService
    override fun encryptionService(): EncryptionService = encryptionService
    override fun mediaPreviewService(): MediaPreviewService = mediaPreviewService

    override fun roomMembershipObserver(): RoomMembershipObserver {
        return RoomMembershipObserver()
    }

    // Mocks

    fun givenCreateRoomResult(result: Result<RoomId>) {
        createRoomResult = result
    }

    fun givenCreateDmResult(result: Result<RoomId>) {
        createDmResult = result
    }

    fun givenFindDmResult(result: Result<RoomId?>) {
        findDmResult = result
    }

    fun givenGetRoomResult(roomId: RoomId, result: BaseRoom?) {
        if (result == null) {
            getRoomResults.remove(roomId)
        } else {
            getRoomResults[roomId] = result
        }
    }

    fun givenSearchUsersResult(searchTerm: String, result: Result<MatrixSearchUserResults>) {
        searchUserResults[searchTerm] = result
    }

    fun givenGetProfileResult(userId: UserId, result: Result<MatrixUser>) {
        getProfileResults[userId] = result
    }

    fun givenUploadMediaResult(result: Result<String>) {
        uploadMediaResult = result
    }

    fun givenSetDisplayNameResult(result: Result<Unit>) {
        setDisplayNameResult = result
    }

    fun givenUploadAvatarResult(result: Result<Unit>) {
        uploadAvatarResult = result
    }

    fun givenRemoveAvatarResult(result: Result<Unit>) {
        removeAvatarResult = result
    }

    private val visitedRoomsId: MutableList<RoomId> = mutableListOf()

    override suspend fun trackRecentlyVisitedRoom(roomId: RoomId): Result<Unit> {
        visitedRoomsId.removeAll { it == roomId }
        visitedRoomsId.add(0, roomId)
        return Result.success(Unit)
    }

    override suspend fun resolveRoomAlias(roomAlias: RoomAlias): Result<Optional<ResolvedRoomAlias>> = simulateLongTask {
        resolveRoomAliasResult(roomAlias)
    }

    override suspend fun getRoomPreview(roomIdOrAlias: RoomIdOrAlias, serverNames: List<String>): Result<NotJoinedRoom> = simulateLongTask {
        getNotJoinedRoomResult(roomIdOrAlias, serverNames)
    }

    override suspend fun getRecentlyVisitedRooms(): Result<List<RoomId>> {
        return Result.success(visitedRoomsId)
    }

    override fun getRoomInfoFlow(roomId: RoomId) = getRoomInfoFlowLambda(roomId)

    var setAllSendQueuesEnabledLambda = lambdaRecorder(ensureNeverCalled = true) { _: Boolean ->
        // no-op
    }

    override suspend fun setAllSendQueuesEnabled(enabled: Boolean) = setAllSendQueuesEnabledLambda(enabled)

    var sendQueueDisabledFlow = emptyFlow<RoomId>()
    override fun sendQueueDisabledFlow(): Flow<RoomId> = sendQueueDisabledFlow

    override fun userIdServerName(): String {
        return userIdServerNameLambda()
    }

    override suspend fun getUrl(url: String): Result<String> {
        return getUrlLambda(url)
    }

    override suspend fun currentSlidingSyncVersion(): Result<SlidingSyncVersion> {
        return currentSlidingSyncVersionLambda()
    }

    override suspend fun availableSlidingSyncVersions(): Result<List<SlidingSyncVersion>> {
        return availableSlidingSyncVersionsLambda()
    }

    override suspend fun canReportRoom(): Boolean {
        return canReportRoomLambda()
    }

    override suspend fun isLivekitRtcSupported(): Boolean {
        return isLivekitRtcSupportedLambda()
    }
}
