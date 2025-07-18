/*
 * Copyright 2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.model

/**
 * Represents the type of display for a room list item.
 */
enum class RoomSummaryDisplayType {
    PLACEHOLDER,
    ROOM,
    INVITE,
    KNOCKED,
}
